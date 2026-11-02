// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase;

public enum EventExceptionCodes {
  MISSING_INFORMATION("CORE-1000", "Request is not complete"),
  UNAUTHORIZED("CORE-1001", "You are unauthorized to do this action"),
  BAD_ISSUER("CORE-1002", "Principal issuer does not correspond to the expected issuer"),
  STALE_EVENT("CORE-1003", "The object is stale and it cannot be created or modified"),
  BAD_USER_ID("CORE-1004", "User Id is not an id"),

  /** Generic internal code; callers retain the failure-specific message. */
  INTERNAL_ERROR("CORE-0000", "Unexpected internal error");

  private final String code;
  private final String description;

  EventExceptionCodes(String code, String description) {
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
