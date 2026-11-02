// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.usecase.identity;

public enum UserExceptionCodes {
  MISSING_INFO("USER-EXCEPTION-1000", ""),
  USER_ID_NOT_PROVIDED("USER-EXCEPTION-1001", "User id not provided"),
  USER_DOES_NOT_EXIST("USER-EXCEPTION-1002", "User does not exist");

  private final String code;
  private final String description;

  UserExceptionCodes(String code, String description) {
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
