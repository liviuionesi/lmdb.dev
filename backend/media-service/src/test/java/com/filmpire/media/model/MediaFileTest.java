package com.filmpire.media.model;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating domain record instantiation and defensive compact constructor validation
 * rules for {@link MediaFile} metadata documents.
 */
class MediaFileTest {

  /**
   * Verifies that initializing a MediaFile record with valid positive file sizing succeeds cleanly.
   */
  @Test
  void whenValidMediaFileCreated_thenFieldsMatchAndNoExceptionThrown() {
    MediaMetadata metadata = new MediaMetadata(1920, 1080, null, "PNG", 0L);
    MediaFile mediaFile = new MediaFile(
        "file-uuid-123",
        "user-uuid-456",
        EntityType.USER,
        MediaType.AVATAR,
        "profile.png",
        "user/user-uuid-456/profile.png",
        2048L,
        "image/png",
        Map.of("thumb", "http://localhost:8085/api/v1/media/file-uuid-123/download?size=thumb"),
        metadata,
        Instant.now(),
        "test-user"
    );

    Assertions.assertEquals("file-uuid-123", mediaFile.id());
    Assertions.assertEquals(EntityType.USER, mediaFile.entityType());
    Assertions.assertEquals(MediaType.AVATAR, mediaFile.mediaType());
    Assertions.assertEquals(2048L, mediaFile.fileSize());
  }

  /**
   * Verifies that providing a negative file size parameter triggers an immediate {@link IllegalArgumentException}.
   */
  @Test
  void whenNegativeFileSizeProvided_thenThrowsIllegalArgumentException() {
    MediaMetadata metadata = new MediaMetadata(100, 100, null, null, null);
    Map<String, String> emptyThumbnails = Map.of();
    Instant now = Instant.now();
    Assertions.assertThrows(IllegalArgumentException.class, () -> new MediaFile(
        "file-uuid-err",
        "entity-err",
        EntityType.MOVIE_REVIEW,
        MediaType.ATTACHMENT,
        "invalid.dat",
        "review/err/invalid.dat",
        -100L,
        "application/octet-stream",
        emptyThumbnails,
        metadata,
        now,
        "reviewer-1"
    ));
  }
}
