// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.security.interceptor;

import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_AUTH;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_BACKING_SESSION_ID;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_EXP;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_SESSIONLESS;

import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDecision;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.business.policy.SessionLifetimePolicy;
import com.opencop.core.sdk.ports.gateway.account.CurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

public class SessionSecurityContextInterceptor implements HandshakeInterceptor {

  public static final String ATTR_PRINCIPAL = "opencop.principal";
  private static final Logger log =
      LoggerFactory.getLogger(SessionSecurityContextInterceptor.class);

  private final boolean devMode;
  private final SessionLifetimePolicy sessionLifetimePolicy;
  private final Clock clock;
  private final CurrentAccountAccessGateway currentAccountGateway;
  private final CurrentAccountAccessPolicy currentAccountAdmissionPolicy;

  public SessionSecurityContextInterceptor(
      boolean devMode,
      SessionLifetimePolicy sessionLifetimePolicy,
      Clock clock,
      CurrentAccountAccessGateway currentAccountGateway,
      CurrentAccountAccessPolicy currentAccountAdmissionPolicy) {
    this.devMode = devMode;
    this.sessionLifetimePolicy = Objects.requireNonNull(sessionLifetimePolicy);
    this.clock = Objects.requireNonNull(clock);
    this.currentAccountGateway = Objects.requireNonNull(currentAccountGateway);
    this.currentAccountAdmissionPolicy = Objects.requireNonNull(currentAccountAdmissionPolicy);
  }

  @Override
  public boolean beforeHandshake(
      @NonNull ServerHttpRequest request,
      @NonNull ServerHttpResponse response,
      @NonNull WebSocketHandler wsHandler,
      @NonNull Map<String, Object> attributes) {

    if (!(request instanceof ServletServerHttpRequest servletReq)) {
      log.debug("Not handshake because request is not a ServletServerHttpRequest");
      return false;
    }

    HttpServletRequest http = servletReq.getServletRequest();

    // This will load Spring Session from Redis using cookie
    var session = http.getSession(false);
    if (session == null) {
      if (devMode) {
        createDevModeFakeUser(attributes);
        return true;
      }
      log.debug("Not handshake because session is null");
      return false; // no session, reject the session
    }

    Object ctxObj =
        session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    if (!(ctxObj instanceof SecurityContext ctx)) {
      log.debug("Not handshake because ctx is not a SecurityContext");
      return false; // session exists, but security context does not
    }

    Authentication auth = ctx.getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      log.debug("Not handshake because auth is null");
      return false;
    }
    if (!(auth.getPrincipal() instanceof OpenCopPrincipal principal)) {
      log.debug("Not handshake because principal is not an OpenCopPrincipal");
      return false;
    }

    Instant sessionCreationTime = Instant.ofEpochMilli(session.getCreationTime());
    if (!sessionLifetimePolicy.isAuthenticationCurrent(
        principal.authenticatedAt(), sessionCreationTime)) {
      log.debug("Not handshake because authentication has expired");
      return false;
    }

    if (!admitCurrentAccount(principal)) {
      return false;
    }

    attributes.put(ATTR_PRINCIPAL, principal);
    attributes.put(ATTR_AUTH, auth);
    attributes.put(ATTR_BACKING_SESSION_ID, session.getId());
    attributes.put(
        ATTR_EXP,
        sessionLifetimePolicy.authenticationDeadline(
            principal.authenticatedAt(), sessionCreationTime));
    attributes.put(ATTR_SESSIONLESS, false);

    return true;
  }

  private boolean admitCurrentAccount(OpenCopPrincipal principal) {
    Optional<CurrentAccountAccess> currentAccount;
    try {
      currentAccount = currentAccountGateway.findByInternalUserId(principal.internalUserId());
    } catch (RuntimeException _) {
      log.warn("Rejecting WebSocket handshake because current-account lookup failed");
      return false;
    }
    if (currentAccountAdmissionPolicy.decide(currentAccount, principal.sessionGeneration())
        != CurrentAccountAccessDecision.ADMIT) {
      log.info("Rejecting WebSocket handshake because the current account is ineligible");
      return false;
    }
    return true;
  }

  private void createDevModeFakeUser(Map<String, Object> attributes) {
    UUID internalUserId = UUID.randomUUID();
    String idpUserId = UUID.randomUUID().toString();

    var authorities = AuthorityUtils.createAuthorityList("ROLE_ADMIN", "SCOPE_profile");

    Instant now = clock.instant();

    OpenCopPrincipal principal =
        new OpenCopPrincipal(
            internalUserId,
            "https://dev-idp.opencop.local",
            idpUserId,
            idpUserId,
            new OpenCopPrincipal.Profile("dev-user", null, null, null, "dev@local", null),
            0,
            now);

    OAuth2AuthenticationToken devAuth =
        new OAuth2AuthenticationToken(principal, authorities, "dev");

    attributes.put(ATTR_PRINCIPAL, devAuth.getPrincipal());
    attributes.put(ATTR_AUTH, devAuth);
    attributes.put(ATTR_EXP, sessionLifetimePolicy.authenticationDeadline(now, now));
    attributes.put(ATTR_SESSIONLESS, true);

    Principal wsPrincipal = principal::getName;
    attributes.put(SessionSecurityContextInterceptor.ATTR_PRINCIPAL, wsPrincipal);
    attributes.put("opencopPrincipal", principal);
  }

  @Override
  public void afterHandshake(
      @NonNull ServerHttpRequest request,
      @NonNull ServerHttpResponse response,
      @NonNull WebSocketHandler wsHandler,
      Exception ex) {
    // no-op
  }
}
