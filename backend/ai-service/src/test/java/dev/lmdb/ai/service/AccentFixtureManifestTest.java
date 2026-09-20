package dev.lmdb.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Checks that {@code accent-fixtures/manifest.json} and the audio files next to it agree (Task #215
 * AC1). {@link AccentedSpeechFixtureTest} needs the real Vosk models and is skipped without them,
 * so it cannot catch a manifest entry with no audio file, or an audio file with no entry. This test
 * needs no models and always runs, in CI too.
 *
 * <p>It reads the two files only: the manifest through Jackson and the audio through the classpath.
 */
@DisplayName("AccentFixtureManifestTest (accent fixtures and manifest agree)")
class AccentFixtureManifestTest {

  private static final String ROOT = "accent-fixtures/";

  /** e.g., {@code piper:<voice>:<id>} */
  private static final List<String> SYNTHETIC_VOICE_PREFIXES = List.of("piper:");

  private static List<JsonNode> manifest;

  /**
   * Reads the manifest once for every test below.
   *
   * @throws IOException the manifest is missing or is not valid JSON
   */
  @BeforeAll
  static void readManifest() throws IOException {
    try (InputStream in = new ClassPathResource(ROOT + "manifest.json").getInputStream()) {
      JsonNode root = new ObjectMapper().readTree(in);
      manifest = new ArrayList<>();
      root.forEach(manifest::add);
    }
  }

  /**
   * Every manifest entry has an audio file, and every audio file has an entry. A fixture that is
   * listed but missing would crash the Vosk test, and one that is not listed would never be checked
   * while looking as if it were.
   *
   * @throws IOException the audio folder cannot be listed
   */
  @Test
  @DisplayName("lists every audio file, and every listed file exists")
  void manifestAndAudioFilesMatch() throws IOException {
    List<String> listed = manifest.stream().map(entry -> entry.get("file").asText()).toList();

    // 1. Find every WAV under accent-fixtures/en and accent-fixtures/de, as "en/name.wav".
    List<String> onDisk = new ArrayList<>();
    Resource[] wavs =
        new PathMatchingResourcePatternResolver().getResources("classpath:" + ROOT + "*/*.wav");
    for (Resource wav : wavs) {
      String url = wav.getURL().toString();
      onDisk.add(url.substring(url.indexOf(ROOT) + ROOT.length()));
    }

    // 2. The two lists must hold the same files.
    assertThat(listed).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(onDisk);
  }

  /**
   * Every fixture names a synthetic voice (Task #215 AC4). A recording of a real person is allowed
   * only with that person's agreement, so adding one has to be a deliberate act: whoever does it
   * adds the new {@code voice} marker to {@link #SYNTHETIC_VOICE_PREFIXES} and this test shows in
   * the review.
   */
  @Test
  @DisplayName("names a synthetic voice for every fixture")
  void everyFixtureNamesASyntheticVoice() {
    assertThat(manifest)
        .allSatisfy(
            entry -> {
              String voice = entry.get("voice").asText();
              assertThat(SYNTHETIC_VOICE_PREFIXES.stream().anyMatch(voice::startsWith))
                  .as("voice '%s' of %s is not a known synthetic voice", voice, entry.get("file"))
                  .isTrue();
            });
  }

  /**
   * The Task asks for at least 6 English and 4 German fixtures, and the Vosk test needs at least
   * two keywords per fixture to be a real check.
   */
  @Test
  @DisplayName("has at least 6 English and 4 German fixtures, each with two keywords")
  void hasEnoughFixturesWithKeywords() {
    long english = manifest.stream().filter(e -> "en".equals(e.get("language").asText())).count();
    long german = manifest.stream().filter(e -> "de".equals(e.get("language").asText())).count();

    assertThat(english).isGreaterThanOrEqualTo(6);
    assertThat(german).isGreaterThanOrEqualTo(4);
    assertThat(manifest)
        .allSatisfy(
            entry -> assertThat(entry.get("expectedKeywords").size()).isGreaterThanOrEqualTo(2));
  }
}
