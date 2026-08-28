package dev.lmdb.ai.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link GrpcAuthInterceptor}, the only thing authenticating callers of ai-service's
 * gRPC surface — api-gateway's JWT chain never sits in front of that port.
 *
 * <p>{@link ServerCall} and {@link ServerCallHandler} are Mockito mocks; what's asserted is whether
 * the interceptor hands the call on to the handler or closes it itself, since "the handler was
 * never reached" is exactly what keeps an unauthenticated caller away from {@link AiGrpcService}.
 */
@DisplayName("GrpcAuthInterceptor (gRPC service-to-service authentication)")
class GrpcAuthInterceptorTest {

  private static final String TOKEN = "s3cret-service-token";

  private ServerCall<Object, Object> call;
  private ServerCallHandler<Object, Object> next;

  /**
   * Builds a fresh mock call and handler per test, with a real {@code MethodDescriptor} so the
   * interceptor's rejection logging (which reads the full method name) runs as it does in
   * production rather than against a stubbed-out descriptor.
   */
  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    call = mock(ServerCall.class);
    next = mock(ServerCallHandler.class);
    when(call.getMethodDescriptor())
        .thenReturn((io.grpc.MethodDescriptor) AIServiceGrpc.getChatWithAssistantMethod());
  }

  /**
   * Given a call presenting the configured token, when intercepted, then it reaches the underlying
   * handler and is not closed — the one path on which an RPC is allowed to touch the service.
   */
  @Test
  @DisplayName("a call presenting the correct token reaches the service")
  void correctTokenIsAccepted() {
    GrpcAuthInterceptor interceptor = new GrpcAuthInterceptor(TOKEN);
    Metadata headers = metadataWithToken(TOKEN);

    interceptor.interceptCall(call, headers, next);

    // Same Metadata instance, by identity — Metadata has no equals(), so an equality-based
    // verification would compare by reference anyway and read misleadingly.
    verify(next).startCall(call, headers);
    verify(call, never()).close(any(), any());
  }

  /**
   * Given a call presenting a token that is wrong, when intercepted, then the call is closed {@code
   * UNAUTHENTICATED} and the service is never invoked. Asserts the service is not reached, not
   * merely that an error was reported.
   */
  @Test
  @DisplayName(
      "a call presenting a wrong token is closed UNAUTHENTICATED without reaching the service")
  void wrongTokenIsRejected() {
    GrpcAuthInterceptor interceptor = new GrpcAuthInterceptor(TOKEN);

    interceptor.interceptCall(call, metadataWithToken("not-the-token"), next);

    assertThat(closedStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    verify(next, never()).startCall(any(), any());
  }

  /**
   * Given a call carrying no token metadata at all — the shape of a request from someone who simply
   * reached the port — when intercepted, then it is closed {@code UNAUTHENTICATED}.
   */
  @Test
  @DisplayName("a call with no token is closed UNAUTHENTICATED without reaching the service")
  void missingTokenIsRejected() {
    GrpcAuthInterceptor interceptor = new GrpcAuthInterceptor(TOKEN);

    interceptor.interceptCall(call, new Metadata(), next);

    assertThat(closedStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    verify(next, never()).startCall(any(), any());
  }

  /**
   * Given no token is configured on the server, when a call arrives presenting any token, then it
   * is still rejected. This is the fail-closed contract: a missing {@code AI_SERVICE_GRPC_TOKEN}
   * must degrade into an outage, never into an unauthenticated gRPC surface.
   */
  @Test
  @DisplayName("with no token configured, every call is rejected rather than allowed")
  void unconfiguredInterceptorFailsClosed() {
    GrpcAuthInterceptor interceptor = new GrpcAuthInterceptor("   ");

    interceptor.interceptCall(call, metadataWithToken(TOKEN), next);

    assertThat(closedStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    verify(next, never()).startCall(any(), any());
  }

  /**
   * Given a rejected call, when the caller reads the status description, then it says only that
   * authentication is required — the interceptor's own reason ("missing" vs "invalid" token) stays
   * in the server log, so probing the endpoint reveals nothing about the token.
   */
  @Test
  @DisplayName("the rejection tells the caller nothing beyond 'authentication required'")
  void rejectionDoesNotLeakWhyItFailed() {
    GrpcAuthInterceptor interceptor = new GrpcAuthInterceptor(TOKEN);

    interceptor.interceptCall(call, metadataWithToken("wrong"), next);
    Status wrongTokenStatus = closedStatus();

    assertThat(wrongTokenStatus.getDescription()).isEqualTo("Authentication required");
    assertThat(wrongTokenStatus.getDescription()).doesNotContain(TOKEN);
  }

  /**
   * Builds request metadata carrying a service token.
   *
   * @param token the token value to present
   * @return metadata with {@code x-service-token} set
   */
  private static Metadata metadataWithToken(String token) {
    Metadata metadata = new Metadata();
    metadata.put(GrpcAuthInterceptor.TOKEN_HEADER, token);
    return metadata;
  }

  /**
   * Captures the status the interceptor closed the call with.
   *
   * @return the {@link Status} passed to {@link ServerCall#close}
   */
  private Status closedStatus() {
    ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
    verify(call).close(statusCaptor.capture(), any());
    return statusCaptor.getValue();
  }
}
