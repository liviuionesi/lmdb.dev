package com.filmpire.media.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Assertions;
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

  @Mock
  private MediaRepository mediaRepository;

  @Mock
  private StorageService storageService;

  @InjectMocks
  private MediaService mediaService;

  /**
   * Asserts that ingesting a valid multipart upload correctly triggers binary stream persistence,
   * generates thumbnail reference links, and registers metadata in MongoDB.
   */
  @Test
  void givenMultipartUpload_whenUploadMedia_thenSavesToStorageAndRepository() throws IOException {
    MockMultipartFile mockFile = new MockMultipartFile(
        "file", "avatar.jpg", "image/jpeg", "test image binary bytes".getBytes());

    when(storageService.generateThumbnails(any(), eq("avatar.jpg")))
        .thenReturn(Map.of("thumb", "http://thumb-url"));

    when(mediaRepository.save(any(MediaFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

    MediaMetadata metadata = new MediaMetadata(500, 500, null, "JPEG", null);
    MediaFile result = mediaService.uploadMedia(
        mockFile,
        "user-789",
        EntityType.USER,
        MediaType.AVATAR,
        metadata,
        "test_user"
    );

    Assertions.assertNotNull(result.id());
    Assertions.assertEquals("user-789", result.entityId());
    Assertions.assertEquals("avatar.jpg", result.originalFilename());
    Assertions.assertEquals(EntityType.USER, result.entityType());
    Assertions.assertEquals(MediaType.AVATAR, result.mediaType());
    Assertions.assertEquals("http://thumb-url", result.thumbnails().get("thumb"));

    verify(storageService).putObject(any(), any(InputStream.class), eq(mockFile.getSize()), eq("image/jpeg"));
    verify(mediaRepository).save(any(MediaFile.class));
  }

  /**
   * Asserts that deleting an existing media asset cleanly removes both MinIO bucket storage objects and database entries.
   */
  @Test
  void givenExistingId_whenDeleteMediaFile_thenRemovesObjectAndDeletesRecord() {
    String testId = "del-uuid-111";
    MediaFile mockFile = new MediaFile(
        testId, "entity-id", EntityType.MOVIE_REVIEW, MediaType.ATTACHMENT,
        "doc.pdf", "movie_review/entity-id/doc.pdf", 1024, "application/pdf",
        Map.of(), new MediaMetadata(null, null, null, null, null), Instant.now(), "reviewer");

    when(mediaRepository.findById(testId)).thenReturn(Optional.of(mockFile));

    boolean result = mediaService.deleteMediaFile(testId);

    Assertions.assertTrue(result);
    verify(storageService).removeObject("movie_review/entity-id/doc.pdf");
    verify(mediaRepository).deleteById(testId);
  }

  /**
   * Asserts that listing assets by target entity identifier queries the repository with accurate criteria.
   */
  @Test
  void whenGetMediaForEntity_thenCallsRepositoryAndReturnsList() {
    String entityId = "movie-review-555";
    when(mediaRepository.findByEntityId(entityId)).thenReturn(List.of());

    List<MediaFile> list = mediaService.getMediaForEntity(entityId);
    Assertions.assertNotNull(list);
    verify(mediaRepository).findByEntityId(entityId);
  }
}
