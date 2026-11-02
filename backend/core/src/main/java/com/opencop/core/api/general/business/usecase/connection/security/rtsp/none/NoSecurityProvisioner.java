// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.rtsp.none;

import com.opencop.core.api.general.business.usecase.connection.security.rtsp.ProvisionedRtspSecurity;
import com.opencop.core.api.general.business.usecase.connection.security.rtsp.RtspSecurityProvisioner;
import com.opencop.core.sdk.business.model.integration.connection.material.rtsp.NoSecurityMaterial;
import java.util.Optional;
import java.util.UUID;

/** A plain RTSP connection: nothing to store. */
public final class NoSecurityProvisioner implements RtspSecurityProvisioner<NoSecurityMaterial> {

  @Override
  public ProvisionedRtspSecurity provision(UUID sourceId, NoSecurityMaterial material) {
    return new ProvisionedRtspSecurity(Optional.empty());
  }
}
