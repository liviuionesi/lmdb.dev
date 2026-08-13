package dev.lmdb.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for ai-service, which serves movie recommendations, a conversational chat
 * assistant, and semantic search over user taste profiles, exposed over both REST ({@link
 * dev.lmdb.ai.controller.AiController}) and gRPC ({@link dev.lmdb.ai.grpc.AiGrpcService}). Eureka
 * client registration is auto-configured when the eureka-client dependency is present.
 */
@SpringBootApplication
public class AiServiceApplication {

  /**
   * Boots the Spring application context.
   *
   * @param args standard Java command-line arguments, passed through to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(AiServiceApplication.class, args);
  }
}
