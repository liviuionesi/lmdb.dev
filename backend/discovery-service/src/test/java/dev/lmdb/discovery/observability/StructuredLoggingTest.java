package dev.lmdb.discovery.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the shared logging configuration ({@code backend/observability/logback-spring.xml},
 * issue #23) actually produces single-line JSON on stdout for this service, not just that the file
 * is present and copied into the jar.
 *
 * <p>Config alone is not evidence: the same class of failure {@link PrometheusEndpointTest}
 * documents for the metrics endpoint applies here — the XML resolving without error does not prove
 * the console appender is the one actually wired to the root logger, or that its encoder emits
 * valid JSON rather than a stack trace on a misconfigured field. This test captures real stdout
 * during a real log call, through the fully assembled Spring context, and parses it.
 */
@SpringBootTest
@DisplayName("Structured Logging Tests")
class StructuredLoggingTest {

  private static final Logger LOG = LoggerFactory.getLogger(StructuredLoggingTest.class);
  private static final String PROBE_MESSAGE = "audit-artifacts structured logging probe (#23)";

  /**
   * The name every JSON line is expected to carry under the {@code application} field. Read from
   * configuration rather than hardcoded, matching {@link PrometheusEndpointTest}'s own reasoning:
   * the contract is "tagged with THIS service's name", not one literal.
   */
  @Value("${spring.application.name}")
  private String applicationName;

  private PrintStream originalOut;
  private ByteArrayOutputStream captured;

  /**
   * Redirects stdout to an in-memory buffer before each test, so the appender's real output can be
   * captured and parsed instead of only being printed to the console.
   */
  @BeforeEach
  void captureStdout() {
    originalOut = System.out;
    captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
  }

  /** Always restores the real stdout, even if the assertion below fails. */
  @AfterEach
  void restoreStdout() {
    System.setOut(originalOut);
  }

  /**
   * A single log line must be exactly one JSON object carrying this service's application name, not
   * Boot's default multi-part human-readable pattern.
   *
   * <p>Steps: (1) emit one log line through the real, fully configured logging context: (2) isolate
   * that line from anything else the context printed during the test; (3) parse it as JSON and
   * check the fields {@code LogstashEncoder} is configured to add.
   */
  @Test
  @DisplayName("A log line is single-line JSON tagged with the application name")
  void logLineIsSingleLineJsonWithApplicationTag() throws Exception {
    // 1. Emit one log line through the real, fully-configured logging context.
    LOG.info(PROBE_MESSAGE);
    System.out.flush();

    // 2. Isolate that line from anything else the context printed during the test.
    String[] lines = captured.toString(StandardCharsets.UTF_8).split("\\R");
    String probeLine =
        Arrays.stream(lines)
            .filter(line -> line.contains(PROBE_MESSAGE))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Probe log line was not captured on stdout"));

    // 3. The captured line must parse as one JSON object (LogstashEncoder's contract), not text.
    JsonNode json = new ObjectMapper().readTree(probeLine);
    assertThat(json.get("message").asText()).isEqualTo(PROBE_MESSAGE);
    assertThat(json.get("application").asText()).isEqualTo(applicationName);
  }
}
