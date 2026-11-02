// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.opencop.core.api.common.SecurityConfig;
import com.opencop.core.api.common.filter.CurrentAccountAccessFilter;
import com.opencop.core.api.common.filter.CurrentAccountAuthenticationFactory;
import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.CurrentAccountAccessFixtures;
import com.opencop.core.api.general.business.factory.user.CurrentAccountAccessFactory;
import com.opencop.core.api.general.business.usecase.user.ResolveCurrentAccountAccessUsecase;
import com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationTelemetry;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry;
import com.opencop.core.api.general.technical.gateway.cache.RedisCurrentAccountAccessGateway;
import com.opencop.core.api.general.technical.gateway.db.user.InternalUserService;
import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.relationship.InternalUserGroupRelationshipEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.observability.user.CurrentAccountSessionTelemetry;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.technical.gateway.db.account.CurrentAccountAccessEntity;
import com.opencop.core.sdk.technical.gateway.db.account.CurrentAccountAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.account.PostgresCurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import com.opencop.core.sdk.technical.security.session.RedisSessionConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.io.ObjectInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@ComponentWebIT(classes = SessionLifecycleComponentIT.TestApplication.class)
@TestPropertySource(
    properties = {
      "opencop.auth.session.idle-timeout=5s",
      "opencop.auth.session.absolute-lifetime=20s",
      "spring.session.servlet.cookie.name=OPENCOP_BFF",
      "spring.session.servlet.cookie.path=/"
    })
class SessionLifecycleComponentIT extends AbstractWebIT {

  private static final UUID INTERNAL_USER_ID =
      UUID.fromString("17c67cac-16ba-4870-8816-da857f25e899");
  private static final Instant AUTHENTICATED_AT = Instant.parse("2026-07-29T12:00:00Z");
  private static final String LEGACY_PRINCIPAL_FIXTURE =
      "/com/opencop/core/sdk/technical/security/legacy-open-cop-principal-v1.ser";

  @Autowired private SessionRepository sessionRepository;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private CookieSerializer cookieSerializer;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MutableClock clock;
  @Autowired private SessionLifecycleEndpoint endpoint;

  @Autowired
  @Qualifier("springSessionRepositoryFilter")
  private Filter sessionRepositoryFilter;

