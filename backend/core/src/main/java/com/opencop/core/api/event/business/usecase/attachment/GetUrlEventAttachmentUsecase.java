// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.attachment;

import com.opencop.core.api.event.ports.business.usecase.attachment.GetEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.GetEventAttachmentCommand;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GetUrlEventAttachmentUsecase implements GetEventAttachment {

  private final MediaUrlRepository mediaUrlRepository;

  public GetUrlEventAttachmentUsecase(MediaUrlRepository mediaUrlRepository) {
    this.mediaUrlRepository = mediaUrlRepository;
  }

  @Override
  public Optional<AttachmentView> resolve(GetEventAttachmentCommand command) {
    return mediaUrlRepository.findById(command.attachmentId()).map(AttachmentView.Url::new);
  }
}
