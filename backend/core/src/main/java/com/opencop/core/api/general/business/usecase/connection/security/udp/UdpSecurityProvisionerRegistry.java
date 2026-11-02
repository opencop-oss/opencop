// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.udp;

import com.opencop.core.api.general.business.usecase.connection.security.udp.none.NoSecurityProvisioner;
import com.opencop.core.sdk.business.model.integration.connection.material.udp.NoSecurityMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.udp.UdpSecurityMaterial;
import java.util.UUID;

/**
 * The provisioner of each kind of UDP security material. A new kind of material fails to compile
 * here until it has one.
 */
public final class UdpSecurityProvisionerRegistry {

  private final NoSecurityProvisioner none;

  UdpSecurityProvisionerRegistry(NoSecurityProvisioner none) {
    this.none = none;
  }

  public static UdpSecurityProvisionerRegistry standard() {
    return new UdpSecurityProvisionerRegistry(new NoSecurityProvisioner());
  }

  public ProvisionedUdpSecurity provision(UUID sourceId, UdpSecurityMaterial material) {
    return switch (material) {
      case NoSecurityMaterial plain -> none.provision(sourceId, plain);
    };
  }
}
