// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.component.general.user.AccountMutationTestApplication.ControllableAccountMutationGateway;
import com.opencop.core.api.component.general.user.AccountMutationTestApplication.ControllableCurrentAccountAccessCacheGateway;
import com.opencop.core.api.component.general.user.AccountMutationTestApplication.PreloadingAccountMutationCaller;
import com.opencop.core.api.component.support.fixture.CurrentAccountAccessFixtures;
import com.opencop.core.api.general.business.usecase.user.AccountMutationUsecase;
import com.opencop.core.api.general.technical.gateway.cache.RedisCurrentAccountAccessGateway;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ComponentWebIT(classes = AccountMutationTestApplication.class)
class AccountMutationComponentIT extends AbstractDatabaseIT {

  private static final UUID USER_ID = UUID.fromString("a4cf810e-e851-40a2-a208-dd26e136c9e7");
  private static final UUID OTHER_USER_ID = UUID.fromString("caab2cf9-8bbb-4a49-a137-37e0d5667825");

  @Autowired private AccountMutationUsecase accountMutationUsecase;
  @Autowired private PreloadingAccountMutationCaller preloadingAccountMutationCaller;
  @Autowired private ControllableAccountMutationGateway mutationGateway;
  @Autowired private ControllableCurrentAccountAccessCacheGateway cacheGateway;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StringRedisTemplate redisTemplate;

  private CurrentAccountAccessFixtures fixtures;
  private ExecutorService executor;

  @DynamicPropertySource
  static void registerRedisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", LocalRedisSupport::host);
    registry.add("spring.data.redis.port", LocalRedisSupport::port);
  }

  @BeforeEach
  void setUp() {
    fixtures = new CurrentAccountAccessFixtures(jdbcTemplate);
    fixtures.reset();
    fixtures.seedUser(USER_ID, true, false);
    fixtures.seedUser(OTHER_USER_ID, true, false);
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    cacheGateway.reset();
    mutationGateway.reset();
    preloadingAccountMutationCaller.reset();
    executor = Executors.newFixedThreadPool(2);
  }

  @AfterEach
  void tearDown() {
    cacheGateway.release();
    preloadingAccountMutationCaller.release();
    if (executor != null) {
      executor.shutdownNow();
    }
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
  }

  @Test
  void concurrentDirtyMutationsForSameUser_preserveStateAndIncreaseRevision() throws Exception {
    cacheGateway.blockNextRequiredEviction(USER_ID);
    Future<?> first = submit(() -> accountMutationUsecase.setAdministrator(USER_ID, true));
    assertTrue(cacheGateway.awaitRequiredEviction());

    preloadingAccountMutationCaller.pauseNextAfterPreload(USER_ID);
    mutationGateway.observeNextLockAttempt(USER_ID);
    cacheGateway.observeNextRequiredEviction(USER_ID);
    Future<?> second =
        submit(
            () ->
                preloadingAccountMutationCaller.setActiveWithDisplayName(
                    USER_ID, "Concurrent SCIM update", false));
    assertTrue(preloadingAccountMutationCaller.awaitPreload());
    preloadingAccountMutationCaller.release();
    assertTrue(mutationGateway.awaitLockAttempt());

    assertFalse(cacheGateway.awaitObservedRequiredEviction(200, TimeUnit.MILLISECONDS));
    cacheGateway.release();
    first.get(5, TimeUnit.SECONDS);
    assertTrue(cacheGateway.awaitObservedRequiredEviction(5, TimeUnit.SECONDS));
    second.get(5, TimeUnit.SECONDS);

    assertTrue(fixtures.administrator(USER_ID));
    assertFalse(fixtures.active(USER_ID));
    assertEquals("Concurrent SCIM update", fixtures.displayName(USER_ID));
    assertEquals(2L, fixtures.accountAccessRevision(USER_ID));
  }

  @Test
  void concurrentMutationsForDifferentUsers_doNotShareALock() throws Exception {
    cacheGateway.blockNextRequiredEviction(USER_ID);
    Future<?> first = submit(() -> accountMutationUsecase.setAdministrator(USER_ID, true));
    assertTrue(cacheGateway.awaitRequiredEviction());

    Future<?> second = submit(() -> accountMutationUsecase.setAdministrator(OTHER_USER_ID, true));

    second.get(5, TimeUnit.SECONDS);
    assertFalse(first.isDone());
    assertTrue(fixtures.administrator(OTHER_USER_ID));
    assertEquals(1L, fixtures.accountAccessRevision(OTHER_USER_ID));

    cacheGateway.release();
    first.get(5, TimeUnit.SECONDS);
  }

  @Test
  void requiredRedisFailure_rollsBackFlushedDatabaseMutation() {
    cacheGateway.failRequiredEviction();

    assertThrows(
        RedisConnectionFailureException.class,
        () -> accountMutationUsecase.setAdministrator(USER_ID, true));

    assertFalse(fixtures.administrator(USER_ID));
    assertEquals(0L, fixtures.accountAccessRevision(USER_ID));
  }

  @Test
  void requiredEviction_exposesNoUncommittedAccountValue() throws Exception {
    redisTemplate
        .opsForValue()
        .set(RedisCurrentAccountAccessGateway.key(USER_ID), "old-committed-value");
    cacheGateway.blockNextRequiredEviction(USER_ID);
    Future<?> mutation = submit(() -> accountMutationUsecase.setAdministrator(USER_ID, true));
    assertTrue(cacheGateway.awaitRequiredEviction());

    assertNull(redisTemplate.opsForValue().get(RedisCurrentAccountAccessGateway.key(USER_ID)));
    assertFalse(fixtures.administrator(USER_ID));
    assertEquals(0L, fixtures.accountAccessRevision(USER_ID));

    cacheGateway.release();
    mutation.get(5, TimeUnit.SECONDS);
    assertTrue(fixtures.administrator(USER_ID));
    assertEquals(1L, fixtures.accountAccessRevision(USER_ID));
  }

  @Test
  void postCommitRedisFailure_doesNotRollBackCommittedMutation() {
    cacheGateway.failPostCommitEviction();

    accountMutationUsecase.setAdministrator(USER_ID, true);

    assertTrue(fixtures.administrator(USER_ID));
    assertEquals(1L, fixtures.accountAccessRevision(USER_ID));
  }

  @Test
  void postCommitEviction_removesOldValueRepopulatedBeforeCommit() {
    cacheGateway.repopulateAfterRequiredEviction("repopulated-old-value");

    accountMutationUsecase.setAdministrator(USER_ID, true);

    assertNull(redisTemplate.opsForValue().get(RedisCurrentAccountAccessGateway.key(USER_ID)));
    assertTrue(fixtures.administrator(USER_ID));
    assertEquals(1L, fixtures.accountAccessRevision(USER_ID));
  }

  @Test
  void deactivationAndDeletionAdvanceTheSessionGeneration() {
    accountMutationUsecase.setActive(USER_ID, false);

    assertEquals(1L, fixtures.sessionGeneration(USER_ID));

    fixtures.setActive(USER_ID, true);

    accountMutationUsecase.delete(USER_ID, java.time.Instant.parse("2026-07-29T12:00:00Z"));

    assertEquals(2L, fixtures.sessionGeneration(USER_ID));
  }

  @Test
  void administratorAndActivationChangesDoNotAdvanceTheSessionGeneration() {
    accountMutationUsecase.setAdministrator(USER_ID, true);
    accountMutationUsecase.setActive(USER_ID, true);

    assertEquals(0L, fixtures.sessionGeneration(USER_ID));
  }

  private Future<?> submit(Runnable mutation) {
    return executor.submit(mutation);
  }
}
