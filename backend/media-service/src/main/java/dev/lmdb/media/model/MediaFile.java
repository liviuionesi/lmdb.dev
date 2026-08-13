package dev.lmdb.media.model;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Immutable Java 25 domain record representing user-uploaded media asset metadata stored within
 * MongoDB collection 'media'.
 *
 * <p>Physical file bytes are persisted externally in an S3-compatible MinIO object store referenced
 * via {@link #storagePath()}. This model strictly handles user uploads and never stores TMDB poster
 * or backdrop byte data (ARCHITECTURE.md §3.8).
 *
 * @param id Unique persistent document identifier (UUID string).
 * @param entityId Target entity identifier (e.g., user account UUID, movie ID, review ID).
 * @param entityType Classification of the target domain entity (USER, MOVIE, MOVIE_REVIEW).
 * @param mediaType Classification of the uploaded asset (IMAGE, VIDEO, AVATAR, ATTACHMENT).
 * @param originalFilename Client-supplied filename prior to storage sanitization.
 * @param storagePath Internal MinIO object storage key / bucket location path.
 * @param fileSize Total storage allocation footprint in bytes.
 * @param mimeType Standardized Internet media content type indicator (e.g. image/png).
 * @param thumbnails Map linking sizing descriptors (small, medium, thumb) to resolvable URLs.
 * @param metadata Nested technical specifications (dimensions, codecs, duration).
 * @param uploadedAt Precise server creation timestamp when ingestion finished.
 * @param uploadedBy Identifier of the user account responsible for creating the upload.
 * @param description Optional user-supplied review comment or caption text associated with the
 *     upload (e.g., movie review text paired with a screenshot attachment).
 */
@Document(collection = "media")
public record MediaFile(
    @Id String id,
    String entityId,
    EntityType entityType,
    MediaType mediaType,
    String originalFilename,
    String storagePath,
    long fileSize,
    String mimeType,
    Map<String, String> thumbnails,
    MediaMetadata metadata,
    Instant uploadedAt,
    String uploadedBy,
    String description) {
  /**
   * Compact record constructor enforcing domain validation rules.
   *
   * @throws IllegalArgumentException if fileSize is negative.
   */
  public MediaFile {
    if (fileSize < 0) {
      throw new IllegalArgumentException("File size cannot be negative");
    }
  }
}
