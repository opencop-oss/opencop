// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.attachment;

import com.opencop.core.api.event.ports.business.usecase.attachment.GetEventAttachment;
import com.opencop.core.api.event.ports.business.usecase.attachment.GetEventAttachmentCommand;
import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import com.opencop.core.api.general.ports.usecase.media.MediaNotFoundException;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GetFileEventAttachmentUsecase implements GetEventAttachment {

  private final RetrieveMedia retrieveMedia;

  public GetFileEventAttachmentUsecase(RetrieveMedia retrieveMedia) {
    this.retrieveMedia = retrieveMedia;
  }

  @Override
  public Optional<AttachmentView> resolve(GetEventAttachmentCommand command) {
    try {
      var view = retrieveMedia.getWithoutAccessCheck(command.attachmentId());
      return Optional.of(new AttachmentView.File(view.record(), view.url(), view.thumbnails()));
    } catch (MediaNotFoundException _) {
      return Optional.empty();
    }
  }
}
