// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.media.MediaRepository;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaAttributes;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.MediaStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class UploadMediaUsecaseTest {

  private MediaRepository repository;
  private MediaStore store;
  private UploadMediaUsecase usecase;

  @BeforeEach
  void setUp() {
    repository = mock(MediaRepository.class);
    store = mock(MediaStore.class);
    usecase = new UploadMediaUsecase(repository, store);

    when(repository.save(any(MediaRecord.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @ParameterizedTest
  @CsvSource({
    "image/png, IMAGE",
    "image/jpeg, IMAGE",
    "image/webp, IMAGE",
    "video/mp4, VIDEO",
    "video/quicktime, VIDEO",
    "application/pdf, OTHER",
    "text/plain, OTHER",
    ", OTHER"
  })
  void classifiesMimeType(String mimeType, MediaContentType expected) {
    assertEquals(expected, UploadMediaUsecase.classifyContentType(mimeType));
  }

  @Test
  void uploadCallsBlobBeforeDatabase() throws IOException {
    var imageBytes = sampleJpg();
    var data = new ByteArrayInputStream(imageBytes);
    var userId = UUID.randomUUID();

    usecase.upload(
        userId,
        "test.jpg",
        "image/jpeg",
        imageBytes.length,
        MediaAccess.LOGGED_IN,
        data,
        null,
        MediaUsage.AVATAR);

    // Verify blob upload was called
    var blobKeyCaptor = ArgumentCaptor.forClass(String.class);
    verify(store).upload(blobKeyCaptor.capture(), any(), anyLong(), anyString(), any());

    // Verify DB save was called with matching blob key
    var recordCaptor = ArgumentCaptor.forClass(MediaRecord.class);
    verify(repository).save(recordCaptor.capture());

    var saved = recordCaptor.getValue();
    assertEquals(blobKeyCaptor.getValue(), saved.blobKey());
    assertEquals("test.jpg", saved.originalFilename());
    assertEquals(MediaContentType.IMAGE, saved.contentType());
    assertEquals(imageBytes.length, saved.sizeBytes());
    assertEquals(MediaAccess.LOGGED_IN, saved.access());
    assertEquals(userId, saved.creatorId());
    assertEquals(userId, saved.ownerId());
    assertEquals("image/jpeg", saved.mimeType());
    assertEquals(new MediaAttributes.Image(16, 12), saved.attributes());
    assertNotNull(saved.id());
    assertNotNull(saved.createdAt());
  }

  @Test
  void uploadPersistsThumbnailOf() throws IOException {
    var imageBytes = sampleWebp();
    var data = new ByteArrayInputStream(imageBytes);
    var userId = UUID.randomUUID();
    var originalId = UUID.randomUUID();
    when(repository.findByIdForUpdate(originalId))
        .thenReturn(Optional.of(original(originalId, userId)));

    usecase.upload(
        userId,
        "thumb.webp",
        "image/webp",
        imageBytes.length,
        MediaAccess.LOGGED_IN,
        data,
        originalId,
        MediaUsage.AVATAR);

    var recordCaptor = ArgumentCaptor.forClass(MediaRecord.class);
    verify(repository).save(recordCaptor.capture());

    assertEquals(originalId, recordCaptor.getValue().thumbnailOf());
    assertEquals(new MediaAttributes.Image(16, 12), recordCaptor.getValue().attributes());
  }

  @Test
  void uploadWithoutThumbnailOf_savesNull() throws IOException {
    var imageBytes = sampleJpg();
    var data = new ByteArrayInputStream(imageBytes);
    var userId = UUID.randomUUID();

    usecase.upload(
        userId,
        "photo.jpg",
        "image/jpeg",
        imageBytes.length,
        MediaAccess.LOGGED_IN,
        data,
        null,
        MediaUsage.AVATAR);

    var recordCaptor = ArgumentCaptor.forClass(MediaRecord.class);
    verify(repository).save(recordCaptor.capture());

    assertEquals(null, recordCaptor.getValue().thumbnailOf());
  }

  @Test
  void thumbnailOfNonexistentParent_throws400() {
    var data = new ByteArrayInputStream(new byte[] {1});
    var userId = UUID.randomUUID();
    var bogusParent = UUID.randomUUID();
    when(repository.findByIdForUpdate(bogusParent)).thenReturn(Optional.empty());

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.upload(
                    userId,
                    "thumb.webp",
                    "image/webp",
                    1,
                    MediaAccess.LOGGED_IN,
                    data,
                    bogusParent,
                    null));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(store, never()).upload(any(), any(), anyLong(), any(), any());
    verify(repository, never()).save(any());
  }

  @Test
  void thumbnailOfThumbnail_throws400() {
    var data = new ByteArrayInputStream(new byte[] {1});
    var userId = UUID.randomUUID();
    var originalId = UUID.randomUUID();
    var thumbId = UUID.randomUUID();

    // thumbId is itself a thumbnail (thumbnailOf = originalId)
    var thumbRecord =
        new MediaRecord(
            thumbId,
            "media/thumb",
            "thumb.webp",
            MediaContentType.IMAGE,
            512,
            MediaAccess.LOGGED_IN,
            userId,
            userId,
            Instant.now(),
            "image/webp",
            new MediaAttributes.Image(128, 128),
            originalId,
            null);
    when(repository.findByIdForUpdate(thumbId)).thenReturn(Optional.of(thumbRecord));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.upload(
                    userId,
                    "thumb2.webp",
                    "image/webp",
                    1,
                    MediaAccess.LOGGED_IN,
                    data,
                    thumbId,
                    null));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(store, never()).upload(any(), any(), anyLong(), any(), any());
    verify(repository, never()).save(any());
  }

  @Test
  void blobKeyContainsMediaIdAndSlugifiedFilename() throws IOException {
    var imageBytes = sampleJpg();
    var data = new ByteArrayInputStream(imageBytes);
    var userId = UUID.randomUUID();

    usecase.upload(
        userId,
        "My Photo (2).jpg",
        "image/jpeg",
        imageBytes.length,
        MediaAccess.LOGGED_IN,
        data,
        null,
        MediaUsage.AVATAR);

    var recordCaptor = ArgumentCaptor.forClass(MediaRecord.class);
    verify(repository).save(recordCaptor.capture());

    var blobKey = recordCaptor.getValue().blobKey();
    // Blob key should contain the media ID and a slugified filename
    var mediaId = recordCaptor.getValue().id().toString();
    assertEquals(true, blobKey.contains(mediaId));
    assertEquals(true, blobKey.startsWith("media/"));
    assertEquals(true, blobKey.endsWith(".jpg"));
  }

  @Test
  void svgUpload_succeedsWithNullDimensions() throws IOException {
    var svgBytes = SyntheticMediaFixtures.svg();
    var data = new ByteArrayInputStream(svgBytes);
    var userId = UUID.randomUUID();

    var result =
        usecase.upload(
            userId,
            "icon.svg",
            "image/svg+xml",
            svgBytes.length,
            MediaAccess.LOGGED_IN,
            data,
            null,
            MediaUsage.AVATAR);

    assertEquals(MediaContentType.IMAGE, result.contentType());
    assertEquals("image/svg+xml", result.mimeType());
    // SVG has no raster dimensions — attributes should be null
    assertEquals(null, result.attributes());

    verify(store).upload(any(), any(), anyLong(), anyString(), any());
    verify(repository).save(any());
  }

  @Test
  void nonImageUploadForAvatar_throws400() {
    var data = new ByteArrayInputStream("hello world".getBytes());
    var userId = UUID.randomUUID();

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.upload(
                    userId,
                    "doc.pdf",
                    "application/pdf",
                    11,
                    MediaAccess.LOGGED_IN,
                    data,
                    null,
                    MediaUsage.AVATAR));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(store, never()).upload(any(), any(), anyLong(), any(), any());
    verify(repository, never()).save(any());
  }

  @Test
  void nonImageUploadForEventDetail_throws400() {
    var data = new ByteArrayInputStream("plain text".getBytes());
    var userId = UUID.randomUUID();

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.upload(
                    userId,
                    "notes.txt",
                    "text/plain",
                    10,
                    MediaAccess.SCOPED,
                    data,
                    null,
                    MediaUsage.EVENT_DETAIL));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(store, never()).upload(any(), any(), anyLong(), any(), any());
    verify(repository, never()).save(any());
  }

  @Test
  void thumbnailWithNonImageMime_throws400() {
    var data = new ByteArrayInputStream("not an image".getBytes());
    var userId = UUID.randomUUID();
    var originalId = UUID.randomUUID();
    when(repository.findByIdForUpdate(originalId))
        .thenReturn(Optional.of(original(originalId, userId)));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.upload(
                    userId,
                    "thumb.pdf",
                    "application/pdf",
                    12,
                    MediaAccess.LOGGED_IN,
                    data,
                    originalId,
                    MediaUsage.AVATAR));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(store, never()).upload(any(), any(), anyLong(), any(), any());
    verify(repository, never()).save(any());
  }

  private static MediaRecord original(UUID id, UUID ownerId) {
    return new MediaRecord(
        id,
        "media/original",
        "photo.jpg",
        MediaContentType.IMAGE,
        1024,
        MediaAccess.LOGGED_IN,
        ownerId,
        ownerId,
        Instant.now(),
        "image/jpeg",
        null,
        null,
        null);
  }

  private static byte[] sampleJpg() throws IOException {
    return UploadMediaUsecaseTest.class.getResourceAsStream("/media/sample.jpg").readAllBytes();
  }

  private static byte[] sampleWebp() throws IOException {
    return UploadMediaUsecaseTest.class.getResourceAsStream("/media/sample.webp").readAllBytes();
  }
}
