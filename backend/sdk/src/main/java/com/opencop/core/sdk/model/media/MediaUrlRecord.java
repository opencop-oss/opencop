// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.media;

import java.time.Instant;
import java.util.UUID;

public record MediaUrlRecord(
    UUID id,
    String url,
    StreamKind streamKind,
    MediaAccess access,
    UUID creatorId,
    UUID ownerId,
    Instant createdAt,
    MediaUsage usage) {}
