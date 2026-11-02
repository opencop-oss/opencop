// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.idp;

public enum IdpExceptionCodes {
  ALREADY_EXISTS("IDP-EXCEPTION-1000", "Already exists"),
  CREATE_FAILED("IDP-EXCEPTION-1001", "Failed to create"),
  DELETE_FAILED("IDP-EXCEPTION-1002", "Failed to delete"),
  NOT_FOUND("IDP-EXCEPTION-1003", "Object not found"),
  UPDATE_FAILED("IDP-EXCEPTION-1004", "Failed to update object"),
  FAILED_TO_COMMUNICATE("IDP-EXCEPTION-1005", "Failed to communicate with idp");

  private final String code;
  private final String description;

  IdpExceptionCodes(String code, String description) {
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