  private CurrentAccountAccessFixtures fixtures;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.session.store-type", () -> "redis");
    registry.add("spring.data.redis.host", LocalRedisSupport::host);
    registry.add("spring.data.redis.port", LocalRedisSupport::port);
    registry.add("opencop.domain", () -> "https://app.opencop.test");
    registry.add("idp.audience", () -> "CORE-API");
  }

  @BeforeEach
  void setUpLifecycleContext() {
    mockMvc =
        webAppContextSetup(context)
            .addFilters(sessionRepositoryFilter)
            .apply(springSecurity())
            .build();
    fixtures = new CurrentAccountAccessFixtures(jdbcTemplate);
    fixtures.reset();
    fixtures.seedUser(INTERNAL_USER_ID, true, false);
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    clock.set(Instant.EPOCH);
    endpoint.reset();
  }

  @Test
  void createdSessionsUseConfiguredLifetimesAndCookieLifetime() {
    var principal = TestPrincipals.oidcPrincipal(INTERNAL_USER_ID);
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, "not-used", principal.getAuthorities());
    Session session = sessionRepository.createSession();
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(authentication));
    sessionRepository.save(session);
    var response = new MockHttpServletResponse();
    cookieSerializer.writeCookieValue(
        new CookieSerializer.CookieValue(new MockHttpServletRequest(), response, session.getId()));

    assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(5));
    assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=20").contains("Expires=");
  }

  @Test
  void authenticatedTrafficCannotSlideTheAbsoluteDeadline() throws Exception {
    var persisted =
        persistSession(principal(INTERNAL_USER_ID, 0, AUTHENTICATED_AT), Duration.ofMinutes(5));
    Cookie cookie = sessionCookie(persisted.sessionId());

    for (long elapsedSeconds : new long[] {5, 10, 19}) {
      clock.set(AUTHENTICATED_AT.plusSeconds(elapsedSeconds));
      mockMvc
          .perform(get("/v1/session-lifecycle").cookie(cookie))
          .andExpect(status().isOk())
          .andExpect(content().string("entered"));
    }

    endpoint.reset();
    clock.set(AUTHENTICATED_AT.plusSeconds(20));
    mockMvc
        .perform(get("/v1/session-lifecycle").cookie(cookie))
        .andExpect(status().isUnauthorized());

    assertThat(endpoint.invocations()).isZero();
    assertThat(sessionRepository.findById(persisted.sessionId())).isNull();
  }

  @Test
  void retainedPrincipalUsesTheBackingSessionCreationTimeForItsAbsoluteDeadline() throws Exception {
    OpenCopPrincipal principal = legacyPrincipal();
    fixtures.seedUser(principal.internalUserId(), true, false);
    fixtures.setSessionGeneration(principal.internalUserId(), principal.sessionGeneration());
    var persisted = persistSession(principal, Duration.ofMinutes(5));
    Cookie cookie = sessionCookie(persisted.sessionId());

    clock.set(persisted.creationTime().plusSeconds(19));
    mockMvc
        .perform(get("/v1/session-lifecycle").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(content().string("entered"));

    endpoint.reset();
    clock.set(persisted.creationTime().plusSeconds(20));
    mockMvc
        .perform(get("/v1/session-lifecycle").cookie(cookie))
        .andExpect(status().isUnauthorized());

    assertThat(endpoint.invocations()).isZero();
    assertThat(sessionRepository.findById(persisted.sessionId())).isNull();
  }

  @Test
  void requestAdmittedBeforeGenerationCommitFinishesAndItsNextRequestIsRejected() throws Exception {
    var persisted =
        persistSession(principal(INTERNAL_USER_ID, 0, Instant.EPOCH), Duration.ofMinutes(5));
    Cookie cookie = sessionCookie(persisted.sessionId());
    endpoint.blockNextRequest();
    var executor = Executors.newSingleThreadExecutor();

    try {
      var admittedRequest =
          executor.submit(
              () -> mockMvc.perform(get("/v1/session-lifecycle").cookie(cookie)).andReturn());
      assertThat(endpoint.awaitEntry()).isTrue();

      fixtures.setSessionGeneration(INTERNAL_USER_ID, 1);
      redisTemplate.delete(RedisCurrentAccountAccessGateway.key(INTERNAL_USER_ID));
      endpoint.release();

      assertThat(admittedRequest.get(5, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);

      endpoint.reset();
      mockMvc
          .perform(get("/v1/session-lifecycle").cookie(cookie))
          .andExpect(status().isUnauthorized());
      assertThat(endpoint.invocations()).isZero();
      assertThat(sessionRepository.findById(persisted.sessionId())).isNull();
    } finally {
      endpoint.release();
      executor.shutdownNow();
    }
  }

  private PersistedSession persistSession(
      OpenCopPrincipal principal, Duration maxInactiveInterval) {
    var authentication =
        new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "oidc");
    var securityContext = SecurityContextHolder.createEmptyContext();
    securityContext.setAuthentication(authentication);
    Session session = sessionRepository.createSession();
    session.setMaxInactiveInterval(maxInactiveInterval);
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
    sessionRepository.save(session);
    return new PersistedSession(session.getId(), session.getCreationTime());
  }

  private Cookie sessionCookie(String sessionId) {
    var response = new MockHttpServletResponse();
    cookieSerializer.writeCookieValue(
        new CookieSerializer.CookieValue(new MockHttpServletRequest(), response, sessionId));
    return response.getCookie("OPENCOP_BFF");
  }

  private static OpenCopPrincipal principal(
      UUID internalUserId, long sessionGeneration, Instant authenticatedAt) {
    return new OpenCopPrincipal(
        internalUserId,
        "https://idp.example.test",
        "stable-id",
        "verified-subject",
        new OpenCopPrincipal.Profile(null, null, null, null, null, null),
        sessionGeneration,
        authenticatedAt);
  }

  private static OpenCopPrincipal legacyPrincipal() throws Exception {
    try (var fixture =
            SessionLifecycleComponentIT.class.getResourceAsStream(LEGACY_PRINCIPAL_FIXTURE);
        var input = new ObjectInputStream(fixture)) {
      return (OpenCopPrincipal) input.readObject();
    }
  }

  private record PersistedSession(String sessionId, Instant creationTime) {}

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(
      basePackageClasses = {
        InternalUserEntity.class,
        InternalGroupEntity.class,
        InternalUserGroupRelationshipEntity.class,
        CurrentAccountAccessEntity.class
      })
  @EnableJpaRepositories(
      basePackageClasses = {InternalUserRepository.class, CurrentAccountAccessRepository.class})
  @Import({
    ComponentTestDatabaseConfig.class,
    RedisSessionConfig.class,
    SecurityConfig.class,
    CurrentAccountAccessFilter.class,
    CurrentAccountAuthenticationFactory.class,
    CurrentAccountAccessFactory.class,
    CurrentAccountAccessPolicy.class,
    ResolveCurrentAccountAccessUsecase.class,
    PostgresCurrentAccountAccessGateway.class,
    CurrentAccountSessionTelemetry.class,
    CurrentAccountCacheTelemetry.class,
    RedisCurrentAccountAccessGateway.class,
    InternalUserService.class,
    StreamAuthorizationTelemetry.class,
    SessionLifecycleEndpoint.class
  })
  static class TestApplication {

    @Bean
    MutableClock clock() {
      return new MutableClock(Instant.EPOCH);
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @RestController
  static final class SessionLifecycleEndpoint {

    private final AtomicInteger invocations = new AtomicInteger();
    private final AtomicBoolean blockNextRequest = new AtomicBoolean();

    private volatile CountDownLatch entered = new CountDownLatch(0);
    private volatile CountDownLatch release = new CountDownLatch(0);

    @GetMapping("/v1/session-lifecycle")
    String enter() {
      invocations.incrementAndGet();
      if (blockNextRequest.compareAndSet(true, false)) {
        entered.countDown();
        awaitRelease();
      }
      return "entered";
    }

    void blockNextRequest() {
      entered = new CountDownLatch(1);
      release = new CountDownLatch(1);
      blockNextRequest.set(true);
    }

    boolean awaitEntry() throws InterruptedException {
      return entered.await(5, TimeUnit.SECONDS);
    }

    void release() {
      release.countDown();
    }

    int invocations() {
      return invocations.get();
    }

    void reset() {
      release();
      invocations.set(0);
      blockNextRequest.set(false);
      entered = new CountDownLatch(0);
      release = new CountDownLatch(0);
    }

    private void awaitRelease() {
      try {
        if (!release.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to release admitted request");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while holding admitted request", e);
      }
    }
  }

  static final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    MutableClock(Instant initialInstant) {
      this(initialInstant, ZoneOffset.UTC);
    }

    private MutableClock(Instant initialInstant, ZoneId zone) {
      this.now = new AtomicReference<>(initialInstant);
      this.zone = zone;
    }

    void set(Instant instant) {
      now.set(instant);
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
      return zone.equals(newZone) ? this : new MutableClock(now.get(), newZone);
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }
}
