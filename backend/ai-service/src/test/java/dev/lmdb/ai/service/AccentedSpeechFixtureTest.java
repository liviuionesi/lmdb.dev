package dev.lmdb.ai.service;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Proves {@link SpeechToTextService} correctly transcribes accented/dialectal English and German
 * speech, not just standard-pronunciation samples (Task #215, Story #200 AC2) — the specific gap
 * {@link SpeechToTextServiceTest} deliberately leaves open (its Javadoc: "Doesn't exercise real
 * Vosk transcription") and {@code AiServiceIntegrationTest}'s {@code fullPipelineTranscribesAnd*}
 * tests explicitly punt to this class ("that verification is still open (Task #215, ADR-021)").
 *
 * <p>Fixtures are synthetic (espeak-ng/mbrola text-to-speech through genuinely regional/non-native
 * voices where available — see {@code infrastructure/scripts/generate-accent-fixtures.sh} for the
 * full accent-vs-acoustic-diversity breakdown per file), not real human recordings: this
 * environment has no microphone and no consenting speaker to record, so machine-recorded speech is
 * how "self-recorded is acceptable" (the Task's own wording) is satisfied here without any risk of
 * committing real personal audio (AC4). A human contributor with real accented recordings can drop
 * them into {@code src/test/resources/accent-fixtures/} and the matching {@code manifest.json}
 * entry (see that file's fields) — this test reads the manifest, not a hardcoded file list, so
 * nothing else changes.
 *
 * <p><b>Runs only when real Vosk models are present.</b> Same reasoning as {@code
 * FullStackJourneyIT}: there is no way to prove Vosk's own accent handling without Vosk's actual
 * models, and no autonomous-run sandbox to date has had network egress to fetch them (see #229).
 * {@link #loadManifestAndModelsIfAvailable()} probes both configured model directories once; every
 * test aborts as <em>skipped</em>, not failed, when either is missing, so {@code ./gradlew build}
 * stays green on a machine without the models while a developer who has run {@code
 * infrastructure/scripts/download-vosk-model.sh} gets the real accent-accuracy proof.
 *
 * <p>Only the speech-to-text half of Story #200 AC2's "full pipeline (STT + intent parsing)" is
 * exercised directly here — feeding these same transcripts through the real, Ollama-backed {@link
 * VoiceCommandParsingService} would require a live Ollama on top of Vosk, which is even less likely
 * to be available together than Vosk alone, and would retest ground {@code
 * AiServiceIntegrationTest}'s {@code fullPipelineTranscribesAnd*} tests and {@link
 * VoiceCommandParsingServiceTest} already cover (chaining correctness and phrasing-variance
 * tolerance, respectively, both with a mocked model). What was actually unverified — whether Vosk
 * itself gets the words right when the speaker isn't standard-pronunciation — is what the keyword
 * assertions below prove; {@code manifest.json}'s {@code expectedCommand}/{@code expectedMode}/
 * {@code expectedGenre} fields record what each transcript is expected to classify to, for a future
 * test (or a human) that also has a live Ollama to extend this with.
 */
@DisplayName("AccentedSpeechFixtureTest (real Vosk transcription, accented/dialectal fixtures)")
class AccentedSpeechFixtureTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    // A manifest edit that mistypes/empties a language's rows must fail loudly, not quietly turn
    // that language's test into a for-loop over nothing — a no-op loop still passes
    // softly.assertAll() (it recorded zero failures), which would report the accent-regression
    // proof as green while actually checking nothing. See #215 review notes.
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
   * Guards against a fixture whose {@code expectedKeywords} is empty — {@code
   * assertThat(x).contains()} with zero varargs trivially succeeds in AssertJ, so an empty list
   * would make that fixture unfalsifiable (it would "pass" no matter what Vosk transcribed,
   * including nothing at all). Requiring at least two independent keywords also keeps a single
   * short/generic word (e.g. a 3-letter substring) from accidentally matching unrelated
   * mis-transcribed output.
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
   * Given each English accent/dialect fixture, when transcribed against the real English Vosk
   * model, then the normalized transcript contains every keyword {@code manifest.json} expects for
   * that command — proving Vosk recognizes the intended command regardless of the speaker's accent,
   * not just the standard-pronunciation case {@link SpeechToTextServiceTest} already can't reach.
   * Every fixture is checked (via {@link SoftAssertions}) even after one fails, so a single bad
   * fixture doesn't hide failures in the rest.
   */
  @Test
  @DisplayName(
      "transcribes every accented/non-native English fixture to its expected command words")
  void transcribesAccentedEnglishFixtures() {
    SoftAssertions softly = new SoftAssertions();
    for (FixtureEntry fixture : englishFixtures) {
      String transcript = transcribe(fixture);
      softly
          .assertThat(normalize(transcript))
          .as("transcript for %s (%s)", fixture.file(), fixture.accentLabel())
          .contains(fixture.expectedKeywords());
    }
    softly.assertAll();
  }

  /**
   * Same as {@link #transcribesAccentedEnglishFixtures}, for German — checked independently so a
   * failure in one language's model doesn't mask the other's, mirroring how {@link
   * SpeechToTextServiceTest} checks English and German failure paths separately.
   */
  @Test
  @DisplayName(
      "transcribes every dialectal/multi-speaker German fixture to its expected command words")
  void transcribesAccentedGermanFixtures() {
    SoftAssertions softly = new SoftAssertions();
    for (FixtureEntry fixture : germanFixtures) {
      String transcript = transcribe(fixture);
      softly
          .assertThat(normalize(transcript))
          .as("transcript for %s (%s)", fixture.file(), fixture.accentLabel())
          .contains(fixture.expectedKeywords());
    }
    softly.assertAll();
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
   * @param voice the espeak-ng/mbrola voice used to synthesize this fixture
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
