// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.business.usecase.event;

public enum EventHandlerExceptionCodes {
  FAILED_TO_PROCESS("EVENT-HANDLER-1000", "Failed to process event"),
  EVENT_NOT_FOUND("EVENT-HANDLER-1001", "Event does not exists in Redis");

  private final String code;
  private final String description;

  EventHandlerExceptionCodes(String code, String description) {
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
