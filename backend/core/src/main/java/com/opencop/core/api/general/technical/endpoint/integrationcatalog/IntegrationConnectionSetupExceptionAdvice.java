// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupRejection;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupUnavailableException;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class IntegrationConnectionSetupExceptionAdvice {

  @ExceptionHandler(IntegrationConnectionSetupUnavailableException.class)
  ResponseEntity<GlobalErrorResponse> connectionSetupUnavailable(
      IntegrationConnectionSetupUnavailableException exception) {
    var reason = exception.reason();
    return ResponseEntity.status(status(reason))
        .body(new GlobalErrorResponse(reason.code(), reason.message()));
  }

  /**
   * An unknown reference is the only one that denies the resource exists. The remaining reasons
   * describe a schema version the catalog does carry but whose current state prevents describing
   * it, the same conflict this package already reports for a blocked deactivation. The response
   * code tells them apart.
   */
  private static HttpStatus status(IntegrationConnectionSetupRejection reason) {
    return reason == IntegrationConnectionSetupRejection.UNKNOWN
        ? HttpStatus.NOT_FOUND
        : HttpStatus.CONFLICT;
  }
}
