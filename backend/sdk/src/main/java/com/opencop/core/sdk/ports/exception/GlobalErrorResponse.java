// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * An API error.
 *
 * @param code stable error code a client branches on
 * @param message safe, human-readable explanation
 * @param detailCode finer-grained reason within the code, when the failure has one
 * @param field the request field the failure concerns, when it concerns one
 * @param details more specific facts about the error, when the route that raised it attached any
 */
public record GlobalErrorResponse(
    String code,
    String message,
    @JsonInclude(JsonInclude.Include.NON_NULL) String detailCode,
    @JsonInclude(JsonInclude.Include.NON_NULL) String field,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ErrorDetail> details) {

  public GlobalErrorResponse {
    details = details == null ? List.of() : List.copyOf(details);
  }

  public GlobalErrorResponse(String code, String message) {
    this(code, message, null, null, null);
  }
}
