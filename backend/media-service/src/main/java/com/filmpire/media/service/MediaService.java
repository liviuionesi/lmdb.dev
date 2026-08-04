package com.filmpire.media.service;

import com.filmpire.media.model.EntityType;
import com.filmpire.media.model.MediaFile;
import com.filmpire.media.model.MediaMetadata;
import com.filmpire.media.model.MediaType;
import com.filmpire.media.repository.MediaRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Core business orchestration service responsible for handling user-uploaded media asset
 * lifecycle operations across MinIO object storage and MongoDB metadata collections.
 */
@Service
public class MediaService {

  private static final Logger log = LoggerFactory.getLogger(MediaService.class);

  private final MediaRepository mediaRepository;
  private final StorageService storageService;

  /**
   * Explicit constructor injection (no @Autowired field injection per project conventions).
   *
   * @param mediaRepository MongoDB repository interface for metadata documents.
   * @param storageService MinIO storage infrastructure operations handler.
   */
  public MediaService(MediaRepository mediaRepository, StorageService storageService) {
    this.mediaRepository = mediaRepository;
    this.storageService = storageService;
  }

  /**
   * Uploads physical binary payload to MinIO storage and records structured metadata in MongoDB.
   *
   * @param file Incoming multi-part client file payload stream.
   * @param entityId Target associated entity identifier (e.g. user UUID or review ID).
   * @param entityType Classification of target domain entity.
   * @param mediaType Classification of media content type.
   * @param width Display resolution width (optional).
   * @param height Display resolution height (optional).
   * @param duration Playback duration in seconds (optional).
   * @param codec Codec identification string (optional).
   * @param bitrate Bit consumption rate (optional).
   * @param uploadedBy User account identifier responsible for upload.
   * @return Persisted immutable {@link MediaFile} record document.
   * @throws IOException If accessing the file stream encounters an I/O failure.
   */
  public MediaFile uploadMedia(
      MultipartFile file,
      String entityId,
      EntityType entityType,
      MediaType mediaType,
      Integer width,
      Integer height,
      Integer duration,
      String codec,
      Long bitrate,
      String uploadedBy) throws IOException {

    String fileId = UUID.randomUUID().toString();
    String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.dat";
    String storagePath = String.format("%s/%s/%s-%s",
        entityType.name().toLowerCase(),
        entityId,
        fileId,
        originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_"));

    log.info("Starting ingestion for file '{}' (size {} bytes) targeting storage path: {}",
        originalFilename, file.getSize(), storagePath);

    // Persist raw bytes into MinIO S3 object bucket
    storageService.putObject(storagePath, file.getInputStream(), file.getSize(), file.getContentType());

    // Generate responsive thumbnail URL references
    Map<String, String> thumbnails = storageService.generateThumbnails(fileId, originalFilename);

    MediaMetadata metadata = new MediaMetadata(width, height, duration, codec, bitrate);

    MediaFile mediaFile = new MediaFile(
        fileId,
        entityId,
        entityType != null ? entityType : EntityType.USER,
        mediaType != null ? mediaType : MediaType.IMAGE,
        originalFilename,
        storagePath,
        file.getSize(),
        file.getContentType() != null ? file.getContentType() : "application/octet-stream",
        thumbnails,
        metadata,
        Instant.now(),
        uploadedBy != null ? uploadedBy : "anonymous"
    );

    MediaFile saved = mediaRepository.save(mediaFile);
    log.info("Successfully completed upload and metadata persistence for asset id: {}", saved.id());
    return saved;
  }

  /**
   * Retrieves specific media metadata document by unique file ID.
   *
   * @param id Target unique persistent UUID string.
   * @return Optional containing matching {@link MediaFile} if found.
   */
  public Optional<MediaFile> getMediaFile(String id) {
    return mediaRepository.findById(id);
  }

  /**
   * Opens raw binary streaming channel from MinIO storage for specified media asset ID.
   *
   * @param id Target persistent media asset UUID string.
   * @return Open readable {@link InputStream} delivering object bytes.
   * @throws RuntimeException If target file ID does not match any existing metadata record.
   */
  public InputStream getMediaStream(String id) {
    MediaFile mediaFile = mediaRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Media file not found for ID: " + id));
    return storageService.getObject(mediaFile.storagePath());
  }

  /**
   * Evicts media resource from both MinIO bucket storage and MongoDB database collections.
   *
   * @param id Target persistent media asset UUID string to delete.
   * @return true if resource was found and successfully purged; false otherwise.
   */
  public boolean deleteMediaFile(String id) {
    Optional<MediaFile> existing = mediaRepository.findById(id);
    if (existing.isEmpty()) {
      return false;
    }
    MediaFile mediaFile = existing.get();
    storageService.removeObject(mediaFile.storagePath());
    mediaRepository.deleteById(id);
    log.info("Purged asset '{}' from MinIO and MongoDB", id);
    return true;
  }

  /**
   * Queries all stored media uploads currently linked to a target domain entity identifier.
   *
   * @param entityId Target entity unique identifier string.
   * @return List of matching {@link MediaFile} record metadata documents.
   */
  public List<MediaFile> getMediaForEntity(String entityId) {
    return mediaRepository.findByEntityId(entityId);
  }
}
