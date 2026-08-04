package com.filmpire.media.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration initializing the S3-compatible {@link MinioClient} bean used for binary
 * object persistence of user-uploaded media files.
 */
@Configuration
public class MinioConfig {

  private final String endpoint;
  private final String accessKey;
  private final String secretKey;

  /**
   * Explicit constructor injection for MinIO connection properties.
   *
   * @param endpoint MinIO server address endpoint URI from application configuration.
   * @param accessKey Account access credential identity token.
   * @param secretKey Account secret authentication token.
   */
  public MinioConfig(
      @Value("${minio.endpoint:http://localhost:9000}") String endpoint,
      @Value("${minio.access-key:minioadmin}") String accessKey,
      @Value("${minio.secret-key:minioadmin}") String secretKey) {
    this.endpoint = endpoint;
    this.accessKey = accessKey;
    this.secretKey = secretKey;
  }

  /**
   * Instantiates and registers the {@link MinioClient} Spring bean.
   *
   * @return Configured MinioClient instance targeting the local/cloud S3 endpoint.
   */
  @Bean
  public MinioClient minioClient() {
    return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
  }
}
