package dev.lmdb.media.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Low-level binary storage infrastructure service managing S3-compatible bucket persistence via
 * MinIO. Handles file ingestion streams, removal operations, retrieval downloads, and thumbnail
 * mapping.
 */
@Service
public class StorageService {

  private static final Logger log = LoggerFactory.getLogger(StorageService.class);

  private final MinioClient minioClient;
  private final String bucketName;

  /**
   * Explicit constructor injection (no @Autowired field injection per project conventions).
   *
   * @param minioClient Initialized MinIO S3 client bean.
   * @param bucketName Target storage bucket identifier from configuration properties.
   */
  public StorageService(
      MinioClient minioClient, @Value("${minio.bucket-name:lmdb-media}") String bucketName) {
    this.minioClient = minioClient;
    this.bucketName = bucketName;
  }

  /** Verifies target storage bucket existence in MinIO and initializes a new bucket if absent. */
  public void ensureBucketExists() {
    try {
      boolean found =
          minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
      if (!found) {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        log.info("Initialized new MinIO storage bucket: {}", bucketName);
      }
    } catch (Exception e) {
      log.error(
          "Failed executing storage bucket initialization check for '{}': {}",
          bucketName,
          e.getMessage());
      throw new RuntimeException("MinIO storage bucket verification failed", e);
    }
  }

  /**
   * Stores raw file binary stream in target MinIO storage bucket.
   *
   * @param objectKey Target persistent object identifier path in bucket.
   * @param stream Input stream delivering file payload bytes.
   * @param size Declared size footprint of payload in bytes.
   * @param contentType Standard MIME content classification header (e.g., image/jpeg).
   */
  public void putObject(String objectKey, InputStream stream, long size, String contentType) {
    ensureBucketExists();
    try {
      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucketName).object(objectKey).stream(stream, size, -1)
              .contentType(contentType != null ? contentType : "application/octet-stream")
              .build());
      log.debug("Successfully persisted object '{}' to bucket '{}'", objectKey, bucketName);
    } catch (Exception e) {
      log.error("Failed uploading object '{}' to MinIO: {}", objectKey, e.getMessage(), e);
      throw new RuntimeException("Storage putObject failed for key: " + objectKey, e);
    }
  }

  /**
   * Retrieves readable binary input stream for stored media object.
   *
   * @param objectKey Persistent identifier path in bucket storage.
   * @return Open {@link InputStream} delivering object binary content.
   */
  public InputStream getObject(String objectKey) {
    try {
      return minioClient.getObject(
          GetObjectArgs.builder().bucket(bucketName).object(objectKey).build());
    } catch (Exception e) {
      log.error("Failed reading stream for object '{}' from MinIO: {}", objectKey, e.getMessage());
      throw new RuntimeException("Storage getObject failed for key: " + objectKey, e);
    }
  }

  /**
   * Evicts stored file object from target MinIO bucket storage.
   *
   * @param objectKey Persistent identifier path in bucket storage.
   */
  public void removeObject(String objectKey) {
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(bucketName).object(objectKey).build());
      log.debug("Evicted object '{}' from bucket '{}'", objectKey, bucketName);
    } catch (Exception e) {
      log.warn(
          "Failed removing object '{}' from MinIO (may already be deleted): {}",
          objectKey,
          e.getMessage());
    }
  }

  /**
   * Generates thumbnail rendering reference URLs for uploaded media files.
   *
   * @param fileId UUID string assigned to the uploaded media resource.
   * @param originalFilename Client supplied filename used for format routing.
   * @return Immutable mapping connecting standard sizing keys to download URLs.
   */
  public Map<String, String> generateThumbnails(String fileId, String originalFilename) {
    // Generates accessible endpoint URL mappings pointing back to media-service download endpoints
    String baseUrl = "/api/v1/media/" + fileId + "/download?size=";
    return Map.of(
        "thumb", baseUrl + "small",
        "medium", baseUrl + "medium",
        "original", "/api/v1/media/" + fileId + "/download");
  }
}
