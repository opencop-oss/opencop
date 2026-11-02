// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.socket;

public enum SocketProtocol {
  NONE("NONE"),
  TCP("TCP"),
  UDP("UDP"),
  TLS("TLS"),
  DTLS("DTLS"),
  MTLS("MTLS");

  private final String protocol;

  SocketProtocol(String protocol) {
    this.protocol = protocol;
  }

  public String getProtocol() {
    return this.protocol;
  }
}
