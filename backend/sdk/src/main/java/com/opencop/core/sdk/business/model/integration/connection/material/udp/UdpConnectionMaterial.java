// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.udp;

import java.util.Objects;

/** Complete UDP endpoint and the security material produced before connection creation. */
public record UdpConnectionMaterial(UdpEndpointMaterial endpoint, UdpSecurityMaterial security) {

  public UdpConnectionMaterial {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(security, "security");
  }

  public static UdpConnectionMaterial plain(UdpEndpointMaterial endpoint) {
    return new UdpConnectionMaterial(endpoint, new NoSecurityMaterial());
  }
}
