// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.user;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.CurrentAccountAccessFixtures;
import com.opencop.core.api.general.business.usecase.user.AccountMutationUsecase;
import com.opencop.core.api.general.business.usecase.user.ResolveCurrentAccountAccessUsecase;
import com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationTelemetry;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry;
import com.opencop.core.api.general.technical.gateway.cache.RedisCurrentAccountAccessGateway;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.observability.user.CurrentAccountSessionTelemetry;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDenialReason;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDeniedException;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.ports.gateway.account.LockedCurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.gateway.db.account.CurrentAccountAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.account.PostgresCurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

@ComponentWebIT(classes = CurrentAccountAccessWebTestApplication.class)
class CurrentAccountAccessComponentIT extends AbstractWebIT {

  private static final UUID USER_ID = UUID.fromString("baad3b65-9589-40b0-a1cf-e7e42be65ef9");
  private static final UUID LEGACY_USER_ID =
      UUID.fromString("a57eb176-3930-459a-a0d3-4bfdef113740");
  private static final String LEGACY_PRINCIPAL_FIXTURE =
      "/com/opencop/core/sdk/technical/security/legacy-open-cop-principal-v1.ser";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private InternalUserRepository internalUserRepository;
  @Autowired private CurrentAccountAccessRepository currentAccountAccessRepository;
  @Autowired private ResolveCurrentAccountAccessUsecase resolveCurrentAccountAccessUsecase;
  @Autowired private AccountMutationUsecase accountMutationUsecase;
  @Autowired private CurrentAccountCacheTelemetry currentAccountCacheTelemetry;
  @Autowired private RedisCurrentAccountAccessGateway redisCurrentAccountAccessGateway;
  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired
  private CurrentAccountAccessWebTestApplication.ControllableCurrentAccountAccessCacheGateway
      cacheGateway;

  @Autowired private MeterRegistry meterRegistry;

  @MockitoBean private StreamAuthorizationTelemetry streamAuthorizationTelemetry;

  private CurrentAccountAccessFixtures currentAccountAccessFixtures;

