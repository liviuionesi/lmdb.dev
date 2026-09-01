package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.lmdb.shared.exception.ServiceUnavailableException;
import dev.lmdb.shared.exception.ValidationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.vosk.Model;

/**
 * Unit tests for {@link SpeechToTextService}'s audio-handling, language-selection, and
 * model-availability logic (#68, bilingual per #200/#212). Doesn't exercise real Vosk transcription
 * — that needs the actual English/German models downloaded
 * (infrastructure/scripts/download-vosk-model.sh), which this suite deliberately doesn't depend on,
 * mirroring how #48 split ai-service's live-Ollama verification out of its unit tests. What IS
 * tested here is deterministic and needs no external model: bad-upload rejection, language
 * validation/defaulting, and graceful degradation — per language — when a model is absent.
 */
@DisplayName("SpeechToTextService (audio validation + language selection + model-availability)")
class SpeechToTextServiceTest {

  private static final String MISSING_EN_MODEL_PATH = "/nonexistent/vosk-model-en-path";
  private static final String MISSING_DE_MODEL_PATH = "/nonexistent/vosk-model-de-path";

  /**
   * A non-audio upload must be rejected before the service ever tries to load the (expensive,
   * possibly absent) Vosk model — verified by using an obviously-missing model path and confirming
   * the failure is still {@link ValidationException}, not {@link ServiceUnavailableException}.
   *
   * <p>Given a multipart upload whose bytes aren't a decodable audio container, when {@code
   * transcribe} is called, then it fails fast with a 400-mapped {@link ValidationException} instead
   * of attempting to load the model.
   */
  @Test
  @DisplayName("rejects a non-audio upload before touching the model")
  void transcribeRejectsNonAudioUploadWithoutLoadingModel() {
    SpeechToTextService service = newService();
    MockMultipartFile notAudio =
        new MockMultipartFile(
            "audio", "clip.wav", "audio/wav", "this is plain text, not a WAV file".getBytes());

    assertThatThrownBy(() -> service.transcribe(notAudio, "en"))
        .isInstanceOf(ValidationException.class);
  }

