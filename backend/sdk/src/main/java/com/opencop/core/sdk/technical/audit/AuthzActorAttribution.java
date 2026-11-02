// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.audit;

import com.opencop.core.sdk.ports.audit.AuthzDenialEvent;
import com.opencop.core.sdk.ports.audit.AuthzPrincipalType;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Shared stash logic for the candidate-attribute pattern: principal type plus best-effort actor
 * identity as request attributes, read later by the 403 backstop. Used by both the in-chain
 * classifier (Core's AuditingAccessDeniedHandler) and the post-chain {@link
 * AuthzActorAttributionFilter}; callers decide what a null authentication means for their position
 * in the chain before calling.
 *
 * <p>Identity is log-only downstream — never a metric tag. An anonymous stash also clears any
 * previously stashed actor so principal type and identity can never contradict.
 */
public final class AuthzActorAttribution {

  private AuthzActorAttribution() {}

  public static void stash(HttpServletRequest request, Authentication authentication) {
    if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
      request.setAttribute(
          AuthzAuditRequestAttributes.PRINCIPAL_TYPE, AuthzPrincipalType.ANONYMOUS);
      request.removeAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID);
      request.removeAttribute(AuthzAuditRequestAttributes.ACTOR_EMAIL);
      return;
    }
    request.setAttribute(
        AuthzAuditRequestAttributes.PRINCIPAL_TYPE, AuthzPrincipalType.AUTHENTICATED);
    if (authentication.getPrincipal() instanceof OpenCopPrincipal principal) {
      request.setAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID, principal.internalUserId());
      var email = principal.email();
      if (email != null) {
        // Stash sanitized so request attributes never hold the raw claim.
        request.setAttribute(
            AuthzAuditRequestAttributes.ACTOR_EMAIL, AuthzDenialEvent.sanitizeActorEmail(email));
      }
    }
  }
}