  @DynamicPropertySource
  static void registerRedisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", LocalRedisSupport::host);
    registry.add("spring.data.redis.port", LocalRedisSupport::port);
  }

  @BeforeEach
  void setUpAccount() {
    currentAccountAccessFixtures = new CurrentAccountAccessFixtures(jdbcTemplate);
    currentAccountAccessFixtures.reset();
    currentAccountAccessFixtures.seedUser(USER_ID, true, true);
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    cacheGateway.reset();
  }

  @Test
  void cacheMiss_populatesEveryFieldWithDefaultFixedTtl() {
    currentAccountAccessFixtures.setSessionGeneration(USER_ID, 7);

    var access = resolveCurrentAccountAccessUsecase.resolve(USER_ID, 7);

    assertEquals(new CurrentAccountAccess(USER_ID, true, true, 7, 0), access);
    var payload = redisTemplate.opsForValue().get(RedisCurrentAccountAccessGateway.key(USER_ID));
    assertNotNull(payload);
    Map<?, ?> cached = new ObjectMapper().readValue(payload, Map.class);
    assertEquals(1, cached.get("contractVersion"));
    assertEquals(USER_ID.toString(), cached.get("internalUserId"));
    assertEquals(true, cached.get("active"));
    assertEquals(true, cached.get("administrator"));
    assertEquals(7, cached.get("sessionGeneration"));
    assertEquals(0, cached.get("accountRevision"));
    long ttlSeconds =
        redisTemplate.getExpire(RedisCurrentAccountAccessGateway.key(USER_ID), TimeUnit.SECONDS);
    assertTrue(ttlSeconds > Duration.ofMinutes(14).toSeconds());
    assertTrue(ttlSeconds <= Duration.ofMinutes(15).toSeconds());
  }

  @Test
  void cacheMissPopulation_isOrderedBeforeSameUserMutationRepair() throws Exception {
    cacheGateway.pauseNextPopulation(USER_ID);
    var executor = Executors.newFixedThreadPool(2);

    try {
      var delayedResolution =
          executor.submit(() -> resolveCurrentAccountAccessUsecase.resolve(USER_ID, 0));
      assertTrue(cacheGateway.awaitPopulation());

      var mutationStarted = new CountDownLatch(1);
      var mutation =
          executor.submit(
              () -> {
                mutationStarted.countDown();
                accountMutationUsecase.setAdministrator(USER_ID, false);
              });
      assertTrue(mutationStarted.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> mutation.get(200, TimeUnit.MILLISECONDS));

      cacheGateway.release();
      assertEquals(
          new CurrentAccountAccess(USER_ID, true, true, 0, 0),
          delayedResolution.get(5, TimeUnit.SECONDS));
      mutation.get(5, TimeUnit.SECONDS);

      var currentAccess = new CurrentAccountAccess(USER_ID, true, false, 0, 1);
      assertNull(redisTemplate.opsForValue().get(RedisCurrentAccountAccessGateway.key(USER_ID)));
      assertEquals(currentAccess, resolveCurrentAccountAccessUsecase.resolve(USER_ID, 0));
    } finally {
      cacheGateway.release();
      executor.shutdownNow();
    }
  }

  @Test
  void separateCoreContexts_shareHitsWithoutAffinityOrSlidingTtl() {
    try (var firstReplica = replica();
        var secondReplica = replica()) {
      assertTrue(firstReplica.resolve().resolve(USER_ID, 0).administrator());
      assertEquals(1, firstReplica.postgresQueries());

      currentAccountAccessFixtures.setAdministrator(USER_ID, false);
      assertTrue(
          Boolean.TRUE.equals(
              redisTemplate.expire(
                  RedisCurrentAccountAccessGateway.key(USER_ID), Duration.ofSeconds(2))));
      long ttlBeforeHit =
          redisTemplate.getExpire(
              RedisCurrentAccountAccessGateway.key(USER_ID), TimeUnit.MILLISECONDS);

      assertTrue(secondReplica.resolve().resolve(USER_ID, 0).administrator());

      long ttlAfterHit =
          redisTemplate.getExpire(
              RedisCurrentAccountAccessGateway.key(USER_ID), TimeUnit.MILLISECONDS);
      assertEquals(0, secondReplica.postgresQueries());
      assertTrue(ttlAfterHit > 0);
      assertTrue(ttlAfterHit <= ttlBeforeHit);
    }
  }

  @Test
  void unsupportedCachedValue_isLoadedFromPostgresAndRepaired() {
    var key = RedisCurrentAccountAccessGateway.key(USER_ID);
    var unsupported =
        new ObjectMapper()
            .writeValueAsString(
                Map.of(
                    "contractVersion",
                    2,
                    "internalUserId",
                    USER_ID,
                    "active",
                    false,
                    "administrator",
                    false,
                    "sessionGeneration",
                    99,
                    "accountRevision",
                    99));
    redisTemplate.opsForValue().set(key, unsupported);

    var access = resolveCurrentAccountAccessUsecase.resolve(USER_ID, 0);

    assertEquals(new CurrentAccountAccess(USER_ID, true, true, 0, 0), access);
    assertEquals(
        access, redisCurrentAccountAccessGateway.findByInternalUserId(USER_ID).orElseThrow());
    assertNotEquals(unsupported, redisTemplate.opsForValue().get(key));
  }

  @Test
  void redisOutage_fallsBackToCurrentPostgresState() {
    var failingConnectionFactory = new LettuceConnectionFactory("127.0.0.1", 1);
    failingConnectionFactory.afterPropertiesSet();
    try {
      var failingTemplate = new StringRedisTemplate(failingConnectionFactory);
      failingTemplate.afterPropertiesSet();
      var failingCache =
          new RedisCurrentAccountAccessGateway(
              failingTemplate,
              new ObjectMapper(),
              Duration.ofMinutes(15),
              currentAccountCacheTelemetry);
      var resolver =
          new ResolveCurrentAccountAccessUsecase(
              new PostgresCurrentAccountAccessGateway(currentAccountAccessRepository),
              failingCache,
              new CurrentAccountAccessPolicy(),
              transactionManager);

      assertEquals(
          new CurrentAccountAccess(USER_ID, true, true, 0, 0), resolver.resolve(USER_ID, 0));
    } finally {
      failingConnectionFactory.destroy();
    }
  }

  @Test
  void successfulMutations_removeEveryPreviouslyCachedAccountDecision() {
    currentAccountAccessFixtures.setAdministrator(USER_ID, false);
    currentAccountAccessFixtures.setSessionGeneration(USER_ID, 7);
    var key = RedisCurrentAccountAccessGateway.key(USER_ID);
    assertEquals(
        new CurrentAccountAccess(USER_ID, true, false, 7, 0),
        resolveCurrentAccountAccessUsecase.resolve(USER_ID, 7));

    accountMutationUsecase.setAdministrator(USER_ID, true);

    assertNull(redisTemplate.opsForValue().get(key));
    assertEquals(
        new CurrentAccountAccess(USER_ID, true, true, 7, 1),
        resolveCurrentAccountAccessUsecase.resolve(USER_ID, 7));

    accountMutationUsecase.setActive(USER_ID, false);

    assertNull(redisTemplate.opsForValue().get(key));
    assertThrows(
        CurrentAccountAccessDeniedException.class,
        () -> resolveCurrentAccountAccessUsecase.resolve(USER_ID, 7));
    assertEquals(
        new CurrentAccountAccess(USER_ID, false, true, 8, 2),
        redisCurrentAccountAccessGateway.findByInternalUserId(USER_ID).orElseThrow());
  }

  @Test
  void administratorGrant_isVisibleOnNextRequestWithoutReplacingTheSession() throws Exception {
    MockHttpSession session =
        authenticatedSession(
            com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID),
            List.of(authority("OIDC_USER"), authority("SCOPE_profile")));
    var persistedContext = persistedContext(session);
    var serializedContext = serialize(persistedContext);

    mockMvc
        .perform(get("/v1/admin/current-account-access").session(session))
        .andExpect(status().isOk())
        .andExpect(content().string("true"));
    mockMvc
        .perform(get("/v1/current-account-authorities").session(session))
        .andExpect(status().isOk())
        .andExpect(content().json("[\"OIDC_USER\",\"SCOPE_profile\",\"ROLE_admin\"]"));

    assertSame(persistedContext, persistedContext(session));
    assertArrayEquals(serializedContext, serialize(persistedContext(session)));
    assertFalse(
        ((OpenCopPrincipal) persistedContext.getAuthentication().getPrincipal()).isGlobalAdmin());
    assertEquals(
        List.of("OIDC_USER", "SCOPE_profile"),
        persistedContext.getAuthentication().getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList());
  }

  @Test
  void administratorDemotion_stripsStoredAdminFromPrincipalAndTokenOnNextRequest()
      throws Exception {
    MockHttpSession session =
        authenticatedSession(
            com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID, true),
            List.of(authority("SCOPE_profile"), authority(Roles.ADMIN)));
    var serializedContext = serialize(persistedContext(session));
    currentAccountAccessFixtures.setAdministrator(USER_ID, false);

    mockMvc
        .perform(get("/v1/current-account-access").session(session))
        .andExpect(status().isOk())
        .andExpect(content().string("false"));
    mockMvc
        .perform(get("/v1/current-account-authorities").session(session))
        .andExpect(status().isOk())
        .andExpect(content().json("[\"SCOPE_profile\"]"));
    mockMvc
        .perform(get("/v1/admin/current-account-access").session(session))
        .andExpect(status().isForbidden());

    assertArrayEquals(serializedContext, serialize(persistedContext(session)));
    assertTrue(
        ((OpenCopPrincipal) persistedContext(session).getAuthentication().getPrincipal())
            .isGlobalAdmin());
    assertEquals(
        List.of("SCOPE_profile", Roles.ADMIN),
        persistedContext(session).getAuthentication().getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList());
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   */
  @Test
  void retainedLegacySession_stripsStoredAdminWithoutRewritingThePersistedContext()
      throws Exception {
    currentAccountAccessFixtures.seedUser(LEGACY_USER_ID, true, false);
    currentAccountAccessFixtures.setSessionGeneration(LEGACY_USER_ID, 7);
    var legacyPrincipal = legacyPrincipal();
    MockHttpSession session =
        authenticatedSession(legacyPrincipal, List.copyOf(legacyPrincipal.getAuthorities()));
    var serializedContext = serialize(persistedContext(session));

    mockMvc
        .perform(get("/v1/current-account-access").session(session))
        .andExpect(status().isOk())
        .andExpect(content().string("false"));
    mockMvc
        .perform(get("/v1/current-account-authorities").session(session))
        .andExpect(status().isOk())
        .andExpect(content().json("[\"SCOPE_openid\",\"SCOPE_profile\"]"));
    mockMvc
        .perform(get("/v1/admin/current-account-access").session(session))
        .andExpect(status().isForbidden());

    assertArrayEquals(serializedContext, serialize(persistedContext(session)));
    assertTrue(
        persistedContext(session).getAuthentication().getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(Roles.ADMIN::equals));
  }

  @Test
  void inactiveAccount_returnsUnauthorizedAndInvalidatesRequestingSession() throws Exception {
    MockHttpSession session = authenticatedSession(false);
    currentAccountAccessFixtures.setActive(USER_ID, false);
    var countBefore = invalidationCount(CurrentAccountAccessDenialReason.INACTIVE);

    mockMvc
        .perform(get("/v1/current-account-access").session(session))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"))
        .andExpect(jsonPath("$.message").value("Login required"))
        .andExpect(jsonPath("$.reason").doesNotExist());

    assertTrue(session.isInvalid());
    assertEquals(countBefore + 1.0d, invalidationCount(CurrentAccountAccessDenialReason.INACTIVE));
  }

  @Test
  void sessionUnusedWhileAccountIsInactive_remainsStaleAfterReactivation() throws Exception {
    MockHttpSession session = authenticatedSession(false);
    accountMutationUsecase.setActive(USER_ID, false);
    accountMutationUsecase.setActive(USER_ID, true);
    var countBefore = invalidationCount(CurrentAccountAccessDenialReason.STALE_GENERATION);

    mockMvc
        .perform(get("/v1/current-account-access").session(session))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"))
        .andExpect(jsonPath("$.message").value("Login required"))
        .andExpect(jsonPath("$.reason").doesNotExist());

    assertTrue(session.isInvalid());
    assertEquals(1L, currentAccountAccessFixtures.sessionGeneration(USER_ID));
    assertEquals(2L, currentAccountAccessFixtures.accountAccessRevision(USER_ID));
    assertEquals(
        countBefore + 1.0d, invalidationCount(CurrentAccountAccessDenialReason.STALE_GENERATION));
  }

  @Test
  void staleSessionGeneration_returnsUnauthorizedAndInvalidatesRequestingSession()
      throws Exception {
    MockHttpSession session = authenticatedSession(false);
    currentAccountAccessFixtures.setSessionGeneration(USER_ID, 1);
    var countBefore = invalidationCount(CurrentAccountAccessDenialReason.STALE_GENERATION);

    mockMvc
        .perform(get("/v1/current-account-access").session(session))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"))
        .andExpect(jsonPath("$.message").value("Login required"))
        .andExpect(jsonPath("$.reason").doesNotExist());

    assertTrue(session.isInvalid());
    assertEquals(
        countBefore + 1.0d, invalidationCount(CurrentAccountAccessDenialReason.STALE_GENERATION));
  }

  @Test
  void missingAccount_returnsGenericUnauthorizedAndEmitsOneBoundedReason() throws Exception {
    MockHttpSession session = authenticatedSession(false);
    currentAccountAccessFixtures.reset();
    var countBefore = invalidationCount(CurrentAccountAccessDenialReason.MISSING_ACCOUNT);

    mockMvc
        .perform(get("/v1/current-account-access").session(session))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"))
        .andExpect(jsonPath("$.message").value("Login required"))
        .andExpect(jsonPath("$.reason").doesNotExist());

    assertTrue(session.isInvalid());
    assertEquals(
        countBefore + 1.0d, invalidationCount(CurrentAccountAccessDenialReason.MISSING_ACCOUNT));
  }

  private double invalidationCount(CurrentAccountAccessDenialReason reason) {
    var counter =
        meterRegistry
            .find(CurrentAccountSessionTelemetry.SESSION_INVALIDATIONS_TOTAL)
            .tag("reason", reason.value())
            .counter();
    return counter == null ? 0.0d : counter.count();
  }

  private static MockHttpSession authenticatedSession(boolean administrator) {
    var principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(
            USER_ID, administrator);
    return authenticatedSession(principal, List.copyOf(principal.getAuthorities()));
  }

  private static MockHttpSession authenticatedSession(
      OpenCopPrincipal principal, List<GrantedAuthority> authorities) {
    var authentication = new OAuth2AuthenticationToken(principal, authorities, "oidc");
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);

    var session = new MockHttpSession();
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    return session;
  }

  private static SimpleGrantedAuthority authority(String value) {
    return new SimpleGrantedAuthority(value);
  }

  private static SecurityContext persistedContext(MockHttpSession session) {
    return (SecurityContext)
        session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
  }

  private static byte[] serialize(SecurityContext context) throws Exception {
    try (var bytes = new ByteArrayOutputStream();
        var output = new ObjectOutputStream(bytes)) {
      output.writeObject(context);
      return bytes.toByteArray();
    }
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   */
  private static OpenCopPrincipal legacyPrincipal() throws Exception {
    try (var fixture =
            CurrentAccountAccessComponentIT.class.getResourceAsStream(LEGACY_PRINCIPAL_FIXTURE);
        var input = new ObjectInputStream(fixture)) {
      return (OpenCopPrincipal) input.readObject();
    }
  }

  private ReplicaContext replica() {
    var postgresGateway =
        new CountingCurrentAccountAccessGateway(
            new PostgresCurrentAccountAccessGateway(currentAccountAccessRepository));
    var context = new AnnotationConfigApplicationContext();
    context.registerBean(LockedCurrentAccountAccessGateway.class, () -> postgresGateway);
    context.registerBean(PlatformTransactionManager.class, () -> transactionManager);
    context.registerBean(
        StringRedisTemplate.class,
        () -> new StringRedisTemplate(redisTemplate.getConnectionFactory()));
    context.registerBean(MeterRegistry.class, SimpleMeterRegistry::new);
    context.registerBean(CurrentAccountCacheTelemetry.class);
    context.registerBean(
        RedisCurrentAccountAccessGateway.class,
        () ->
            new RedisCurrentAccountAccessGateway(
                context.getBean(StringRedisTemplate.class),
                new ObjectMapper(),
                Duration.ofMinutes(15),
                context.getBean(CurrentAccountCacheTelemetry.class)));
    context.registerBean(CurrentAccountAccessPolicy.class);
    context.registerBean(ResolveCurrentAccountAccessUsecase.class);
    context.refresh();
    return new ReplicaContext(context, postgresGateway);
  }

  private static final class CountingCurrentAccountAccessGateway
      implements LockedCurrentAccountAccessGateway {

    private final LockedCurrentAccountAccessGateway delegate;
    private final AtomicInteger queries = new AtomicInteger();

    private CountingCurrentAccountAccessGateway(LockedCurrentAccountAccessGateway delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<CurrentAccountAccess> findByInternalUserIdForCachePopulation(
        UUID internalUserId) {
      queries.incrementAndGet();
      return delegate.findByInternalUserIdForCachePopulation(internalUserId);
    }
  }

  private record ReplicaContext(
      AnnotationConfigApplicationContext context,
      CountingCurrentAccountAccessGateway postgresGateway)
      implements AutoCloseable {

    private ResolveCurrentAccountAccessUsecase resolve() {
      return context.getBean(ResolveCurrentAccountAccessUsecase.class);
    }

    private int postgresQueries() {
      return postgresGateway.queries.get();
    }

    @Override
    public void close() {
      context.close();
    }
  }
}
