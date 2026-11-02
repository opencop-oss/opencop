// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.gateway.socket.connector;

public enum SocketConnectorExceptionCodes {
  MISSING_CA_CERT("TAK-CONNECT-2000", "CA certificate not found in client.p12"),
  TLS_EXTRACTION_FAILED("TAK-CONNECT-2001", "Failed to extract TLS credentials properly"),
  SOCKET_CONNECTION_FAILED("TAK-CONNECT-2002", "Socket Connection Failed");

  private final String code;
  private final String description;

  SocketConnectorExceptionCodes(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String code() {
    return code;
  }

  public String description() {
    return this.description;
  }
}
