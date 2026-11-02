// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.repository;

public enum RepositoryExceptionCodes {
  BAD_REQUEST("REPOSITORY-EXCEPTION-1000", "Bad Request"),
  FAILED_TO_PERSIST("IDP-EXCEPTION-1001", "Failed to persist");

  private final String code;
  private final String description;

  RepositoryExceptionCodes(String code, String description) {
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
