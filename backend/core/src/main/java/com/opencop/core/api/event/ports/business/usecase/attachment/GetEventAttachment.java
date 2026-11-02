// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase.attachment;

import com.opencop.core.api.general.ports.usecase.media.AttachmentView;
import java.util.Optional;

public interface GetEventAttachment {

  Optional<AttachmentView> resolve(GetEventAttachmentCommand command);
}
