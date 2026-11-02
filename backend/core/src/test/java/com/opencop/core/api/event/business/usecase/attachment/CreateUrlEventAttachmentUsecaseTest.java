// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.attachment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand.FilePayload;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand.UrlPayload;
import com.opencop.core.api.event.ports.business.usecase.attachment.EventAttachmentType;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.ports.usecase.media.RegisterMediaUrl;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
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
class CreateUrlEventAttachmentUsecaseTest {

  @Mock private RegisterMediaUrl registerMediaUrl;
  @Mock private EventAttachmentRepository eventAttachmentRepository;

  private CreateUrlEventAttachmentUsecase usecase;

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String OBJECT_ID = UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    usecase = new CreateUrlEventAttachmentUsecase(registerMediaUrl, eventAttachmentRepository);
  }

  @Test
  void supportsOnlyJsonUrlPayloads() {
    var urlPayload = new UrlPayload("rtsp://camera.local/stream", StreamKind.RTSP);
    var filePayload = new FilePayload(null, List.of());
    assertTrue(
        usecase.supports(MediaType.APPLICATION_JSON, EventAttachmentType.URL, command(urlPayload)));
    assertFalse(
        usecase.supports(
            MediaType.MULTIPART_FORM_DATA, EventAttachmentType.URL, command(urlPayload)));
    assertFalse(
        usecase.supports(
            MediaType.APPLICATION_JSON, EventAttachmentType.FILE, command(urlPayload)));
    assertFalse(
        usecase.supports(
            MediaType.APPLICATION_JSON, EventAttachmentType.URL, command(filePayload)));
  }

  @Test
  void create_registersUrlAndReturnsUrlView() {
    var urlId = UUID.randomUUID();
    var mediaUrl = urlRecord(urlId, "rtsp://camera.local/stream");
    var command = command(new UrlPayload("rtsp://camera.local/stream", StreamKind.RTSP));
    when(registerMediaUrl.register(
            eq(USER_ID),
            eq("rtsp://camera.local/stream"),
            eq(StreamKind.RTSP),
            eq(MediaAccess.SCOPED),
            eq(MediaUsage.EVENT_DETAIL)))
        .thenReturn(mediaUrl);

    var result = usecase.create(command);

    var urlView = assertInstanceOf(AttachmentView.Url.class, result);
    assertEquals(mediaUrl, urlView.record());
    verify(eventAttachmentRepository).save(SOURCE_ID, OBJECT_ID, urlId);
  }

  private static CreateEventAttachmentCommand command(
      CreateEventAttachmentCommand.Payload payload) {
    return new CreateEventAttachmentCommand(principal(), SOURCE_ID, OBJECT_ID, payload);
  }

  private static MediaUrlRecord urlRecord(UUID id, String url) {
    return new MediaUrlRecord(
        id,
        url,
        StreamKind.RTSP,
        MediaAccess.SCOPED,
        USER_ID,
        USER_ID,
        Instant.now(),
        MediaUsage.EVENT_DETAIL);
  }

  private static OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
  }
}
