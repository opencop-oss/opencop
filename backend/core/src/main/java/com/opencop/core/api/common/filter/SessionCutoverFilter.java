// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.jspecify.annotations.NonNull;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.filter.OncePerRequestFilter;

public final class SessionCutoverFilter extends OncePerRequestFilter {

  private static final String LEGACY_CSRF_COOKIE_NAME = "XSRF-TOKEN";

  private final CookieSerializer sessionCookieSerializer;
  private final RequestMatcher apiRequestMatcher;
  private final SessionCutoverTelemetry telemetry;

  public SessionCutoverFilter(
      CookieSerializer sessionCookieSerializer,
      RequestMatcher apiRequestMatcher,
      SessionCutoverTelemetry telemetry) {
    this.sessionCookieSerializer = sessionCookieSerializer;
    this.apiRequestMatcher = apiRequestMatcher;
    this.telemetry = telemetry;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    var apiRequest = apiRequestMatcher.matches(request);
    var unsafeApiRequest = apiRequest && CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request);
    var sessionExistedAtRequestEntry = request.getSession(false) != null;
    var invalidPresentedSession = hasInvalidPresentedSession(request, apiRequest);

    if (retireLegacyCsrfCookie(request, response)) {
      telemetry.recordLegacyCsrfCookieObservation();
    }
    if (invalidPresentedSession) {
      telemetry.recordInvalidSessionRecovery();
    }
    if (invalidPresentedSession && !unsafeApiRequest) {
      sessionCookieSerializer.writeCookieValue(new CookieValue(request, response, ""));
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      if (unsafeApiRequest && !sessionExistedAtRequestEntry) {
        invalidateSessionCreatedDuringRequest(request);
      }
    }
  }

  private static boolean hasInvalidPresentedSession(
      HttpServletRequest request, boolean apiRequest) {
    return apiRequest
        && request.getRequestedSessionId() != null
        && !request.isRequestedSessionIdValid();
  }

  private static void invalidateSessionCreatedDuringRequest(HttpServletRequest request) {
    try {
      var session = request.getSession(false);
      if (session != null) {
        session.invalidate();
      }
    } catch (IllegalStateException _) {
      // Another security component may already have invalidated the disposable session.
    }
  }

  private static boolean retireLegacyCsrfCookie(
      HttpServletRequest request, HttpServletResponse response) {
    if (request.getCookies() == null
        || Arrays.stream(request.getCookies())
            .noneMatch(cookie -> LEGACY_CSRF_COOKIE_NAME.equals(cookie.getName()))) {
      return false;
    }

    var deletion = new Cookie(LEGACY_CSRF_COOKIE_NAME, "");
    deletion.setPath("/");
    deletion.setMaxAge(0);
    deletion.setSecure(request.isSecure());
    response.addCookie(deletion);
    return true;
  }
}
