// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.auth;

import com.opencop.core.api.general.business.usecase.user.GlobalSignOutUsecase;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/bff")
public class GlobalSignOutEndpoint {

  private static final Logger log = LoggerFactory.getLogger(GlobalSignOutEndpoint.class);
  private static final String UNAVAILABLE_CODE = "GLOBAL_SIGN_OUT_UNAVAILABLE";
  private static final String UNAVAILABLE_MESSAGE =
      "Global sign-out is temporarily unavailable. Please retry.";
  private static final String CURRENT_SESSION_FAILURE =
      "GLOBAL_SIGN_OUT_FAILED operation=global_sign_out"
          + " stage=current_session exception_class={} trace_id={}";
  private final GlobalSignOutUsecase globalSignOut;
  private final CookieSerializer sessionCookieSerializer;
  private final LogoutHandler securityContextLogoutHandler = securityContextLogoutHandler();

  public GlobalSignOutEndpoint(
      GlobalSignOutUsecase globalSignOut, CookieSerializer sessionCookieSerializer) {
    this.globalSignOut = globalSignOut;
    this.sessionCookieSerializer = sessionCookieSerializer;
  }

  @PostMapping("/logout")
  public ResponseEntity<GlobalErrorResponse> signOut(
      @AuthenticationPrincipal OpenCopPrincipal principal,
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) {
    var session = request.getSession(false);
    if (session == null) {
      return unavailable();
    }

    try {
      globalSignOut.signOut(principal.internalUserId());
    } catch (RuntimeException exception) {
      log.warn(
          "GLOBAL_SIGN_OUT_FAILED operation=global_sign_out"
              + " stage=required exception_class={} trace_id={}",
          exception.getClass().getSimpleName(),
          currentTraceId());
      return unavailable();
    }

    invalidateCurrentSession(session);
    clearSecurityContext(request, response, authentication);
    clearSessionCookie(request, response);
    return ResponseEntity.noContent().build();
  }

  private void invalidateCurrentSession(HttpSession session) {
    try {
      session.invalidate();
    } catch (RuntimeException exception) {
      log.warn(CURRENT_SESSION_FAILURE, exception.getClass().getSimpleName(), currentTraceId());
    }
  }

  private void clearSecurityContext(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    try {
      securityContextLogoutHandler.logout(request, response, authentication);
    } catch (RuntimeException _) {
      // The remaining response cleanup is still required after logical sign-out committed.
    }
  }

  private void clearSessionCookie(HttpServletRequest request, HttpServletResponse response) {
    try {
      sessionCookieSerializer.writeCookieValue(new CookieValue(request, response, ""));
    } catch (RuntimeException _) {
      // Cookie cleanup remains independent from security-context cleanup and the response.
    }
  }

  private static ResponseEntity<GlobalErrorResponse> unavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new GlobalErrorResponse(UNAVAILABLE_CODE, UNAVAILABLE_MESSAGE));
  }

  private static String currentTraceId() {
    var spanContext = Span.current().getSpanContext();
    return spanContext.isValid() ? spanContext.getTraceId() : "unavailable";
  }

  private static LogoutHandler securityContextLogoutHandler() {
    var handler = new SecurityContextLogoutHandler();
    handler.setInvalidateHttpSession(false);
    return handler;
  }
}
