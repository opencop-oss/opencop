// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(
    assignableTypes = {
      WatchboxesEndpoint.class,
      WatchboxDetectionsEndpoint.class,
      WatchboxDevStatusOverrideEndpoint.class,
      WatchboxConfigAuditEndpoint.class
    })
public class WatchboxExceptionAdvice {

  private static final Logger log = LoggerFactory.getLogger(WatchboxExceptionAdvice.class);

  @ExceptionHandler(WatchboxException.class)
  public ResponseEntity<WatchboxErrorResponse> handleWatchboxValidation(WatchboxException ex) {
    return ResponseEntity.status(ex.status())
        .body(
            new WatchboxErrorResponse(
                ex.code(), ex.getMessage(), ex.fields(), ex.reasons(), ex.context()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<WatchboxErrorResponse> handleUnreadableBody(
      HttpMessageNotReadableException ex) {
    log.warn("Watchbox JSON parse error");
    return ResponseEntity.badRequest()
        .body(
            new WatchboxErrorResponse(
                WatchboxErrorCode.WATCHBOX_INVALID_REQUEST,
                "Invalid request body",
                List.of("body"),
                List.of()));
  }
}
