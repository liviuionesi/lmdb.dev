package dev.lmdb.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.lmdb.shared.exception.ServiceUnavailableException;
import dev.lmdb.shared.exception.ValidationException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

/**
 * Offline speech-to-text (#68, bilingual per #200/#212). Runs entirely against local <a
 * href="https://alphacephei.com/vosk/">Vosk</a> models — no audio ever leaves this service, and
 * there is no cloud STT provider or API key (ADR-004's $0 budget).
 *
 * <p>English and German are each backed by their own {@link Model} (ADR-021 picks {@code
 * vosk-model-en-us-0.22-lgraph} and {@code vosk-model-small-de-0.15} respectively). A Vosk {@link
 * Model} is expensive to load (reads the model directory from disk) and immutable once loaded, so
 * each language's model is loaded once, lazily, on that language's first transcription request —
 * requesting English never forces German to load and vice versa, and a missing/not-yet-downloaded
 * model for one language must not prevent the other language, or ai-service's other features (chat,
 * recommendations, semantic search), from starting. Both models are kept resident once loaded
 * rather than evicted on language switch (see the memory-limit bump alongside this class in {@code
 * infrastructure/kubernetes/base/ai-service/deployment.yaml}) — ADR-021 flags that this trade-off
 * needs real combined-RSS measurement, which this environment can't do (no egress to the model
 * host); the limit bump is a conservative, unmeasured buffer pending that verification. A {@link
 * Recognizer} is NOT thread-safe, so a fresh one is created per request from the shared {@link
 * Model}.
 */
@Service
@Slf4j
public class SpeechToTextService implements DisposableBean {

  /** Vosk's English/German models are both trained for 16kHz mono PCM16 audio. */
  private static final float SAMPLE_RATE_HZ = 16_000f;

  private static final AudioFormat TARGET_FORMAT =
      new AudioFormat(SAMPLE_RATE_HZ, 16, 1, true, false);

  /** Language used when the caller omits one — preserves speech-to-text's pre-#212 behavior. */
  private static final String DEFAULT_LANGUAGE = "en";

  /** Filesystem path to each supported language's unzipped Vosk model directory. */
  private final Map<String, String> modelPathsByLanguage;

  private final ObjectMapper objectMapper;

  /** Loaded {@link Model}s, keyed by language code; absent until that language's first request. */
  private final Map<String, Model> loadedModels = new ConcurrentHashMap<>();

  /**
   * One monitor per supported language, so loading English blocks only concurrent English requests
   * — never German ones, and vice versa.
   */
  private final Map<String, Object> loadLocks;

  private volatile boolean destroyed;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  /**
   * @param enModelPath filesystem path to the unzipped English Vosk model directory
   * @param deModelPath filesystem path to the unzipped German Vosk model directory
   * @param objectMapper parses Vosk's {@code {"text": "..."}} result JSON
   */
  public SpeechToTextService(
      @Value("${speech-to-text.vosk.model-path}") String enModelPath,
      @Value("${speech-to-text.vosk.model-path-de}") String deModelPath,
      ObjectMapper objectMapper) {
    this.modelPathsByLanguage = Map.of(DEFAULT_LANGUAGE, enModelPath, "de", deModelPath);
    this.loadLocks = Map.of(DEFAULT_LANGUAGE, new Object(), "de", new Object());
    this.objectMapper = objectMapper;
  }

