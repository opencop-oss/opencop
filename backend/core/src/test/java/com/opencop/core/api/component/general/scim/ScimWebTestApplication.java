// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.scim;

import com.opencop.core.api.common.scim.ScimJsonConfig;
import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.business.policy.AccountMutationPolicy;
import com.opencop.core.api.general.business.policy.runtimefeature.RuntimeFeatureAudiencePolicy;
import com.opencop.core.api.general.business.policy.runtimefeature.RuntimeFeatureNamePolicy;
import com.opencop.core.api.general.business.usecase.runtimefeature.ManageRuntimeFeaturesUsecase;
import com.opencop.core.api.general.business.usecase.user.AccountMutationUsecase;
import com.opencop.core.api.general.business.usecase.user.ScimUserMutationUsecase;
import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import com.opencop.core.api.general.ports.gateway.runtimefeature.WatchboxTrackingResetGateway;
import com.opencop.core.api.general.ports.gateway.runtimefeature.WatchboxTrackingResetTelemetry;
import com.opencop.core.api.general.ports.technical.gateway.ScimUserMutationGateway;
import com.opencop.core.api.general.ports.usecase.runtimefeature.ManageRuntimeFeatures;
import com.opencop.core.api.general.technical.endpoint.scim.ProvisioningEndpoint;
import com.opencop.core.api.general.technical.endpoint.scim.ScimExceptionHandler;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry;
import com.opencop.core.api.general.technical.gateway.cache.RedisCurrentAccountAccessGateway;
import com.opencop.core.api.general.technical.gateway.db.runtimefeature.JpaWatchboxTrackingResetGateway;
import com.opencop.core.api.general.technical.gateway.db.user.JpaUserPreferenceRepository;
import com.opencop.core.api.general.technical.gateway.db.user.PostgresAccountMutationGateway;
import com.opencop.core.api.general.technical.gateway.db.user.UserPreferenceEntity;
import com.opencop.core.api.general.technical.gateway.db.user.UserPreferenceJpaRepository;
import com.opencop.core.api.general.technical.gateway.iam.IamRuntimeFeatureUserGroupAccess;
import com.opencop.core.api.general.technical.gateway.scim.ScimGroupService;
import com.opencop.core.api.general.technical.gateway.scim.ScimUserService;
import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupRepository;
import com.opencop.core.api.general.technical.gateway.scim.relationship.InternalUserGroupRelationshipEntity;
import com.opencop.core.api.general.technical.gateway.scim.relationship.UserGroupRepository;
import com.opencop.core.api.general.technical.gateway.scim.relationship.UserGroupService;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.observability.runtimefeature.MicrometerWatchboxTrackingResetTelemetry;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.JpaRuntimeFeatureStore;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureAudienceUserGroupJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureEntity;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureHistoryJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxEntity;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxJpaRepository;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.UserResource;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(
    basePackages = "com.opencop.core.sdk.technical.gateway.db.iam",
    basePackageClasses = {
      InternalUserEntity.class,
      InternalGroupEntity.class,
      InternalUserGroupRelationshipEntity.class,
      UserPreferenceEntity.class,
      RuntimeFeatureEntity.class,
      WatchboxEntity.class
    })
@EnableJpaRepositories(
    basePackageClasses = {
      InternalUserRepository.class,
      InternalGroupRepository.class,
      UserGroupRepository.class,
      IamGroupRepository.class,
      IamGroupMembersRepository.class,
      UserPreferenceJpaRepository.class,
      RuntimeFeatureJpaRepository.class,
      WatchboxJpaRepository.class
    })
@Import({
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class,
  ScimJsonConfig.class,
  ProvisioningEndpoint.class,
  ScimExceptionHandler.class,
  ScimUserService.class,
  AccountMutationPolicy.class,
  AccountMutationUsecase.class,
  ScimUserMutationUsecase.class,
  PostgresAccountMutationGateway.class,
  CurrentAccountCacheTelemetry.class,
  RedisCurrentAccountAccessGateway.class,
  ScimGroupService.class,
  UserGroupService.class,
  JpaUserPreferenceRepository.class,
  JpaWatchboxTrackingResetGateway.class,
  MicrometerWatchboxTrackingResetTelemetry.class
})
public class ScimWebTestApplication {

  @Bean
  @Primary
  ControllableScimUserMutationGateway controllableScimUserMutationGateway(
      ScimUserService delegate) {
    return new ControllableScimUserMutationGateway(delegate);
  }

