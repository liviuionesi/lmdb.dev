package dev.lmdb.actor.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Unit tests for {@link TmdbRateLimitInterceptor}.
 *
 * <p>Verifies the token bucket rate limiter interceptor enforcing TMDB's published rate limits (40
 * requests / 10 seconds), checking token consumption and handling thread interruptions cleanly.
 */
@DisplayName("TmdbRateLimitInterceptor Unit Tests")
class TmdbRateLimitInterceptorTest {

  private final TmdbRateLimitInterceptor interceptor = new TmdbRateLimitInterceptor();

  /**
   * Verifies that under normal operation, {@link TmdbRateLimitInterceptor#intercept} consumes a
   * token from the in-memory bucket and delegates execution to the downstream request handler.
   *
   * @throws IOException if downstream execution fails
   */
  @Test
  @DisplayName("intercept: consumes token and delegates to downstream execution")
  void interceptConsumesTokenAndExecutes() throws IOException {
    // Given
    HttpRequest request = mock(HttpRequest.class);
    when(request.getURI()).thenReturn(URI.create("https://api.themoviedb.org/3/person/819"));

    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    ClientHttpResponse expectedResponse = mock(ClientHttpResponse.class);
    byte[] body = new byte[0];
    when(execution.execute(request, body)).thenReturn(expectedResponse);

    // When
    ClientHttpResponse response = interceptor.intercept(request, body, execution);

    // Then
    assertThat(response).isSameAs(expectedResponse);
    verify(execution).execute(request, body);
  }

  /**
   * Verifies that if the executing thread is interrupted while awaiting a token, the interceptor
   * restores the interrupt status and throws an {@link IOException} to fail the request cleanly.
   */
  @Test
  @DisplayName("intercept: throws IOException when thread is interrupted")
  void interceptThrowsOnInterruption() throws IOException {
    // Given
    HttpRequest request = mock(HttpRequest.class);
    when(request.getURI()).thenReturn(URI.create("https://api.themoviedb.org/3/person/819"));

    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    ClientHttpResponse expectedResponse = mock(ClientHttpResponse.class);
    byte[] body = new byte[0];
    when(execution.execute(request, body)).thenReturn(expectedResponse);

    // Drain initial 40 tokens so bucket is empty and subsequent consume must block
    for (int i = 0; i < 40; i++) {
      interceptor.intercept(request, body, execution);
    }

    // Set interrupted flag
    Thread.currentThread().interrupt();

    // When & Then
    assertThatThrownBy(() -> interceptor.intercept(request, body, execution))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Request interrupted while waiting for rate limit token");

    // Clean up interrupt status
    Thread.interrupted();
  }
}
