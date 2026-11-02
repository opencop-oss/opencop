// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.usecase.connection;

public enum ConnectionManagerExceptionCodes {
  SOCKET_NOT_EXIST("CONNECT-1000", "Socket does not exist"),
  SOCKET_ALREADY_CONNECTED("CONNECT-1001", "Socket already connected"),
  CONNECTION_FAILED("CONNECT-1002", "Connection Failed"),
  RECONNECTION_FAILED("CONNECT-1003", "Reconnection failed"),
  DISCONNECTION_FAILED("CONNECT-1004", "Disconnection Failed"),
  SOCKET_NOT_FOUND("CONNECT-1005", "Socket not found"),
  SOCKET_ALREADY_CONNECTED_USE_RECONNECT("CONNECT-1006", "Socket already exists, use reconnect"),
  ZIP_FILE_SAVE_FAILED("CONNECT-1007", "Error saving the credentials"),
  SHA_256_NOT_SUPPORTED("CONNECT-1008", "SHA-256 not supported"),
  SOCKET_NAME_ALREADY_TAKEN("CONNECT-1009", "Socket name already taken"),
  SOCKET_CREDENTIALS_ALREADY_USED("CONNECT-1010", "Socket credentials already used"),
  CREDENTIALS_EXTRACTION_FAILED(
      "CONNECT-1011", "Error extracting the credentials from the zip file"),
  FILE_COPY_ERROR("CONNECT-3001", "Error copying file"),
  CONNECTION_INTERRUPTED("CONNECT-1012", "Connection wait was interrupted");

  private final String code;
  private final String description;

  ConnectionManagerExceptionCodes(String code, String description) {
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
