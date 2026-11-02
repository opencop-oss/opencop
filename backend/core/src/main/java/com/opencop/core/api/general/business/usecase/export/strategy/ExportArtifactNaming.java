// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.export.strategy;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ExportArtifactNaming {

  String subjectType();

  UUID subjectId(UUID exportId);

  String filename(UUID exportId, OffsetDateTime generatedAt, String fileExtension);
}
