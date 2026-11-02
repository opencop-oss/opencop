// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.user;

import com.opencop.core.api.general.business.model.user.AccountMutationState;
import com.opencop.core.api.general.business.policy.AccountMutationPolicy;
import com.opencop.core.api.general.business.usecase.user.AccountMutationUsecase;
import com.opencop.core.api.general.ports.gateway.AccountMutationGateway;
import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry;
import com.opencop.core.api.general.technical.gateway.cache.RedisCurrentAccountAccessGateway;
import com.opencop.core.api.general.technical.gateway.db.user.PostgresAccountMutationGateway;
import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.relationship.InternalUserGroupRelationshipEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(
    basePackageClasses = {
      InternalUserEntity.class,
      InternalGroupEntity.class,
      InternalUserGroupRelationshipEntity.class
    })
@EnableJpaRepositories(basePackageClasses = InternalUserRepository.class)
@Import({
  ComponentTestDatabaseConfig.class,
  AccountMutationPolicy.class,
  AccountMutationUsecase.class,
  PostgresAccountMutationGateway.class,
  CurrentAccountCacheTelemetry.class,
  RedisCurrentAccountAccessGateway.class,
  AccountMutationTestApplication.CacheControlConfig.class
})
public class AccountMutationTestApplication {

  @TestConfiguration(proxyBeanMethods = false)
  static class CacheControlConfig {

    @Bean
    @Primary
    ControllableCurrentAccountAccessCacheGateway controllableCurrentAccountAccessCacheGateway(
        RedisCurrentAccountAccessGateway delegate,
        StringRedisTemplate redisTemplate,
        CurrentAccountCacheTelemetry telemetry) {
      return new ControllableCurrentAccountAccessCacheGateway(delegate, redisTemplate, telemetry);
    }

    @Bean
    @Primary
    ControllableAccountMutationGateway controllableAccountMutationGateway(
        PostgresAccountMutationGateway delegate) {
      return new ControllableAccountMutationGateway(delegate);
    }

    @Bean
    PreloadingAccountMutationCaller preloadingAccountMutationCaller(
        InternalUserRepository repository, AccountMutationUsecase accountMutationUsecase) {
      return new PreloadingAccountMutationCaller(repository, accountMutationUsecase);
    }
  }

  static class PreloadingAccountMutationCaller {

    private final InternalUserRepository repository;
    private final AccountMutationUsecase accountMutationUsecase;
    private final AtomicBoolean pauseNextMutation = new AtomicBoolean();

    private volatile UUID pausedUserId;
    private volatile CountDownLatch preloadReached = new CountDownLatch(0);
    private volatile CountDownLatch releaseMutation = new CountDownLatch(0);

    PreloadingAccountMutationCaller(
        InternalUserRepository repository, AccountMutationUsecase accountMutationUsecase) {
      this.repository = repository;
      this.accountMutationUsecase = accountMutationUsecase;
    }

    @Transactional
    public void setActiveWithDisplayName(UUID internalUserId, String displayName, boolean active) {
      var user = repository.findById(internalUserId).orElseThrow();
      user.setDisplayName(displayName);
      pauseAfterPreload(internalUserId);
      accountMutationUsecase.setActive(internalUserId, active);
    }

    void pauseNextAfterPreload(UUID internalUserId) {
      pausedUserId = internalUserId;
      preloadReached = new CountDownLatch(1);
      releaseMutation = new CountDownLatch(1);
      pauseNextMutation.set(true);
    }

    boolean awaitPreload() throws InterruptedException {
      return preloadReached.await(5, TimeUnit.SECONDS);
    }

    void release() {
      releaseMutation.countDown();
    }

    void reset() {
      release();
      pausedUserId = null;
      pauseNextMutation.set(false);
      preloadReached = new CountDownLatch(0);
      releaseMutation = new CountDownLatch(0);
    }

    private void pauseAfterPreload(UUID internalUserId) {
      if (!internalUserId.equals(pausedUserId) || !pauseNextMutation.compareAndSet(true, false)) {
        return;
      }

      preloadReached.countDown();
      try {
        if (!releaseMutation.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out before attempting account row lock");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted before attempting account row lock", e);
      }
    }
  }

  static final class ControllableAccountMutationGateway implements AccountMutationGateway {

    private final PostgresAccountMutationGateway delegate;
    private final AtomicBoolean observeNextLockAttempt = new AtomicBoolean();

    private volatile UUID observedUserId;
    private volatile CountDownLatch lockAttempted = new CountDownLatch(0);

    ControllableAccountMutationGateway(PostgresAccountMutationGateway delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<AccountMutationState> findByInternalUserIdForUpdate(UUID internalUserId) {
      if (internalUserId.equals(observedUserId)
          && observeNextLockAttempt.compareAndSet(true, false)) {
        lockAttempted.countDown();
      }
      return delegate.findByInternalUserIdForUpdate(internalUserId);
    }

    @Override
    public void saveAndFlush(AccountMutationState account) {
      delegate.saveAndFlush(account);
    }

    void observeNextLockAttempt(UUID internalUserId) {
      observedUserId = internalUserId;
      lockAttempted = new CountDownLatch(1);
      observeNextLockAttempt.set(true);
    }

    boolean awaitLockAttempt() throws InterruptedException {
      return lockAttempted.await(5, TimeUnit.SECONDS);
    }

    void reset() {
      observedUserId = null;
      observeNextLockAttempt.set(false);
      lockAttempted = new CountDownLatch(0);
    }
  }

