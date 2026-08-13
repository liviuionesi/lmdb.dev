package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.lmdb.shared.exception.ServiceUnavailableException;
import dev.lmdb.shared.exception.ValidationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Unit tests for {@link SpeechToTextService}'s audio-handling and model-availability logic (#68).
 * Doesn't exercise real Vosk transcription — that needs the ~40MB model downloaded
 * (infrastructure/scripts/download- vosk-model.sh), which this suite deliberately doesn't depend
 * on, mirroring how #48 split ai-service's live-Ollama verification out of its unit tests. What IS
 * tested here is deterministic and needs no external model: bad-upload rejection, and graceful
 * degradation when the model is absent.
 */
@DisplayName("SpeechToTextService (audio validation + model-availability)")
class SpeechToTextServiceTest {

  private static final String MISSING_MODEL_PATH = "/nonexistent/vosk-model-path";

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
    SpeechToTextService service = new SpeechToTextService(MISSING_MODEL_PATH, new ObjectMapper());
    MockMultipartFile notAudio =
        new MockMultipartFile(
            "audio", "clip.wav", "audio/wav", "this is plain text, not a WAV file".getBytes());

    assertThatThrownBy(() -> service.transcribe(notAudio)).isInstanceOf(ValidationException.class);
  }

  /**
   * A well-formed audio upload against a missing model directory must degrade to 503, not crash the
   * request thread — the same contract {@link dev.lmdb.ai.controller.GlobalExceptionHandler}
   * already gives an unreachable Ollama.
   *
   * <p>Given a valid WAV upload, when the configured model path doesn't exist, then {@code
   * transcribe} fails with {@link ServiceUnavailableException} rather than an unchecked I/O error.
   */
  @Test
  @DisplayName("degrades to ServiceUnavailableException when the model isn't downloaded yet")
  void transcribeDegradesWhenModelMissing() throws IOException {
    SpeechToTextService service = new SpeechToTextService(MISSING_MODEL_PATH, new ObjectMapper());
    MockMultipartFile validWav =
        new MockMultipartFile("audio", "clip.wav", "audio/wav", silentWav());

    assertThatThrownBy(() -> service.transcribe(validWav))
        .isInstanceOf(ServiceUnavailableException.class)
        .hasMessageContaining("model not found");
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
