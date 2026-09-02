package dev.lmdb.shared.project;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Verifies the repository scaffolding that issues #2, #3, #4 and #5 delivered.
 *
 * <p>Those issues closed on criteria whose only evidence was a date written into the issue text
 * ("verified 2025-11-14"). A date records the past and cannot go red when the layout changes, so
 * the claims are asserted here instead and run by {@code ./gradlew test} in Backend CI.
 *
 * <p>This test reads files from the repository, not from the classpath. It resolves the repository
 * root by walking up from the working directory until it finds {@code settings.gradle}, so it does
 * not care which module Gradle runs it from.
 *
 * @author LMDB Development Team
 * @version 1.0.0
 */
class ProjectStructureTest {

  /** The nine Gradle modules the root build is expected to include. */
  private static final List<String> EXPECTED_MODULES =
      List.of(
          "api-gateway",
          "discovery-service",
          "config-service",
          "movie-service",
          "user-service",
          "actor-service",
          "ai-service",
          "media-service",
          "shared-library");

  /** Repository root, resolved once for every test in this class. */
  private static final Path ROOT = locateRepositoryRoot();

  /**
   * Walks up from the working directory until a directory containing {@code settings.gradle} is
   * found. Gradle sets the working directory to the module being tested, so the root is two levels
   * up in practice; the walk keeps this correct if the module ever moves.
   *
   * @return the repository root directory
   * @throws IllegalStateException if no ancestor directory contains {@code settings.gradle}
   */
  private static Path locateRepositoryRoot() {
    Path candidate = Path.of("").toAbsolutePath();
    while (candidate != null) {
      if (Files.exists(candidate.resolve("settings.gradle"))) {
        return candidate;
      }
      candidate = candidate.getParent();
    }
    throw new IllegalStateException("settings.gradle not found in any parent directory");
  }

  /**
   * Reads a repository file as a string.
   *
   * @param relativePath path relative to the repository root
   * @return the file's contents
   * @throws IOException if the file cannot be read
   */
  private static String read(String relativePath) throws IOException {
    return Files.readString(ROOT.resolve(relativePath));
  }

  /**
   * The root build must include all nine backend modules and nothing else. #3 claimed this; a
   * module silently dropped from {@code settings.gradle} would stop being compiled, tested and
   * scanned without any other check noticing.
   *
   * @throws IOException if settings.gradle cannot be read
   */
  @Test
  void settingsGradleIncludesExactlyTheNineBackendModules() throws IOException {
    Matcher matcher = Pattern.compile("'backend:([a-z-]+)'").matcher(read("settings.gradle"));

    List<String> included = matcher.results().map(result -> result.group(1)).toList();

    assertThat(included).containsExactlyInAnyOrderElementsOf(EXPECTED_MODULES);
  }

  /** Every backend module carries its own README, as #2 required. */
  @Test
  void everyBackendModuleHasAReadme() {
    assertThat(EXPECTED_MODULES)
        .allSatisfy(
            module ->
                assertThat(ROOT.resolve("backend/" + module + "/README.md"))
                    .as("README.md for %s", module)
                    .exists());
  }

  /**
   * The infrastructure and documentation directory layout from #2. The frontend lives at {@code
   * frontend/lmdb}; the original criterion named {@code frontend/filmpire}, which ADR-013 replaced.
   */
  @Test
  void scaffoldedDirectoriesExist() {
    assertThat(ROOT.resolve("frontend/lmdb")).isDirectory();
    assertThat(ROOT.resolve("infrastructure/docker")).isDirectory();
    assertThat(ROOT.resolve("infrastructure/kubernetes")).isDirectory();
    assertThat(ROOT.resolve("infrastructure/scripts")).isDirectory();
    assertThat(ROOT.resolve("docs/architecture/adr")).isDirectory();
    assertThat(ROOT.resolve("docs/api")).isDirectory();
    assertThat(ROOT.resolve("docs/guides")).isDirectory();
    assertThat(ROOT.resolve(".gitignore")).isRegularFile();
  }

