package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Checks that {@link SpeechToTextService} recognizes voice commands spoken with accents, using the
 * real Vosk models (Task #215, Story #200 AC2). {@link SpeechToTextServiceTest} does not run Vosk,
 * and {@code AiServiceIntegrationTest} leaves this question open on purpose.
 *
 * <p>The fixtures are synthetic speech, not recordings of a person. {@code
 * accent-fixtures/README.md} says where each one comes from and how to add more, and {@code
 * infrastructure/scripts/generate-accent-fixtures.sh} builds them. This test reads {@code
 * manifest.json}, so a new fixture needs no change here.
 *
 * <p><b>The test asserts an accuracy floor per language, not a perfect score.</b> A speech
 * recognizer does not hear every accent equally well. A fixture counts as recognized when the
 * transcript contains all of its keywords. Each language must reach {@link #MIN_ENGLISH_ACCURACY}
 * or {@link #MIN_GERMAN_ACCURACY}, and the failure message lists every miss with what Vosk heard.
 * If Vosk, a model or the audio conversion breaks, accuracy falls far below the floor. Measured on
 * 2026-09-20 with the models from ADR-021: English 33 of 44 (75%), German 5 of 12 (42%). The floors
 * sit below those numbers, so a model change that lowers accuracy a little does not fail the build,
 * but a real regression does.
 *
 * <p><b>Runs only when the real Vosk models are present.</b> The models are large and are not in
 * git. {@link #loadManifestAndModelsIfAvailable()} looks for both model directories, and every test
 * is skipped, not failed, when either is missing, so {@code ./gradlew build} stays green on a
 * machine without them. To run it, run {@code infrastructure/scripts/download-vosk-model.sh}, then
 * set {@code VOSK_MODEL_PATH} and {@code VOSK_MODEL_PATH_DE} to the directories it created under
 * {@code infrastructure/docker/models}.
 *
 * <p>Only speech-to-text is checked here. Passing the transcripts on to the Ollama-backed {@link
 * VoiceCommandParsingService} needs a running Ollama as well, and {@code AiServiceIntegrationTest}
 * and {@link VoiceCommandParsingServiceTest} already cover that step with a mocked model. The
 * manifest's {@code expectedCommand}, {@code expectedMode} and {@code expectedGenre} fields record
 * what each phrase should classify to, for a test that adds a live Ollama.
 */
@DisplayName("AccentedSpeechFixtureTest (real Vosk transcription, accented/dialectal fixtures)")
class AccentedSpeechFixtureTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** Lowest share of English fixtures that must be recognized. Set from a real Vosk run. */
  private static final double MIN_ENGLISH_ACCURACY = 0.65;

  /** Lowest share of German fixtures that must be recognized. Set from a real Vosk run. */
  private static final double MIN_GERMAN_ACCURACY = 0.33;

  /**
   * Same env vars and defaults {@code application.yml} configures {@link SpeechToTextService} with.
   */
  private static final String EN_MODEL_PATH =
      System.getenv().getOrDefault("VOSK_MODEL_PATH", "./models/vosk-model-en-us-0.22-lgraph");

  private static final String DE_MODEL_PATH =
      System.getenv().getOrDefault("VOSK_MODEL_PATH_DE", "./models/vosk-model-small-de-0.15");

  private static boolean modelsPresent;
  private static SpeechToTextService service;
  private static List<FixtureEntry> englishFixtures;
  private static List<FixtureEntry> germanFixtures;

  /**
   * Resolves whether both languages' Vosk models are on disk and, if so, loads the manifest and
   * builds one shared {@link SpeechToTextService} for every test below — a {@link org.vosk.Model}
   * is expensive to load, so this pays that cost once rather than per test.
   *
   * @throws IOException the manifest resource can't be read/parsed (a genuine test-setup bug, not a
   *     missing-model condition — that's handled separately via {@link #modelsPresent})
   */
  @BeforeAll
  static void loadManifestAndModelsIfAvailable() throws IOException {
    modelsPresent =
        Files.isDirectory(Path.of(EN_MODEL_PATH)) && Files.isDirectory(Path.of(DE_MODEL_PATH));
    if (!modelsPresent) {
      return;
    }
    service = new SpeechToTextService(EN_MODEL_PATH, DE_MODEL_PATH, OBJECT_MAPPER);
    List<FixtureEntry> all = readManifest();
    all.forEach(AccentedSpeechFixtureTest::requireKeywords);
    englishFixtures = all.stream().filter(f -> "en".equals(f.language())).toList();
    germanFixtures = all.stream().filter(f -> "de".equals(f.language())).toList();
    // A manifest edit that mistypes or empties a language's rows must fail loudly. With no fixtures
    // there is no accuracy to measure, and the check would prove nothing.
    if (englishFixtures.isEmpty() || germanFixtures.isEmpty()) {
      throw new IllegalStateException(
          "accent-fixtures manifest.json has no entries for language 'en' or 'de' — expected"
              + " both non-empty, found en="
              + englishFixtures.size()
              + " de="
              + germanFixtures.size());
    }
  }

  /**
   * Guards against a fixture whose {@code expectedKeywords} is empty. {@code allMatch} on an empty
   * list is always true, so that fixture would count as recognized whatever Vosk heard, including
   * nothing at all. Requiring at least two keywords also keeps one short word from matching
   * unrelated output by accident.
   *
   * @param fixture the manifest entry to validate
   * @throws IllegalStateException {@code fixture} has fewer than two expected keywords
   */
  private static void requireKeywords(FixtureEntry fixture) {
    if (fixture.expectedKeywords() == null || fixture.expectedKeywords().size() < 2) {
      throw new IllegalStateException(
          "accent-fixtures manifest.json entry '"
              + fixture.file()
              + "' needs at least 2 expectedKeywords to be a meaningful check, found: "
              + fixture.expectedKeywords());
    }
  }

  /** Frees the shared model's native handles once every test in this class has run. */
  @AfterAll
  static void tearDown() {
    if (service != null) {
      service.destroy();
    }
  }

  /**
   * Skips every test in this class, rather than failing it, when either language's Vosk model isn't
   * downloaded — see this class's Javadoc for why that's the deliberate outcome here.
   */
  @BeforeEach
  void requireModels() {
    assumeTrue(
        modelsPresent,
        "Vosk models not found at '"
            + EN_MODEL_PATH
            + "' / '"
            + DE_MODEL_PATH
            + "' — run infrastructure/scripts/download-vosk-model.sh first (needs network egress"
            + " to alphacephei.com, not available in every environment; see #229).");
  }

  /**
   * Given the English fixtures (Scottish, Canadian, Indian, Romanian- and German-accented), when
   * each is transcribed by the real English Vosk model, then at least {@link #MIN_ENGLISH_ACCURACY}
   * of them contain all the keywords {@code manifest.json} expects. A floor and not a perfect
   * score, because Vosk misses some accent and phrase pairs (see the class comment). The floor
   * still fails when the model, the language choice or the audio conversion breaks.
   */
  @Test
  @DisplayName("recognizes accented and non-native English commands at or above the floor")
  void transcribesAccentedEnglishFixtures() {
    assertRecognizedAtLeast(englishFixtures, MIN_ENGLISH_ACCURACY);
  }

  /**
   * Same as {@link #transcribesAccentedEnglishFixtures}, for the twelve German fixtures (two
   * speakers, six phrases each). It is a separate test so a failure in one language's model does
   * not hide the other's. None of the German fixtures is dialectal, because Piper has no Austrian,
   * Swiss or Bavarian voice. They check two standard-German speakers only.
   */
  @Test
  @DisplayName("recognizes standard German commands from two speakers at or above the floor")
  void transcribesAccentedGermanFixtures() {
    assertRecognizedAtLeast(germanFixtures, MIN_GERMAN_ACCURACY);
  }

  /**
   * Transcribes every fixture in a language and checks that enough of them contain all of their
   * expected keywords. A fixture that does not is a miss. Every miss goes into the failure message
   * with what Vosk heard, so a failing run shows which accents and phrases are the weak ones.
   *
   * @param fixtures the fixtures of one language
   * @param minimumAccuracy the lowest share of fixtures that must be recognized, from 0 to 1
   */
  private static void assertRecognizedAtLeast(List<FixtureEntry> fixtures, double minimumAccuracy) {
    List<String> misses = new ArrayList<>();
    for (FixtureEntry fixture : fixtures) {
      String heard = normalize(transcribe(fixture));
      if (!fixture.expectedKeywords().stream().allMatch(heard::contains)) {
        misses.add(
            "%s (%s): heard \"%s\", expected %s"
                .formatted(
                    fixture.file(), fixture.accentLabel(), heard, fixture.expectedKeywords()));
      }
    }
    int recognized = fixtures.size() - misses.size();
    assertThat((double) recognized / fixtures.size())
        .as(
            "%d of %d fixtures recognized, floor %.0f%%. Misses:%n%s",
            recognized, fixtures.size(), minimumAccuracy * 100, String.join("\n", misses))
        .isGreaterThanOrEqualTo(minimumAccuracy);
  }

  /**
   * Transcribes one fixture's audio file via the real, shared {@link SpeechToTextService}.
   *
   * @param fixture the manifest entry naming the audio file and language to transcribe
   * @return the raw (non-normalized) recognized text
   */
  private static String transcribe(FixtureEntry fixture) {
    try (InputStream audio =
        new ClassPathResource("accent-fixtures/" + fixture.file()).getInputStream()) {
      MockMultipartFile upload =
          new MockMultipartFile("audio", fixture.file(), "audio/wav", audio.readAllBytes());
      return service.transcribe(upload, fixture.language());
    } catch (IOException e) {
      throw new AssertionError("Could not read fixture " + fixture.file(), e);
    }
  }

  /**
   * Lowercases and strips punctuation Vosk sometimes doesn't emit consistently, so keyword
   * containment checks aren't tripped up by casing or a stray comma/period.
   *
   * @param transcript the raw transcript from {@link SpeechToTextService#transcribe}
   * @return the transcript, lowercased with punctuation removed
   */
  private static String normalize(String transcript) {
    return transcript.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", "");
  }

  /**
   * @return every fixture entry from {@code accent-fixtures/manifest.json} on the test classpath
   * @throws IOException the manifest resource is missing or isn't valid JSON
   */
  private static List<FixtureEntry> readManifest() throws IOException {
    try (InputStream manifest =
        new ClassPathResource("accent-fixtures/manifest.json").getInputStream()) {
      return OBJECT_MAPPER.readValue(manifest, new TypeReference<List<FixtureEntry>>() {});
    }
  }

  /**
   * One row of {@code accent-fixtures/manifest.json}. Only {@code file}, {@code language}, {@code
   * accentLabel}, and {@code expectedKeywords} are read by this test; {@code voice}, {@code
   * phrase}, {@code expectedCommand}, {@code expectedMode}, and {@code expectedGenre} are carried
   * through for documentation and for a future intent-parsing extension (see this class's Javadoc)
   * — Jackson ignores JSON fields with no matching record component only when {@code
   * FAIL_ON_UNKNOWN_PROPERTIES} is off, so those extra fields are declared here too rather than
   * relying on that.
   *
   * @param file path to the audio file, relative to {@code accent-fixtures/}
   * @param language the Vosk language code to transcribe against ({@code en}/{@code de})
   * @param voice the voice that synthesized this fixture: a Piper voice ({@code
   *     piper:<voice>:<id>})
   * @param accentLabel human-readable description of the accent/dialect (or lack thereof — see this
   *     class's Javadoc) this fixture represents
   * @param phrase the phrase spoken, i.e. the ground truth this fixture was synthesized from
   * @param expectedKeywords normalized (lowercase) words the transcript must contain to count as
   *     recognized
   * @param expectedCommand the {@link dev.lmdb.ai.dto.VoiceCommandType} this transcript should
   *     classify to, not asserted here (see class Javadoc)
   * @param expectedMode the {@link dev.lmdb.ai.dto.ThemeMode} expected when {@code expectedCommand}
   *     is {@code CHANGE_MODE}, {@code null} otherwise
   * @param expectedGenre the genre/category expected when {@code expectedCommand} is {@code
   *     CHOOSE_GENRE}, {@code null} otherwise
   */
  private record FixtureEntry(
      String file,
      String language,
      String voice,
      String accentLabel,
      String phrase,
      List<String> expectedKeywords,
      String expectedCommand,
      String expectedMode,
      String expectedGenre) {}
}
