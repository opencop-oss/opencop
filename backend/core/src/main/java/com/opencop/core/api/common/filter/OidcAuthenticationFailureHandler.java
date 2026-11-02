// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

public class OidcAuthenticationFailureHandler implements AuthenticationFailureHandler {

  private static final String ACCESS_DENIED = "opencop_access_denied";
  private static final String AUTHORIZATION_REQUEST_NOT_FOUND = "authorization_request_not_found";
  private static final String VALIDATED_REDIRECT_URI = "validated_redirect_uri";
  private static final Logger log = LoggerFactory.getLogger(OidcAuthenticationFailureHandler.class);

  private final URI allowedDomain;

  public OidcAuthenticationFailureHandler(String allowedDomain) {
    this.allowedDomain = URI.create(allowedDomain);
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    var errorCode = errorCode(exception);
    var diagnosticCategory = diagnosticCategory(exception, errorCode);
    var requestedSessionValid = request.isRequestedSessionIdValid();
    var sessionCookiePresent =
        request.getCookies() != null
            && Arrays.stream(request.getCookies())
                .anyMatch(cookie -> "OPENCOP_BFF".equals(cookie.getName()));
    var session = request.getSession(false);
    var authenticatedSession = isAuthenticated(session);
    SecurityContextHolder.clearContext();
    clearTemporaryState(session);
    if (!authenticatedSession) {
      invalidate(session);
    }
    logFailure(diagnosticCategory, requestedSessionValid, sessionCookiePresent, exception);
    response.sendRedirect(redirectUri(errorCode).toString());
  }

  private URI redirectUri(String category) {
    if (ACCESS_DENIED.equals(category)) {
      return allowedDomain.resolve("/access_denied");
    }
    if (AUTHORIZATION_REQUEST_NOT_FOUND.equals(category)) {
      return allowedDomain.resolve("/login_failed?reason=auth_request_not_found");
    }
    return allowedDomain.resolve("/login_failed");
  }

  private static String errorCode(AuthenticationException exception) {
    return exception instanceof OAuth2AuthenticationException oauthException
        ? oauthException.getError().getErrorCode()
        : "";
  }

  private static String diagnosticCategory(AuthenticationException exception, String errorCode) {
    if (!(exception instanceof OAuth2AuthenticationException)) {
      return "unexpected_authentication_failure";
    }
    return switch (errorCode) {
      case ACCESS_DENIED -> "admission_denied";
      case AUTHORIZATION_REQUEST_NOT_FOUND -> AUTHORIZATION_REQUEST_NOT_FOUND;
      case "invalid_oidc_claim" -> "invalid_claim";
      case "invalid_oidc_audience" -> "invalid_audience";
      case "opencop_admission_required" -> "unsupported_principal";
      case "opencop_login_failed" -> "unexpected_login_failure";
      default -> "oauth_authentication_failure";
    };
  }

  private static void logFailure(
      String diagnosticCategory,
      boolean requestedSessionValid,
      boolean sessionCookiePresent,
      AuthenticationException exception) {
    switch (diagnosticCategory) {
      case "admission_denied", AUTHORIZATION_REQUEST_NOT_FOUND ->
          log.info(
              "OAuth2 login failed [{}] (sessionValid={}, cookiePresent={})",
              diagnosticCategory,
              requestedSessionValid,
              sessionCookiePresent);
      case "unexpected_login_failure", "unexpected_authentication_failure" ->
          logUnexpectedFailure(
              diagnosticCategory, requestedSessionValid, sessionCookiePresent, exception);
      default ->
          log.warn(
              "OAuth2 login failed [{}] (sessionValid={}, cookiePresent={})",
              diagnosticCategory,
              requestedSessionValid,
              sessionCookiePresent);
    }
  }

  private static void logUnexpectedFailure(
      String diagnosticCategory,
      boolean requestedSessionValid,
      boolean sessionCookiePresent,
      AuthenticationException exception) {
    var source = exception.getCause() == null ? exception : exception.getCause();
    var redactedDiagnostic =
        new IllegalStateException("OIDC authentication failure details redacted");
    redactedDiagnostic.setStackTrace(source.getStackTrace());
    log.error(
        "OAuth2 login failed [{}] (sessionValid={}, cookiePresent={}, causeType={})",
        diagnosticCategory,
        requestedSessionValid,
        sessionCookiePresent,
        source.getClass().getName(),
        redactedDiagnostic);
  }

  private static void invalidate(HttpSession session) {
    if (session == null) {
      return;
    }
    try {
      session.invalidate();
    } catch (IllegalStateException _) {
      // Failure cleanup is idempotent when an earlier security component already invalidated it.
    }
  }

  private static boolean isAuthenticated(HttpSession session) {
    if (session == null) {
      return false;
    }
    try {
      var context =
          session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
      if (!(context instanceof SecurityContext securityContext)) {
        return false;
      }
      var authentication = securityContext.getAuthentication();
      return authentication != null && authentication.isAuthenticated();
    } catch (IllegalStateException _) {
      return false;
    }
  }

  private static void clearTemporaryState(HttpSession session) {
    if (session == null) {
      return;
    }
    try {
      session.removeAttribute(VALIDATED_REDIRECT_URI);
    } catch (IllegalStateException _) {
      // Failure cleanup is idempotent when an earlier security component already invalidated it.
    }
  }
}
