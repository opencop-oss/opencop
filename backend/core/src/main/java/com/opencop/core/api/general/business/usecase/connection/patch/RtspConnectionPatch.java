// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.patch;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.ConnectionMetadataPatch;
import com.opencop.core.api.general.business.model.connection.ValidatedConnectionMetadataPatch;
import com.opencop.core.api.general.business.policy.connection.access.AllowedAccessModesPolicy;
import com.opencop.core.api.general.business.policy.connection.access.ConnectionAccessChangePolicy;
import com.opencop.core.sdk.model.connection.add.TransportKind;

public final class RtspConnectionPatch implements ConnectionProtocolPatch {

  @Override
  public TransportKind transportKind() {
    return TransportKind.RTSP;
  }

  @Override
  public boolean validateAndRequiresReconciliation(
      ConnectionMetadata current,
      ConnectionMetadataPatch requested,
      ValidatedConnectionMetadataPatch effective) {
    if (effective.access() != null) {
      AllowedAccessModesPolicy.assertTransportAllows(TransportKind.RTSP, effective.access());
      ConnectionAccessChangePolicy.assertSupported(current.access(), effective.access());
    }
    return current.enabled() && (effective.access() != null || effective.integration() != null);
  }
}
