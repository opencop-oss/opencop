// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.export;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportArtifact(
    UUID exportId,
    String subjectType,
    UUID subjectId,
    UUID creatorId,
    String format,
    String blobPath,
    String filename,
    OffsetDateTime generatedAt,
    OffsetDateTime expiresAt) {}
