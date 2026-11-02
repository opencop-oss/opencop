// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.export.strategy;

import com.opencop.core.api.general.business.export.ActiveFeatureExportRow;
import com.opencop.core.api.general.business.export.ExportSelection;
import com.opencop.core.api.general.technical.model.export.ExportRequest;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;

public interface ExportSelectionStrategy {

  ExportSelection.Mode supportedMode();

  void assertActive(ExportSelection selection);

  void assertAuthorizedPreFetch(OpenCopPrincipal principal, ExportSelection selection);

  List<ActiveFeatureExportRow> readRows(ExportSelection selection);

  void assertRowsIntegrity(ExportSelection selection, List<ActiveFeatureExportRow> rows);

  void assertAuthorizedPostFetch(
      OpenCopPrincipal principal, ExportSelection selection, List<ActiveFeatureExportRow> rows);

  default List<ActiveFeatureExportRow> finalizeRows(
      ExportSelection selection, List<ActiveFeatureExportRow> rows) {
    return rows;
  }

  ExportArtifactNaming naming(ExportRequest request, ExportSelection selection);
}
