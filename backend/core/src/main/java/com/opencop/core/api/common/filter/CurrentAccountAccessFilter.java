// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import com.opencop.core.api.general.business.model.auth.SessionLifetimeExpiredException;
import com.opencop.core.api.general.business.usecase.user.ResolveCurrentAccountAccessUsecase;
import com.opencop.core.api.general.technical.observability.user.CurrentAccountSessionTelemetry;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDeniedException;
import com.opencop.core.sdk.business.policy.SessionLifetimePolicy;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CurrentAccountAccessFilter extends OncePerRequestFilter {

  private final ResolveCurrentAccountAccessUsecase resolveCurrentAccountAccess;
  private final CurrentAccountAuthenticationFactory authenticationFactory;
  private final SpaAuthenticationEntryPoint authenticationEntryPoint;
  private final CurrentAccountSessionTelemetry sessionTelemetry;
  private final SessionLifetimePolicy sessionLifetimePolicy;

  public CurrentAccountAccessFilter(
      ResolveCurrentAccountAccessUsecase resolveCurrentAccountAccess,
      CurrentAccountAuthenticationFactory authenticationFactory,
      SpaAuthenticationEntryPoint authenticationEntryPoint,
      CurrentAccountSessionTelemetry sessionTelemetry,
      SessionLifetimePolicy sessionLifetimePolicy) {
    this.resolveCurrentAccountAccess = resolveCurrentAccountAccess;
    this.authenticationFactory = authenticationFactory;
    this.authenticationEntryPoint = authenticationEntryPoint;
    this.sessionTelemetry = sessionTelemetry;
    this.sessionLifetimePolicy = sessionLifetimePolicy;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof OAuth2AuthenticationToken oauthAuthentication)
        || !(oauthAuthentication.getPrincipal() instanceof OpenCopPrincipal principal)) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      var session = request.getSession(false);
      if (session == null) {
        throw new SessionLifetimeExpiredException();
      }
      if (!sessionLifetimePolicy.isAuthenticationCurrent(
          principal.authenticatedAt(), Instant.ofEpochMilli(session.getCreationTime()))) {
        throw new SessionLifetimeExpiredException();
      }
      var access =
          resolveCurrentAccountAccess.resolve(
              principal.internalUserId(), principal.sessionGeneration());
      var requestContext = SecurityContextHolder.createEmptyContext();
      requestContext.setAuthentication(authenticationFactory.create(oauthAuthentication, access));
      SecurityContextHolder.setContext(requestContext);
      filterChain.doFilter(request, response);
    } catch (CurrentAccountAccessDeniedException exception) {
      sessionTelemetry.recordInvalidation(exception.reason());
      reject(request, response, "Current account access denied", exception);
    } catch (SessionLifetimeExpiredException exception) {
      reject(request, response, "Session lifetime ended", exception);
    }
  }

  private void reject(
      HttpServletRequest request,
      HttpServletResponse response,
      String message,
      RuntimeException cause)
      throws IOException {
    SecurityContextHolder.clearContext();
    var session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    authenticationEntryPoint.commence(
        request, response, new InsufficientAuthenticationException(message, cause));
  }
}
