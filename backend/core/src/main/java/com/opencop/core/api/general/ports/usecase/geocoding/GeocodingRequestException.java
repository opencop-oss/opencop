// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.geocoding;

import com.opencop.core.sdk.ports.exception.ExceptionCode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;

/** A geocoding request the host refused or could not complete, mapped to its HTTP status. */
public class GeocodingRequestException extends GlobalOpenCopException {

  public GeocodingRequestException(ExceptionCode exceptionCode) {
    super(exceptionCode.code(), exceptionCode.description(), exceptionCode.httpStatus());
  }
}
