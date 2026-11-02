// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.gateway.repository;

public enum RepositoryExceptionCodes {
  DOES_NOT_EXIST("REPOSITORY-EXCEPTION-1000", "Does not exist"),
  ELEMENT_NOT_FOUND("REPOSITORY-EXCEPTION-1001", "Could not find object with the provided id"),
  ALREADY_EXIST("REPOSITORY-EXCEPTION-1002", "Already exist"),
  NAME_ALREADY_TAKEN("REPOSITORY-EXCEPTION-1003", "Name already taken");

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
