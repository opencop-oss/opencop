// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.media;

import com.opencop.core.api.common.filter.SpaAuthenticationEntryPoint;
import com.opencop.core.api.general.business.usecase.media.StreamAuthorizationReason;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public class StreamAuthorizationAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final Logger log =
      LoggerFactory.getLogger(StreamAuthorizationAuthenticationEntryPoint.class);

  private final StreamAuthorizationTelemetry telemetry;
  private final AuthenticationEntryPoint delegate;

  public StreamAuthorizationAuthenticationEntryPoint(StreamAuthorizationTelemetry telemetry) {
    this.telemetry = telemetry;
    this.delegate = new SpaAuthenticationEntryPoint();
  }

  @Override
  public void commence(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull AuthenticationException authException)
      throws IOException, ServletException {
    recordUnauthenticatedStreamAuthorization();
    delegate.commence(request, response, authException);
  }

  private void recordUnauthenticatedStreamAuthorization() {
    try {
      telemetry.recordDenial(StreamAuthorizationReason.UNAUTHENTICATED);
    } catch (RuntimeException e) {
      log.warn("Failed to record unauthenticated stream authorization telemetry", e);
    }
  }
}
