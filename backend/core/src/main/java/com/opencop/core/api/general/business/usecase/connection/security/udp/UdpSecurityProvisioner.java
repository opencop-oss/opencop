// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.security.udp;

import com.opencop.core.sdk.business.model.integration.connection.material.udp.UdpSecurityMaterial;
import java.util.UUID;

/** Turns one kind of UDP security material into what a new connection needs. */
public interface UdpSecurityProvisioner<M extends UdpSecurityMaterial> {

  ProvisionedUdpSecurity provision(UUID sourceId, M material);
}
