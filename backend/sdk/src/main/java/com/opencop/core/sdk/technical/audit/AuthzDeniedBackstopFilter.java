// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.audit;

import com.opencop.core.sdk.ports.audit.AuthzAuditReporting;
import com.opencop.core.sdk.ports.audit.AuthzDenialEvent;
import com.opencop.core.sdk.ports.audit.AuthzPrincipalType;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.audit.AuthzResource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Final-status 403 backstop: counts every servlet 403 that no rich emitter already reported.
 *
 * <p>Registered outside the security filter chain (see {@link AuthzAuditConfig}), so by the time
 * the response unwinds here the SecurityContext has been cleared by the chain. Attribution
 * therefore comes only from request attributes stashed by in-chain classifiers — {@link
 * AuthzActorAttributionFilter} for actor identity and principal type, Core's
 * AuditingAccessDeniedHandler for CSRF classification; whatever was not stashed stays honestly
 * UNKNOWN. Identity remains log-only downstream, never a metric tag.
 */
public class AuthzDeniedBackstopFilter extends OncePerRequestFilter {

  private final AuthzAuditReporting reporter;

  public AuthzDeniedBackstopFilter(AuthzAuditReporting reporter) {
    this.reporter = reporter;
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    // Re-run on the ASYNC dispatch so a 403 finalized in a subsequent ASYNC
    // dispatch (all Spring MVC async paths) is still observed; the
    // isAsyncStarted() guard below keeps emission single. Known residual gap:
    // a 403 set via AsyncContext.complete() with no re-dispatch (raw servlet
    // async) is never counted — closing it would need an AsyncListener.
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } finally {
      // The status is only final once the request is no longer async; ERROR
      // dispatches are skipped by the OncePerRequestFilter default, and
      // sendError(403) is already visible on this unwind.
      if (!request.isAsyncStarted()
          && response.getStatus() == 403
          && request.getAttribute(AuthzAuditRequestAttributes.REPORTED) == null) {
        reporter.reportDenied(buildGenericEvent(request));
        // Mark directly on the request: the reporter's RequestContextHolder
        // marking is request-thread scoped and already unwound at this point.
        request.setAttribute(AuthzAuditRequestAttributes.REPORTED, Boolean.TRUE);
      }
    }
  }

  private AuthzDenialEvent buildGenericEvent(HttpServletRequest request) {
    try {
      return AuthzDenialEvent.generic(
          principalType(request),
          actorUserId(request),
          actorEmail(request),
          AuthzResource.httpRequest(request.getMethod(), request.getRequestURI()),
          AuthzRequestActions.routeAction(request),
          reasonCode(request));
    } catch (RuntimeException e) {
      return AuthzDenialEvent.generic(
          AuthzPrincipalType.UNKNOWN,
          null,
          null,
          AuthzResource.httpRequest("UNKNOWN", "UNKNOWN"),
          null,
          AuthzReasonCode.UNATTRIBUTED);
    }
  }

  private static AuthzReasonCode reasonCode(HttpServletRequest request) {
    var candidate = request.getAttribute(AuthzAuditRequestAttributes.CANDIDATE_REASON);
    return candidate instanceof AuthzReasonCode reason ? reason : AuthzReasonCode.UNATTRIBUTED;
  }

  private static UUID actorUserId(HttpServletRequest request) {
    var stashed = request.getAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID);
    return stashed instanceof UUID userId ? userId : null;
  }

  private static String actorEmail(HttpServletRequest request) {
    var stashed = request.getAttribute(AuthzAuditRequestAttributes.ACTOR_EMAIL);
    return stashed instanceof String email ? email : null;
  }

  private static AuthzPrincipalType principalType(HttpServletRequest request) {
    var candidate = request.getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE);
    if (candidate instanceof AuthzPrincipalType principalType) {
      return principalType;
    }
    // Defensive fallbacks for non-production filter arrangements; at the
    // production position both sources are normally empty and the honest
    // answer is UNKNOWN.
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null) {
      return authentication instanceof AnonymousAuthenticationToken
          ? AuthzPrincipalType.ANONYMOUS
          : AuthzPrincipalType.AUTHENTICATED;
    }
    return request.getUserPrincipal() == null
        ? AuthzPrincipalType.UNKNOWN
        : AuthzPrincipalType.AUTHENTICATED;
  }
}
