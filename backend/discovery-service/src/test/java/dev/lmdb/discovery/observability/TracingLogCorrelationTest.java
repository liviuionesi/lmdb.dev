package dev.lmdb.discovery.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that Micrometer Tracing (ADR-007, Task #42) correlates its trace/span IDs into the
 * shared JSON log format ({@code backend/observability/logback-spring.xml}), not just that a span
 * can be created.
 *
 * <p>{@link StructuredLoggingTest} proves the JSON format itself; this test proves the specific
 * MDC contract {@code logback-spring.xml} declares ({@code includeMdcKeyName} for {@code traceId}
 * and {@code spanId}) actually fires. A log line emitted with no active span would leave those
 * fields null, so the encoder configuration alone does not prove correlation — only a log line
 * captured inside an active span does.
 */
@SpringBootTest
@DisplayName("Tracing/Log Correlation Tests (#42)")
class TracingLogCorrelationTest {

  private static final Logger LOG = LoggerFactory.getLogger(TracingLogCorrelationTest.class);
  private static final String PROBE_MESSAGE = "audit-artifacts tracing correlation probe (#42)";

  @Autowired private Tracer tracer;

  private PrintStream originalOut;
  private ByteArrayOutputStream captured;

  /**
   * Redirects stdout to an in-memory buffer before each test, mirroring {@link
   * StructuredLoggingTest}, so the JSON line the appender actually writes can be parsed instead of
   * only being printed to the console.
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
   * Given an active span, when a log line is emitted inside that span's scope, then the JSON line
   * carries that exact traceId/spanId — the same MDC population path Micrometer's HTTP server
   * instrumentation drives for a real inbound request.
   *
   * <p>Steps: (1) start a span directly through the {@link Tracer} bean and open its scope, so a
   * log call inside observes the identical MDC path a traced request would; (2) isolate the probe
   * line from anything else the context logged; (3) parse it and check the two MDC keys {@code
   * logback-spring.xml} names.
   */
  @Test
  @DisplayName("A log line emitted inside an active span carries its traceId and spanId")
  void logLineInsideSpanCarriesTraceAndSpanId() throws Exception {
    // 1. Start a span through the real Tracer bean and open its scope.
    Span span = tracer.nextSpan().name("tracing-log-correlation-probe").start();
    String expectedTraceId;
    String expectedSpanId;
    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      expectedTraceId = span.context().traceId();
      expectedSpanId = span.context().spanId();
      LOG.info(PROBE_MESSAGE);
    } finally {
      span.end();
    }
    System.out.flush();

    // 2. Isolate the probe line from anything else logged during context startup.
    String[] lines = captured.toString(StandardCharsets.UTF_8).split("\\R");
    String probeLine =
        Arrays.stream(lines)
            .filter(line -> line.contains(PROBE_MESSAGE))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Probe log line was not captured on stdout"));

    // 3. The JSON line must carry the exact traceId/spanId the active span held.
    JsonNode json = new ObjectMapper().readTree(probeLine);
    assertThat(json.get("traceId").asText()).isEqualTo(expectedTraceId);
    assertThat(json.get("spanId").asText()).isEqualTo(expectedSpanId);
  }
}
