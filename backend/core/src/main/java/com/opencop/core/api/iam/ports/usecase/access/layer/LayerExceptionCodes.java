// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.usecase.access.layer;

public enum LayerExceptionCodes {
  EMPTY_SERVER_ID("USER-EXCEPTION-1000", "Server Id is empty, but is mandatory"),
  SERVER_ALREADY_EXISTS("USER-EXCEPTION-1001", "Server already exists"),
  SERVER_DOES_NOT_EXISTS("USER-EXCEPTION-1002", "Server does not exists"),
  BAD_PRINCIPAL("USER-EXCEPTION-1003", "Principal provided does not respect the format"),
  LAYER_REQUIRES_OWNER("USER-EXCEPTION-1004", "Layer must have at least one owner");

  private final String code;
  private final String description;

  LayerExceptionCodes(String code, String description) {
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
