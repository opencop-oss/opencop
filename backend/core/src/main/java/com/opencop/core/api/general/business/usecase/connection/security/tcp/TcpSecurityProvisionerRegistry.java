// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.tcp;

import com.opencop.core.api.general.business.usecase.connection.security.tcp.mtls.MtlsSecurityProvisioner;
import com.opencop.core.api.general.business.usecase.connection.security.tcp.none.NoSecurityProvisioner;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.MtlsCredentialMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.NoSecurityMaterial;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpSecurityMaterial;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import java.util.UUID;

/**
 * The provisioner of each kind of TCP security material. A new kind of material fails to compile
 * here until it has one.
 */
public final class TcpSecurityProvisionerRegistry {

  private final NoSecurityProvisioner none;
  private final MtlsSecurityProvisioner mtls;

  TcpSecurityProvisionerRegistry(NoSecurityProvisioner none, MtlsSecurityProvisioner mtls) {
    this.none = none;
    this.mtls = mtls;
  }

  public static TcpSecurityProvisionerRegistry standard(ConnectionStore connectionStore) {
    return new TcpSecurityProvisionerRegistry(
        new NoSecurityProvisioner(), new MtlsSecurityProvisioner(connectionStore));
  }

  public ProvisionedTcpSecurity provision(UUID sourceId, TcpSecurityMaterial material) {
    return switch (material) {
      case NoSecurityMaterial plain -> none.provision(sourceId, plain);
      case MtlsCredentialMaterial credentials -> mtls.provision(sourceId, credentials);
    };
  }
}
