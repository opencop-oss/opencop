// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.security.interceptor;

import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_AUTH;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_BACKING_SESSION_ID;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_EXP;
import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_SESSIONLESS;
import static com.opencop.websocket.technical.spring.websocket.WebSocketProtocolTestFixtures.protocolViolationTracker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.business.policy.SessionLifetimePolicy;
import com.opencop.core.sdk.ports.gateway.account.CurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import com.opencop.websocket.technical.spring.websocket.PrincipalHandshakeHandler;
import com.opencop.websocket.technical.spring.websocket.UserSession;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

class SessionSecurityContextInterceptorTest {

  private static final String LEGACY_FIXTURE =
      "/com/opencop/core/sdk/technical/security/legacy-open-cop-principal-v1.ser";
  private static final Duration ABSOLUTE_LIFETIME = Duration.ofHours(8);
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-28T12:00:00Z"), java.time.ZoneOffset.UTC);
  private static final SessionLifetimePolicy LIFETIME_POLICY =
      new SessionLifetimePolicy(ABSOLUTE_LIFETIME, CLOCK);

  @Test
  void callbackShapedSerializedSecurityContextCompletesTheSessionHandshake() throws Exception {
    var authenticatedAt = CLOCK.instant().minus(Duration.ofHours(1));
    var principal =
        new OpenCopPrincipal(
            UUID.fromString("5d264bdf-447c-4d4a-a47a-ee20d4faf433"),
            "https://idp.example.test",
            "stable-id",
            "verified-subject",
            new OpenCopPrincipal.Profile(null, null, null, null, null, null),
            17,
            authenticatedAt);
    var callbackAuthentication =
        new OAuth2AuthenticationToken(
            principal,
            AuthorityUtils.createAuthorityList(
                "OIDC_USER", "SCOPE_openid", "SCOPE_profile", "SCOPE_email"),
            "oidc");
    var serializedSessionContext =
        roundTrip(new SecurityContextImpl(callbackAuthentication), SecurityContext.class);
    var serializedPrincipal =
        (OpenCopPrincipal) serializedSessionContext.getAuthentication().getPrincipal();

    var handshake = handshake(serializedSessionContext);

    assertThat(handshake.accepted()).isTrue();
    assertThat(handshake.attributes())
        .containsEntry(SessionSecurityContextInterceptor.ATTR_PRINCIPAL, serializedPrincipal)
        .containsEntry(ATTR_AUTH, handshake.authentication())
        .containsEntry(ATTR_BACKING_SESSION_ID, handshake.backingSessionId())
        .containsEntry(ATTR_EXP, authenticatedAt.plus(ABSOLUTE_LIFETIME))
        .containsEntry(ATTR_SESSIONLESS, false);
    assertThat(handshake.authentication().getAuthorizedClientRegistrationId()).isEqualTo("oidc");
    assertDownstreamHandshakeContract(handshake, serializedPrincipal);
  }

  @Test
  void expiredAuthenticationIsRejectedBeforeCurrentAccountLookup() {
    var principal =
        new OpenCopPrincipal(
            UUID.fromString("5d264bdf-447c-4d4a-a47a-ee20d4faf433"),
            "https://idp.example.test",
            "stable-id",
            "verified-subject",
            new OpenCopPrincipal.Profile(null, null, null, null, null, null),
            17,
            CLOCK.instant().minus(ABSOLUTE_LIFETIME));
    var authentication =
        new OAuth2AuthenticationToken(principal, AuthorityUtils.NO_AUTHORITIES, "oidc");
    var session = new MockHttpSession();
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(authentication));
    var request = new MockHttpServletRequest();
    request.setSession(session);
    request.setUserPrincipal(authentication);
    var attributes = new HashMap<String, Object>();
    var currentAccountGateway = mock(CurrentAccountAccessGateway.class);
    var interceptor =
        new SessionSecurityContextInterceptor(
            false, LIFETIME_POLICY, CLOCK, currentAccountGateway, new CurrentAccountAccessPolicy());

    var accepted =
        interceptor.beforeHandshake(
            new ServletServerHttpRequest(request),
            mock(ServerHttpResponse.class),
            mock(WebSocketHandler.class),
            attributes);

    assertThat(accepted).isFalse();
    assertThat(attributes).isEmpty();
    verifyNoInteractions(currentAccountGateway);
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   */
  @Test
  void retainedLegacyPrincipalCompletesHandshakeWithoutRewritingTheStoredSession()
      throws Exception {
    var principal = legacyPrincipal();
    var authentication =
        new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "test-client");
    var securityContext = new SecurityContextImpl(authentication);
    var session = new TrackingSession();
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
    var writesBeforeHandshake = session.attributeWrites;
    var request = new MockHttpServletRequest();
    request.setSession(session);
    request.setUserPrincipal(authentication);
    var attributes = new HashMap<String, Object>();

    var servletRequest = new ServletServerHttpRequest(request);
    var webSocketHandler = mock(WebSocketHandler.class);
    var accepted =
        interceptor(false, principal)
            .beforeHandshake(
                servletRequest, mock(ServerHttpResponse.class), webSocketHandler, attributes);
    var handshake =
        new HandshakeResult(
            accepted,
            authentication,
            attributes,
            servletRequest,
            webSocketHandler,
            session.getId());

