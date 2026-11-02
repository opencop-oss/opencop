// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.api.event.business.policy.EventAuthorizationPolicy;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand;
import com.opencop.core.api.event.ports.business.usecase.attachment.DeleteEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.DeleteEventAttachmentCommand;
import com.opencop.core.api.event.ports.business.usecase.attachment.EventAttachmentType;
import com.opencop.core.api.event.ports.business.usecase.attachment.GetEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.GetEventAttachmentCommand;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.api.general.ports.usecase.media.PrepareAttachmentStream;
import com.opencop.core.api.general.technical.gateway.authorization.AuthorizationService;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class EventAttachmentEndpointTest {

  @Mock private EventAttachmentRepository eventAttachmentRepository;
  @Mock private EventAuthorizationPolicy eventAuth;
  @Mock private AuthorizationService authz;
  @Mock private PrepareAttachmentStream prepareAttachmentStream;

  private EventAttachmentEndpoint endpoint;
  private ReturningCreateAttachment createUsecase;
  private ReturningGetAttachment getUsecase;
  private ReturningDeleteAttachment deleteUsecase;

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String OBJECT_ID = UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    createUsecase =
        new ReturningCreateAttachment(
            new AttachmentView.Url(urlRecord(UUID.randomUUID(), "rtsp://camera.local/stream")));
    var createRegistry =
        new EventAttachmentCreateRegistry(
            List.of(createUsecase),
            new EventAttachmentPolicy(eventAuth, eventAttachmentRepository, authz, 20),
            new ObjectMapper());
    getUsecase =
        new ReturningGetAttachment(
            new AttachmentView.Url(urlRecord(UUID.randomUUID(), "rtsp://camera.local/stream")));
    var getRegistry =
        new EventAttachmentGetRegistry(
            List.of(getUsecase),
            new EventAttachmentPolicy(eventAuth, eventAttachmentRepository, authz, 20),
            eventAttachmentRepository);
    deleteUsecase = new ReturningDeleteAttachment(true);
    var deleteRegistry =
        new EventAttachmentDeleteRegistry(
            List.of(deleteUsecase),
            new EventAttachmentPolicy(eventAuth, eventAttachmentRepository, authz, 20));
    endpoint =
        new EventAttachmentEndpoint(
            createRegistry, getRegistry, deleteRegistry, prepareAttachmentStream);
  }

  @Test
  void create_returnsCreatedJsonFromRegistryResult() throws IOException {
    var request = jsonRequest("rtsp://camera.local/stream");

    var response = endpoint.create(principal(), SOURCE_ID, OBJECT_ID, request);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
    assertEquals(createUsecase.view, response.getBody());
    assertEquals(SOURCE_ID, createUsecase.command.sourceId());
    assertEquals(OBJECT_ID, createUsecase.command.objectId());
    assertEquals(USER_ID, createUsecase.command.userId());
  }

  @Test
  void list_returnsFileAndUrlAttachments() {
    var mediaId = UUID.randomUUID();
    var urlId = UUID.randomUUID();
    when(eventAttachmentRepository.findAttachmentIdsByObjectId(SOURCE_ID, OBJECT_ID))
        .thenReturn(List.of(mediaId, urlId));

    var response = endpoint.list(principal(), SOURCE_ID, OBJECT_ID);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(2, response.getBody().size());
    assertEquals(
        List.of(mediaId, urlId),
        getUsecase.commands.stream().map(GetEventAttachmentCommand::attachmentId).toList());
    verify(eventAuth).assertMember(any(OpenCopPrincipal.class), eq(SOURCE_ID), eq(OBJECT_ID));
  }

  @Test
  void list_eventNotFound_throws404() {
    doThrow(new GlobalOpenCopException("Event not found", HttpStatus.NOT_FOUND))
        .when(eventAuth)
        .assertMember(any(OpenCopPrincipal.class), eq(SOURCE_ID), eq(OBJECT_ID));

    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> endpoint.list(principal(), SOURCE_ID, OBJECT_ID));

    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  @Test
  void get_delegatesToGetRegistryAndReturnsView() {
    var attachmentId = UUID.randomUUID();
    when(eventAttachmentRepository.exists(SOURCE_ID, OBJECT_ID, attachmentId)).thenReturn(true);

    var response = endpoint.get(principal(), SOURCE_ID, OBJECT_ID, attachmentId);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(getUsecase.view, response.getBody());
    assertEquals(attachmentId, getUsecase.commands.getFirst().attachmentId());
  }

  @Test
  void get_notFound_throws404() {
    var id = UUID.randomUUID();
    when(eventAttachmentRepository.exists(SOURCE_ID, OBJECT_ID, id)).thenReturn(false);

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> endpoint.get(principal(), SOURCE_ID, OBJECT_ID, id));

    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  @Test
  void delete_delegatesToDeleteRegistryAndReturnsNoContent() {
    var attachmentId = UUID.randomUUID();
    when(eventAttachmentRepository.exists(SOURCE_ID, OBJECT_ID, attachmentId)).thenReturn(true);

    var response = endpoint.delete(principal(), SOURCE_ID, OBJECT_ID, attachmentId);

    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    assertEquals(attachmentId, deleteUsecase.command.attachmentId());
  }

  @Test
  void delete_whenAttachmentAssociationMissingThrows404() {
    var id = UUID.randomUUID();
    when(eventAttachmentRepository.exists(SOURCE_ID, OBJECT_ID, id)).thenReturn(false);

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> endpoint.delete(principal(), SOURCE_ID, OBJECT_ID, id));

    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    verify(eventAttachmentRepository, never()).delete(any(), any(), any());
  }

  @Test
  void delete_whenResourceArchived_throws409() {
    var mediaId = UUID.randomUUID();
    doThrow(new GlobalOpenCopException("Archived", HttpStatus.CONFLICT))
        .when(eventAuth)
        .assertEditorForActiveResource(
            any(OpenCopPrincipal.class), eq(SOURCE_ID), eq(OBJECT_ID), eq("delete attachment"));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> endpoint.delete(principal(), SOURCE_ID, OBJECT_ID, mediaId));

    assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    verify(eventAttachmentRepository, never()).delete(any(), any(), any());
  }

  private static OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
  }

  private MockMultipartHttpServletRequest jsonRequest(String url) throws IOException {
    var request = new MockMultipartHttpServletRequest();
    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
    request.setContent(
        new ObjectMapper()
            .writeValueAsBytes(new EventAttachmentCreateRegistry.RegisterUrlRequest(url, "RTSP")));
    return request;
  }

  private static MediaRecord mediaRecord(UUID id) {
    return mediaRecord(id, USER_ID);
  }

  private static MediaRecord mediaRecord(UUID id, UUID ownerId) {
    return new MediaRecord(
        id,
        "media/" + id + "/photo.jpg",
        "photo.jpg",
        MediaContentType.IMAGE,
        1024,
        MediaAccess.SCOPED,
        ownerId,
        ownerId,
        Instant.now(),
        "image/jpeg",
        null,
        null,
        MediaUsage.EVENT_DETAIL);
  }

  private static MediaUrlRecord urlRecord(UUID id, String url) {
    return urlRecord(id, USER_ID, url);
  }

  private static MediaUrlRecord urlRecord(UUID id, UUID ownerId, String url) {
    return new MediaUrlRecord(
        id,
        url,
        StreamKind.RTSP,
        MediaAccess.SCOPED,
        ownerId,
        ownerId,
        Instant.now(),
        MediaUsage.EVENT_DETAIL);
  }

  private static final class ReturningCreateAttachment implements CreateEventAttachment {
    private final AttachmentView view;
    private CreateEventAttachmentCommand command;

    private ReturningCreateAttachment(AttachmentView view) {
      this.view = view;
    }

    @Override
    public boolean supports(
        MediaType contentType,
        EventAttachmentType attachmentType,
        CreateEventAttachmentCommand command) {
      return true;
    }

    @Override
    public AttachmentView create(CreateEventAttachmentCommand command) {
      this.command = command;
      return view;
    }
  }

  private static final class ReturningGetAttachment implements GetEventAttachment {
    private final AttachmentView view;
    private final List<GetEventAttachmentCommand> commands = new ArrayList<>();

    private ReturningGetAttachment(AttachmentView view) {
      this.view = view;
    }

    @Override
    public Optional<AttachmentView> resolve(GetEventAttachmentCommand command) {
      commands.add(command);
      return Optional.of(view);
    }
  }

  private static final class ReturningDeleteAttachment implements DeleteEventAttachment {
    private final boolean handled;
    private DeleteEventAttachmentCommand command;

    private ReturningDeleteAttachment(boolean handled) {
      this.handled = handled;
    }

    @Override
    public Optional<ResolvedDeleteEventAttachment> resolve(DeleteEventAttachmentCommand command) {
      this.command = command;
      if (!handled) {
        return Optional.empty();
      }
      return Optional.of(() -> {});
    }
  }
}
