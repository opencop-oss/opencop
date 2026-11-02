// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.api.event.ports.business.usecase.attachment.DeleteEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.DeleteEventAttachmentCommand;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class EventAttachmentDeleteRegistryTest {

  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();
  private static final String OBJECT_ID = UUID.randomUUID().toString();

  @Test
  void delete_authorizesAndStopsAtFirstSupportingUsecase() {
    var policy = mock(EventAttachmentPolicy.class);
    var first = new CapturingDeleteAttachment(false);
    var second = new CapturingDeleteAttachment(true);
    var registry = new EventAttachmentDeleteRegistry(List.of(first, second), policy);
    var principal = principal();
    var attachmentId = UUID.randomUUID();

    registry.delete(principal, SOURCE_ID, OBJECT_ID, attachmentId);

    verify(policy).assertCanDelete(principal, SOURCE_ID, OBJECT_ID, attachmentId);
    assertEquals(attachmentId, first.command.attachmentId());
    assertEquals(attachmentId, second.command.attachmentId());
  }

  @Test
  void delete_whenNoUsecaseSupportsAttachmentThrows404() {
    var registry =
        new EventAttachmentDeleteRegistry(
            List.of(new CapturingDeleteAttachment(false)), mock(EventAttachmentPolicy.class));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> registry.delete(principal(), SOURCE_ID, OBJECT_ID, UUID.randomUUID()));

    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  private static OpenCopPrincipal principal() {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID);
  }

  private static final class CapturingDeleteAttachment implements DeleteEventAttachment {
    private final boolean handled;
    private DeleteEventAttachmentCommand command;

    private CapturingDeleteAttachment(boolean handled) {
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
