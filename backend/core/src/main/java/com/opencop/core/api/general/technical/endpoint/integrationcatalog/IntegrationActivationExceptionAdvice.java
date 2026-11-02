// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationInUseException;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationNotDeactivatableException;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationNotFoundException;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class IntegrationActivationExceptionAdvice {

  @ExceptionHandler(IntegrationNotFoundException.class)
  ResponseEntity<GlobalErrorResponse> integrationNotFound(IntegrationNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new GlobalErrorResponse(IntegrationNotFoundException.CODE, exception.getMessage()));
  }

  @ExceptionHandler(IntegrationNotDeactivatableException.class)
  ResponseEntity<GlobalErrorResponse> integrationNotDeactivatable(
      IntegrationNotDeactivatableException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            new GlobalErrorResponse(
                IntegrationNotDeactivatableException.CODE, exception.getMessage()));
  }

  @ExceptionHandler(IntegrationInUseException.class)
  ResponseEntity<IntegrationInUseResponse> integrationInUse(IntegrationInUseException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(IntegrationInUseResponse.from(exception));
  }

  public record IntegrationInUseResponse(
      String code, String message, List<BlockingConnectionResponse> connections) {

    static IntegrationInUseResponse from(IntegrationInUseException exception) {
      return new IntegrationInUseResponse(
          IntegrationInUseException.CODE,
          exception.getMessage(),
          exception.connections().stream().map(BlockingConnectionResponse::from).toList());
    }
  }

  public record BlockingConnectionResponse(UUID id, String name) {

    static BlockingConnectionResponse from(ConnectionMetadata connection) {
      return new BlockingConnectionResponse(connection.id(), connection.name());
    }
  }
}
