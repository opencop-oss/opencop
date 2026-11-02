// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.attachment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand.EventAttachmentFile;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand.FilePayload;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand.UrlPayload;
import com.opencop.core.api.event.ports.business.usecase.attachment.EventAttachmentType;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.api.general.ports.usecase.media.MediaNotFoundException;
import com.opencop.core.api.general.ports.usecase.media.MediaView;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.ports.usecase.media.UploadMedia;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
class CreateFileEventAttachmentUsecaseTest {

  @Mock private UploadMedia uploadMedia;
  @Mock private RetrieveMedia retrieveMedia;
  @Mock private EventAttachmentRepository eventAttachmentRepository;

  private CreateFileEventAttachmentUsecase usecase;

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String OBJECT_ID = UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    usecase =
        new CreateFileEventAttachmentUsecase(uploadMedia, retrieveMedia, eventAttachmentRepository);
  }

  @Test
  void supportsOnlyMultipartFilePayloads() {
    var filePayload = new FilePayload(file("photo.jpg", "image/jpeg", 1), List.of());
    var urlPayload = new UrlPayload("rtsp://camera.local/stream", StreamKind.RTSP);
    assertTrue(
        usecase.supports(
            MediaType.MULTIPART_FORM_DATA, EventAttachmentType.FILE, command(filePayload)));
    assertFalse(
        usecase.supports(
            MediaType.APPLICATION_JSON, EventAttachmentType.FILE, command(filePayload)));
    assertFalse(
        usecase.supports(
            MediaType.MULTIPART_FORM_DATA, EventAttachmentType.URL, command(filePayload)));
    assertFalse(
        usecase.supports(
            MediaType.MULTIPART_FORM_DATA, EventAttachmentType.FILE, command(urlPayload)));
  }

  @Test
  void create_uploadsOriginalAndThumbnailsAndReturnsFileView() throws MediaNotFoundException {
    var mediaId = UUID.randomUUID();
    var thumbId = UUID.randomUUID();
    var originalRecord = mediaRecord(mediaId);
    var thumbnailRecord = mediaRecord(thumbId);
    var command =
        command(
            new FilePayload(
                file("photo.jpg", "image/jpeg", 3), List.of(file("thumb.webp", "image/webp", 2))));
    when(uploadMedia.upload(
            eq(USER_ID),
            eq("photo.jpg"),
            eq("image/jpeg"),
            eq(3L),
            eq(MediaAccess.SCOPED),
            any(),
            isNull(),
            eq(MediaUsage.EVENT_DETAIL)))
        .thenReturn(originalRecord);
    when(uploadMedia.upload(
            eq(USER_ID),
            eq("thumb.webp"),
            eq("image/webp"),
            eq(2L),
            eq(MediaAccess.SCOPED),
            any(),
            eq(mediaId),
            eq(MediaUsage.EVENT_DETAIL)))
        .thenReturn(thumbnailRecord);
    when(retrieveMedia.getWithoutAccessCheck(mediaId))
        .thenReturn(new MediaView(originalRecord, "https://blob/photo.jpg", List.of()));

    var result = usecase.create(command);

    var fileView = assertInstanceOf(AttachmentView.File.class, result);
    assertEquals(originalRecord, fileView.record());
    verify(eventAttachmentRepository).save(SOURCE_ID, OBJECT_ID, mediaId);
  }

  private static CreateEventAttachmentCommand command(
      CreateEventAttachmentCommand.Payload payload) {
    return new CreateEventAttachmentCommand(principal(), SOURCE_ID, OBJECT_ID, payload);
  }

  private static EventAttachmentFile file(String filename, String contentType, long size) {
    return new EventAttachmentFile(
        filename, contentType, size, new ByteArrayInputStream(new byte[(int) size]));
  }

  private static MediaRecord mediaRecord(UUID id) {
    return new MediaRecord(
        id,
        "media/" + id + "/photo.jpg",
        "photo.jpg",
        MediaContentType.IMAGE,
        1024,
        MediaAccess.SCOPED,
        USER_ID,
        USER_ID,
        Instant.now(),
        "image/jpeg",
        null,
        null,
        MediaUsage.EVENT_DETAIL);
  }

  private static OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
  }
}
