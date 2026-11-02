// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stashes best-effort actor attribution as request attributes for the 403 backstop.
 *
 * <p>Registered just inside the security chain boundary (see {@link AuthzAuditConfig}), so it runs
 * on the request's way IN while the SecurityContext is still populated — the backstop itself
 * unwinds outside the chain after the context is cleared and can only read what was stashed here.
 * This is the same candidate-attribute pattern Core's AuditingAccessDeniedHandler uses for CSRF
 * classification.
 *
 * <p>Scope: denials raised INSIDE the security filter chain (CSRF, authorization rules) never reach
 * this filter — the chain rejects before forwarding. Those stay actor-less unless the rejecting
 * handler stashes identity itself (Core's AuditingAccessDeniedHandler does).
 *
 * <p>Identity attributes are log-only downstream (never metric tags), and attribution must never
 * affect the request: extraction failures are logged at debug (class name only) and leave the
 * honest UNKNOWN fallbacks in place.
 */
public class AuthzActorAttributionFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(AuthzActorAttributionFilter.class);

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    // Re-stash on ASYNC dispatch keeps the attribution consistent with the
    // auth that dispatch resolves; the stash keeps principal type and actor
    // identity in sync (a downgrade to ANONYMOUS also clears the actor).
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      stashAttribution(request);
    } catch (RuntimeException e) {
      // Never affect the request; the backstop falls back to UNKNOWN. Logged
      // because a persistent extraction failure would otherwise be an
      // unexplained attribution gap (class name only — no identity).
      log.debug("AUTHZ_ACTOR_ATTRIBUTION_FAILED exception_class={}", e.getClass().getSimpleName());
    }
    chain.doFilter(request, response);
  }

  private static void stashAttribution(HttpServletRequest request) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      // Post-chain a null context means "unknowable" — leave the honest
      // UNKNOWN fallbacks rather than guessing (the in-chain handler treats
      // null as ANONYMOUS because pre-auth CSRF denials arrive that way).
      return;
    }
    AuthzActorAttribution.stash(request, authentication);
  }
}
