// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.idp;

public enum PrincipalExceptionCodes {
  EMPTY_PRINCIPAL("PH-1000", "Provided principal is empty"),
  BAD_FORMAT("PH-1001", "Principal format is not correct"),
  BAD_ISSUER("PH-1002", "Principal issuer does not correspond to the expected issuer"),
  BAD_USER_ID("PH-1003", "User Id is not an id"),
  INVALID("PH-1004", "Invalid User Principal"),
  CONTEXT_ERROR("PH-1005", "Could not build User principal from auth context");

  private final String code;
  private final String description;

  PrincipalExceptionCodes(String code, String description) {
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
