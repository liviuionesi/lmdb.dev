package com.filmpire.media.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.filmpire.media.model.EntityType;
import com.filmpire.media.model.MediaFile;
import com.filmpire.media.model.MediaMetadata;
import com.filmpire.media.model.MediaType;
import com.filmpire.media.service.MediaService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Unit test suite verifying HTTP response wrapping, service delegation, parameter handling,
 * and error code translation for {@link MediaController}.
 */
@ExtendWith(MockitoExtension.class)
public class MediaControllerTest {

  @Mock
  private MediaService mediaService;

  private MediaController mediaController;

  @BeforeEach
  public void setUp() {
    mediaController = new MediaController(mediaService);
  }

  /**
   * Asserts that submitting a valid multipart upload file cleanly delegates to the service
   * and wraps the returned asset document within an HTTP 201 Created response.
   */
  @Test
  public void whenUploadEndpointInvoked_thenReturns201Created() throws Exception {
    MockMultipartFile mockFile = new MockMultipartFile(
        "file", "photo.png", "image/png", "fake png data".getBytes());

    MediaFile resp = new MediaFile(
        "uuid-1234", "usr-1", EntityType.USER, MediaType.AVATAR,
        "photo.png", "user/usr-1/photo.png", 13, "image/png",
        Map.of("thumb", "http://thumb"), new MediaMetadata(200, 200, null, null, null),
        Instant.now(), "tester");

    when(mediaService.uploadMedia(any(), eq("usr-1"), eq(EntityType.USER), eq(MediaType.AVATAR),
        any(), any(), any(), any(), any(), eq("tester"))).thenReturn(resp);

    ResponseEntity<MediaFile> result = mediaController.uploadMedia(
        mockFile, "usr-1", EntityType.USER, MediaType.AVATAR, null, null, null, null, null, "tester");

    Assertions.assertEquals(HttpStatus.CREATED, result.getStatusCode());
    Assertions.assertNotNull(result.getBody());
    Assertions.assertEquals("uuid-1234", result.getBody().id());
    Assertions.assertEquals("usr-1", result.getBody().entityId());
  }

  /**
   * Asserts that requesting existing asset metadata returns HTTP 200 OK and matching record body.
   */
  @Test
  public void givenExistingId_whenGetMetadata_thenReturns200Ok() {
    String fileId = "uuid-8888";
    MediaFile found = new MediaFile(
        fileId, "movie-99", EntityType.MOVIE, MediaType.IMAGE,
        "poster.jpg", "movie/movie-99/poster.jpg", 5000, "image/jpeg",
        Map.of(), new MediaMetadata(600, 900, null, null, null), Instant.now(), "admin");

    when(mediaService.getMediaFile(fileId)).thenReturn(Optional.of(found));

    ResponseEntity<MediaFile> result = mediaController.getMediaFile(fileId);

    Assertions.assertEquals(HttpStatus.OK, result.getStatusCode());
    Assertions.assertNotNull(result.getBody());
    Assertions.assertEquals(fileId, result.getBody().id());
  }

  /**
   * Asserts that downloading binary content streams accurately populates headers and returns HTTP 200 OK.
   */
  @Test
  public void givenExistingId_whenDownloadMedia_thenReturnsStreamResourceWith200Ok() {
    String fileId = "uuid-8888";
    MediaFile found = new MediaFile(
        fileId, "movie-99", EntityType.MOVIE, MediaType.IMAGE,
        "poster.jpg", "movie/movie-99/poster.jpg", 5000, "image/jpeg",
        Map.of(), new MediaMetadata(600, 900, null, null, null), Instant.now(), "admin");

    InputStream stream = new ByteArrayInputStream("fake stream".getBytes());
    when(mediaService.getMediaFile(fileId)).thenReturn(Optional.of(found));
    when(mediaService.getMediaStream(fileId)).thenReturn(stream);

    ResponseEntity<Resource> result = mediaController.downloadMedia(fileId, "original");

    Assertions.assertEquals(HttpStatus.OK, result.getStatusCode());
    Assertions.assertNotNull(result.getBody());
    Assertions.assertEquals("5000", result.getHeaders().getFirst("Content-Length"));
  }

  /**
   * Asserts that invoking DELETE /api/v1/media/{id} responds with HTTP 204 No Content upon removal.
   */
  @Test
  public void givenExistingId_whenDeleteEndpointInvoked_thenReturns204NoContent() {
    when(mediaService.deleteMediaFile("del-target-99")).thenReturn(true);

    ResponseEntity<Void> result = mediaController.deleteMediaFile("del-target-99");

    Assertions.assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
  }

  /**
   * Asserts that querying media uploads by entity ID returns HTTP 200 with list of results.
   */
  @Test
  public void whenQueryByEntityId_thenReturns200OkWithArray() {
    when(mediaService.getMediaForEntity("usr-profile-1")).thenReturn(List.of());

    ResponseEntity<List<MediaFile>> result = mediaController.getMediaForEntity("usr-profile-1");

    Assertions.assertEquals(HttpStatus.OK, result.getStatusCode());
    Assertions.assertNotNull(result.getBody());
  }
}
