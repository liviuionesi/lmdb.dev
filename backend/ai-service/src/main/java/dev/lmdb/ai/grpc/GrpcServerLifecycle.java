package dev.lmdb.ai.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Starts and stops the ai-service gRPC server ({@code grpc.server.port}, default 9084) alongside
 * the Spring application lifecycle. No third-party gRPC-Spring starter is used — {@link
 * AiGrpcService} is a small enough surface that a plain {@link SmartLifecycle} bean wrapping {@link
 * ServerBuilder} is simpler than an extra dependency.
 *
 * <p>Every RPC is authenticated by {@link GrpcAuthInterceptor}. This port is meant to be reachable
 * only from inside the container network — it is not published to the host in {@code
 * infrastructure/docker/docker-compose.yml}, and api-gateway does not route to it.
 */
@Component
@Slf4j
public class GrpcServerLifecycle implements SmartLifecycle {

  private final AiGrpcService aiGrpcService;
  private final GrpcAuthInterceptor authInterceptor;
  private final int port;
  private Server server;

  /**
   * @param aiGrpcService the gRPC service implementation to bind to the server
   * @param port the port to listen on, {@code grpc.server.port}, default 9084
   * @param authToken the shared service token every caller must present, {@code
   *     grpc.server.auth-token}; unset leaves the server fail-closed
   */
  public GrpcServerLifecycle(
      AiGrpcService aiGrpcService,
      @Value("${grpc.server.port:9084}") int port,
      @Value("${grpc.server.auth-token:}") String authToken) {
    this.aiGrpcService = aiGrpcService;
    this.authInterceptor = new GrpcAuthInterceptor(authToken);
    this.port = port;
  }

  /**
   * Builds and starts the gRPC server, binding {@link #aiGrpcService} behind {@link
   * GrpcAuthInterceptor} so no RPC can reach the service without authenticating first.
   *
   * @throws IllegalStateException if the server fails to bind to {@link #port}
   */
  @Override
  public void start() {
    try {
      server =
          ServerBuilder.forPort(port)
              .addService(ServerInterceptors.intercept(aiGrpcService, authInterceptor))
              .build()
              .start();
      log.info("gRPC server started on port {}", port);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start gRPC server on port " + port, e);
    }
  }

  /**
   * Shuts down the gRPC server, waiting up to 5 seconds for in-flight calls to finish before
   * returning. Restores the thread's interrupt status if interrupted while waiting, rather than
   * swallowing it.
   */
  @Override
  public void stop() {
    if (server != null) {
      try {
        server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      } finally {
        server = null;
      }
    }
  }

  /**
   * @return {@code true} if the gRPC server has been started and not yet shut down
   */
  @Override
  public boolean isRunning() {
    return server != null && !server.isShutdown();
  }
}
