// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.rtsp;

import com.opencop.core.api.general.business.usecase.connection.security.rtsp.none.NoSecurityProvisioner;
import com.opencop.core.sdk.business.model.integration.connection.material.rtsp.NoSecurityMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.rtsp.RtspSecurityMaterial;
import java.util.UUID;

/**
 * The provisioner of each kind of RTSP security material. A new kind of material fails to compile
 * here until it has one.
 */
public final class RtspSecurityProvisionerRegistry {

  private final NoSecurityProvisioner none;

  RtspSecurityProvisionerRegistry(NoSecurityProvisioner none) {
    this.none = none;
  }

  public static RtspSecurityProvisionerRegistry standard() {
    return new RtspSecurityProvisionerRegistry(new NoSecurityProvisioner());
  }

  public ProvisionedRtspSecurity provision(UUID sourceId, RtspSecurityMaterial material) {
    return switch (material) {
      case NoSecurityMaterial plain -> none.provision(sourceId, plain);
    };
  }
}
