// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.udp.none;

import com.opencop.core.api.general.business.usecase.connection.security.udp.ProvisionedUdpSecurity;
import com.opencop.core.api.general.business.usecase.connection.security.udp.UdpSecurityProvisioner;
import com.opencop.core.sdk.business.model.integration.connection.material.udp.NoSecurityMaterial;
import java.util.Optional;
import java.util.UUID;

/** A plain UDP connection: nothing to store. */
public final class NoSecurityProvisioner implements UdpSecurityProvisioner<NoSecurityMaterial> {

  @Override
  public ProvisionedUdpSecurity provision(UUID sourceId, NoSecurityMaterial material) {
    return new ProvisionedUdpSecurity(Optional.empty());
  }
}
