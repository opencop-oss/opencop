// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.tcp;

import java.util.Objects;

/** Complete TCP endpoint and the security material produced before connection creation. */
public record TcpConnectionMaterial(TcpEndpointMaterial endpoint, TcpSecurityMaterial security) {

  public TcpConnectionMaterial {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(security, "security");
  }

  public static TcpConnectionMaterial plain(TcpEndpointMaterial endpoint) {
    return new TcpConnectionMaterial(endpoint, new NoSecurityMaterial());
  }

  public static TcpConnectionMaterial mtls(
      TcpEndpointMaterial endpoint, MtlsCredentialMaterial credentials) {
    return new TcpConnectionMaterial(endpoint, credentials);
  }
}
