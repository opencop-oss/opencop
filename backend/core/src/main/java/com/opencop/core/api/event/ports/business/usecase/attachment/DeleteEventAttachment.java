// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase.attachment;

import java.util.Optional;

public interface DeleteEventAttachment {

  Optional<ResolvedDeleteEventAttachment> resolve(DeleteEventAttachmentCommand command);

  interface ResolvedDeleteEventAttachment {
    void delete();
  }
}