  static final class ControllableCurrentAccountAccessCacheGateway
      implements CurrentAccountAccessCacheGateway, DisposableBean {

    private final RedisCurrentAccountAccessGateway delegate;
    private final StringRedisTemplate redisTemplate;
    private final LettuceConnectionFactory failingConnectionFactory;
    private final RedisCurrentAccountAccessGateway failingDelegate;
    private final AtomicBoolean blockNextRequiredEviction = new AtomicBoolean();
    private final AtomicBoolean observeNextRequiredEviction = new AtomicBoolean();

    private volatile CountDownLatch requiredEvictionReached = new CountDownLatch(0);
    private volatile CountDownLatch releaseRequiredEviction = new CountDownLatch(0);
    private volatile UUID blockedUserId;
    private volatile UUID observedUserId;
    private volatile CountDownLatch observedRequiredEviction = new CountDownLatch(0);
    private volatile Mode mode = Mode.NORMAL;
    private volatile String repopulatedValue;

    ControllableCurrentAccountAccessCacheGateway(
        RedisCurrentAccountAccessGateway delegate,
        StringRedisTemplate redisTemplate,
        CurrentAccountCacheTelemetry telemetry) {
      this.delegate = delegate;
      this.redisTemplate = redisTemplate;
      this.failingConnectionFactory = new LettuceConnectionFactory("127.0.0.1", 1);
      failingConnectionFactory.afterPropertiesSet();
      var failingTemplate = new StringRedisTemplate(failingConnectionFactory);
      failingTemplate.afterPropertiesSet();
      this.failingDelegate =
          new RedisCurrentAccountAccessGateway(
              failingTemplate, new ObjectMapper(), Duration.ofMinutes(15), telemetry);
    }

    @Override
    public Optional<CurrentAccountAccess> findByInternalUserId(UUID internalUserId) {
      return delegate.findByInternalUserId(internalUserId);
    }

    @Override
    public void populate(CurrentAccountAccess access) {
      delegate.populate(access);
    }

    @Override
    public void evict(UUID internalUserId) {
      if (mode == Mode.FAIL_REQUIRED) {
        failingDelegate.evict(internalUserId);
        return;
      }

      delegate.evict(internalUserId);
      if (mode == Mode.REPOPULATE_AFTER_REQUIRED) {
        redisTemplate
            .opsForValue()
            .set(RedisCurrentAccountAccessGateway.key(internalUserId), repopulatedValue);
      }
      signalObservedRequiredEviction(internalUserId);
      blockAfterRequiredEviction(internalUserId);
    }

    @Override
    public void scheduleEvictionAfterCommit(UUID internalUserId) {
      if (mode == Mode.FAIL_POST_COMMIT) {
        failingDelegate.scheduleEvictionAfterCommit(internalUserId);
        return;
      }
      delegate.scheduleEvictionAfterCommit(internalUserId);
    }

    void reset() {
      release();
      mode = Mode.NORMAL;
      repopulatedValue = null;
      blockedUserId = null;
      observedUserId = null;
      blockNextRequiredEviction.set(false);
      observeNextRequiredEviction.set(false);
      requiredEvictionReached = new CountDownLatch(0);
      releaseRequiredEviction = new CountDownLatch(0);
      observedRequiredEviction = new CountDownLatch(0);
    }

    void blockNextRequiredEviction(UUID internalUserId) {
      blockedUserId = internalUserId;
      requiredEvictionReached = new CountDownLatch(1);
      releaseRequiredEviction = new CountDownLatch(1);
      blockNextRequiredEviction.set(true);
    }

    boolean awaitRequiredEviction() throws InterruptedException {
      return requiredEvictionReached.await(5, TimeUnit.SECONDS);
    }

    void observeNextRequiredEviction(UUID internalUserId) {
      observedUserId = internalUserId;
      observedRequiredEviction = new CountDownLatch(1);
      observeNextRequiredEviction.set(true);
    }

    boolean awaitObservedRequiredEviction(long timeout, TimeUnit timeUnit)
        throws InterruptedException {
      return observedRequiredEviction.await(timeout, timeUnit);
    }

    void release() {
      releaseRequiredEviction.countDown();
    }

    void failRequiredEviction() {
      mode = Mode.FAIL_REQUIRED;
    }

    void failPostCommitEviction() {
      mode = Mode.FAIL_POST_COMMIT;
    }

    void repopulateAfterRequiredEviction(String value) {
      mode = Mode.REPOPULATE_AFTER_REQUIRED;
      repopulatedValue = value;
    }

    @Override
    public void destroy() {
      release();
      failingConnectionFactory.destroy();
    }

    private void blockAfterRequiredEviction(UUID internalUserId) {
      if (!internalUserId.equals(blockedUserId)
          || !blockNextRequiredEviction.compareAndSet(true, false)) {
        return;
      }

      requiredEvictionReached.countDown();
      try {
        if (!releaseRequiredEviction.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to release required cache eviction");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while holding account mutation open", e);
      }
    }

    private void signalObservedRequiredEviction(UUID internalUserId) {
      if (internalUserId.equals(observedUserId)
          && observeNextRequiredEviction.compareAndSet(true, false)) {
        observedRequiredEviction.countDown();
      }
    }

    private enum Mode {
      NORMAL,
      FAIL_REQUIRED,
      FAIL_POST_COMMIT,
      REPOPULATE_AFTER_REQUIRED
    }
  }
}
