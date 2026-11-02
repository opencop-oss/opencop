// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.export.strategy;

import com.opencop.core.api.general.business.export.ExportFilenameFactory;
import java.time.OffsetDateTime;
import java.util.UUID;

final class SelectionNaming implements ExportArtifactNaming {

  private static final String SELECTION_SUBJECT_TYPE = "SELECTION";

  private final ExportFilenameFactory filenameFactory;

  SelectionNaming(ExportFilenameFactory filenameFactory) {
    this.filenameFactory = filenameFactory;
  }

  @Override
  public String subjectType() {
    return SELECTION_SUBJECT_TYPE;
  }

  @Override
  public UUID subjectId(UUID exportId) {
    return exportId;
  }

  @Override
  public String filename(UUID exportId, OffsetDateTime generatedAt, String fileExtension) {
    return filenameFactory.createSelection(exportId, generatedAt, fileExtension);
  }
}
