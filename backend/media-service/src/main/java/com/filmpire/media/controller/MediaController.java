package com.filmpire.media.controller;

import com.filmpire.media.model.EntityType;
import com.filmpire.media.model.MediaFile;
import com.filmpire.media.model.MediaMetadata;
import com.filmpire.media.model.MediaType;
import com.filmpire.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller managing end-to-end user uploaded media asset operations.
 * Provides endpoints for asset upload ingestion, metadata retrieval, binary streaming, and deletion.
 */
@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Media Asset Lifecycle API", description = "Endpoints handling custom user-uploaded binary media storage, thumbnails, and retrieval")
public class MediaController {

  private final MediaService mediaService;

  /**
   * Explicit constructor injection per repository architectural conventions.
   *
   * @param mediaService Business orchestration service handling storage and database actions.
   */
  public MediaController(MediaService mediaService) {
    this.mediaService = mediaService;
  }

  /**
   * Ingests a new multipart client media file upload and stores it in MinIO and MongoDB.
   *
   * @param file Multi-part upload binary file payload.
   * @param entityId Target associated entity identifier.
   * @param entityType Classification of associated domain entity.
   * @param mediaType Classification of asset format type.
   * @param width Resolution pixel width metadata (optional).
   * @param height Resolution pixel height metadata (optional).
   * @param duration Video audio duration in seconds metadata (optional).
   * @param codec Codec identifier metadata string (optional).
   * @param bitrate Data stream bit rate metadata (optional).
   * @param uploadedBy User account identifier responsible for submission (optional).
   * @return ResponseEntity holding persisted {@link MediaFile} record metadata with HTTP 201 status.
   */
  @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Upload User Media Asset", description = "Ingests file upload, persists binary bytes to MinIO storage, generates thumbnail URLs, and saves metadata to MongoDB.")
  @ApiResponse(responseCode = "201", description = "File asset successfully persisted and registered", content = @Content(schema = @Schema(implementation = MediaFile.class)))
  public ResponseEntity<MediaFile> uploadMedia(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "entityId", defaultValue = "general") String entityId,
      @RequestParam(value = "entityType", defaultValue = "USER") EntityType entityType,
      @RequestParam(value = "mediaType", defaultValue = "IMAGE") MediaType mediaType,
      @RequestParam(value = "width", required = false) Integer width,
      @RequestParam(value = "height", required = false) Integer height,
      @RequestParam(value = "duration", required = false) Integer duration,
      @RequestParam(value = "codec", required = false) String codec,
      @RequestParam(value = "bitrate", required = false) Long bitrate,
      @RequestParam(value = "uploadedBy", defaultValue = "anonymous") String uploadedBy) {
    try {
      MediaMetadata metadata = new MediaMetadata(width, height, duration, codec, bitrate);
      MediaFile mediaFile = mediaService.uploadMedia(
          file, entityId, entityType, mediaType, metadata, uploadedBy);
      return ResponseEntity.status(HttpStatus.CREATED).body(mediaFile);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Retrieves specific media metadata document by unique file UUID string.
   *
   * @param id Target media asset UUID identifier string.
   * @return ResponseEntity holding matching {@link MediaFile} or 404 Not Found if missing.
   */
  @GetMapping("/{id}")
  @Operation(summary = "Get Asset Metadata", description = "Retrieves stored MongoDB metadata document including thumbnail URL references for given asset ID.")
  public ResponseEntity<MediaFile> getMediaFile(
      @Parameter(description = "UUID identifier of the media asset") @PathVariable String id) {
    Optional<MediaFile> mediaFile = mediaService.getMediaFile(id);
    return mediaFile.map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Streams stored binary content directly from MinIO bucket storage.
   *
   * @param id Target media asset UUID identifier string.
   * @param size Optional rendering size key modifier (small, medium, original).
   * @return Streaming resource payload with correct MIME headers.
   */
  @GetMapping("/{id}/download")
  @Operation(summary = "Download Asset Binary Stream", description = "Streams stored binary content from S3 MinIO storage with appropriate HTTP content type headers.")
  public ResponseEntity<Resource> downloadMedia(
      @Parameter(description = "UUID identifier of the media asset") @PathVariable String id,
      @RequestParam(value = "size", defaultValue = "original") String size) {
    Optional<MediaFile> fileOpt = mediaService.getMediaFile(id);
    if (fileOpt.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    MediaFile mediaFile = fileOpt.get();
    try {
      InputStream stream = mediaService.getMediaStream(id);
      InputStreamResource resource = new InputStreamResource(stream);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + mediaFile.originalFilename() + "\"")
          .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(mediaFile.fileSize()))
          .contentType(org.springframework.http.MediaType.parseMediaType(mediaFile.mimeType()))
          .body(resource);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Evicts stored media resource from MinIO binary storage and MongoDB metadata index.
   *
   * @param id Target media asset UUID identifier string.
   * @return HTTP 204 No Content upon success, or 404 Not Found if asset did not exist.
   */
  @DeleteMapping("/{id}")
  @Operation(summary = "Delete Media Asset", description = "Removes stored binary object from MinIO and evicts corresponding metadata document from MongoDB.")
  public ResponseEntity<Void> deleteMediaFile(
      @Parameter(description = "UUID identifier of the media asset to delete") @PathVariable String id) {
    boolean deleted = mediaService.deleteMediaFile(id);
    return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }

  /**
   * Retrieves list of all media uploads bound to a specific domain entity identifier.
   *
   * @param entityId Unique identifier of target entity (e.g. user profile UUID or review ID).
   * @return List of matching {@link MediaFile} record documents.
   */
  @GetMapping("/entity/{entityId}")
  @Operation(summary = "List Entity Assets", description = "Queries MongoDB for all media uploads associated with a target entity ID (such as a user account or movie review).")
  public ResponseEntity<List<MediaFile>> getMediaForEntity(
      @Parameter(description = "Entity identifier string") @PathVariable String entityId) {
    List<MediaFile> list = mediaService.getMediaForEntity(entityId);
    return ResponseEntity.ok(list);
  }
}
