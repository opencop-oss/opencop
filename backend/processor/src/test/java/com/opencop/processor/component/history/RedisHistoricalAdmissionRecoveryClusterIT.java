// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.component.redis.LocalRedisClusterSupport;
import com.opencop.core.sdk.component.redis.RedisClusterCandidateContext;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.business.policy.history.HistoricalJobAdmissionRecoveryPolicy;
import com.opencop.processor.business.usecase.history.HistoricalJobAdmissionRecovery;
import com.opencop.processor.technical.gateway.history.ProcessorHistoricalRedisScripts;
import com.opencop.processor.technical.gateway.history.RedisHistoricalJobAdmissionGateway;
import com.opencop.processor.technical.gateway.history.RedisHistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.admission.HistoricalJobAdmissionLifecycle;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision.Action;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class RedisHistoricalAdmissionRecoveryClusterIT {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449150");
  private static final UUID REQUEST_A = UUID.fromString("550e8400-e29b-41d4-a716-446655449151");
  private static final UUID REQUEST_B = UUID.fromString("550e8400-e29b-41d4-a716-446655449152");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void recovery_withSelectedClusterScanPattern_releasesTerminalActiveJob() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      assertThat(layout.activeRunningScanPattern())
          .isEqualTo("opencop:history:{history}:admission:user:*:active-running");

      var redis = cluster.stringTemplate();
      var properties = properties();
      properties.setMaxConcurrentSubjectsPerJob(1);
      var scripts = new ProcessorHistoricalRedisScripts();
      var stateGateway =
          new RedisHistoricalJobStateGateway(redis, layout, properties, CLOCK, scripts);
      var coordinator =
          RedisHistoricalJobAdmissionGateway.create(
              redis, new ObjectMapper(), properties, scripts, layout);
      var active = job(REQUEST_A, Instant.parse("2026-08-21T10:00:00Z"));
      var replacement = job(REQUEST_B, Instant.parse("2026-08-21T10:00:01Z"));

      stateGateway.createReceived(active);
      assertThat(coordinator.register(active).action()).isEqualTo(Action.START_NOW);
      assertThat(stateGateway.markQueued(active.jobId())).isTrue();
      assertThat(stateGateway.markRunning(active.jobId(), "history-cluster-worker")).isTrue();
      assertThat(stateGateway.markCompleted(active.jobId())).isTrue();

      stateGateway.createReceived(replacement);
      var queued = coordinator.register(replacement);
      assertThat(queued.action()).isEqualTo(Action.QUEUE_ONLY);
      assertThat(stateGateway.markQueued(replacement.jobId(), queued.queueRole())).isTrue();

      var decisions = coordinator.recoverTerminalActiveJobs(stateGateway, ignored -> false);

      assertThat(decisions).hasSize(1);
      assertThat(decisions.getFirst().jobsToStart())
          .extracting(HistoricalTracksJobSubmission::jobId)
          .containsExactly(replacement.jobId());
    }
  }

  @Test
  void recovery_clusterMissingStatePromotesQueuedReplacementThroughLifecycle() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var nonSeedUser = nonSeedUser(fixture, layout);
      var activeRunningKey = layout.activeRunningKey(nonSeedUser);

      var redis = cluster.stringTemplate();
      var properties = properties();
      properties.setMaxConcurrentSubjectsPerJob(1);
      var scripts = new ProcessorHistoricalRedisScripts();
      var stateGateway =
          new RedisHistoricalJobStateGateway(redis, layout, properties, CLOCK, scripts);
      var coordinator =
          RedisHistoricalJobAdmissionGateway.create(
              redis, new ObjectMapper(), properties, scripts, layout);
      var active = job(nonSeedUser, REQUEST_A, Instant.parse("2026-08-21T10:00:00Z"));
      var replacement = job(nonSeedUser, REQUEST_B, Instant.parse("2026-08-21T10:00:01Z"));

      registerRunning(stateGateway, coordinator, active);
      registerQueued(stateGateway, coordinator, replacement);
      assertThat(redis.delete(layout.jobState(active.jobId()))).isTrue();

      var submittedJobs = new ArrayList<HistoricalTracksJobSubmission>();
      var recovery =
          recovery(
              stateGateway,
              coordinator,
              (job, ignored) -> submittedJobs.add(job),
              ignored -> false);
      try {
        recovery.recoverStaleAdmission();
      } finally {
        recovery.close();
      }

      assertThat(submittedJobs)
          .extracting(HistoricalTracksJobSubmission::jobId)
          .containsExactly(replacement.jobId());
      assertThat(redis.opsForSet().isMember(activeRunningKey, active.jobId().toString())).isFalse();
      assertThat(redis.opsForSet().isMember(activeRunningKey, replacement.jobId().toString()))
          .isTrue();
      assertThat(redis.hasKey(layout.queuedPayload(nonSeedUser, replacement.jobId()).redisKey()))
          .isFalse();
    }
  }

  @Test
  void recovery_discoversTerminalAndOrphanJobsBeforeDispatchingPromotedReplacement() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var nonSeedUser = USER_ID;
      var activeRunningKey = layout.activeRunningKey(nonSeedUser);
      fixture.assertKeyOwnedByNonSeedPrimary(activeRunningKey);

      var redis = cluster.stringTemplate();
      var properties = properties();
      properties.setMaxConcurrentSubjectsPerJob(1);
      var scripts = new ProcessorHistoricalRedisScripts();
      var stateGateway =
          new RedisHistoricalJobStateGateway(redis, layout, properties, CLOCK, scripts);
      var coordinator =
          RedisHistoricalJobAdmissionGateway.create(
              redis, new ObjectMapper(), properties, scripts, layout);
      var requestId = UUID.fromString("550e8400-e29b-41d4-a716-446655449153");
      var active = job(nonSeedUser, requestId, Instant.parse("2026-08-21T10:00:00Z"));
      var terminalQueued = job(nonSeedUser, requestId, Instant.parse("2026-08-21T10:00:01Z"));
      var runnableQueued = job(nonSeedUser, requestId, Instant.parse("2026-08-21T10:00:02Z"));

      registerRunning(stateGateway, coordinator, active);
      registerQueued(stateGateway, coordinator, terminalQueued);
      registerQueued(stateGateway, coordinator, runnableQueued);
      assertThat(stateGateway.markCompleted(active.jobId())).isTrue();
      assertThat(stateGateway.markCompleted(terminalQueued.jobId())).isTrue();

      var submittedJobs = new ArrayList<HistoricalTracksJobSubmission>();
      var recovery =
          recovery(
              stateGateway,
              coordinator,
              (job, ignored) -> submittedJobs.add(job),
              ignored -> false);
      try {
        recovery.recoverStaleAdmission();
      } finally {
        recovery.close();
      }

      assertThat(submittedJobs)
          .extracting(HistoricalTracksJobSubmission::jobId)
          .containsExactly(runnableQueued.jobId());
      assertThat(redis.opsForSet().isMember(activeRunningKey, active.jobId().toString())).isFalse();
      assertThat(redis.opsForSet().isMember(activeRunningKey, terminalQueued.jobId().toString()))
          .isFalse();
      assertThat(redis.opsForSet().isMember(activeRunningKey, runnableQueued.jobId().toString()))
          .isTrue();
      assertThat(redis.hasKey(layout.queuedPayload(nonSeedUser, terminalQueued.jobId()).redisKey()))
          .isFalse();
      assertThat(redis.hasKey(layout.queuedPayload(nonSeedUser, runnableQueued.jobId()).redisKey()))
          .isFalse();
    }
  }

  @Test
  void recovery_clusterStateReadDataAccessFailureRetainsMemberUntilHealthyObservation() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var nonSeedUser = nonSeedUser(fixture, layout);
      var activeRunningKey = layout.activeRunningKey(nonSeedUser);

      var redis = cluster.stringTemplate();
      var properties = properties();
      properties.setMaxConcurrentSubjectsPerJob(1);
      var scripts = new ProcessorHistoricalRedisScripts();
      var stateGateway =
          new RedisHistoricalJobStateGateway(redis, layout, properties, CLOCK, scripts);
      var coordinator =
          RedisHistoricalJobAdmissionGateway.create(
              redis, new ObjectMapper(), properties, scripts, layout);
      var active =
          job(
              nonSeedUser,
              UUID.fromString("550e8400-e29b-41d4-a716-446655449154"),
              Instant.parse("2026-08-21T10:00:00Z"));
      var replacement =
          job(
              nonSeedUser,
              UUID.fromString("550e8400-e29b-41d4-a716-446655449155"),
              Instant.parse("2026-08-21T10:00:01Z"));

      registerRunning(stateGateway, coordinator, active);
      registerQueued(stateGateway, coordinator, replacement);
      assertThat(stateGateway.markCompleted(active.jobId())).isTrue();
      var activeStateKey = layout.jobState(active.jobId());
      redis.opsForValue().set(activeStateKey, "wrong-state-type");
      assertThat(redis.type(activeStateKey)).isEqualTo(DataType.STRING);

      var submittedJobs = new ArrayList<HistoricalTracksJobSubmission>();
      var recovery =
          recovery(
              stateGateway,
              coordinator,
              (job, ignored) -> submittedJobs.add(job),
              ignored -> false);
      try {
        recovery.recoverStaleAdmission();
        assertThat(submittedJobs).isEmpty();
        assertThat(redis.opsForSet().isMember(activeRunningKey, active.jobId().toString()))
            .isTrue();

        redis.delete(activeStateKey);
        stateGateway.createReceived(active);
        assertThat(stateGateway.markQueued(active.jobId())).isTrue();
        assertThat(stateGateway.markRunning(active.jobId(), "history-cluster-recovery")).isTrue();
        assertThat(stateGateway.markCompleted(active.jobId())).isTrue();
        recovery.recoverStaleAdmission();
      } finally {
        recovery.close();
      }

      assertThat(submittedJobs)
          .extracting(HistoricalTracksJobSubmission::jobId)
          .containsExactly(replacement.jobId());
      assertThat(redis.opsForSet().isMember(activeRunningKey, active.jobId().toString())).isFalse();
      assertThat(redis.opsForSet().isMember(activeRunningKey, replacement.jobId().toString()))
          .isTrue();
      assertThat(redis.hasKey(layout.queuedPayload(nonSeedUser, replacement.jobId()).redisKey()))
          .isFalse();
    }
  }

  @Test
  void recovery_secondClusterObservationFailureRetainsFirstPassMutationWithoutDispatch() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var nonSeedUser = nonSeedUser(fixture, layout);
      var activeRunningKey = layout.activeRunningKey(nonSeedUser);

      var redis = cluster.stringTemplate();
      var properties = properties();
      properties.setMaxConcurrentSubjectsPerJob(1);
      var scripts = new ProcessorHistoricalRedisScripts();
      var stateGateway =
          new RedisHistoricalJobStateGateway(redis, layout, properties, CLOCK, scripts);
      var coordinator =
          RedisHistoricalJobAdmissionGateway.create(
              redis, new ObjectMapper(), properties, scripts, layout);
      var requestId = UUID.fromString("550e8400-e29b-41d4-a716-446655449156");
      var active = job(nonSeedUser, requestId, Instant.parse("2026-08-21T10:00:00Z"));
      var replacement = job(nonSeedUser, requestId, Instant.parse("2026-08-21T10:00:01Z"));
      registerRunning(stateGateway, coordinator, active);
      registerQueued(stateGateway, coordinator, replacement);
      assertThat(stateGateway.markCompleted(active.jobId())).isTrue();

      var failingRedis = new ScanFailureRedisTemplate(redis.getConnectionFactory());
      failingRedis.afterPropertiesSet();
      failingRedis.failOnScanCall(
          2, new RedisConnectionFailureException("second admission scan unavailable"));
      var failingCoordinator =
          RedisHistoricalJobAdmissionGateway.create(
              failingRedis, new ObjectMapper(), properties, scripts, layout);
      var submittedJobs = new ArrayList<HistoricalTracksJobSubmission>();
      var recovery =
          recovery(
              stateGateway,
              failingCoordinator,
              (job, ignored) -> submittedJobs.add(job),
              ignored -> false);
      try {
        assertThatThrownBy(recovery::recoverStaleAdmission)
            .isInstanceOf(RedisConnectionFailureException.class);
      } finally {
        recovery.close();
      }

      assertThat(redis.opsForSet().isMember(activeRunningKey, active.jobId().toString())).isFalse();
      assertThat(redis.opsForSet().isMember(activeRunningKey, replacement.jobId().toString()))
          .isTrue();
      assertThat(redis.hasKey(layout.queuedPayload(nonSeedUser, replacement.jobId()).redisKey()))
          .isFalse();
      assertThat(submittedJobs).isEmpty();
    }
  }

  private static HistoricalJobAdmissionRecovery recovery(
      RedisHistoricalJobStateGateway stateGateway,
      RedisHistoricalJobAdmissionGateway coordinator,
      BiConsumer<HistoricalTracksJobSubmission, Long> submitter,
      Predicate<UUID> jobIsTracked) {
    var lifecycle =
        new HistoricalJobAdmissionLifecycle(
            stateGateway,
            coordinator,
            new HistoricalObservability(new SimpleMeterRegistry()),
            jobIsTracked,
            ignored -> {},
            submitter,
            (job, replacementRequestId) -> false,
            (eventName, context) -> {});
    var properties = properties();
    return new HistoricalJobAdmissionRecovery(
        coordinator,
        stateGateway,
        mock(ScheduledExecutorService.class),
        properties.getAdmissionRecoveryInterval(),
        properties.getTtlRefreshInterval(),
        properties.getMaxRuntime(),
        properties.getJobStateTtl(),
        new HistoricalJobAdmissionRecoveryPolicy(),
        jobIsTracked,
        lifecycle::processRecoveredDecision);
  }

  private static void registerRunning(
      RedisHistoricalJobStateGateway stateGateway,
      RedisHistoricalJobAdmissionGateway coordinator,
      HistoricalTracksJobSubmission job) {
    stateGateway.createReceived(job);
    assertThat(coordinator.register(job).action()).isEqualTo(Action.START_NOW);
    assertThat(stateGateway.markQueued(job.jobId())).isTrue();
    assertThat(stateGateway.markRunning(job.jobId(), "history-cluster-recovery")).isTrue();
  }

  private static void registerQueued(
      RedisHistoricalJobStateGateway stateGateway,
      RedisHistoricalJobAdmissionGateway coordinator,
      HistoricalTracksJobSubmission job) {
    stateGateway.createReceived(job);
    var decision = coordinator.register(job);
    assertThat(decision.action()).isEqualTo(Action.QUEUE_ONLY);
    assertThat(stateGateway.markQueued(job.jobId(), decision.queueRole())).isTrue();
  }

  private static UUID nonSeedUser(
      LocalRedisClusterSupport.Fixture fixture, HistoricalJobControlKeyLayout layout) {
    fixture.assertKeyOwnedByNonSeedPrimary(layout.activeRunningKey(USER_ID));
    return USER_ID;
  }

  private static final class ScanFailureRedisTemplate extends StringRedisTemplate {

    private int scanCalls;
    private int failOnScanCall;
    private RuntimeException scanFailure;

    private ScanFailureRedisTemplate(RedisConnectionFactory connectionFactory) {
      super(connectionFactory);
    }

    private void failOnScanCall(int call, RuntimeException failure) {
      failOnScanCall = call;
      scanFailure = failure;
    }

    @Override
    public Cursor<String> scan(ScanOptions options) {
      scanCalls++;
      if (scanCalls == failOnScanCall) {
        throw scanFailure;
      }
      return super.scan(options);
    }
  }

  private static HistoricalExecutionProperties properties() {
    var properties = new HistoricalExecutionProperties();
    properties.setJobStateTtl(Duration.ofHours(24));
    properties.setAdmissionTtl(Duration.ofHours(1));
    properties.setMaxConcurrentSubjectsPerJob(2);
    return properties;
  }

  private static HistoricalTracksJobSubmission job(UUID requestId, Instant requestedAt) {
    return job(USER_ID, requestId, requestedAt);
  }

  private static HistoricalTracksJobSubmission job(
      UUID userId, UUID requestId, Instant requestedAt) {
    return new HistoricalTracksJobSubmission(
        UUID.randomUUID(),
        requestId,
        JobIssuer.user(userId),
        List.of(
            new HistoricalTracksSubject(UUID.randomUUID(), SubjectType.SOURCE, UUID.randomUUID())),
        userId,
        requestedAt,
        Instant.parse("2026-08-21T09:00:00Z"),
        Instant.parse("2026-08-21T10:00:00Z"),
        Instant.parse("2026-08-21T09:00:00Z"),
        Instant.parse("2026-08-21T10:00:00Z"),
        HistoricalResolution.MINUTE_1,
        60,
        List.of());
  }
}
