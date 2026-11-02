// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.media;

import java.time.Instant;
import java.util.UUID;

// blobKey is intentionally included in API responses. Single-consumer app with
// trusted users, and the blob path is also visible in SAS URLs. If the API becomes
// multi-tenant or public-facing, extract a response DTO that excludes internal fields.
public record MediaRecord(
    UUID id,
    String blobKey,
    String originalFilename,
    MediaContentType contentType,
    long sizeBytes,
    MediaAccess access,
    UUID creatorId,
    UUID ownerId,
    Instant createdAt,
    String mimeType,
    MediaAttributes attributes,
    UUID thumbnailOf,
    MediaUsage usage) {}