    assertThat(accepted).isTrue();
    assertThat(attributes)
        .containsEntry(SessionSecurityContextInterceptor.ATTR_PRINCIPAL, principal)
        .containsEntry(ATTR_AUTH, authentication)
        .containsEntry(ATTR_BACKING_SESSION_ID, session.getId())
        .containsEntry(
            ATTR_EXP, Instant.ofEpochMilli(session.getCreationTime()).plus(ABSOLUTE_LIFETIME))
        .containsEntry(ATTR_SESSIONLESS, false);
    assertThat(session.attributeWrites).isEqualTo(writesBeforeHandshake);
    assertThat(
            session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
        .isSameAs(securityContext);
    assertDownstreamHandshakeContract(handshake, principal);
  }

  @Test
  void noHttpSessionInDevelopmentCreatesSessionlessUserWithFixedDeadline() {
    var request = new MockHttpServletRequest();
    var attributes = new HashMap<String, Object>();
    var servletRequest = new ServletServerHttpRequest(request);
    var webSocketHandler = mock(WebSocketHandler.class);

    var accepted =
        interceptor(true, null)
            .beforeHandshake(
                servletRequest, mock(ServerHttpResponse.class), webSocketHandler, attributes);

    assertThat(accepted).isTrue();
    assertThat(attributes)
        .containsEntry(ATTR_EXP, CLOCK.instant().plus(ABSOLUTE_LIFETIME))
        .containsEntry(ATTR_SESSIONLESS, true)
        .doesNotContainKey(ATTR_BACKING_SESSION_ID);
    var webSocketSession = mock(WebSocketSession.class);
    when(webSocketSession.getAttributes()).thenReturn(attributes);
    assertThat(new UserSession(webSocketSession, protocolViolationTracker()).isSessionless())
        .isTrue();
  }

  private static HandshakeResult handshake(SecurityContext securityContext) {
    var authentication = (OAuth2AuthenticationToken) securityContext.getAuthentication();
    var session = new MockHttpSession();
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
    var request = new MockHttpServletRequest();
    request.setSession(session);
    request.setUserPrincipal(authentication);
    var attributes = new HashMap<String, Object>();
    var servletRequest = new ServletServerHttpRequest(request);
    var webSocketHandler = mock(WebSocketHandler.class);

    var accepted =
        interceptor(false, (OpenCopPrincipal) authentication.getPrincipal())
            .beforeHandshake(
                servletRequest, mock(ServerHttpResponse.class), webSocketHandler, attributes);

    return new HandshakeResult(
        accepted, authentication, attributes, servletRequest, webSocketHandler, session.getId());
  }

  private static <T> T roundTrip(Object value, Class<T> type) throws Exception {
    var bytes = new ByteArrayOutputStream();
    try (var output = new ObjectOutputStream(bytes)) {
      output.writeObject(value);
    }
    try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return type.cast(input.readObject());
    }
  }

  private static SessionSecurityContextInterceptor interceptor(
      boolean devMode, OpenCopPrincipal principal) {
    return new SessionSecurityContextInterceptor(
        devMode,
        LIFETIME_POLICY,
        CLOCK,
        internalUserId ->
            Optional.of(
                new CurrentAccountAccess(
                    internalUserId,
                    true,
                    false,
                    principal == null ? 0 : principal.sessionGeneration(),
                    0)),
        new CurrentAccountAccessPolicy());
  }

  private static void assertDownstreamHandshakeContract(
      HandshakeResult handshake, OpenCopPrincipal principal) {
    assertThat(
            new ExposedPrincipalHandshakeHandler()
                .determine(
                    handshake.request(), handshake.webSocketHandler(), handshake.attributes()))
        .isSameAs(handshake.authentication());

    var webSocketSession = mock(WebSocketSession.class);
    when(webSocketSession.getAttributes()).thenReturn(handshake.attributes());
    assertThat(new UserSession(webSocketSession, protocolViolationTracker()).getUserId())
        .isEqualTo(principal.internalUserId());
  }

  private static OpenCopPrincipal legacyPrincipal() throws Exception {
    try (var fixture =
            SessionSecurityContextInterceptorTest.class.getResourceAsStream(LEGACY_FIXTURE);
        var input = new ObjectInputStream(fixture)) {
      return (OpenCopPrincipal) input.readObject();
    }
  }

  private record HandshakeResult(
      boolean accepted,
      OAuth2AuthenticationToken authentication,
      HashMap<String, Object> attributes,
      ServerHttpRequest request,
      WebSocketHandler webSocketHandler,
      String backingSessionId) {}

  private static final class ExposedPrincipalHandshakeHandler extends PrincipalHandshakeHandler {

    private Principal determine(
        ServerHttpRequest request,
        WebSocketHandler webSocketHandler,
        HashMap<String, Object> attributes) {
      return determineUser(request, webSocketHandler, attributes);
    }
  }

  private static final class TrackingSession extends MockHttpSession {

    private int attributeWrites;

    @Override
    public void setAttribute(String name, Object value) {
      attributeWrites++;
      super.setAttribute(name, value);
    }
  }
}
