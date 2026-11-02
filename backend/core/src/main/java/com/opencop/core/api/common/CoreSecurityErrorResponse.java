// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public enum CoreSecurityErrorResponse {
  UNAUTHORIZED(
      HttpStatus.UNAUTHORIZED,
      "{\"error\":\"unauthorized\",\"message\":\"Login required\",\"loginUrl\":\""
          + CoreSecurityErrorResponse.LOGIN_URL
          + "\"}",
      true),
  FORBIDDEN(HttpStatus.FORBIDDEN, "{\"error\":\"forbidden\"}", false),
  CSRF_REJECTED(
      HttpStatus.FORBIDDEN, "{\"error\":\"forbidden\",\"code\":\"CSRF_REJECTED\"}", false);

  static final String LOGIN_URL = "/oauth2/authorization/oidc";

  private final HttpStatus status;
  private final String body;
  private final boolean preventCaching;

  CoreSecurityErrorResponse(HttpStatus status, String body, boolean preventCaching) {
    this.status = status;
    this.body = body;
    this.preventCaching = preventCaching;
  }

  public ResponseEntity<String> responseEntity() {
    var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
    if (preventCaching) {
      response.cacheControl(CacheControl.noStore());
    }
    return response.body(body);
  }

  public void writeTo(HttpServletResponse response) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    if (preventCaching) {
      response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
    }
    response.getWriter().write(body);
  }
}
