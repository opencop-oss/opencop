// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.tcp;

import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpSecurityMaterial;
import java.util.UUID;

/** Turns one kind of TCP security material into the security a new connection stores. */
public interface TcpSecurityProvisioner<M extends TcpSecurityMaterial> {

  ProvisionedTcpSecurity provision(UUID sourceId, M material);
}
