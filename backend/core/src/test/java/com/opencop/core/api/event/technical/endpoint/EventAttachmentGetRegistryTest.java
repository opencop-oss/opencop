// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.api.event.ports.business.usecase.attachment.GetEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.GetEventAttachmentCommand;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class EventAttachmentGetRegistryTest {

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String OBJECT_ID = UUID.randomUUID().toString();

  @Test
  void get_authorizesAndReturnsFirstResolvedAttachment() {
    var policy = mock(EventAttachmentPolicy.class);
    var repository = mock(EventAttachmentRepository.class);
    var first = new CapturingGetAttachment(Optional.empty());
    var view = new AttachmentView.Url(urlRecord(UUID.randomUUID()));
    var second = new CapturingGetAttachment(Optional.of(view));
    var registry = new EventAttachmentGetRegistry(List.of(first, second), policy, repository);
    var principal = principal();
    var attachmentId = UUID.randomUUID();

    var result = registry.get(principal, SOURCE_ID, OBJECT_ID, attachmentId);

    assertEquals(view, result);
    verify(policy).assertCanGet(principal, SOURCE_ID, OBJECT_ID, attachmentId);
    assertEquals(attachmentId, first.commands.getFirst().attachmentId());
    assertEquals(attachmentId, second.commands.getFirst().attachmentId());
  }

  @Test
  void get_whenNoUsecaseResolvesAttachmentThrows404() {
    var registry =
        new EventAttachmentGetRegistry(
            List.of(new CapturingGetAttachment(Optional.empty())),
            mock(EventAttachmentPolicy.class),
            mock(EventAttachmentRepository.class));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> registry.get(principal(), SOURCE_ID, OBJECT_ID, UUID.randomUUID()));

    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  @Test
  void list_authorizesAndSkipsUnresolvedAttachments() {
    var policy = mock(EventAttachmentPolicy.class);
    var repository = mock(EventAttachmentRepository.class);
    var firstId = UUID.randomUUID();
    var secondId = UUID.randomUUID();
    var view = new AttachmentView.Url(urlRecord(firstId));
    when(repository.findAttachmentIdsByObjectId(SOURCE_ID, OBJECT_ID))
        .thenReturn(List.of(firstId, secondId));
    var registry =
        new EventAttachmentGetRegistry(
            List.of(new MatchingGetAttachment(firstId, view)), policy, repository);
    var principal = principal();

    var result = registry.list(principal, SOURCE_ID, OBJECT_ID);

    assertEquals(List.of(view), result);
    verify(policy).assertCanList(principal, SOURCE_ID, OBJECT_ID);
  }

  private static OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
  }

  private static MediaUrlRecord urlRecord(UUID id) {
    return new MediaUrlRecord(
        id,
        "rtsp://camera.local/stream",
        StreamKind.RTSP,
        MediaAccess.SCOPED,
        USER_ID,
        USER_ID,
        Instant.now(),
        MediaUsage.EVENT_DETAIL);
  }

  private static final class CapturingGetAttachment implements GetEventAttachment {
    private final Optional<AttachmentView> result;
    private final List<GetEventAttachmentCommand> commands = new ArrayList<>();

    private CapturingGetAttachment(Optional<AttachmentView> result) {
      this.result = result;
    }

    @Override
    public Optional<AttachmentView> resolve(GetEventAttachmentCommand command) {
      commands.add(command);
      return result;
    }
  }

  private static final class MatchingGetAttachment implements GetEventAttachment {
    private final UUID attachmentId;
    private final AttachmentView view;

    private MatchingGetAttachment(UUID attachmentId, AttachmentView view) {
      this.attachmentId = attachmentId;
      this.view = view;
    }

    @Override
    public Optional<AttachmentView> resolve(GetEventAttachmentCommand command) {
      if (attachmentId.equals(command.attachmentId())) {
        return Optional.of(view);
      }
      return Optional.empty();
    }
  }
}
