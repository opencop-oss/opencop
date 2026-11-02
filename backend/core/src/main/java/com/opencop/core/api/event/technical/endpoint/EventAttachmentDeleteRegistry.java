// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.api.event.ports.business.usecase.attachment.DeleteEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.DeleteEventAttachmentCommand;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class EventAttachmentDeleteRegistry {

  private final List<DeleteEventAttachment> usecases;
  private final EventAttachmentPolicy attachmentPolicy;

  EventAttachmentDeleteRegistry(
      List<DeleteEventAttachment> usecases, EventAttachmentPolicy attachmentPolicy) {
    this.usecases = List.copyOf(usecases);
    this.attachmentPolicy = attachmentPolicy;
  }

  void delete(OpenCopPrincipal authentication, UUID sourceId, String objectId, UUID attachmentId) {
    attachmentPolicy.assertCanDelete(authentication, sourceId, objectId, attachmentId);
    var command =
        new DeleteEventAttachmentCommand(authentication, sourceId, objectId, attachmentId);
    usecases.stream()
        .map(usecase -> usecase.resolve(command))
        .flatMap(Optional::stream)
        .findFirst()
        .orElseThrow(() -> new GlobalOpenCopException("Attachment not found", HttpStatus.NOT_FOUND))
        .delete();
  }
}
