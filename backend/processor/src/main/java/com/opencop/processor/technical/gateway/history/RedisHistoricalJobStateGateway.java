// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed implementation of {@link HistoricalJobStateGateway}. Atomicity is owned by the
 * underlying Lua scripts (see {@link RedisHistoricalJobStateGuardedUpdater}, {@link
 * RedisHistoricalJobStateCreator}, {@link RedisHistoricalJobCancellationRequestUpdater}, {@link
 * RedisHistoricalJobStateTtlRefresher}); this class is a pure router and intentionally adds no
 * JVM-local locking. Every write path — including the TTL-only {@link #refreshActiveTtl} — is
 * atomic in Redis, so a stale in-process snapshot can never clobber a concurrent terminal
 * transition.
 */
@Component
public class RedisHistoricalJobStateGateway implements HistoricalJobStateGateway {

  private static final List<String> TERMINAL_STATUSES = List.of("COMPLETED", "FAILED", "CANCELLED");
  private static final List<String> CANCELLATION_REQUESTED_STAGE =
      List.of(HistoricalJobStage.CANCELLATION_REQUESTED.name());
  private static final List<String> NO_STAGE_GUARD = List.of();
  private static final long NO_TTL_REFRESH = 0L;

  private final RedisHistoricalJobStateStore stateStore;
  private final RedisHistoricalJobStateGuardedUpdater guardedUpdater;
  private final RedisHistoricalJobStateCreator stateCreator;
  private final RedisHistoricalJobCancellationRequestUpdater cancellationRequestUpdater;
  private final RedisHistoricalJobStateTtlRefresher ttlRefresher;
  private final HistoricalExecutionProperties properties;
  private final Clock clock;

  /**
   * Creates a standalone adapter for direct test construction. Production callers use the
   * constructor accepting the topology-selected key layout.
   */
  public RedisHistoricalJobStateGateway(
      StringRedisTemplate redisTemplate,
      HistoricalExecutionProperties properties,
      Clock clock,
      ProcessorHistoricalRedisScripts scripts) {
    this(redisTemplate, HistoricalJobControlKeyLayout.STANDALONE, properties, clock, scripts);
  }

  @Autowired
  public RedisHistoricalJobStateGateway(
      StringRedisTemplate redisTemplate,
      HistoricalJobControlKeyLayout keyLayout,
      HistoricalExecutionProperties properties,
      Clock clock,
      ProcessorHistoricalRedisScripts scripts) {
    Objects.requireNonNull(keyLayout, "keyLayout");
    this.stateStore = new RedisHistoricalJobStateStore(redisTemplate, keyLayout, properties);
    this.guardedUpdater =
        new RedisHistoricalJobStateGuardedUpdater(
            redisTemplate, keyLayout, scripts.updateWithGuards());
    this.stateCreator =
        new RedisHistoricalJobStateCreator(
            redisTemplate, keyLayout, scripts.createReceivedIfAbsent());
    this.cancellationRequestUpdater =
        new RedisHistoricalJobCancellationRequestUpdater(
            redisTemplate, keyLayout, scripts.markCancellationRequested());
    this.ttlRefresher =
        new RedisHistoricalJobStateTtlRefresher(
            redisTemplate, keyLayout, scripts.refreshActiveTtl());
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public void createReceived(HistoricalTracksJobSubmission job) {
    stateStore.persistWithTtl(HistoricalJobState.received(job, now(), properties.getJobStateTtl()));
  }

  @Override
  public boolean createReceivedIfAbsent(HistoricalTracksJobSubmission job) {
    HistoricalJobState received =
        HistoricalJobState.received(job, now(), properties.getJobStateTtl());
    return stateCreator.createReceivedIfAbsent(
        job, received, properties.getJobStateTtl().toMillis());
  }

  @Override
  public void createReceived(HistoricalSubjectExecutionMeta laneMetadata) {
    stateStore.persistWithTtl(
        HistoricalJobState.received(laneMetadata, now(), properties.getJobStateTtl()));
  }

  @Override
  public boolean createReceivedIfAbsent(HistoricalSubjectExecutionMeta laneMetadata) {
    HistoricalJobState received =
        HistoricalJobState.received(laneMetadata, now(), properties.getJobStateTtl());
    return stateCreator.createReceivedIfAbsent(
        laneMetadata, received, properties.getJobStateTtl().toMillis());
  }

  @Override
  public boolean markQueued(UUID jobId, String queueRole) {
    return guardedTransition(jobId, current -> current.queued(now(), ttl(), queueRole));
  }

  @Override
  public boolean markRunning(UUID jobId, String workerId) {
    return guardedTransition(jobId, current -> current.running(now(), ttl(), workerId));
  }

  @Override
  public boolean markPlanning(UUID jobId) {
    return guardedTransition(jobId, current -> current.planning(now(), ttl()));
  }

  @Override
  public boolean markPlanned(UUID jobId) {
    return guardedTransition(jobId, current -> current.planned(now(), ttl()));
  }

  @Override
  public boolean markCompleted(UUID jobId) {
    return guardedTransition(jobId, current -> current.completed(now(), ttl()));
  }

  @Override
  public boolean markDeliveredCompleted(UUID jobId) {
    return markCompleted(jobId);
  }

  @Override
  public boolean markDeliveryPending(UUID jobId) {
    return guardedTransition(jobId, current -> current.deliveryPending(now(), ttl()));
  }

  @Override
  public boolean markDeliveryFailed(UUID jobId, String failureReason) {
    return markFailed(jobId, HistoricalJobStage.DELIVERY_FAILED, failureReason);
  }

  @Override
  public boolean markFailed(UUID jobId, HistoricalJobStage stage, String failureReason) {
    Objects.requireNonNull(stage, "stage");
    return guardedTransition(
        jobId,
        current -> current.failed(now(), ttl(), stage, failureReason),
        NO_STAGE_GUARD,
        ttlMillis());
  }

  @Override
  public boolean markCancellationRequested(UUID jobId, String reason) {
    Optional<HistoricalJobState> current = findByJobId(jobId);
    if (current.isEmpty()) {
      return false;
    }
    Instant updatedAt = now();
    Instant expiresAt = updatedAt.plus(ttl());
    return cancellationRequestUpdater.markRequested(
        jobId, reason, updatedAt, expiresAt, TERMINAL_STATUSES, ttlMillis());
  }

  @Override
  public boolean markCancelled(UUID jobId, String reason) {
    return guardedTransition(
        jobId, current -> current.cancelled(now(), reason), NO_STAGE_GUARD, NO_TTL_REFRESH);
  }

  @Override
  public boolean refreshActiveTtl(UUID jobId) {
    Instant updatedAt = now();
    Instant expiresAt = updatedAt.plus(ttl());
    return ttlRefresher.refresh(jobId, updatedAt, expiresAt, ttlMillis());
  }

  @Override
  public Optional<HistoricalJobState> findByJobId(UUID jobId) {
    return stateStore.findByJobId(jobId);
  }

  private boolean guardedTransition(UUID jobId, UnaryOperator<HistoricalJobState> transition) {
    return guardedTransition(jobId, transition, CANCELLATION_REQUESTED_STAGE, ttlMillis());
  }

  private boolean guardedTransition(
      UUID jobId,
      UnaryOperator<HistoricalJobState> transition,
      List<String> blockedStages,
      long ttlMillis) {
    Optional<HistoricalJobState> current = findByJobId(jobId);
    if (current.isEmpty()) {
      return false;
    }
    HistoricalJobState target = transition.apply(current.get());
    return guardedUpdater.update(jobId, target, TERMINAL_STATUSES, blockedStages, ttlMillis);
  }

  private Instant now() {
    return Instant.now(clock);
  }

  private Duration ttl() {
    return properties.getJobStateTtl();
  }

  private long ttlMillis() {
    return properties.getJobStateTtl().toMillis();
  }
}
