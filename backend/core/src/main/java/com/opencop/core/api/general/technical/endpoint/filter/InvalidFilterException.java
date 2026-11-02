// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.filter;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.springframework.http.HttpStatus;

/**
 * A client-supplied {@code filter} query param could not be parsed, or named a field/operator
 * combination the read does not support. Extends GlobalOpenCopException so GlobalExceptionManager
 * turns it into a 400 with code {@code INVALID_FILTER}.
 *
 * <p>Lives at the HTTP boundary rather than beside the filter model, which throws a plain {@link
 * IllegalArgumentException} to stay free of Spring Web. Messages name only the offending
 * field/operator token, never the raw value: the constructor logs at WARN, and arbitrary user input
 * does not belong in logs.
 *
 * <p>Shared by every filtered endpoint, so all of them reject a bad filter with the same code.
 */
public class InvalidFilterException extends GlobalOpenCopException {

  private static final String CODE = "INVALID_FILTER";

  public InvalidFilterException(String message) {
    super(CODE, message, HttpStatus.BAD_REQUEST);
  }
}
