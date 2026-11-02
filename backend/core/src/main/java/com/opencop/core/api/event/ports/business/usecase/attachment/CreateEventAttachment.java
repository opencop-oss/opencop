// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase.attachment;

import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import org.springframework.http.MediaType;

public interface CreateEventAttachment {

  boolean supports(
      MediaType contentType,
      EventAttachmentType attachmentType,
      CreateEventAttachmentCommand command);

  AttachmentView create(CreateEventAttachmentCommand command);
}
