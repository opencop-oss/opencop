// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import java.util.Optional;
import java.util.UUID;

public interface StreamableAttachmentResolver {

  Optional<StreamableAttachment> resolve(UUID attachmentId);
}
