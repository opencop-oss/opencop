// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.endpoint;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.api.event.ports.business.usecase.attachment.GetEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.GetEventAttachmentCommand;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class EventAttachmentGetRegistry {

  private final List<GetEventAttachment> usecases;
  private final EventAttachmentPolicy attachmentPolicy;
  private final EventAttachmentRepository eventAttachmentRepository;

  EventAttachmentGetRegistry(
      List<GetEventAttachment> usecases,
      EventAttachmentPolicy attachmentPolicy,
      EventAttachmentRepository eventAttachmentRepository) {
    this.usecases = List.copyOf(usecases);
    this.attachmentPolicy = attachmentPolicy;
    this.eventAttachmentRepository = eventAttachmentRepository;
  }

  List<AttachmentView> list(OpenCopPrincipal authentication, UUID sourceId, String objectId) {
    attachmentPolicy.assertCanList(authentication, sourceId, objectId);
    return eventAttachmentRepository.findAttachmentIdsByObjectId(sourceId, objectId).stream()
        .flatMap(attachmentId -> resolve(authentication, sourceId, objectId, attachmentId).stream())
        .toList();
  }

  AttachmentView get(
      OpenCopPrincipal authentication, UUID sourceId, String objectId, UUID attachmentId) {
    attachmentPolicy.assertCanGet(authentication, sourceId, objectId, attachmentId);
    return resolve(authentication, sourceId, objectId, attachmentId)
        .orElseThrow(
            () -> new GlobalOpenCopException("Attachment not found", HttpStatus.NOT_FOUND));
  }

  private Optional<AttachmentView> resolve(
      OpenCopPrincipal authentication, UUID sourceId, String objectId, UUID attachmentId) {
    var command = new GetEventAttachmentCommand(authentication, sourceId, objectId, attachmentId);
    return usecases.stream()
        .map(usecase -> usecase.resolve(command))
        .flatMap(Optional::stream)
        .findFirst();
  }
}
