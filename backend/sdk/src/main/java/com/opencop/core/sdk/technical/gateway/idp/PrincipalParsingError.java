// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.idp;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.springframework.http.HttpStatus;

public class PrincipalParsingError extends GlobalOpenCopException {

  public PrincipalParsingError(PrincipalExceptionCodes error) {
    super(error.code(), error.description(), HttpStatus.BAD_REQUEST);
  }

  public PrincipalParsingError(String code, String message) {
    super(code, message, HttpStatus.BAD_REQUEST);
  }
}
