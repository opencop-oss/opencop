// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand.FilePayload;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand.UrlPayload;
import com.opencop.core.api.event.ports.business.usecase.attachment.EventAttachmentType;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

class EventAttachmentCreateRegistryTest {

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String OBJECT_ID = UUID.randomUUID().toString();

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final EventAttachmentPolicy attachmentPolicy = mock(EventAttachmentPolicy.class);

  @Test
  void create_jsonRequestDispatchesUrlPayloadToSupportingUsecase() throws IOException {
    var urlUsecase = new CapturingCreateAttachment(EventAttachmentType.URL);
    var fileUsecase = new CapturingCreateAttachment(EventAttachmentType.FILE);
    var registry =
        new EventAttachmentCreateRegistry(
            List.of(fileUsecase, urlUsecase), attachmentPolicy, objectMapper);
    var request = jsonRequest("rtsp://camera.local/stream");
    request.setContentType("application/json;charset=UTF-8");

    var result = registry.create(principal(), SOURCE_ID, OBJECT_ID, request);

    assertEquals(urlUsecase.view, result);
    assertEquals(SOURCE_ID, urlUsecase.command.sourceId());
    var payload = assertInstanceOf(UrlPayload.class, urlUsecase.command.payload());
    assertEquals("rtsp://camera.local/stream", payload.url());
    assertEquals(StreamKind.RTSP, payload.streamKind());
  }

  @Test
  void create_missingStreamKindInfersOnServer() throws IOException {
    var urlUsecase = new CapturingCreateAttachment(EventAttachmentType.URL);
    var registry =
        new EventAttachmentCreateRegistry(List.of(urlUsecase), attachmentPolicy, objectMapper);
    var request = new MockHttpServletRequest();
    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
    request.setContent(
        objectMapper.writeValueAsBytes(
            new EventAttachmentCreateRegistry.RegisterUrlRequest(
                "rtsp://camera.local/stream", null)));

    registry.create(principal(), SOURCE_ID, OBJECT_ID, request);

    var payload = assertInstanceOf(UrlPayload.class, urlUsecase.command.payload());
    assertNull(payload.streamKind());
  }

