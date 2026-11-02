// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.media.MediaRepository;
import com.opencop.core.sdk.model.media.MediaAccess;
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

class DeleteMediaUsecaseTest {

  private MediaRepository repository;
  private MediaStore store;
  private DeleteMediaUsecase usecase;

  private static final UUID MEDIA_ID = UUID.randomUUID();
  private static final UUID THUMBNAIL_ID = UUID.randomUUID();
  private static final String BLOB_KEY = "media/2026/03/12/" + MEDIA_ID + "/photo.jpg";
  private static final String THUMBNAIL_BLOB_KEY =
      "media/2026/03/12/" + THUMBNAIL_ID + "/thumb.jpg";

  @BeforeEach
  void setUp() {
    repository = mock(MediaRepository.class);
    store = mock(MediaStore.class);
    usecase = new DeleteMediaUsecase(repository, store);
  }

  @Test
  void notFound_throws404() {
    when(repository.findById(MEDIA_ID)).thenReturn(Optional.empty());

    var ex = assertThrows(GlobalOpenCopException.class, () -> usecase.delete(MEDIA_ID));

    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    verify(repository, never()).deleteById(MEDIA_ID);
    verify(store, never()).delete(BLOB_KEY);
  }

  @Test
  void deletesDbRecordAndBlob() {
    when(repository.findById(MEDIA_ID)).thenReturn(Optional.of(media()));

    usecase.delete(MEDIA_ID);

    verify(repository).deleteById(MEDIA_ID);
    verify(store).delete(BLOB_KEY);
  }

  @Test
  void delete_whenAnotherMediaReferencesBlobKey_deletesDbRecordButKeepsBlob() {
    when(repository.findById(MEDIA_ID)).thenReturn(Optional.of(media()));
    when(repository.existsByBlobKeyExcludingIds(eq(BLOB_KEY), anyCollection())).thenReturn(true);

    usecase.delete(MEDIA_ID);

    verify(repository).deleteById(MEDIA_ID);
    verify(store, never()).delete(BLOB_KEY);
  }

  @Test
  void delete_whenThumbnailBlobKeyIsShared_keepsSharedThumbnailBlob() {
    var thumbnail = thumbnail();
    when(repository.findById(MEDIA_ID)).thenReturn(Optional.of(media()));
    when(repository.findThumbnailsOf(MEDIA_ID)).thenReturn(List.of(thumbnail));
    when(repository.existsByBlobKeyExcludingIds(eq(BLOB_KEY), anyCollection())).thenReturn(false);
    when(repository.existsByBlobKeyExcludingIds(eq(THUMBNAIL_BLOB_KEY), anyCollection()))
        .thenReturn(true);

    usecase.delete(MEDIA_ID);

    verify(repository).deleteById(MEDIA_ID);
    verify(store).delete(BLOB_KEY);
    verify(store, never()).delete(THUMBNAIL_BLOB_KEY);
  }

  @Test
  void delete_whenThumbnailBlobKeyIsUnique_deletesThumbnailBlob() {
    var thumbnail = thumbnail();
    when(repository.findById(MEDIA_ID)).thenReturn(Optional.of(media()));
    when(repository.findThumbnailsOf(MEDIA_ID)).thenReturn(List.of(thumbnail));

    usecase.delete(MEDIA_ID);

    verify(repository).deleteById(MEDIA_ID);
    verify(store).delete(BLOB_KEY);
    verify(store).delete(THUMBNAIL_BLOB_KEY);
  }

  private static MediaRecord media() {
    return new MediaRecord(
        MEDIA_ID,
        BLOB_KEY,
        "photo.jpg",
        MediaContentType.IMAGE,
        1024,
        MediaAccess.LOGGED_IN,
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instant.now(),
        "image/jpeg",
        null,
        null,
        null);
  }

  private static MediaRecord thumbnail() {
    return new MediaRecord(
        THUMBNAIL_ID,
        THUMBNAIL_BLOB_KEY,
        "thumb.jpg",
        MediaContentType.IMAGE,
        128,
        MediaAccess.LOGGED_IN,
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instant.now(),
        "image/jpeg",
        null,
        MEDIA_ID,
        null);
  }
}
