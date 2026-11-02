// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.user;

import com.opencop.core.api.common.SecurityConfig;
import com.opencop.core.api.common.filter.CurrentAccountAccessFilter;
import com.opencop.core.api.common.filter.CurrentAccountAuthenticationFactory;
import com.opencop.core.api.general.business.factory.user.CurrentAccountAccessFactory;
import com.opencop.core.api.general.business.policy.AccountMutationPolicy;
import com.opencop.core.api.general.business.usecase.user.AccountMutationUsecase;
import com.opencop.core.api.general.business.usecase.user.ResolveCurrentAccountAccessUsecase;
import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry;
import com.opencop.core.api.general.technical.gateway.cache.RedisCurrentAccountAccessGateway;
import com.opencop.core.api.general.technical.gateway.db.user.InternalUserService;
import com.opencop.core.api.general.technical.gateway.db.user.PostgresAccountMutationGateway;
import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.relationship.InternalUserGroupRelationshipEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.observability.user.CurrentAccountSessionTelemetry;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.technical.gateway.db.account.CurrentAccountAccessEntity;
import com.opencop.core.sdk.technical.gateway.db.account.CurrentAccountAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.account.PostgresCurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
  SecurityConfig.class,
  CurrentAccountAccessFilter.class,
  CurrentAccountAuthenticationFactory.class,
  CurrentAccountAccessFactory.class,
  CurrentAccountAccessPolicy.class,
  ResolveCurrentAccountAccessUsecase.class,
  PostgresCurrentAccountAccessGateway.class,
  CurrentAccountCacheTelemetry.class,
  CurrentAccountSessionTelemetry.class,
  RedisCurrentAccountAccessGateway.class,
  AccountMutationPolicy.class,
  AccountMutationUsecase.class,
  PostgresAccountMutationGateway.class,
  InternalUserService.class,
  CurrentAccountAccessWebTestApplication.CacheControlConfig.class,
  CurrentAccountAccessWebTestApplication.CurrentAccountAccessEndpoint.class
})
public class CurrentAccountAccessWebTestApplication {

  @Bean
  Clock clock() {
    return Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
  }

  @Bean
  CookieSerializer cookieSerializer() {
    return new DefaultCookieSerializer();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class CacheControlConfig {

    @Bean
    @Primary
    ControllableCurrentAccountAccessCacheGateway controllableCurrentAccountAccessCacheGateway(
        RedisCurrentAccountAccessGateway delegate) {
      return new ControllableCurrentAccountAccessCacheGateway(delegate);
    }
  }

  static final class ControllableCurrentAccountAccessCacheGateway
      implements CurrentAccountAccessCacheGateway {

    private final RedisCurrentAccountAccessGateway delegate;
    private final AtomicBoolean pauseNextPopulation = new AtomicBoolean();

    private volatile UUID pausedUserId;
    private volatile CountDownLatch populationReached = new CountDownLatch(0);
    private volatile CountDownLatch releasePopulation = new CountDownLatch(0);

    ControllableCurrentAccountAccessCacheGateway(RedisCurrentAccountAccessGateway delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<CurrentAccountAccess> findByInternalUserId(UUID internalUserId) {
      return delegate.findByInternalUserId(internalUserId);
    }

    @Override
    public void populate(CurrentAccountAccess access) {
      pauseBeforePopulation(access.internalUserId());
      delegate.populate(access);
    }

    @Override
    public void evict(UUID internalUserId) {
      delegate.evict(internalUserId);
    }

    @Override
    public void scheduleEvictionAfterCommit(UUID internalUserId) {
      delegate.scheduleEvictionAfterCommit(internalUserId);
    }

    void pauseNextPopulation(UUID internalUserId) {
      pausedUserId = internalUserId;
      populationReached = new CountDownLatch(1);
      releasePopulation = new CountDownLatch(1);
      pauseNextPopulation.set(true);
    }

    boolean awaitPopulation() throws InterruptedException {
      return populationReached.await(5, TimeUnit.SECONDS);
    }

    void release() {
      releasePopulation.countDown();
    }

    void reset() {
      release();
      pausedUserId = null;
      pauseNextPopulation.set(false);
      populationReached = new CountDownLatch(0);
      releasePopulation = new CountDownLatch(0);
    }

    private void pauseBeforePopulation(UUID internalUserId) {
      if (!internalUserId.equals(pausedUserId) || !pauseNextPopulation.compareAndSet(true, false)) {
        return;
      }

      populationReached.countDown();
      try {
        if (!releasePopulation.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting to release cache population");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while holding cache population", e);
      }
    }
  }

  @RestController
  static class CurrentAccountAccessEndpoint {

    @GetMapping("/v1/admin/current-account-access")
    boolean currentAdministrator(@AuthenticationPrincipal OpenCopPrincipal principal) {
      return principal.isGlobalAdmin();
    }

    @GetMapping("/v1/current-account-access")
    boolean currentAccountAccess(@AuthenticationPrincipal OpenCopPrincipal principal) {
      return principal.isGlobalAdmin();
    }

    @GetMapping("/v1/current-account-authorities")
    List<String> currentAccountAuthorities(Authentication authentication) {
      return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
  }
}
