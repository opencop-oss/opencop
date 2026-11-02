// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.attachment;

import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand;
import com.opencop.core.api.event.ports.business.usecase.attachment.CreateEventAttachmentCommand.UrlPayload;
import com.opencop.core.api.event.ports.business.usecase.attachment.EventAttachmentType;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.ports.usecase.media.RegisterMediaUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CreateUrlEventAttachmentUsecase implements CreateEventAttachment {

  private static final Logger log = LoggerFactory.getLogger(CreateUrlEventAttachmentUsecase.class);

  private final RegisterMediaUrl registerMediaUrl;
  private final EventAttachmentRepository eventAttachmentRepository;

  public CreateUrlEventAttachmentUsecase(
      RegisterMediaUrl registerMediaUrl, EventAttachmentRepository eventAttachmentRepository) {
    this.registerMediaUrl = registerMediaUrl;
    this.eventAttachmentRepository = eventAttachmentRepository;
  }

  @Override
  public boolean supports(
      MediaType contentType,
      EventAttachmentType attachmentType,
      CreateEventAttachmentCommand command) {
    return MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
        && attachmentType == EventAttachmentType.URL
        && command.payload() instanceof UrlPayload;
  }

  @Override
  @Transactional
  public AttachmentView create(CreateEventAttachmentCommand command) {
    var payload = (UrlPayload) command.payload();

    var mediaUrl =
        registerMediaUrl.register(
            command.userId(),
            payload.url(),
            payload.streamKind(),
            MediaAccess.SCOPED,
            MediaUsage.EVENT_DETAIL);

    eventAttachmentRepository.save(command.sourceId(), command.objectId(), mediaUrl.id());

    log.info(
        "URL attachment registered: objectId={}, mediaUrlId={}, userId={}",
        command.objectId(),
        mediaUrl.id(),
        command.userId());
    return new AttachmentView.Url(mediaUrl);
  }
}
