// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.attachment;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.api.event.ports.business.usecase.attachment.DeleteEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.DeleteEventAttachmentCommand;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DeleteUrlEventAttachmentUsecase implements DeleteEventAttachment {

  private static final Logger log = LoggerFactory.getLogger(DeleteUrlEventAttachmentUsecase.class);

  private final MediaUrlRepository mediaUrlRepository;
  private final EventAttachmentRepository eventAttachmentRepository;
  private final EventAttachmentPolicy attachmentPolicy;

  public DeleteUrlEventAttachmentUsecase(
      MediaUrlRepository mediaUrlRepository,
      EventAttachmentRepository eventAttachmentRepository,
      EventAttachmentPolicy attachmentPolicy) {
    this.mediaUrlRepository = mediaUrlRepository;
    this.eventAttachmentRepository = eventAttachmentRepository;
    this.attachmentPolicy = attachmentPolicy;
  }

  @Override
  public Optional<ResolvedDeleteEventAttachment> resolve(DeleteEventAttachmentCommand command) {
    return mediaUrlRepository
        .findById(command.attachmentId())
        .map(mediaUrl -> new ResolvedUrlDelete(command, mediaUrl));
  }

  private final class ResolvedUrlDelete implements ResolvedDeleteEventAttachment {

    private final DeleteEventAttachmentCommand command;
    private final MediaUrlRecord mediaUrl;

    private ResolvedUrlDelete(DeleteEventAttachmentCommand command, MediaUrlRecord mediaUrl) {
      this.command = command;
      this.mediaUrl = mediaUrl;
    }

    @Override
    public void delete() {
      attachmentPolicy.assertOwnerOrAdmin(command.authentication(), mediaUrl.ownerId());

      mediaUrlRepository.deleteById(command.attachmentId());
      eventAttachmentRepository.delete(
          command.sourceId(), command.objectId(), command.attachmentId());
      log.info(
          "URL attachment deleted: objectId={}, mediaUrlId={}, userId={}",
          command.objectId(),
          command.attachmentId(),
          command.userId());
    }
  }
}