  /**
   * The Java toolchain is pinned at the root and inherited by every module, as #3 required. The
   * root build reads {@code javaVersion} from {@code gradle.properties}, so pinning it there pins
   * it everywhere.
   *
   * @throws IOException if gradle.properties cannot be read
   */
  @Test
  void javaToolchainIsPinnedAtTheRoot() throws IOException {
    assertThat(read("gradle.properties")).contains("javaVersion=25");
  }

  /**
   * Issue and pull request templates from #4. The four issue templates are what the backlog audit
   * (#251) checks every issue against, so their absence would silently remove that standard.
   */
  @Test
  void issueAndPullRequestTemplatesExist() {
    assertThat(ROOT.resolve(".github/ISSUE_TEMPLATE/epic.md")).isRegularFile();
    assertThat(ROOT.resolve(".github/ISSUE_TEMPLATE/user-story.md")).isRegularFile();
    assertThat(ROOT.resolve(".github/ISSUE_TEMPLATE/task.md")).isRegularFile();
    assertThat(ROOT.resolve(".github/ISSUE_TEMPLATE/bug.md")).isRegularFile();
    assertThat(ROOT.resolve(".github/PULL_REQUEST_TEMPLATE.md")).isRegularFile();
  }

  /**
   * Both continuous integration workflows from #4 trigger on push. The original criterion recorded
   * only that CI had been seen green on one day; this asserts the trigger that makes it run at all.
   *
   * @throws IOException if either workflow file cannot be read
   */
  @Test
  void continuousIntegrationWorkflowsTriggerOnPush() throws IOException {
    assertThat(read(".github/workflows/backend-ci.yml")).contains("on:").contains("push:");
    assertThat(read(".github/workflows/frontend-ci.yml")).contains("on:").contains("push:");
    assertThat(ROOT.resolve(".github/workflows/project-automation.yml")).isRegularFile();
  }

  /**
   * Dependabot covers the four ecosystems #4 required. A dropped ecosystem stops its dependency
   * updates arriving, which is invisible until a vulnerability is reported against it.
   *
   * @throws IOException if the Dependabot configuration cannot be read
   */
  @Test
  void dependabotCoversTheFourEcosystems() throws IOException {
    String config = read(".github/dependabot.yml");

    assertThat(config)
        .contains("package-ecosystem: \"gradle\"")
        .contains("package-ecosystem: \"npm\"")
        .contains("package-ecosystem: \"github-actions\"")
        .contains("package-ecosystem: \"docker\"");
  }

  /**
   * The three databases from #5 each declare a healthcheck and a named volume. Without a
   * healthcheck a dependent service starts against a database that is not ready; without a named
   * volume its data is lost on every recreate.
   *
   * @throws IOException if the compose file cannot be read
   */
  @Test
  void composeDefinesThreeDatabasesWithHealthchecksAndNamedVolumes() throws IOException {
    String compose = read("infrastructure/docker/docker-compose.yml");

    // 1. Each database service block must contain its own healthcheck.
    for (String service : List.of("postgres", "mongodb", "redis")) {
      String block = serviceBlock(compose, service);
      assertThat(block).as("healthcheck for %s", service).contains("healthcheck:");
    }

    // 2. The named volumes must be declared at the top level, not left anonymous.
    assertThat(compose).contains("postgres_data:").contains("mongo_data:").contains("redis_data:");
  }

  /**
   * Extracts one service's block from a compose file: everything from the service key until the
   * next key at the same two-space indentation.
   *
   * @param compose the whole compose file
   * @param service the service name to extract
   * @return that service's block, without the key line itself
   */
  private static String serviceBlock(String compose, String service) {
    Matcher matcher =
        Pattern.compile(
                "^  " + service + ":\\n(.*?)(?=^  \\S|\\Z)", Pattern.DOTALL | Pattern.MULTILINE)
            .matcher(compose);
    assertThat(matcher.find()).as("service %s present in compose file", service).isTrue();
    return matcher.group(1);
  }
}
