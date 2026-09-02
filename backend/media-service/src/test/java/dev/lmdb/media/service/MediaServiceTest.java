package dev.lmdb.media.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.lmdb.media.model.EntityType;
import dev.lmdb.media.model.MediaFile;
import dev.lmdb.media.model.MediaMetadata;
import dev.lmdb.media.model.MediaType;
import dev.lmdb.media.repository.MediaRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Unit test suite for {@link MediaService} verifying orchestration between MinIO storage operations
 * and MongoDB document repository persistence using Mockito doubles.
 */
@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

  @Mock private MediaRepository mediaRepository;

  @Mock private StorageService storageService;

  @InjectMocks private MediaService mediaService;

  /**
   * Asserts that ingesting a valid multipart upload correctly triggers binary stream persistence,
   * generates thumbnail reference links, and registers metadata in MongoDB.
   */
  @Test
  void givenMultipartUpload_whenUploadMedia_thenSavesToStorageAndRepository() throws IOException {
    MockMultipartFile mockFile =
        new MockMultipartFile(
            "file", "avatar.jpg", "image/jpeg", "test image binary bytes".getBytes());

    when(storageService.generateThumbnails(any(), eq("avatar.jpg")))
        .thenReturn(Map.of("thumb", "http://thumb-url"));

    when(mediaRepository.save(any(MediaFile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MediaMetadata metadata = new MediaMetadata(500, 500, null, "JPEG", null);
    MediaFile result =
        mediaService.uploadMedia(
            mockFile, "user-789", EntityType.USER, MediaType.AVATAR, metadata, "test_user", null);

    assertNotNull(result.id());
    assertEquals("user-789", result.entityId());
    assertEquals("avatar.jpg", result.originalFilename());
    assertEquals(EntityType.USER, result.entityType());
    assertEquals(MediaType.AVATAR, result.mediaType());
    assertEquals("http://thumb-url", result.thumbnails().get("thumb"));

    verify(storageService)
        .putObject(any(), any(InputStream.class), eq(mockFile.getSize()), eq("image/jpeg"));
    verify(mediaRepository).save(any(MediaFile.class));
  }

  /**
   * Asserts that deleting an existing media asset cleanly removes both MinIO bucket storage objects
   * and database entries.
   */
  @Test
  void givenExistingId_whenDeleteMediaFile_thenRemovesObjectAndDeletesRecord() {
    String testId = "del-uuid-111";
    MediaFile mockFile =
        new MediaFile(
            testId,
            "entity-id",
            EntityType.MOVIE_REVIEW,
            MediaType.ATTACHMENT,
            "doc.pdf",
            "movie_review/entity-id/doc.pdf",
            1024,
            "application/pdf",
            Map.of(),
            new MediaMetadata(null, null, null, null, null),
            Instant.now(),
            "reviewer",
            null);

    when(mediaRepository.findById(testId)).thenReturn(Optional.of(mockFile));

    boolean result = mediaService.deleteMediaFile(testId);

    assertTrue(result);
    verify(storageService).removeObject("movie_review/entity-id/doc.pdf");
    verify(mediaRepository).deleteById(testId);
  }

  /**
   * Asserts that listing assets by target entity identifier queries the repository with accurate
   * criteria.
   */
  @Test
  void whenGetMediaForEntity_thenCallsRepositoryAndReturnsList() {
    String entityId = "movie-review-555";
    when(mediaRepository.findByEntityId(entityId)).thenReturn(List.of());

    List<MediaFile> list = mediaService.getMediaForEntity(entityId);
    assertNotNull(list);
    verify(mediaRepository).findByEntityId(entityId);
  }
}
