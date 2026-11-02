// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.attachment;

import com.opencop.core.api.event.business.policy.EventAttachmentPolicy;
import com.opencop.core.api.event.ports.business.usecase.attachment.DeleteEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.DeleteEventAttachmentCommand;
import com.opencop.core.api.general.ports.usecase.media.DeleteMedia;
import com.opencop.core.api.general.ports.usecase.media.MediaNotFoundException;
import com.opencop.core.api.general.ports.usecase.media.MediaView;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DeleteFileEventAttachmentUsecase implements DeleteEventAttachment {

  private static final Logger log = LoggerFactory.getLogger(DeleteFileEventAttachmentUsecase.class);

  private final RetrieveMedia retrieveMedia;
  private final DeleteMedia deleteMedia;
  private final EventAttachmentRepository eventAttachmentRepository;
  private final EventAttachmentPolicy attachmentPolicy;

  public DeleteFileEventAttachmentUsecase(
      RetrieveMedia retrieveMedia,
      DeleteMedia deleteMedia,
      EventAttachmentRepository eventAttachmentRepository,
      EventAttachmentPolicy attachmentPolicy) {
    this.retrieveMedia = retrieveMedia;
    this.deleteMedia = deleteMedia;
    this.eventAttachmentRepository = eventAttachmentRepository;
    this.attachmentPolicy = attachmentPolicy;
  }

  @Override
  public Optional<ResolvedDeleteEventAttachment> resolve(DeleteEventAttachmentCommand command) {
    return fileView(command).map(view -> new ResolvedFileDelete(command, view));
  }

  private final class ResolvedFileDelete implements ResolvedDeleteEventAttachment {

    private final DeleteEventAttachmentCommand command;
    private final MediaView view;

    private ResolvedFileDelete(DeleteEventAttachmentCommand command, MediaView view) {
      this.command = command;
      this.view = view;
    }

    @Override
    public void delete() {
      attachmentPolicy.assertOwnerOrAdmin(command.authentication(), view.record().ownerId());

      for (var thumb : view.thumbnails()) {
        try {
          deleteMedia.delete(thumb.id());
        } catch (Exception e) {
          log.warn("Failed to delete attachment thumbnail {}: {}", thumb.id(), e.getMessage());
        }
      }
      deleteMedia.delete(command.attachmentId());
      eventAttachmentRepository.delete(
          command.sourceId(), command.objectId(), command.attachmentId());
      log.info(
          "File attachment deleted: objectId={}, mediaId={}, userId={}",
          command.objectId(),
          command.attachmentId(),
          command.userId());
    }
  }

  private Optional<MediaView> fileView(DeleteEventAttachmentCommand command) {
    try {
      return Optional.of(retrieveMedia.getWithoutAccessCheck(command.attachmentId()));
    } catch (MediaNotFoundException _) {
      return Optional.empty();
    } catch (GlobalOpenCopException e) {
      if (e.getHttpStatus() == HttpStatus.NOT_FOUND) {
        return Optional.empty();
      }
      throw e;
    }
  }
}
