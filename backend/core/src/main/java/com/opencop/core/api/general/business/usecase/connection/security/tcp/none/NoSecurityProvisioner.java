// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.tcp.none;

import com.opencop.core.api.general.business.model.connection.TcpTimeouts;
import com.opencop.core.api.general.business.usecase.connection.security.tcp.ProvisionedTcpSecurity;
import com.opencop.core.api.general.business.usecase.connection.security.tcp.TcpSecurityProvisioner;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.NoSecurityMaterial;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import java.util.Optional;
import java.util.UUID;

/** A plain TCP connection: nothing to store, default timeouts. */
public final class NoSecurityProvisioner implements TcpSecurityProvisioner<NoSecurityMaterial> {

  @Override
  public ProvisionedTcpSecurity provision(UUID sourceId, NoSecurityMaterial material) {
    return new ProvisionedTcpSecurity(new NoSecurity(), TcpTimeouts.DEFAULT, Optional.empty());
  }
}