  /**
   * Transcribes an uploaded audio clip to text.
   *
   * @param audioFile a WAV (PCM) upload; other sample rates/channel counts are resampled to what
   *     Vosk expects
   * @param language the language to transcribe against ({@code en}/{@code de}, case-insensitive),
   *     or {@code null}/blank to default to English — matches the {@code /speech-to-text}
   *     endpoint's behavior before this parameter existed
   * @return the recognized text, empty if Vosk understood nothing
   * @throws ValidationException the upload isn't a readable audio file, or {@code language} isn't
   *     one of the supported codes
   * @throws ServiceUnavailableException the requested language's Vosk model isn't downloaded yet
   */
  public String transcribe(MultipartFile audioFile, String language) {
    // 1. Validate the (cheap) client input before paying the model-load cost — language first
    // since it needs no I/O at all, then the audio container itself.
    String normalizedLanguage = normalizeLanguage(language);
    byte[] pcm = toPcm16Mono16kHz(audioFile);

    lock.readLock().lock();
    try {
      Model loadedModel = getOrLoadModel(normalizedLanguage);
      if (loadedModel == null) {
        throw new ServiceUnavailableException("speech-to-text", "service is shutting down");
      }
      try (Recognizer recognizer = new Recognizer(loadedModel, SAMPLE_RATE_HZ)) {
        recognizer.acceptWaveForm(pcm, pcm.length);
        return extractText(recognizer.getFinalResult());
      } catch (IOException e) {
        throw new ServiceUnavailableException("speech-to-text", "recognizer failed to start", e);
      }
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Resolves and validates the caller's requested language.
   *
   * @param language the raw, possibly {@code null}/blank/mixed-case language code from the request
   * @return the normalized (lowercase) language code, defaulted to {@link #DEFAULT_LANGUAGE} when
   *     {@code language} is {@code null}/blank
   * @throws ValidationException {@code language} is non-blank but not a supported code
   */
  private String normalizeLanguage(String language) {
    if (language == null || language.isBlank()) {
      return DEFAULT_LANGUAGE;
    }
    String normalized = language.trim().toLowerCase(Locale.ROOT);
    if (!modelPathsByLanguage.containsKey(normalized)) {
      throw new ValidationException(
          "language",
          "unsupported language '"
              + language
              + "' — expected one of "
              + modelPathsByLanguage.keySet());
    }
    return normalized;
  }

  /**
   * Loads the given language's Vosk model on first use (double-checked locking, keyed per language
   * — cheap after that language's first call, and only one thread pays the disk-read cost for it).
   *
   * @param language a normalized, already-validated language code
   * @return the shared, immutable model for that language
   * @throws ServiceUnavailableException that language's model directory is missing or unreadable
   */
  private Model getOrLoadModel(String language) {
    if (destroyed) {
      return null;
    }
    Model loaded = loadedModels.get(language);
    if (loaded != null) {
      return loaded;
    }

    synchronized (loadLocks.get(language)) {
      if (destroyed) {
        return null;
      }
      Model existing = loadedModels.get(language);
      if (existing != null) {
        return existing;
      }
      String modelPath = modelPathsByLanguage.get(language);
      log.info("Loading Vosk speech-to-text model for language '{}' from {}", language, modelPath);
      LibVosk.setLogLevel(LogLevel.WARNINGS);
      try {
        Model newModel = new Model(modelPath);
        loadedModels.put(language, newModel);
        return newModel;
      } catch (IOException e) {
        throw new ServiceUnavailableException(
            "speech-to-text",
            "model not found at '"
                + modelPath
                + "' for language '"
                + language
                + "' — run infrastructure/scripts/download-vosk-model.sh first",
            e);
      }
    }
  }

  /**
   * Reads an uploaded audio file and resamples it to the mono 16kHz PCM16 format Vosk requires,
   * regardless of what format the browser sent.
   *
   * @param audioFile the raw upload
   * @return raw little-endian PCM16 samples, no WAV header
   * @throws ValidationException the upload isn't a decodable audio file
   */
  private byte[] toPcm16Mono16kHz(MultipartFile audioFile) {
    // AudioSystem needs to read-ahead and reset while sniffing the
    // format — MultipartFile's own InputStream doesn't support that.
    try (AudioInputStream sourceStream =
        AudioSystem.getAudioInputStream(new BufferedInputStream(audioFile.getInputStream()))) {
      try (AudioInputStream pcmStream =
          AudioSystem.getAudioInputStream(TARGET_FORMAT, sourceStream)) {
        return pcmStream.readAllBytes();
      }
    } catch (UnsupportedAudioFileException | IllegalArgumentException e) {
      throw new ValidationException(
          "audio", "Unsupported audio format — expected a WAV (PCM) file: " + e.getMessage());
    } catch (IOException e) {
      throw new ValidationException(
          "audio", "Could not read the uploaded audio file: " + e.getMessage());
    }
  }

  /**
   * Pulls the {@code text} field out of Vosk's JSON result.
   *
   * @param resultJson Vosk's {@code {"text": "..."}} response
   * @return the recognized text, empty if the field is missing or the JSON is malformed
   */
  private String extractText(String resultJson) {
    try {
      JsonNode node = objectMapper.readTree(resultJson);
      return node.path("text").asText("");
    } catch (IOException e) {
      log.warn("Vosk returned unparseable result JSON: {}", resultJson, e);
      return "";
    }
  }

  /**
   * Frees every loaded language's native Vosk model on shutdown — each holds an off-heap handle the
   * JVM's GC doesn't know about.
   */
  @Override
  public void destroy() {
    lock.writeLock().lock();
    try {
      destroyed = true;
      loadedModels.values().forEach(Model::close);
      loadedModels.clear();
    } finally {
      lock.writeLock().unlock();
    }
  }
}
