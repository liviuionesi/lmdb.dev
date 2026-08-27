package dev.lmdb.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.lmdb.shared.exception.ServiceUnavailableException;
import dev.lmdb.shared.exception.ValidationException;
import java.io.BufferedInputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
 * Offline speech-to-text (#68). Runs entirely against a local <a
 * href="https://alphacephei.com/vosk/">Vosk</a> model — no audio ever leaves this service, and
 * there is no cloud STT provider or API key (ADR-004's $0 budget).
 *
 * <p>The Vosk {@link Model} is expensive to load (reads the model directory from disk) and
 * immutable once loaded, so it's loaded once, lazily, on the first transcription request rather
 * than at startup — a missing/not-yet- downloaded model must not prevent ai-service's other
 * features (chat, recommendations, semantic search) from starting. A {@link Recognizer} is NOT
 * thread-safe, so a fresh one is created per request from the shared {@link Model}.
 */
@Service
@Slf4j
public class SpeechToTextService implements DisposableBean {

  /** Vosk's small English model is trained for 16kHz mono PCM16 audio. */
  private static final float SAMPLE_RATE_HZ = 16_000f;

  private static final AudioFormat TARGET_FORMAT =
      new AudioFormat(SAMPLE_RATE_HZ, 16, 1, true, false);

  private final String modelPath;
  private final ObjectMapper objectMapper;
  private volatile Model model;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  /**
   * @param modelPath filesystem path to an unzipped Vosk model directory
   * @param objectMapper parses Vosk's {@code {"text": "..."}} result JSON
   */
  public SpeechToTextService(
      @Value("${speech-to-text.vosk.model-path}") String modelPath, ObjectMapper objectMapper) {
    this.modelPath = modelPath;
    this.objectMapper = objectMapper;
  }

  /**
   * Transcribes an uploaded audio clip to text.
   *
   * @param audioFile a WAV (PCM) upload; other sample rates/channel counts are resampled to what
   *     Vosk expects
   * @return the recognized text, empty if Vosk understood nothing
   * @throws ValidationException the upload isn't a readable audio file
   * @throws ServiceUnavailableException the Vosk model isn't downloaded yet
   */
  public String transcribe(MultipartFile audioFile) {
    // Validate the (cheap) client input before paying the model-load cost.
    byte[] pcm = toPcm16Mono16kHz(audioFile);

    lock.readLock().lock();
    try {
      Model loadedModel = getOrLoadModel();
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
   * Loads the Vosk model on first use (double-checked locking — cheap after the first call, and
   * only one thread pays the disk-read cost).
   *
   * @return the shared, immutable model
   * @throws ServiceUnavailableException the model directory is missing or unreadable
   */
  private Model getOrLoadModel() {
    Model loaded = model;
    if (loaded != null) {
      return loaded;
    }

    synchronized (this) {
      if (model == null) {
        log.info("Loading Vosk speech-to-text model from {}", modelPath);
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        try {
          model = new Model(modelPath);
        } catch (IOException e) {
          throw new ServiceUnavailableException(
              "speech-to-text",
              "model not found at '"
                  + modelPath
                  + "' — run infrastructure/scripts/download-vosk-model.sh first",
              e);
        }
      }
      return model;
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
   * Frees the native Vosk model on shutdown — it holds an off-heap handle that the JVM's GC doesn't
   * know about.
   */
  @Override
  public void destroy() {
    lock.writeLock().lock();
    try {
      if (model != null) {
        model.close();
        model = null;
      }
    } finally {
      lock.writeLock().unlock();
    }
  }
}
