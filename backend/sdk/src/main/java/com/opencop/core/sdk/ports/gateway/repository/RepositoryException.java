// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.repository;

public class RepositoryException extends Exception {

  private final String code;

  public RepositoryException(RepositoryExceptionCodes exception) {
    super(exception.description());
    this.code = exception.code();
  }

  public RepositoryException(RepositoryExceptionCodes exception, Throwable cause) {
    super(exception.description(), cause);
    this.code = exception.code();
  }

  public String getCode() {
    return code;
  }
}
