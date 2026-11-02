// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.business.usecase.session;

public enum SessionHandlerExceptionCodes {
  SESSION_HANDLER("SESSION-HANDLER-1000", "Session");

  private final String code;
  private final String description;

  SessionHandlerExceptionCodes(String code, String description) {
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
