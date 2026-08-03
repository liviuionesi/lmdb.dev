package com.filmpire.ai.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
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
 */
@Component
@Slf4j
public class GrpcServerLifecycle implements SmartLifecycle {

  private final AiGrpcService aiGrpcService;
  private final int port;
  private Server server;

  /**
   * @param aiGrpcService the gRPC service implementation to bind to the server
   * @param port the port to listen on, {@code grpc.server.port}, default 9084
   */
  public GrpcServerLifecycle(
      AiGrpcService aiGrpcService, @Value("${grpc.server.port:9084}") int port) {
    this.aiGrpcService = aiGrpcService;
    this.port = port;
  }

  /**
   * Builds and starts the gRPC server bound to {@link #aiGrpcService}.
   *
   * @throws IllegalStateException if the server fails to bind to {@link #port}
   */
  @Override
  public void start() {
    try {
      server = ServerBuilder.forPort(port).addService(aiGrpcService).build().start();
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
      } catch (InterruptedException e) {
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
