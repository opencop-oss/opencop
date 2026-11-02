// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase.attachment;

import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;

public record GetEventAttachmentCommand(
    OpenCopPrincipal authentication, UUID sourceId, String objectId, UUID attachmentId) {}
