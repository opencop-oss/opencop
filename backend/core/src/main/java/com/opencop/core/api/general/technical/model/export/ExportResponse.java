// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.export;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportResponse(
    UUID exportId,
    String filename,
    ExportRequest.Format format,
    OffsetDateTime generatedAt,
    OffsetDateTime expiresAt,
    String downloadPath) {}
