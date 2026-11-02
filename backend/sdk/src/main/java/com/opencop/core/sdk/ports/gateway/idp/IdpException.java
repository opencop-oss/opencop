// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.idp;

public class IdpException extends Exception {

  private final String code;

  public IdpException(IdpExceptionCodes exception) {
    super(exception.description());
    this.code = exception.code();
  }

  public String getCode() {
    return code;
  }
}
