// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.security.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.business.policy.SessionLifetimePolicy;
import com.opencop.core.sdk.ports.gateway.account.CurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.socket.WebSocketHandler;

class CurrentAccountHandshakeAdmissionTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-4466554410a1");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-28T12:00:00Z"), java.time.ZoneOffset.UTC);

  @Test
  void authoritativelyAdmitsTheCurrentAccount() {
    var currentAccount = new CurrentAccountAccess(USER_ID, true, false, 17, 4);
    var fixture = fixture(17, Optional.of(currentAccount));

    assertThat(fixture.handshake()).isTrue();
  }

  @Test
  void rejectsMissingInactiveAndStaleAccounts() {
    var deniedAccounts =
        java.util.List.of(
            Optional.<CurrentAccountAccess>empty(),
            Optional.of(new CurrentAccountAccess(USER_ID, false, false, 17, 4)),
            Optional.of(new CurrentAccountAccess(USER_ID, true, false, 18, 4)));

    deniedAccounts.forEach(
        account -> {
          var fixture = fixture(17, account);

          assertThat(fixture.handshake()).isFalse();
        });
  }

  @Test
  void rejectsPostgresFailure() {
    var fixture = fixture(17, Optional.of(new CurrentAccountAccess(USER_ID, true, false, 17, 4)));
    when(fixture.gateway().findByInternalUserId(USER_ID))
        .thenThrow(new IllegalStateException("postgres unavailable"));

    assertThat(fixture.handshake()).isFalse();
  }

  private static Fixture fixture(
      long sessionGeneration, Optional<CurrentAccountAccess> currentAccount) {
    var principal = principal(sessionGeneration);
    var authentication =
        new OAuth2AuthenticationToken(
            principal, AuthorityUtils.createAuthorityList("OIDC_USER"), "oidc");
    var session = new MockHttpSession();
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(authentication));
    var request = new MockHttpServletRequest();
    request.setSession(session);
    request.setUserPrincipal(authentication);
    var gateway = mock(CurrentAccountAccessGateway.class);
    when(gateway.findByInternalUserId(USER_ID)).thenReturn(currentAccount);
    var interceptor =
        new SessionSecurityContextInterceptor(
            false,
            new SessionLifetimePolicy(Duration.ofHours(8), CLOCK),
            CLOCK,
            gateway,
            new CurrentAccountAccessPolicy());
    return new Fixture(principal, session, request, gateway, interceptor, new HashMap<>());
  }

  private static OpenCopPrincipal principal(long sessionGeneration) {
    return new OpenCopPrincipal(
        USER_ID,
        "https://idp.example.test",
        "stable-id",
        "verified-subject",
        new OpenCopPrincipal.Profile(null, null, null, null, null, null),
        sessionGeneration,
        Instant.parse("2026-07-28T10:00:00Z"));
  }

  private record Fixture(
      OpenCopPrincipal principal,
      MockHttpSession session,
      MockHttpServletRequest request,
      CurrentAccountAccessGateway gateway,
      SessionSecurityContextInterceptor interceptor,
      HashMap<String, Object> attributes) {

    boolean handshake() {
      return interceptor.beforeHandshake(
          new ServletServerHttpRequest(request),
          mock(ServerHttpResponse.class),
          mock(WebSocketHandler.class),
          attributes);
    }
  }
}
