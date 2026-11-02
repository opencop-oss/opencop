// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.technical.audit.AuthzAuditRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class CoreSecurityExceptionAdvice {

  private static final Logger log = LoggerFactory.getLogger(CoreSecurityExceptionAdvice.class);

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<String> forbidden(
      AccessDeniedException exception, HttpServletRequest request) {
    if (exception instanceof AuthzAccessDeniedException authzException) {
      request.setAttribute(
          AuthzAuditRequestAttributes.CANDIDATE_REASON, authzException.reasonCode());
      log.debug(
          "CORE_SECURITY_ACCESS_DENIED exception_type={} reason={}",
          exception.getClass().getSimpleName(),
          authzException.reasonCode());
    } else {
      log.debug(
          "CORE_SECURITY_ACCESS_DENIED exception_type={}", exception.getClass().getSimpleName());
    }
    return CoreSecurityErrorResponse.FORBIDDEN.responseEntity();
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<String> unauthorized(AuthenticationException exception) {
    log.debug(
        "CORE_SECURITY_AUTHENTICATION_FAILED exception_type={}",
        exception.getClass().getSimpleName());
    return CoreSecurityErrorResponse.UNAUTHORIZED.responseEntity();
  }
}
