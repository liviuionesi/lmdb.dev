package dev.lmdb.ai.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates every RPC reaching {@link AiGrpcService} against a shared service token.
 *
 * <p>The gRPC surface carries the same chat and recommendation logic the REST surface does, but
 * none of api-gateway's JWT chain sits in front of it — nothing routes through the gateway to get
 * here. Without this interceptor, reaching the port at all is sufficient to read and append to any
 * user's conversations.
 *
 * <p>This is service-to-service authentication, not user authentication: a caller proves it is an
 * LMDB service, and the {@code user_id} it then names is trusted on that basis. It is deliberately
 * <em>fail-closed</em> — when no token is configured, every call is rejected rather than every call
 * being allowed, so a missing environment variable degrades into an outage rather than silently
 * into an open door.
 */
@Slf4j
public class GrpcAuthInterceptor implements ServerInterceptor {

  /** Metadata key carrying the caller's service token. Must be lower-case per gRPC's contract. */
  static final Metadata.Key<String> TOKEN_HEADER =
      Metadata.Key.of("x-service-token", Metadata.ASCII_STRING_MARSHALLER);

  private final byte[] expectedToken;

  /**
   * @param expectedToken the shared service token from {@code grpc.server.auth-token}; blank or
   *     {@code null} leaves the server fail-closed, rejecting every call
   */
  public GrpcAuthInterceptor(String expectedToken) {
    this.expectedToken =
        expectedToken == null || expectedToken.isBlank()
            ? null
            : expectedToken.getBytes(StandardCharsets.UTF_8);
    if (this.expectedToken == null) {
      log.warn(
          "grpc.server.auth-token is not set — the gRPC server will reject every call. "
              + "Set AI_SERVICE_GRPC_TOKEN to enable service-to-service calls.");
    }
  }

  /**
   * Rejects the call unless it presents the configured service token.
   *
   * @param call the in-flight server call
   * @param headers the call's request metadata, searched for {@link #TOKEN_HEADER}
   * @param next the handler invoked only once the caller is authenticated
   * @param <R> the RPC's request type
   * @param <S> the RPC's response type
   * @return the real call listener when authenticated, otherwise a no-op listener for the call this
   *     method has already closed with {@code UNAUTHENTICATED}
   */
  @Override
  public <R, S> ServerCall.Listener<R> interceptCall(
      ServerCall<R, S> call, Metadata headers, ServerCallHandler<R, S> next) {
    if (expectedToken == null) {
      return reject(call, "gRPC authentication is not configured");
    }

    String presented = headers.get(TOKEN_HEADER);
    if (presented == null) {
      return reject(call, "Missing service token");
    }

    // Constant-time comparison: a length-or-prefix-sensitive equals() would let a caller recover
    // the token a byte at a time from response timing.
    if (!MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedToken)) {
      return reject(call, "Invalid service token");
    }

    return next.startCall(call, headers);
  }

  /**
   * Closes the call as {@code UNAUTHENTICATED} and returns a listener that ignores anything the
   * caller has already sent.
   *
   * @param call the call to close
   * @param reason logged locally; never sent to the caller, who is told only that authentication
   *     failed
   * @param <R> the RPC's request type
   * @param <S> the RPC's response type
   * @return a no-op listener for the now-closed call
   */
  private static <R, S> ServerCall.Listener<R> reject(ServerCall<R, S> call, String reason) {
    log.warn(
        "Rejected gRPC call to {}: {}", call.getMethodDescriptor().getFullMethodName(), reason);
    call.close(Status.UNAUTHENTICATED.withDescription("Authentication required"), new Metadata());
    return new ServerCall.Listener<>() {};
  }
}
