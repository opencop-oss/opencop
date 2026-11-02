// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.attachment;

import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand.FilePayload;
import com.opencop.core.api.event.ports.business.usecase.attachment.EventAttachmentType;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.api.general.ports.usecase.media.MediaNotFoundException;
import com.opencop.core.api.general.ports.usecase.media.MediaView;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.ports.usecase.media.UploadMedia;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateFileEventAttachmentUsecase implements CreateEventAttachment {

  private static final Logger log = LoggerFactory.getLogger(CreateFileEventAttachmentUsecase.class);

  private final UploadMedia uploadMedia;
  private final RetrieveMedia retrieveMedia;
  private final EventAttachmentRepository eventAttachmentRepository;

  public CreateFileEventAttachmentUsecase(
      UploadMedia uploadMedia,
      RetrieveMedia retrieveMedia,
      EventAttachmentRepository eventAttachmentRepository) {
    this.uploadMedia = uploadMedia;
    this.retrieveMedia = retrieveMedia;
    this.eventAttachmentRepository = eventAttachmentRepository;
  }

  @Override
  public boolean supports(
      MediaType contentType,
      EventAttachmentType attachmentType,
      CreateEventAttachmentCommand command) {
    return MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType)
        && attachmentType == EventAttachmentType.FILE
        && command.payload() instanceof FilePayload;
  }

  @Override
  @Transactional
  public AttachmentView create(CreateEventAttachmentCommand command) {
    var payload = (FilePayload) command.payload();
    var original = payload.original();
    var thumbnails = payload.thumbnails();

    var originalRecord =
        upload(
            command,
            original.originalFilename(),
            original.contentType(),
            original.size(),
            original.data(),
            null);

    for (var thumb : thumbnails) {
      upload(
          command,
          thumb.originalFilename(),
          thumb.contentType(),
          thumb.size(),
          thumb.data(),
          originalRecord.id());
    }

    eventAttachmentRepository.save(command.sourceId(), command.objectId(), originalRecord.id());

    var view = uploadedMediaView(originalRecord.id());
    log.info(
        "Attachment uploaded: objectId={}, mediaId={}, userId={}",
        command.objectId(),
        originalRecord.id(),
        command.userId());
    return new AttachmentView.File(view.record(), view.url(), view.thumbnails());
  }

  private MediaView uploadedMediaView(UUID mediaId) {
    try {
      return retrieveMedia.getWithoutAccessCheck(mediaId);
    } catch (MediaNotFoundException e) {
      throw new IllegalStateException("Uploaded media could not be retrieved: " + mediaId, e);
    }
  }

  private MediaRecord upload(
      CreateEventAttachmentCommand command,
      String originalFilename,
      String contentType,
      long size,
      InputStream data,
      UUID thumbnailOf) {
    return uploadMedia.upload(
        command.userId(),
        originalFilename,
        contentType,
        size,
        MediaAccess.SCOPED,
        data,
        thumbnailOf,
        MediaUsage.EVENT_DETAIL);
  }
}