  @Test
  void create_unknownStreamKindThrows400() throws IOException {
    var registry =
        new EventAttachmentCreateRegistry(
            List.of(new CapturingCreateAttachment(EventAttachmentType.URL)),
            attachmentPolicy,
            objectMapper);
    var request = new MockHttpServletRequest();
    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
    request.setContent(
        objectMapper.writeValueAsBytes(
            new EventAttachmentCreateRegistry.RegisterUrlRequest(
                "https://cdn.example.com/live.m3u8", "BOGUS")));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> registry.create(principal(), SOURCE_ID, OBJECT_ID, request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
  }

  /** A newline in the raw value must not be able to forge a fake log entry. */
  @Test
  void create_unknownStreamKindWithNewlineIsSanitizedInMessage() throws IOException {
    var registry =
        new EventAttachmentCreateRegistry(
            List.of(new CapturingCreateAttachment(EventAttachmentType.URL)),
            attachmentPolicy,
            objectMapper);
    var request = new MockHttpServletRequest();
    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
    request.setContent(
        objectMapper.writeValueAsBytes(
            new EventAttachmentCreateRegistry.RegisterUrlRequest(
                "https://cdn.example.com/live.m3u8",
                "BOGUS\n2099-01-01 WARN forged.Logger -- forged entry")));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> registry.create(principal(), SOURCE_ID, OBJECT_ID, request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertFalse(ex.getMessage().contains("\n"));
    assertFalse(ex.getMessage().contains("\r"));
    assertTrue(ex.getMessage().contains("Allowed: "));
  }

  @Test
  void create_multipartRequestDispatchesFilePayloadWithThumbnails() throws IOException {
    var fileUsecase = new CapturingCreateAttachment(EventAttachmentType.FILE);
    var registry =
        new EventAttachmentCreateRegistry(List.of(fileUsecase), attachmentPolicy, objectMapper);
    var request = multipartRequest();

    var result = registry.create(principal(), SOURCE_ID, OBJECT_ID, request);

    assertEquals(fileUsecase.view, result);
    var payload = assertInstanceOf(FilePayload.class, fileUsecase.command.payload());
    assertEquals("photo.jpg", payload.original().originalFilename());
    assertEquals("image/jpeg", payload.original().contentType());
    assertEquals(3L, payload.original().size());
    assertEquals(1, payload.thumbnails().size());
    assertEquals("thumb.webp", payload.thumbnails().getFirst().originalFilename());
  }

  @Test
  void create_missingContentTypeThrows400() {
    var registry = new EventAttachmentCreateRegistry(List.of(), attachmentPolicy, objectMapper);
    var request = new MockHttpServletRequest();

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> registry.create(principal(), SOURCE_ID, OBJECT_ID, request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
  }

  @Test
  void create_unsupportedContentTypeThrows415() {
    var registry = new EventAttachmentCreateRegistry(List.of(), attachmentPolicy, objectMapper);
    var request = new MockHttpServletRequest();
    request.setContentType(MediaType.TEXT_PLAIN_VALUE);

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> registry.create(principal(), SOURCE_ID, OBJECT_ID, request));

    assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getHttpStatus());
  }

  @Test
  void create_jsonWithBlankUrlThrows400() throws IOException {
    var registry =
        new EventAttachmentCreateRegistry(
            List.of(new CapturingCreateAttachment(EventAttachmentType.URL)),
            attachmentPolicy,
            objectMapper);
    var request = jsonRequest(null);

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> registry.create(principal(), SOURCE_ID, OBJECT_ID, request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
  }

  @Test
  void create_multipartWithoutOriginalThrows400() {
    var registry =
        new EventAttachmentCreateRegistry(
            List.of(new CapturingCreateAttachment(EventAttachmentType.FILE)),
            attachmentPolicy,
            objectMapper);
    var request = new MockMultipartHttpServletRequest();
    request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE);

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> registry.create(principal(), SOURCE_ID, OBJECT_ID, request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
  }

  private MockHttpServletRequest jsonRequest(String url) throws IOException {
    var request = new MockHttpServletRequest();
    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
    request.setContent(
        objectMapper.writeValueAsBytes(
            new EventAttachmentCreateRegistry.RegisterUrlRequest(url, "RTSP")));
    return request;
  }

  private static MockMultipartHttpServletRequest multipartRequest() {
    var request = new MockMultipartHttpServletRequest();
    request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=test");
    request.addFile(
        new MockMultipartFile("original", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3}));
    request.addFile(
        new MockMultipartFile("thumbnails", "thumb.webp", "image/webp", new byte[] {4}));
    return request;
  }

  private static OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
  }

  private static final class CapturingCreateAttachment implements CreateEventAttachment {
    private final EventAttachmentType supportedType;
    private final AttachmentView view =
        new AttachmentView.Url(
            new MediaUrlRecord(
                UUID.randomUUID(),
                "rtsp://camera.local/stream",
                StreamKind.RTSP,
                MediaAccess.SCOPED,
                USER_ID,
                USER_ID,
                Instant.now(),
                MediaUsage.EVENT_DETAIL));
    private CreateEventAttachmentCommand command;

    private CapturingCreateAttachment(EventAttachmentType supportedType) {
      this.supportedType = supportedType;
    }

    @Override
    public boolean supports(
        MediaType contentType,
        EventAttachmentType attachmentType,
        CreateEventAttachmentCommand command) {
      return switch (supportedType) {
        case FILE ->
            MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType)
                && attachmentType == EventAttachmentType.FILE
                && command.payload() instanceof FilePayload;
        case URL ->
            MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                && attachmentType == EventAttachmentType.URL
                && command.payload() instanceof UrlPayload;
      };
    }

    @Override
    public AttachmentView create(CreateEventAttachmentCommand command) {
      this.command = command;
      return view;
    }
  }
}
