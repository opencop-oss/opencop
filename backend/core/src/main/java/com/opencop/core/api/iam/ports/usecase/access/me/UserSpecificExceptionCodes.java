// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.usecase.access.me;

public enum UserSpecificExceptionCodes {
  EMPTY_SERVER_ID("USER-EXCEPTION-1000", "Server Id is empty, but is mandatory"),
  SERVER_ALREADY_EXISTS("USER-EXCEPTION-1001", "Server already exists"),
  SERVER_DOES_NOT_EXISTS("USER-EXCEPTION-1002", "Server does not exists"),
  PRINCIPAL_FORMAT_ERROR("USER-EXCEPTION-1003", "Wrong principal format"),
  LAYER_ACCESS_DENIED("USER-EXCEPTION-1004", "User does not have access to the specified layer");

  private final String code;
  private final String description;

  UserSpecificExceptionCodes(String code, String description) {
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
