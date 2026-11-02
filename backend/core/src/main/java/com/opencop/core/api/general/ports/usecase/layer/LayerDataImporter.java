// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.layer;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.technical.model.data.DataImportRequest;
import java.util.UUID;

public interface LayerDataImporter {

  /**
   * Accepts an import for mapping. The work itself finishes asynchronously in the processor, so a
   * successful return means the import was accepted, not that anything landed.
   */
  void importData(Caller requestor, UUID layerId, DataImportRequest request);
}