  /**
   * Given an unsupported language code, when {@code transcribe} is called with an otherwise-valid
   * audio upload, then it fails fast with {@link ValidationException} rather than attempting to
   * load any model — mirrors the non-audio-upload fast-fail above, but for the language parameter.
   */
  @Test
  @DisplayName("rejects an unsupported language before touching any model")
  void transcribeRejectsUnsupportedLanguage() throws IOException {
    SpeechToTextService service = newService();
    MockMultipartFile validWav =
        new MockMultipartFile("audio", "clip.wav", "audio/wav", silentWav());

    assertThatThrownBy(() -> service.transcribe(validWav, "fr"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("fr");
  }

  /**
   * Given no language parameter (the HTTP layer's {@code null} when the caller omits it), when a
   * valid audio upload is transcribed, then the service defaults to English — verified via the
   * missing-model failure naming the English model path specifically, proving English (not German,
   * not a third default) is what was actually selected.
   */
  @Test
  @DisplayName("defaults to English when the language parameter is omitted (null)")
  void transcribeDefaultsToEnglishWhenLanguageOmitted() throws IOException {
    SpeechToTextService service = newService();
    MockMultipartFile validWav =
        new MockMultipartFile("audio", "clip.wav", "audio/wav", silentWav());

    assertThatThrownBy(() -> service.transcribe(validWav, null))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessageContaining(MISSING_EN_MODEL_PATH);
  }

  /**
   * Given a language code in a different case ({@code "DE"}), when transcribed, then it's
   * normalized to the same German model as lowercase {@code "de"} — verified via the missing-model
   * failure naming the German model path, not rejected as unsupported.
   */
  @Test
  @DisplayName("normalizes a mixed-case German language code before model selection")
  void transcribeNormalizesGermanLanguageCase() throws IOException {
    SpeechToTextService service = newService();
    MockMultipartFile validWav =
        new MockMultipartFile("audio", "clip.wav", "audio/wav", silentWav());

    assertThatThrownBy(() -> service.transcribe(validWav, "DE"))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessageContaining(MISSING_DE_MODEL_PATH);
  }

  /**
   * Same as above, for English — checked independently of the German case because {@code
   * normalizeLanguage}'s case-folding ({@code toLowerCase(Locale.ROOT)}) applies uniformly, but the
   * {@code null}/blank defaulting path returns {@link SpeechToTextService}'s default constant
   * directly without going through case-folding at all; only an explicit {@code "EN"} actually
   * exercises the lowercase branch for English, so the default-path test above can't stand in for
   * it.
   *
   * <p>Given a language code in a different case ({@code "EN"}), when transcribed, then it's
   * normalized to the same English model as lowercase {@code "en"} — verified via the missing-model
   * failure naming the English model path, not rejected as unsupported.
   */
  @Test
  @DisplayName("normalizes a mixed-case English language code before model selection")
  void transcribeNormalizesEnglishLanguageCase() throws IOException {
    SpeechToTextService service = newService();
    MockMultipartFile validWav =
        new MockMultipartFile("audio", "clip.wav", "audio/wav", silentWav());

    assertThatThrownBy(() -> service.transcribe(validWav, "EN"))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessageContaining(MISSING_EN_MODEL_PATH);
  }

  /**
   * A well-formed audio upload against a missing model directory must degrade to 503, not crash the
   * request thread — the same contract {@link dev.lmdb.ai.controller.GlobalExceptionHandler}
   * already gives an unreachable Ollama.
   *
   * <p>Given a valid WAV upload, when the configured English model path doesn't exist, then {@code
   * transcribe} fails with {@link ServiceUnavailableException} rather than an unchecked I/O error.
   */
  @Test
  @DisplayName(
      "degrades to ServiceUnavailableException when the English model isn't downloaded yet")
  void transcribeDegradesWhenEnglishModelMissing() throws IOException {
    SpeechToTextService service = newService();
    MockMultipartFile validWav =
        new MockMultipartFile("audio", "clip.wav", "audio/wav", silentWav());

    assertThatThrownBy(() -> service.transcribe(validWav, "en"))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessageContaining("model not found");
  }

  /**
   * Same as above, for German — verified independently to prove German has its own model-loading
   * path, not a fallback onto English's.
   *
   * <p>Given a valid WAV upload, when the configured German model path doesn't exist, then {@code
   * transcribe} fails with {@link ServiceUnavailableException} rather than an unchecked I/O error.
   */
  @Test
  @DisplayName("degrades to ServiceUnavailableException when the German model isn't downloaded yet")
  void transcribeDegradesWhenGermanModelMissing() throws IOException {
    SpeechToTextService service = newService();
    MockMultipartFile validWav =
        new MockMultipartFile("audio", "clip.wav", "audio/wav", silentWav());

    assertThatThrownBy(() -> service.transcribe(validWav, "de"))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessageContaining("model not found");
  }

  /**
   * Proves the two languages' model-loading *failure state* isn't shared through one field:
   * requesting English first (which fails, since its path is missing) must not affect a later
   * German request — each failure names its own language's path, not the other's. This would catch
   * a bug where the two languages' configured paths got swapped, or where a single field/lock was
   * reused instead of one per language. It does NOT prove the stronger "requesting English never
   * even attempts to load German" claim from this class's Javadoc — both configured paths are
   * broken here, so an (incorrect) eager-load-both implementation would produce the same two
   * per-language failure messages this test checks for. That stronger guarantee instead follows
   * from {@link SpeechToTextService#transcribe} only ever calling {@code getOrLoadModel} once, with
   * the single resolved language — visible by inspection, not exercisable without a way to observe
   * or mock the model-loading step itself (no seam exists for that below the {@link org.vosk.Model}
   * constructor).
   *
   * <p>Given English is requested and fails, when German is requested next on the same service
   * instance, then German's failure still names German's own model path, not English's.
   */
  @Test
  @DisplayName(
      "keeps English and German model-loading failures independent (one failing doesn't taint the"
          + " other)")
  void transcribeKeepsLanguagesIndependentAfterOneFails() throws IOException {
    SpeechToTextService service = newService();
    MockMultipartFile validWav =
        new MockMultipartFile("audio", "clip.wav", "audio/wav", silentWav());

    assertThatThrownBy(() -> service.transcribe(validWav, "en"))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessageContaining(MISSING_EN_MODEL_PATH);
    assertThatThrownBy(() -> service.transcribe(validWav, "de"))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessageContaining(MISSING_DE_MODEL_PATH)
        .hasMessageNotContaining(MISSING_EN_MODEL_PATH);
  }

  /**
   * Given the service has already been destroyed, when {@code transcribe} is called with a valid
   * audio file, then it throws {@link ServiceUnavailableException} with a shutdown message rather
   * than crashing with an NPE or SIGSEGV from a freed native handle. Verifies the {@link
   * java.util.concurrent.locks.ReadWriteLock} in {@link SpeechToTextService} correctly prevents
   * use-after-free.
   */
  @Test
  @DisplayName("transcribe after destroy() throws ServiceUnavailableException, not a crash")
  void transcribeAfterDestroyThrowsCleanError() throws IOException {
    SpeechToTextService service = newService();
    service.destroy();
    MockMultipartFile validWav =
        new MockMultipartFile("audio", "clip.wav", "audio/wav", silentWav());

    assertThatThrownBy(() -> service.transcribe(validWav, "en"))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessageContaining("shutting down");
  }

  /**
   * Every other test in this suite points at missing model directories, so {@code destroy()} is
   * otherwise only ever exercised against an empty {@code loadedModels} map — it would still pass
   * if {@code destroy()} closed only one of several loaded models, swallowed a {@code close()}
   * failure without closing the rest, or never cleared the map. No real Vosk model is available to
   * populate that map for real (see this class's Javadoc), so this test reaches into {@link
   * SpeechToTextService}'s private {@code loadedModels} field via reflection and seeds it with two
   * Mockito mocks (safe: {@link Model} isn't final and has a no-arg constructor) standing in for
   * already-loaded English and German models.
   *
   * <p>Given two languages both have a loaded model, when {@code destroy()} is called, then both
   * models are closed and the map is left empty — not just the first one found.
   */
  @Test
  @DisplayName("destroy() closes every loaded language's model, not just one")
  void destroyClosesEveryLoadedModel() throws Exception {
    // 1. Build a service and seed its private loadedModels map directly — transcribe() can't
    // reach this state here since both configured paths are missing (see class Javadoc).
    SpeechToTextService service = newService();
    Model englishModel = mock(Model.class);
    Model germanModel = mock(Model.class);
    Field loadedModelsField = SpeechToTextService.class.getDeclaredField("loadedModels");
    loadedModelsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Model> loadedModels = (Map<String, Model>) loadedModelsField.get(service);
    loadedModels.put("en", englishModel);
    loadedModels.put("de", germanModel);

    // 2. destroy() should close both, regardless of load order, and leave nothing behind.
    service.destroy();

    verify(englishModel).close();
    verify(germanModel).close();
    assertThat(loadedModels).isEmpty();
  }

  /**
   * @return a service instance pointed at two obviously-missing model directories, one per language
   *     — every test exercises validation/degradation paths only, never real Vosk transcription
   */
  private static SpeechToTextService newService() {
    return new SpeechToTextService(
        MISSING_EN_MODEL_PATH, MISSING_DE_MODEL_PATH, new ObjectMapper());
  }

  /**
   * Builds a tiny, valid WAV (0.1s of silence, mono 16-bit 16kHz) entirely in memory — enough to
   * pass {@code AudioSystem}'s format validation without needing a fixture file on disk.
   *
   * @return raw bytes of a well-formed WAV file
   */
  private static byte[] silentWav() throws IOException {
    AudioFormat format = new AudioFormat(16_000f, 16, 1, true, false);
    byte[] silence = new byte[3200]; // 0.1s at 16kHz mono 16-bit
    try (AudioInputStream audioInputStream =
        new AudioInputStream(
            new ByteArrayInputStream(silence), format, silence.length / format.getFrameSize())) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, out);
      return out.toByteArray();
    }
  }
}
