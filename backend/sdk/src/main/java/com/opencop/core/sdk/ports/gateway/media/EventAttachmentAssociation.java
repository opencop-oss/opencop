// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.media;

import java.util.UUID;

public record EventAttachmentAssociation(UUID sourceId, String objectId, UUID attachmentId) {}
