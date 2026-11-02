// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import com.opencop.processor.ports.gateway.db.DataSourceTransportGateway;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventAutoAttachmentServiceTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String OBJECT_ID = "track-42";
  private static final String URL = "rtsp://cam.example.com/live";

  private EventAutoAttachmentService service(
      DataSourceTransportGateway gateway,
      MediaUrlRepository mediaUrlRepo,
      EventAttachmentRepository attachmentRepo) {
    return new EventAutoAttachmentService(
        gateway,
        mediaUrlRepo,
        attachmentRepo,
        Caffeine.newBuilder().build(),
        Caffeine.newBuilder().build());
  }

  @Test
  void attachFromRecord_savesUrlAndAttachment_whenSourceIsRtsp() {
    var gateway = mock(DataSourceTransportGateway.class);
    var mediaUrlRepo = mock(MediaUrlRepository.class);
    var attachmentRepo = mock(EventAttachmentRepository.class);
    when(gateway.getRtspUrl(SOURCE_ID)).thenReturn(Optional.of(URL));
    when(attachmentRepo.exists(eq(SOURCE_ID), eq(OBJECT_ID), any())).thenReturn(false);
    var service = service(gateway, mediaUrlRepo, attachmentRepo);

    service.attachFromRecord(SOURCE_ID, OBJECT_ID);

    verify(mediaUrlRepo).save(any(MediaUrlRecord.class));
    verify(attachmentRepo).save(eq(SOURCE_ID), eq(OBJECT_ID), any(UUID.class));
  }

  @Test
  void attachFromRecord_skipsReposAndGateway_onObjectCacheHit() {
    var gateway = mock(DataSourceTransportGateway.class);
    var mediaUrlRepo = mock(MediaUrlRepository.class);
    var attachmentRepo = mock(EventAttachmentRepository.class);
    when(gateway.getRtspUrl(SOURCE_ID)).thenReturn(Optional.of(URL));
    when(attachmentRepo.exists(eq(SOURCE_ID), eq(OBJECT_ID), any())).thenReturn(false);
    var service = service(gateway, mediaUrlRepo, attachmentRepo);

    service.attachFromRecord(SOURCE_ID, OBJECT_ID);
    service.attachFromRecord(SOURCE_ID, OBJECT_ID);

    // Gateway queried once (source cache), repos written once (object cache)
    verify(gateway, times(1)).getRtspUrl(SOURCE_ID);
    verify(mediaUrlRepo, times(1)).save(any());
  }

  @Test
  void attachFromRecord_doesNotCacheObject_whenSaveFails() {
    var gateway = mock(DataSourceTransportGateway.class);
    var mediaUrlRepo = mock(MediaUrlRepository.class);
    var attachmentRepo = mock(EventAttachmentRepository.class);
    when(gateway.getRtspUrl(SOURCE_ID)).thenReturn(Optional.of(URL));
    when(attachmentRepo.exists(eq(SOURCE_ID), eq(OBJECT_ID), any())).thenReturn(false);
    doThrow(new RuntimeException("DB error")).when(mediaUrlRepo).save(any());
    var service = service(gateway, mediaUrlRepo, attachmentRepo);

    service.attachFromRecord(SOURCE_ID, OBJECT_ID);
    service.attachFromRecord(SOURCE_ID, OBJECT_ID);

    // Save retried because the first call was not cached (it failed)
    verify(mediaUrlRepo, times(2)).save(any());
  }

  @Test
  void attachFromRecord_swallowsException_doesNotPropagate() {
    var gateway = mock(DataSourceTransportGateway.class);
    var mediaUrlRepo = mock(MediaUrlRepository.class);
    var attachmentRepo = mock(EventAttachmentRepository.class);
    when(gateway.getRtspUrl(SOURCE_ID)).thenReturn(Optional.of(URL));
    when(attachmentRepo.exists(eq(SOURCE_ID), eq(OBJECT_ID), any())).thenReturn(false);
    doThrow(new RuntimeException("unexpected")).when(mediaUrlRepo).save(any());
    var service = service(gateway, mediaUrlRepo, attachmentRepo);

    assertThatNoException().isThrownBy(() -> service.attachFromRecord(SOURCE_ID, OBJECT_ID));
  }

  @Test
  void attachFromRecord_skipsWrite_whenNotRtspSource() {
    var gateway = mock(DataSourceTransportGateway.class);
    var mediaUrlRepo = mock(MediaUrlRepository.class);
    var attachmentRepo = mock(EventAttachmentRepository.class);
    when(gateway.getRtspUrl(SOURCE_ID)).thenReturn(Optional.empty());
    var service = service(gateway, mediaUrlRepo, attachmentRepo);

    service.attachFromRecord(SOURCE_ID, OBJECT_ID);

    verifyNoInteractions(mediaUrlRepo, attachmentRepo);
  }

  @Test
  void attachFromRecord_queriesGatewayOnce_forNonRtspSource() {
    var gateway = mock(DataSourceTransportGateway.class);
    when(gateway.getRtspUrl(SOURCE_ID)).thenReturn(Optional.empty());
    var service =
        service(gateway, mock(MediaUrlRepository.class), mock(EventAttachmentRepository.class));

    service.attachFromRecord(SOURCE_ID, "obj-1");
    service.attachFromRecord(SOURCE_ID, "obj-2");
    service.attachFromRecord(SOURCE_ID, "obj-3");

    // Source cache hit after first call — gateway queried only once regardless of object count
    verify(gateway, times(1)).getRtspUrl(SOURCE_ID);
  }

  @Test
  void attachFromRecord_attachesOnce_perDistinctObjectId() {
    var gateway = mock(DataSourceTransportGateway.class);
    var mediaUrlRepo = mock(MediaUrlRepository.class);
    var attachmentRepo = mock(EventAttachmentRepository.class);
    when(gateway.getRtspUrl(SOURCE_ID)).thenReturn(Optional.of(URL));
    when(attachmentRepo.exists(any(), any(), any())).thenReturn(false);
    var service = service(gateway, mediaUrlRepo, attachmentRepo);

    service.attachFromRecord(SOURCE_ID, OBJECT_ID);
    service.attachFromRecord(SOURCE_ID, "track-99");

    verify(attachmentRepo).save(eq(SOURCE_ID), eq(OBJECT_ID), any());
    verify(attachmentRepo).save(eq(SOURCE_ID), eq("track-99"), any());
  }

  @Test
  void attachFromRecord_skipsWrite_whenAlreadyExistsInRepo() {
    var gateway = mock(DataSourceTransportGateway.class);
    var mediaUrlRepo = mock(MediaUrlRepository.class);
    var attachmentRepo = mock(EventAttachmentRepository.class);
    when(gateway.getRtspUrl(SOURCE_ID)).thenReturn(Optional.of(URL));
    when(attachmentRepo.exists(eq(SOURCE_ID), eq(OBJECT_ID), any())).thenReturn(true);
    var service = service(gateway, mediaUrlRepo, attachmentRepo);

    service.attachFromRecord(SOURCE_ID, OBJECT_ID);

    verify(mediaUrlRepo, never()).save(any());
    verify(attachmentRepo, never()).save(any(), any(), any());
  }
}
