// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.scim;

import com.opencop.core.api.general.business.model.user.ScimUserMutationConflictException;
import com.unboundid.scim2.common.messages.ErrorResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ScimExceptionHandler {

  private static final MediaType SCIM_JSON = MediaType.valueOf("application/scim+json");
  private static final String ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";

  private static final Logger log = LoggerFactory.getLogger(ScimExceptionHandler.class);

  private static ErrorResponse scimError(int status, String detail, String scimType) {
    var err = new ErrorResponse(status);
    err.setSchemaUrns(List.of(ERROR_SCHEMA));
    err.setDetail(detail);
    if (scimType != null) err.setScimType(scimType);
    return err;
  }

  @ExceptionHandler(ScimExceptions.ScimBadRequestException.class)
  public ResponseEntity<ErrorResponse> badRequest(ScimExceptions.ScimBadRequestException e) {
    log.warn("SCIM 400 badRequest: {}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(SCIM_JSON)
        .body(scimError(400, e.getMessage(), null));
  }

  @ExceptionHandler(ScimExceptions.ScimInvalidFilterException.class)
  public ResponseEntity<ErrorResponse> badFilter(ScimExceptions.ScimInvalidFilterException e) {
    log.warn("SCIM 400 invalidFilter: {}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(SCIM_JSON)
        .body(scimError(400, e.getMessage(), "invalidFilter"));
  }

  @ExceptionHandler(ScimExceptions.ScimNotFoundException.class)
  public ResponseEntity<ErrorResponse> notFound(ScimExceptions.ScimNotFoundException e) {
    log.info("SCIM 404: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(SCIM_JSON)
        .body(scimError(404, e.getMessage(), null));
  }

  @ExceptionHandler(ScimExceptions.ScimConflictException.class)
  public ResponseEntity<ErrorResponse> conflict(ScimExceptions.ScimConflictException e) {
    log.warn("SCIM 409: {}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .contentType(SCIM_JSON)
        .body(scimError(409, e.getMessage(), null));
  }

  @ExceptionHandler(ScimUserMutationConflictException.class)
  public ResponseEntity<ErrorResponse> conflict(ScimUserMutationConflictException e) {
    log.warn("SCIM 409: {}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .contentType(SCIM_JSON)
        .body(scimError(409, e.getMessage(), null));
  }
}
