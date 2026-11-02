// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.technical.audit.AuthzActorAttribution;
import com.opencop.core.sdk.technical.audit.AuthzAuditRequestAttributes;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

public class AuditingAccessDeniedHandler implements AccessDeniedHandler {

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      org.springframework.security.access.AccessDeniedException accessDeniedException)
      throws IOException, ServletException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    var csrfRejected = accessDeniedException instanceof CsrfException;
    try {
      if (csrfRejected) {
        request.setAttribute(
            AuthzAuditRequestAttributes.CANDIDATE_REASON, AuthzReasonCode.CSRF_REJECTED);
      }
      // In-chain denials never reach the AuthzActorAttributionFilter (it
      // sits downstream of the chain), so stash identity here — a null
      // context at this point IS anonymous (pre-auth CSRF denials arrive
      // that way), which is exactly what the shared stash assumes.
      AuthzActorAttribution.stash(request, authentication);
    } catch (RuntimeException _) {
      // Classification must never cost the response; the backstop falls back
      // to UNKNOWN attribution.
    }

    if (!csrfRejected) {
      CoreSecurityErrorResponse.FORBIDDEN.writeTo(response);
      return;
    }
    if (isAuthenticated(authentication)) {
      CoreSecurityErrorResponse.CSRF_REJECTED.writeTo(response);
      return;
    }

    CoreSecurityErrorResponse.UNAUTHORIZED.writeTo(response);
  }

  private static boolean isAuthenticated(Authentication authentication) {
    return authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken);
  }
}
