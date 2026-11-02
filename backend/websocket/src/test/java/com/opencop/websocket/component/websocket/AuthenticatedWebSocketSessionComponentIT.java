// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.websocket;

import static com.opencop.websocket.technical.spring.security.AuthConstants.SESSION_ENDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.component.redis.RedisComponentSupport;
import com.opencop.core.sdk.ports.gateway.account.CurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.technical.gateway.db.account.PostgresCurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import com.opencop.core.sdk.technical.security.session.AuthSessionProperties;
import com.opencop.websocket.component.support.WebSocketComponentTestApplication;
import com.opencop.websocket.component.support.fixture.RecordingBinaryWebSocketHandler;
import com.opencop.websocket.component.support.fixture.WebSocketFixtures;
import com.opencop.websocket.technical.spring.websocket.SessionExpiryKiller;
import com.opencop.websocket.technical.spring.websocket.UserSession;
import com.opencop.websocket.technical.spring.websocket.WsSessionRegistry;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;

@SpringBootTest(
    classes = {
      WebSocketComponentTestApplication.class,
      AuthenticatedWebSocketSessionComponentIT.FailureControlsConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticatedWebSocketSessionComponentIT extends AbstractDatabaseIT {

  private static final String LEGACY_FIXTURE =
      "/com/opencop/core/sdk/technical/security/legacy-open-cop-principal-v1.ser";
  private static final UUID MINIMAL_USER_ID =
      UUID.fromString("5d264bdf-447c-4d4a-a47a-ee20d4faf433");
  private static final int PROTOCOL_VIOLATION_LIMIT = 10;

  @LocalServerPort private int port;

  @Autowired private CookieSerializer cookieSerializer;
  @Autowired private AuthSessionProperties sessionProperties;
  @Autowired private RedisConnectionFactory redisConnectionFactory;
  @Autowired private SessionRepository<? extends Session> sessionRepository;
  @Autowired private SessionExpiryKiller sessionExpiryKiller;
  @Autowired private WsSessionRegistry sessionRegistry;
  @Autowired private DataSource dataSource;
  @Autowired private CurrentAccountGatewayFailureControl currentAccountGatewayFailureControl;

  private final WebSocketFixtures fixture = new WebSocketFixtures();

  @DynamicPropertySource
  static void registerAuthenticatedWebSocketProperties(DynamicPropertyRegistry registry) {
    registry.add("idp.issuer", () -> "https://component-tests.opencop.local");
    registry.add(
        "spring.flyway.placeholders.postgisExtensionSql",
        () -> FlywayGeoFeaturesPlaceholders.POSTGIS_EXTENSION_SQL);
    registry.add(
        "spring.flyway.placeholders.geoFeaturesGeomType",
        () -> FlywayGeoFeaturesPlaceholders.GEO_FEATURES_GEOM_TYPE);
    registry.add(
        "spring.flyway.placeholders.geoFeaturesGeomIndexSql",
        () -> FlywayGeoFeaturesPlaceholders.GEO_FEATURES_GEOM_INDEX_SQL);
    registry.add(
        "spring.flyway.placeholders.geoFeaturesHistoryChunkIndexSql",
        () -> FlywayGeoFeaturesPlaceholders.GEO_FEATURES_HISTORY_CHUNK_INDEX_SQL);
    registry.add(
        "spring.flyway.placeholders.geoFeaturesActiveHistoryLookupIndexSql",
        () -> FlywayGeoFeaturesPlaceholders.GEO_FEATURES_ACTIVE_HISTORY_LOOKUP_INDEX_SQL);
    RedisComponentSupport.registerRedisProperties(registry);
    registry.add("redis.ssl", () -> "false");
    registry.add("opencop.dev.mode", () -> "false");
    registry.add("spring.session.store-type", () -> "redis");
    registry.add("websocket.allowed-origins", () -> "*");
  }

  @BeforeEach
  void resetDependencies() {
    try (var connection = redisConnectionFactory.getConnection()) {
      connection.serverCommands().flushAll();
    }
    currentAccountGatewayFailureControl.reset();
  }

  @AfterEach
  void closeWebSocketSessions() {
    fixture.reset();
    for (var userSession : sessionRegistry.getUsersSessions()) {
      sessionExpiryKiller.cancel(userSession);
      sessionRegistry.remove(userSession);
    }
  }

  @Test
  void minimalRedisSessionCompletesTheAuthenticatedWebSocketHandshake() throws Exception {
    Instant authenticatedAt = Instant.now();
    var principal =
        new OpenCopPrincipal(
            MINIMAL_USER_ID,
            "https://idp.example.test",
            "stable-id",
            "verified-subject",
            new OpenCopPrincipal.Profile(null, null, null, null, null, null),
            17,
            authenticatedAt);
    var sessionId =
        persistSecurityContext(
            principal,
            AuthorityUtils.createAuthorityList(
                "OIDC_USER", "SCOPE_openid", "SCOPE_profile", "SCOPE_email"),
            "oidc");
    seedCurrentAccount(MINIMAL_USER_ID, true, 17);

    var clientHandler = new RecordingBinaryWebSocketHandler();
    var webSocketSession =
        fixture.connect(websocketUrl(), clientHandler, sessionCookieHeaders(sessionId));

    var registeredSession = awaitSingleUserSession();
    assertThat(webSocketSession.isOpen()).isTrue();
    assertThat(registeredSession.getUserId()).isEqualTo(MINIMAL_USER_ID);
    assertThat(registeredSession.getBackingSessionId()).isEqualTo(sessionId);
    assertThat(registeredSession.getAuthenticationDeadline())
        .isEqualTo(authenticatedAt.plus(sessionProperties.getAbsoluteLifetime()));
    assertThat(registeredSession.getWebsocketSession().getPrincipal())
        .isInstanceOfSatisfying(
            OAuth2AuthenticationToken.class,
            authentication -> {
              assertThat(authentication.getAuthorizedClientRegistrationId()).isEqualTo("oidc");
              assertThat(authentication.getPrincipal()).isInstanceOf(OpenCopPrincipal.class);
            });
    assertThat(clientHandler.transportError()).isNull();
  }

  @Test
  void protocolLimitClosureLeavesAnotherSocketForTheSameUserOpen() throws Exception {
    var principal =
        new OpenCopPrincipal(
            MINIMAL_USER_ID,
            "https://idp.example.test",
            "stable-id",
            "verified-subject",
            new OpenCopPrincipal.Profile(null, null, null, null, null, null),
            17,
            Instant.now());
    var sessionId =
        persistSecurityContext(
            principal,
            AuthorityUtils.createAuthorityList(
                "OIDC_USER", "SCOPE_openid", "SCOPE_profile", "SCOPE_email"),
            "oidc");
    seedCurrentAccount(MINIMAL_USER_ID, true, 17);

    var violatorHandler = new RecordingBinaryWebSocketHandler();
    var violator =
        fixture.connect(websocketUrl(), violatorHandler, sessionCookieHeaders(sessionId));
    var siblingHandler = new RecordingBinaryWebSocketHandler();
    var sibling = fixture.connect(websocketUrl(), siblingHandler, sessionCookieHeaders(sessionId));
    var registered =
        awaitValue(
            () ->
                sessionRegistry.getUsersSessions().size() == 2
                    ? sessionRegistry.getUsersSessions()
                    : null,
            "two authenticated sockets for one user");
    assertThat(registered)
        .extracting(UserSession::getUserId)
        .containsExactly(MINIMAL_USER_ID, MINIMAL_USER_ID);

    for (int i = 0; i < PROTOCOL_VIOLATION_LIMIT; i++) {
      fixture.send(violator, fixture.subscribe("/invalid/" + i));
    }

    var closeStatus = awaitValue(violatorHandler::closeStatus, "same-user protocol limit closure");
    assertThat(closeStatus.getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
    assertThat(closeStatus.getReason()).isEqualTo("Protocol limit exceeded");
    var retained =
        awaitValue(
            () ->
                sessionRegistry.getUsersSessions().size() == 1
                    ? sessionRegistry.getUsersSessions().getFirst()
                    : null,
            "sibling session retained after same-user closure");
    assertThat(retained.getUserId()).isEqualTo(MINIMAL_USER_ID);
    assertThat(sibling.isOpen()).isTrue();
    assertThat(siblingHandler.closeStatus()).isNull();
    assertThat(violator.isOpen()).isFalse();
  }

  @Test
  void alreadyExpiredAuthenticationIsRejectedByTheRealHandshakeBeforeAdmission() {
    UUID userId = UUID.randomUUID();
    Instant authenticatedAt = Instant.now().minus(sessionProperties.getAbsoluteLifetime());
    var principal =
        new OpenCopPrincipal(
            userId,
            "https://idp.example.test",
            "stable-" + userId,
            "verified-" + userId,
            new OpenCopPrincipal.Profile(null, null, null, null, null, null),
            17,
            authenticatedAt);
    String sessionId =
        persistSecurityContext(
            principal, AuthorityUtils.createAuthorityList("OIDC_USER", "SCOPE_openid"), "oidc");
    seedCurrentAccount(userId, true, 17);

    assertRejectedWithoutAdmission(userId, sessionId);

    var retainedSession = sessionRepository.findById(sessionId);
    assertThat(retainedSession).isNotNull();
    assertThat(currentAccountGatewayFailureControl.lookupCount()).isZero();
  }

  @Test
  void fixedAuthenticationDeadlineClosesDuringContinuousHeartbeatTrafficWithoutTouchingIdleTime()
      throws Exception {
    UUID userId = UUID.randomUUID();
    Instant authenticatedAt =
        Instant.now().minus(sessionProperties.getAbsoluteLifetime()).plusSeconds(2);
    var principal =
        new OpenCopPrincipal(
            userId,
            "https://idp.example.test",
            "stable-id",
            "verified-subject",
            new OpenCopPrincipal.Profile(null, null, null, null, null, null),
            17,
            authenticatedAt);
    String sessionId =
        persistSecurityContext(
            principal,
            AuthorityUtils.createAuthorityList(
                "OIDC_USER", "SCOPE_openid", "SCOPE_profile", "SCOPE_email"),
            "oidc");
    seedCurrentAccount(userId, true, 17);
    var clientHandler = new RecordingBinaryWebSocketHandler();
    var webSocketSession =
        fixture.connect(websocketUrl(), clientHandler, sessionCookieHeaders(sessionId));
    UserSession registeredSession = awaitSingleUserSession();
    Instant lastAccessedAfterHandshake =
        sessionRepository.findById(sessionId).getLastAccessedTime();

    var trafficDeadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (clientHandler.closeStatus() == null && System.nanoTime() < trafficDeadline) {
      if (webSocketSession.isOpen()) {
        try {
          fixture.send(webSocketSession, fixture.heartbeat());
        } catch (Exception ignored) {
          // The deadline may close the socket between the open check and the send.
        }
      }
      Thread.sleep(50);
    }

    var closeStatus = awaitValue(clientHandler::closeStatus, "fixed authentication deadline close");
    assertThat(closeStatus.getCode()).isEqualTo(SESSION_ENDED);
    assertThat(closeStatus.getReason()).isEqualTo("Session ended");
    assertThat(registeredSession.getAuthenticationDeadline())
        .isEqualTo(authenticatedAt.plus(sessionProperties.getAbsoluteLifetime()));
    assertThat(sessionRepository.findById(sessionId))
        .isNotNull()
        .extracting(Session::getLastAccessedTime)
        .isEqualTo(lastAccessedAfterHandshake);
  }

  @Test
  void sharedSessionConfigurationUsesTheLifecycleDefaults() {
    Session session = (Session) sessionRepository.createSession();
    var request = new MockHttpServletRequest();
    request.setSecure(true);
    var response = new MockHttpServletResponse();

    cookieSerializer.writeCookieValue(
        new CookieSerializer.CookieValue(request, response, session.getId()));

    assertThat(sessionProperties.getIdleTimeout()).isEqualTo(Duration.ofMinutes(30));
    assertThat(sessionProperties.getAbsoluteLifetime()).isEqualTo(Duration.ofHours(8));
    assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
    assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
        .contains("Max-Age=28800")
        .contains("Expires=");
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   */
  @Test
  void retainedLegacyRedisSessionCompletesHandshakeWithoutRewritingSecurityContext()
      throws Exception {
    var principal = legacyPrincipal();
    seedCurrentAccount(principal.internalUserId(), true, principal.sessionGeneration());
    var sessionId =
        persistSecurityContext(principal, principal.getAuthorities(), "legacy-oidc-client");
    var securityContextBeforeHandshake = persistedSecurityContextBytes(sessionId);

    var clientHandler = new RecordingBinaryWebSocketHandler();
    var webSocketSession =
        fixture.connect(websocketUrl(), clientHandler, sessionCookieHeaders(sessionId));

    var registeredSession = awaitSingleUserSession();
    assertThat(webSocketSession.isOpen()).isTrue();
    assertThat(registeredSession.getUserId()).isEqualTo(principal.internalUserId());
    assertThat(clientHandler.transportError()).isNull();
    assertThat(persistedSecurityContextBytes(sessionId)).isEqualTo(securityContextBeforeHandshake);
  }

  @Test
  void missingAndInactiveAccountsAreRejectedByTheRealHandshake() {
    record DeniedAccount(boolean present, boolean active, long generation) {}
    var deniedAccounts =
        java.util.List.of(new DeniedAccount(false, false, 17), new DeniedAccount(true, false, 17));

    deniedAccounts.forEach(
        denied -> {
          UUID userId = UUID.randomUUID();
          var principal =
              new OpenCopPrincipal(
                  userId,
                  "https://idp.example.test",
                  "stable-" + userId,
                  "verified-" + userId,
                  new OpenCopPrincipal.Profile(null, null, null, null, null, null),
                  17,
                  Instant.now());
          if (denied.present()) {
            seedCurrentAccount(userId, denied.active(), denied.generation());
          }
          String sessionId =
              persistSecurityContext(
                  principal,
                  AuthorityUtils.createAuthorityList("OIDC_USER", "SCOPE_openid"),
                  "oidc");
          var clientHandler = new RecordingBinaryWebSocketHandler();

          assertThatThrownBy(
                  () ->
                      fixture.connect(
                          websocketUrl(), clientHandler, sessionCookieHeaders(sessionId)))
              .isInstanceOf(Exception.class);
          assertThat(sessionRegistry.getUserSessions(userId)).isEmpty();
        });
  }

  @Test
  void retainedStaleSessionIsRejectedByTheCommittedGenerationDuringRealHandshake() {
    UUID userId = UUID.randomUUID();
    seedCurrentAccount(userId, true, 18);
    String sessionId =
        persistSecurityContext(
            principal(userId, 17),
            AuthorityUtils.createAuthorityList("OIDC_USER", "SCOPE_openid"),
            "oidc");

    assertRejectedWithoutAdmission(userId, sessionId);

    assertThat(sessionRepository.findById(sessionId)).isNotNull();
  }

  @Test
  void disappearedBackingSessionRejectsUpgradeBeforePostgresLookupOrRegistration() {
    UUID userId = UUID.randomUUID();
    seedCurrentAccount(userId, true, 17);
    String sessionId =
        persistSecurityContext(
            principal(userId, 17),
            AuthorityUtils.createAuthorityList("OIDC_USER", "SCOPE_openid"),
            "oidc");
    sessionRepository.deleteById(sessionId);

    assertRejectedWithoutAdmission(userId, sessionId);

    assertThat(currentAccountGatewayFailureControl.lookupCount()).isZero();
  }

  @Test
  void postgresFailureRejectsUpgradeWithoutRegistration() {
    UUID userId = UUID.randomUUID();
    seedCurrentAccount(userId, true, 17);
    String sessionId =
        persistSecurityContext(
            principal(userId, 17),
            AuthorityUtils.createAuthorityList("OIDC_USER", "SCOPE_openid"),
            "oidc");
    currentAccountGatewayFailureControl.failNextLookup();

    assertRejectedWithoutAdmission(userId, sessionId);

    assertThat(currentAccountGatewayFailureControl.lookupCount()).isOne();
  }

  private void seedCurrentAccount(UUID userId, boolean active, long sessionGeneration) {
    new JdbcTemplate(dataSource)
        .update(
            """
            INSERT INTO opencop_user (
              id, idp_user_id, idp_username, idp_issuer, username, active, administrator,
              session_generation
            ) VALUES (?, ?, ?, ?, ?, ?, false, ?)
            ON CONFLICT (id) DO UPDATE
            SET active = EXCLUDED.active,
                session_generation = EXCLUDED.session_generation
            """,
            userId,
            "idp-" + userId,
            "user-" + userId,
            "https://issuer.test",
            "user-" + userId,
            active,
            sessionGeneration);
  }

  private void assertRejectedWithoutAdmission(UUID userId, String sessionId) {
    var clientHandler = new RecordingBinaryWebSocketHandler();

    assertThatThrownBy(
            () -> fixture.connect(websocketUrl(), clientHandler, sessionCookieHeaders(sessionId)))
        .isInstanceOf(Exception.class);
    assertThat(sessionRegistry.getUsersSessions()).isEmpty();
    assertThat(sessionRegistry.getUserSessions(userId)).isEmpty();
  }

  private String persistSecurityContext(
      OpenCopPrincipal principal,
      Collection<? extends GrantedAuthority> authorities,
      String registrationId) {
    var authentication = new OAuth2AuthenticationToken(principal, authorities, registrationId);
    var securityContext = SecurityContextHolder.createEmptyContext();
    securityContext.setAuthentication(authentication);
    return persistSession(sessionRepository, securityContext);
  }

  private static OpenCopPrincipal principal(UUID userId, long sessionGeneration) {
    return new OpenCopPrincipal(
        userId,
        "https://idp.example.test",
        "stable-" + userId,
        "verified-" + userId,
        new OpenCopPrincipal.Profile(null, null, null, null, null, null),
        sessionGeneration,
        Instant.now());
  }

  private static String persistSession(
      SessionRepository repository, SecurityContext securityContext) {
    Session session = (Session) repository.createSession();
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
    repository.save(session);
    return session.getId();
  }

  private WebSocketHttpHeaders sessionCookieHeaders(String sessionId) {
    var request = new MockHttpServletRequest();
    request.setSecure(true);
    var response = new MockHttpServletResponse();
    cookieSerializer.writeCookieValue(
        new CookieSerializer.CookieValue(request, response, sessionId));
    var setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).isNotNull();
    var headers = new WebSocketHttpHeaders();
    headers.add(HttpHeaders.COOKIE, setCookie.substring(0, setCookie.indexOf(';')));
    return headers;
  }

  private byte[] persistedSecurityContextBytes(String sessionId) {
    var key = ("opencop:bff:sessions:" + sessionId).getBytes(StandardCharsets.UTF_8);
    try (var connection = redisConnectionFactory.getConnection()) {
      return connection.hashCommands().hGetAll(key).entrySet().stream()
          .filter(
              entry ->
                  new String(entry.getKey(), StandardCharsets.UTF_8)
                      .contains(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
          .map(Map.Entry::getValue)
          .findFirst()
          .orElseThrow()
          .clone();
    }
  }

  private UserSession awaitSingleUserSession() {
    return awaitValue(
        () ->
            sessionRegistry.getUsersSessions().size() == 1
                ? sessionRegistry.getUsersSessions().getFirst()
                : null,
        "authenticated WebSocket registration");
  }

  private <T> T awaitValue(Supplier<T> supplier, String description) {
    var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      var value = supplier.get();
      if (value != null) {
        return value;
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting for " + description, e);
      }
    }
    throw new AssertionError("Timed out waiting for " + description);
  }

  private String websocketUrl() {
    return "ws://127.0.0.1:" + port + "/ws";
  }

  private static OpenCopPrincipal legacyPrincipal() throws Exception {
    try (var fixture =
            AuthenticatedWebSocketSessionComponentIT.class.getResourceAsStream(LEGACY_FIXTURE);
        var input = new ObjectInputStream(fixture)) {
      return (OpenCopPrincipal) input.readObject();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FailureControlsConfig {

    @Bean
    CurrentAccountGatewayFailureControl currentAccountGatewayFailureControl() {
      return new CurrentAccountGatewayFailureControl();
    }

    @Bean
    @Primary
    CurrentAccountAccessGateway controllableCurrentAccountAccessGateway(
        PostgresCurrentAccountAccessGateway delegate,
        CurrentAccountGatewayFailureControl failureControl) {
      return new FailureInjectingCurrentAccountAccessGateway(delegate, failureControl);
    }
  }

  static final class CurrentAccountGatewayFailureControl {

    private final AtomicBoolean failNextLookup = new AtomicBoolean();
    private final AtomicInteger lookupCount = new AtomicInteger();

    void failNextLookup() {
      failNextLookup.set(true);
    }

    int lookupCount() {
      return lookupCount.get();
    }

    boolean shouldFailLookup() {
      lookupCount.incrementAndGet();
      return failNextLookup.getAndSet(false);
    }

    void reset() {
      failNextLookup.set(false);
      lookupCount.set(0);
    }
  }

  static final class FailureInjectingCurrentAccountAccessGateway
      implements CurrentAccountAccessGateway {

    private final PostgresCurrentAccountAccessGateway delegate;
    private final CurrentAccountGatewayFailureControl failureControl;

    FailureInjectingCurrentAccountAccessGateway(
        PostgresCurrentAccountAccessGateway delegate,
        CurrentAccountGatewayFailureControl failureControl) {
      this.delegate = delegate;
      this.failureControl = failureControl;
    }

    @Override
    public Optional<CurrentAccountAccess> findByInternalUserId(UUID internalUserId) {
      if (failureControl.shouldFailLookup()) {
        throw new IllegalStateException("Injected current-account lookup failure");
      }
      return delegate.findByInternalUserId(internalUserId);
    }
  }
}