  @Bean
  @Primary
  ControllableCurrentAccountAccessCacheGateway controllableCurrentAccountAccessCacheGateway(
      RedisCurrentAccountAccessGateway delegate) {
    return new ControllableCurrentAccountAccessCacheGateway(delegate);
  }

  static final class ControllableScimUserMutationGateway implements ScimUserMutationGateway {

    private final ScimUserService delegate;
    private final AtomicBoolean coordinateCreates = new AtomicBoolean();
    private final AtomicBoolean coordinatePatches = new AtomicBoolean();
    private final AtomicBoolean coordinateMutationResponse = new AtomicBoolean();
    private final AtomicInteger createIndex = new AtomicInteger();
    private final AtomicInteger patchIndex = new AtomicInteger();

    private volatile CountDownLatch firstCreateReturned = new CountDownLatch(0);
    private volatile CountDownLatch secondCreateReturned = new CountDownLatch(0);
    private volatile CountDownLatch releaseFirstCreate = new CountDownLatch(0);
    private volatile CountDownLatch firstPatchReturned = new CountDownLatch(0);
    private volatile CountDownLatch secondPatchStarted = new CountDownLatch(0);
    private volatile CountDownLatch secondPatchReturned = new CountDownLatch(0);
    private volatile CountDownLatch releaseFirstPatch = new CountDownLatch(0);
    private volatile CountDownLatch firstMutationCommitted = new CountDownLatch(0);
    private volatile CountDownLatch releaseFirstMutationResponse = new CountDownLatch(0);
    private volatile List<Boolean> lastPatchActiveStates = List.of();

    ControllableScimUserMutationGateway(ScimUserService delegate) {
      this.delegate = delegate;
    }

    @Override
    public CreateMutation create(UserResource request) {
      int index = coordinateCreates.get() ? createIndex.incrementAndGet() : 0;
      var mutation = delegate.create(request);
      if (index == 1) {
        firstCreateReturned.countDown();
        awaitRelease();
      } else if (index == 2) {
        secondCreateReturned.countDown();
      }
      return mutation;
    }

    @Override
    public ActiveMutation replace(String internalUserId, UserResource request) {
      var mutation = delegate.replace(internalUserId, request);
      coordinateResponseAfterCommit();
      return mutation;
    }

    @Override
    public PatchMutation patch(UUID internalUserId, PatchRequest request) {
      int index = coordinatePatches.get() ? patchIndex.incrementAndGet() : 0;
      if (index == 2) {
        secondPatchStarted.countDown();
      }
      var mutation = delegate.patch(internalUserId, request);
      lastPatchActiveStates = mutation.requestedActiveStates();
      if (index == 1) {
        firstPatchReturned.countDown();
        awaitRelease(releaseFirstPatch, "Timed out before releasing the first SCIM patch");
      } else if (index == 2) {
        secondPatchReturned.countDown();
      }
      return mutation;
    }

    @Override
    public UUID requireExisting(UUID internalUserId) {
      return delegate.requireExisting(internalUserId);
    }

    void coordinateNextTwoCreates() {
      reset();
      firstCreateReturned = new CountDownLatch(1);
      secondCreateReturned = new CountDownLatch(1);
      releaseFirstCreate = new CountDownLatch(1);
      coordinateCreates.set(true);
    }

    boolean awaitFirstCreateReturned() throws InterruptedException {
      return firstCreateReturned.await(5, TimeUnit.SECONDS);
    }

    boolean awaitSecondCreateReturned() throws InterruptedException {
      return secondCreateReturned.await(5, TimeUnit.SECONDS);
    }

    void releaseFirstCreate() {
      releaseFirstCreate.countDown();
    }

    void coordinateNextTwoPatches() {
      reset();
      firstPatchReturned = new CountDownLatch(1);
      secondPatchStarted = new CountDownLatch(1);
      secondPatchReturned = new CountDownLatch(1);
      releaseFirstPatch = new CountDownLatch(1);
      coordinatePatches.set(true);
    }

    boolean awaitFirstPatchReturned() throws InterruptedException {
      return firstPatchReturned.await(5, TimeUnit.SECONDS);
    }

    boolean awaitSecondPatchStarted() throws InterruptedException {
      return secondPatchStarted.await(5, TimeUnit.SECONDS);
    }

    boolean awaitSecondPatchReturned() throws InterruptedException {
      return secondPatchReturned.await(2, TimeUnit.SECONDS);
    }

    void releaseFirstPatch() {
      releaseFirstPatch.countDown();
    }

