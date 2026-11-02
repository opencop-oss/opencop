// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.UnknownTransportProtocolException;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class IntegrationCatalogExceptionAdvice {

  /**
   * The filter names something this build does not define, so no catalog state could satisfy it.
   * That is a malformed request rather than a missing resource, and an empty success would be
   * indistinguishable from a protocol no integration happens to serve.
   */
  @ExceptionHandler(UnknownTransportProtocolException.class)
  ResponseEntity<GlobalErrorResponse> unknownTransportProtocol(
      UnknownTransportProtocolException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new GlobalErrorResponse(
                UnknownTransportProtocolException.CODE, exception.getMessage()));
  }
}
