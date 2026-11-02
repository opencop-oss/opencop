// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.patch;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.ConnectionMetadataPatch;
import com.opencop.core.api.general.business.model.connection.ValidatedConnectionMetadataPatch;
import com.opencop.core.sdk.model.connection.add.TransportKind;

/** Protocol-specific validation and reconciliation decision for one metadata patch. */
public interface ConnectionProtocolPatch {
  TransportKind transportKind();

  boolean validateAndRequiresReconciliation(
      ConnectionMetadata current,
      ConnectionMetadataPatch requested,
      ValidatedConnectionMetadataPatch effective);
}