    void coordinateNextMutationResponse() {
      releaseFirstMutationResponse();
      firstMutationCommitted = new CountDownLatch(1);
      releaseFirstMutationResponse = new CountDownLatch(1);
      coordinateMutationResponse.set(true);
    }

    boolean awaitFirstMutationCommitted() throws InterruptedException {
      return firstMutationCommitted.await(5, TimeUnit.SECONDS);
    }

    void releaseFirstMutationResponse() {
      releaseFirstMutationResponse.countDown();
    }

    List<Boolean> lastPatchActiveStates() {
      return lastPatchActiveStates;
    }

    void reset() {
      releaseFirstCreate();
      releaseFirstPatch();
      releaseFirstMutationResponse();
      coordinateCreates.set(false);
      coordinatePatches.set(false);
      coordinateMutationResponse.set(false);
      createIndex.set(0);
      patchIndex.set(0);
      firstCreateReturned = new CountDownLatch(0);
      secondCreateReturned = new CountDownLatch(0);
      releaseFirstCreate = new CountDownLatch(0);
      firstPatchReturned = new CountDownLatch(0);
      secondPatchStarted = new CountDownLatch(0);
      secondPatchReturned = new CountDownLatch(0);
      releaseFirstPatch = new CountDownLatch(0);
      firstMutationCommitted = new CountDownLatch(0);
      releaseFirstMutationResponse = new CountDownLatch(0);
      lastPatchActiveStates = List.of();
    }

    private void awaitRelease() {
      awaitRelease(releaseFirstCreate, "Timed out before releasing the first SCIM create");
    }

    private void coordinateResponseAfterCommit() {
      if (!coordinateMutationResponse.compareAndSet(true, false)) {
        return;
      }
      if (!TransactionSynchronizationManager.isSynchronizationActive()) {
        throw new IllegalStateException("SCIM response was assembled outside a transaction");
      }
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              firstMutationCommitted.countDown();
              awaitRelease(
                  releaseFirstMutationResponse,
                  "Timed out before releasing the first SCIM mutation response");
            }
          });
    }

    private static void awaitRelease(CountDownLatch latch, String timeoutMessage) {
      try {
        if (!latch.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException(timeoutMessage);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while pausing a SCIM mutation", e);
      }
    }
  }

  static final class ControllableCurrentAccountAccessCacheGateway
      implements CurrentAccountAccessCacheGateway {

    private final RedisCurrentAccountAccessGateway delegate;
    private final AtomicBoolean failNextRequiredEviction = new AtomicBoolean();

    ControllableCurrentAccountAccessCacheGateway(RedisCurrentAccountAccessGateway delegate) {
      this.delegate = delegate;
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
      if (failNextRequiredEviction.compareAndSet(true, false)) {
        throw new RedisConnectionFailureException("Required current-account cache eviction failed");
      }
      delegate.evict(internalUserId);
    }

    @Override
    public void scheduleEvictionAfterCommit(UUID internalUserId) {
      delegate.scheduleEvictionAfterCommit(internalUserId);
    }

    void failNextRequiredEviction() {
      failNextRequiredEviction.set(true);
    }

    void reset() {
      failNextRequiredEviction.set(false);
    }
  }

  @Bean
  JpaRuntimeFeatureStore runtimeFeatureStore(
      RuntimeFeatureJpaRepository featureRepository,
      RuntimeFeatureAudienceUserGroupJpaRepository audienceUserGroupRepository,
      RuntimeFeatureHistoryJpaRepository historyRepository,
      ObjectMapper objectMapper) {
    return new JpaRuntimeFeatureStore(
        featureRepository,
        audienceUserGroupRepository,
        historyRepository,
        objectMapper,
        Clock.systemUTC());
  }

  @Bean
  RuntimeFeatureAudiencePolicy runtimeFeatureAudiencePolicy(
      IamGroupRepository groupRepository, IamGroupMembersRepository groupMembersRepository) {
    return new RuntimeFeatureAudiencePolicy(
        new IamRuntimeFeatureUserGroupAccess(groupRepository, groupMembersRepository));
  }

  @Bean
  ManageRuntimeFeatures manageRuntimeFeatures(
      JpaRuntimeFeatureStore store,
      RuntimeFeatureAudiencePolicy audiencePolicy,
      WatchboxTrackingResetGateway trackingResetGateway,
      WatchboxTrackingResetTelemetry trackingResetTelemetry) {
    return new ManageRuntimeFeaturesUsecase(
        store,
        audiencePolicy,
        new RuntimeFeatureNamePolicy(),
        trackingResetGateway,
        trackingResetTelemetry);
  }
}
