// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.media.MediaRepository;
import com.opencop.core.api.general.ports.usecase.media.MediaNotFoundException;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaAttributes;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.MediaStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RetrieveMediaUsecaseTest {

  private MediaRepository repository;
  private MediaStore store;
  private RetrieveMediaUsecase usecase;

  private static final UUID OWNER_ID = UUID.randomUUID();
  private static final UUID OTHER_USER_ID = UUID.randomUUID();
  private static final UUID MEDIA_ID = UUID.randomUUID();
  private static final String BLOB_KEY = "media/2026/03/12/" + MEDIA_ID + "/photo.jpg";
  private static final String SAS_URL = "https://storage.blob.core.windows.net/..." + BLOB_KEY;

  @BeforeEach
  void setUp() {
    repository = mock(MediaRepository.class);
    store = mock(MediaStore.class);
    usecase = new RetrieveMediaUsecase(repository, store);
    when(repository.findThumbnailsOf(MEDIA_ID)).thenReturn(List.of());
  }

  @Test
  void notFound_throws404() {
    when(repository.findById(MEDIA_ID)).thenReturn(Optional.empty());

    var ex =
        assertThrows(MediaNotFoundException.class, () -> usecase.getAsUser(MEDIA_ID, OWNER_ID));

    assertEquals("Media not found: " + MEDIA_ID, ex.getMessage());
    verify(store, never()).resolve(BLOB_KEY);
  }

  @Test
  void loggedInMedia_anyUserCanAccess() throws MediaNotFoundException {
    var record = media(MediaAccess.LOGGED_IN);
    when(repository.findById(MEDIA_ID)).thenReturn(Optional.of(record));
    when(store.resolve(BLOB_KEY)).thenReturn(SAS_URL);

    var view = usecase.getAsUser(MEDIA_ID, OTHER_USER_ID);

    assertEquals(record, view.record());
    assertEquals(SAS_URL, view.url());
  }

  @Test
  void scopedMedia_anyNonAdminDenied() {
    when(repository.findById(MEDIA_ID)).thenReturn(Optional.of(media(MediaAccess.SCOPED)));

    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.getAsUser(MEDIA_ID, OTHER_USER_ID));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    verify(store, never()).resolve(BLOB_KEY);
  }

  @Test
  void scopedMedia_ownerAlsoDenied() {
    when(repository.findById(MEDIA_ID)).thenReturn(Optional.of(media(MediaAccess.SCOPED)));

    var ex =
        assertThrows(GlobalOpenCopException.class, () -> usecase.getAsUser(MEDIA_ID, OWNER_ID));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
  }

  @Test
  void adminBypassesScopedAccess() throws MediaNotFoundException {
    var record = media(MediaAccess.SCOPED);
    when(repository.findById(MEDIA_ID)).thenReturn(Optional.of(record));
    when(store.resolve(BLOB_KEY)).thenReturn(SAS_URL);

    var view = usecase.getWithoutAccessCheck(MEDIA_ID);

    assertEquals(record, view.record());
    assertEquals(SAS_URL, view.url());
  }

  @Test
  void original_withNoThumbnails_returnsEmptyThumbnailList() throws MediaNotFoundException {
    var record = media(MediaAccess.LOGGED_IN);
    when(repository.findById(MEDIA_ID)).thenReturn(Optional.of(record));
    when(store.resolve(BLOB_KEY)).thenReturn(SAS_URL);
    when(repository.findThumbnailsOf(MEDIA_ID)).thenReturn(List.of());

    var view = usecase.getAsUser(MEDIA_ID, OWNER_ID);

    assertEquals(List.of(), view.thumbnails());
    verify(repository).findThumbnailsOf(MEDIA_ID);
  }

  @Test
  void original_withThumbnails_resolvesThumbnailUrls() throws MediaNotFoundException {
    var record = media(MediaAccess.LOGGED_IN);
    when(repository.findById(MEDIA_ID)).thenReturn(Optional.of(record));
    when(store.resolve(BLOB_KEY)).thenReturn(SAS_URL);

    var thumbId = UUID.randomUUID();
    var thumbBlobKey = "media/2026/03/16/" + thumbId + "/thumb-128.webp";
    var thumbSasUrl = "https://storage.blob.core.windows.net/..." + thumbBlobKey;
    var thumb =
        new MediaRecord(
            thumbId,
            thumbBlobKey,
            "thumb-128.webp",
            MediaContentType.IMAGE,
            512,
            MediaAccess.LOGGED_IN,
            OWNER_ID,
            OWNER_ID,
            Instant.now(),
            "image/webp",
            new MediaAttributes.Image(128, 128),
            MEDIA_ID,
            null);
    when(repository.findThumbnailsOf(MEDIA_ID)).thenReturn(List.of(thumb));
    when(store.resolve(thumbBlobKey)).thenReturn(thumbSasUrl);

    var view = usecase.getAsUser(MEDIA_ID, OWNER_ID);

    assertEquals(1, view.thumbnails().size());
    var ref = view.thumbnails().getFirst();
    assertEquals(thumbId, ref.id());
    assertEquals(thumbSasUrl, ref.url());
    assertEquals(128, ref.widthPx());
    assertEquals(128, ref.heightPx());
    assertEquals("image/webp", ref.mimeType());
  }

  @Test
  void original_withMultipleThumbnails_resolvesAll() throws MediaNotFoundException {
    var record = media(MediaAccess.LOGGED_IN);
    when(repository.findById(MEDIA_ID)).thenReturn(Optional.of(record));
    when(store.resolve(BLOB_KEY)).thenReturn(SAS_URL);

    var thumb64Id = UUID.randomUUID();
    var thumb256Id = UUID.randomUUID();
    var thumb64Key = "media/2026/03/16/" + thumb64Id + "/thumb-64.webp";
    var thumb256Key = "media/2026/03/16/" + thumb256Id + "/thumb-256.webp";
    when(store.resolve(thumb64Key)).thenReturn("sas:" + thumb64Key);
    when(store.resolve(thumb256Key)).thenReturn("sas:" + thumb256Key);

    when(repository.findThumbnailsOf(MEDIA_ID))
        .thenReturn(
            List.of(
                new MediaRecord(
                    thumb64Id,
                    thumb64Key,
                    "thumb-64.webp",
                    MediaContentType.IMAGE,
                    256,
                    MediaAccess.LOGGED_IN,
                    OWNER_ID,
                    OWNER_ID,
                    Instant.now(),
                    "image/webp",
                    new MediaAttributes.Image(64, 64),
                    MEDIA_ID,
                    null),
                new MediaRecord(
                    thumb256Id,
                    thumb256Key,
                    "thumb-256.webp",
                    MediaContentType.IMAGE,
                    2048,
                    MediaAccess.LOGGED_IN,
                    OWNER_ID,
                    OWNER_ID,
                    Instant.now(),
                    "image/webp",
                    new MediaAttributes.Image(256, 256),
                    MEDIA_ID,
                    null)));

    var view = usecase.getAsUser(MEDIA_ID, OWNER_ID);

    assertEquals(2, view.thumbnails().size());
    assertEquals(64, view.thumbnails().get(0).widthPx());
    assertEquals(256, view.thumbnails().get(1).widthPx());
  }

  @Test
  void thumbnail_returnsEmptyThumbnailList() throws MediaNotFoundException {
    var originalId = UUID.randomUUID();
    var thumbId = UUID.randomUUID();
    var thumbBlobKey = "media/2026/03/16/" + thumbId + "/thumb-128.webp";
    var thumbSasUrl = "https://storage.blob.core.windows.net/..." + thumbBlobKey;
    var thumbRecord =
        new MediaRecord(
            thumbId,
            thumbBlobKey,
            "thumb-128.webp",
            MediaContentType.IMAGE,
            512,
            MediaAccess.LOGGED_IN,
            OWNER_ID,
            OWNER_ID,
            Instant.now(),
            "image/webp",
            new MediaAttributes.Image(128, 128),
            originalId,
            null);
    when(repository.findById(thumbId)).thenReturn(Optional.of(thumbRecord));
    when(store.resolve(thumbBlobKey)).thenReturn(thumbSasUrl);

    var view = usecase.getAsUser(thumbId, OWNER_ID);

    assertEquals(List.of(), view.thumbnails());
    // Should NOT query for sub-thumbnails of a thumbnail
    verify(repository, never()).findThumbnailsOf(thumbId);
  }

  private static MediaRecord media(MediaAccess access) {
    return new MediaRecord(
        MEDIA_ID,
        BLOB_KEY,
        "photo.jpg",
        MediaContentType.IMAGE,
        1024,
        access,
        OWNER_ID,
        OWNER_ID,
        Instant.now(),
        "image/jpeg",
        null,
        null,
        null);
  }
}
