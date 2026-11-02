// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.component.redis.LocalRedisClusterSupport;
import com.opencop.core.sdk.component.redis.RedisClusterCandidateContext;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.technical.gateway.history.ProcessorHistoricalRedisScripts;
import com.opencop.processor.technical.gateway.history.RedisHistoricalJobAdmissionGateway;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision.Action;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.lettuce.core.cluster.SlotHash;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class RedisHistoricalJobAdmissionClusterIT {

  private static final UUID USER_A = UUID.fromString("550e8400-e29b-41d4-a716-446655449150");
  private static final UUID USER_B = UUID.fromString("550e8400-e29b-41d4-a716-446655449141");
  private static final UUID REQUEST_A = UUID.fromString("550e8400-e29b-41d4-a716-446655449151");
  private static final UUID REQUEST_B = UUID.fromString("550e8400-e29b-41d4-a716-446655449152");
  private static final UUID REQUEST_C = UUID.fromString("550e8400-e29b-41d4-a716-446655449153");

  @Test
  void selectedClusterLayout_registersQueuesReplacesAndRestoresHashPayloads() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var redis = cluster.stringTemplate();
      var properties = properties();
      properties.setMaxConcurrentSubjectsPerJob(1);
      var coordinator =
          RedisHistoricalJobAdmissionGateway.create(
              redis, new ObjectMapper(), properties, new ProcessorHistoricalRedisScripts(), layout);

      var active = job(USER_A, REQUEST_A, Instant.parse("2026-08-21T10:00:00Z"));
      var backlog = job(USER_A, REQUEST_A, Instant.parse("2026-08-21T10:00:01Z"));
      var firstReplacement = job(USER_A, REQUEST_B, Instant.parse("2026-08-21T10:00:02Z"));
      var replacement = job(USER_A, REQUEST_C, Instant.parse("2026-08-21T10:00:03Z"));

      assertThat(SlotHash.getSlot(layout.activeRequestKey(USER_A)))
          .isEqualTo(SlotHash.getSlot(layout.activeRequestKey(USER_B)));
      assertThat(SlotHash.getSlot(layout.activeRequestKey(USER_A)))
          .isEqualTo(SlotHash.getSlot(layout.queuedPayload(USER_A, backlog.jobId()).redisKey()));

      assertThat(coordinator.register(active).action()).isEqualTo(Action.START_NOW);
      assertThat(coordinator.register(backlog).action()).isEqualTo(Action.QUEUE_ONLY);

      var backlogPayload = layout.queuedPayload(USER_A, backlog.jobId());
      assertThat(redis.type(backlogPayload.redisKey())).isEqualTo(DataType.HASH);
      assertThat(redis.opsForHash().get(backlogPayload.redisKey(), backlogPayload.payloadField()))
          .isNotNull();
      var deadline =
          Long.parseLong(
              (String)
                  redis
                      .opsForHash()
                      .get(backlogPayload.redisKey(), backlogPayload.deadlineField()));
      assertThat(deadline).isGreaterThan(System.currentTimeMillis());
      assertThat(
              redis.hasKey(
                  HistoricalJobControlKeyLayout.STANDALONE.queuedPayloadKey(
                      backlog.jobId().toString())))
          .isFalse();

      assertThat(coordinator.register(firstReplacement).action()).isEqualTo(Action.QUEUE_ONLY);
      var replacementDecision = coordinator.register(replacement);
      assertThat(replacementDecision.action()).isEqualTo(Action.QUEUE_ONLY);
      assertThat(replacementDecision.replacementSuperseded()).isTrue();
      assertThat(replacementDecision.jobsToCancel())
          .extracting(cancellation -> cancellation.jobId())
          .containsExactly(firstReplacement.jobId());
      assertThat(
              redis
                  .opsForHash()
                  .hasKey(
                      layout.queuedPayload(USER_A, firstReplacement.jobId()).redisKey(),
                      firstReplacement.jobId().toString()))
          .isFalse();

      var replacementPayload = layout.queuedPayload(USER_A, replacement.jobId());
      assertThat(
              redis
                  .opsForHash()
                  .hasKey(replacementPayload.redisKey(), replacementPayload.payloadField()))
          .isTrue();

      var exit = coordinator.recordExecutionExit(active.jobId());
      assertThat(exit.jobsToStart())
          .extracting(HistoricalTracksJobSubmission::jobId)
          .containsExactly(backlog.jobId());

      var terminal = coordinator.recordTerminal(backlog.jobId());
      assertThat(terminal.jobsToStart())
          .extracting(HistoricalTracksJobSubmission::jobId)
          .containsExactly(replacement.jobId());
      assertThat(
              redis
                  .opsForHash()
                  .hasKey(replacementPayload.redisKey(), replacementPayload.payloadField()))
          .isFalse();
    }
  }

  @Test
  void clusterScripts_preflightWrongTypesBeforeMutationAndReadExpiredPayloadAsAbsent() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      var redis = cluster.stringTemplate();
      var properties = properties();
      properties.setMaxConcurrentSubjectsPerJob(1);
      var coordinator =
          RedisHistoricalJobAdmissionGateway.create(
              redis, new ObjectMapper(), properties, new ProcessorHistoricalRedisScripts(), layout);

      var wrongTypeUser = USER_B;
      var wrongTypeJob = job(wrongTypeUser, REQUEST_A, Instant.parse("2026-08-21T10:01:00Z"));
      redis.opsForValue().set(layout.activeRunningKey(wrongTypeUser), "not-a-set");
      assertThatThrownBy(() -> coordinator.register(wrongTypeJob))
          .hasStackTraceContaining("WRONGTYPE");
      assertThat(redis.hasKey(layout.activeRequestKey(wrongTypeUser))).isFalse();
      assertThat(redis.hasKey(layout.jobMeta(wrongTypeJob.jobId()))).isFalse();

      redis.delete(layout.activeRunningKey(wrongTypeUser));
      redis
          .opsForValue()
          .set(layout.queuedPayload(wrongTypeUser, wrongTypeJob.jobId()).redisKey(), "not-a-hash");
      assertThatThrownBy(() -> coordinator.register(wrongTypeJob))
          .hasStackTraceContaining("WRONGTYPE");
      assertThat(redis.hasKey(layout.activeRequestKey(wrongTypeUser))).isFalse();
      assertThat(redis.hasKey(layout.jobMeta(wrongTypeJob.jobId()))).isFalse();

      var active = job(USER_A, REQUEST_A, Instant.parse("2026-08-21T10:02:00Z"));
      var queued = job(USER_A, REQUEST_A, Instant.parse("2026-08-21T10:02:01Z"));
      assertThat(coordinator.register(active).action()).isEqualTo(Action.START_NOW);
      assertThat(coordinator.register(queued).action()).isEqualTo(Action.QUEUE_ONLY);
      var queuedPayload = layout.queuedPayload(USER_A, queued.jobId());
      redis.opsForHash().put(queuedPayload.redisKey(), queuedPayload.deadlineField(), "1");

      var terminal = coordinator.recordTerminal(active.jobId());
      assertThat(terminal.jobsToStart()).isEmpty();
      assertThat(terminal.jobsToCancel())
          .extracting(cancellation -> cancellation.jobId())
          .containsExactly(queued.jobId());
      assertThat(redis.opsForHash().hasKey(queuedPayload.redisKey(), queuedPayload.payloadField()))
          .isFalse();
    }
  }

  @Test
  void clusterRegistration_ordersTiedReplacementsAndReplacesAnExpiredReplacementList() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var redis = cluster.stringTemplate();
      var properties = properties();
      properties.setMaxConcurrentSubjectsPerJob(1);
      var coordinator = coordinator(redis, properties, layout);
      var active = job(USER_A, REQUEST_A, Instant.parse("2026-08-21T10:03:00Z"));
      var firstReplacement = job(USER_A, REQUEST_B, Instant.parse("2026-08-21T10:03:01Z"));
      var tiedNewer = job(USER_A, REQUEST_C, firstReplacement.requestedAt());
      var tiedOlder =
          job(
              USER_A,
              UUID.fromString("00000000-0000-0000-0000-000000000001"),
              firstReplacement.requestedAt());

      assertThat(coordinator.register(active).action()).isEqualTo(Action.START_NOW);
      assertThat(coordinator.register(firstReplacement).replacementQueued()).isTrue();

      var newerDecision = coordinator.register(tiedNewer);
      assertThat(newerDecision.replacementSuperseded()).isTrue();
      assertThat(newerDecision.jobsToCancel())
          .extracting(cancellation -> cancellation.jobId())
          .containsExactly(firstReplacement.jobId());
      assertThat(
              redis
                  .opsForHash()
                  .hasKey(
                      layout.queuedPayload(USER_A, firstReplacement.jobId()).redisKey(),
                      firstReplacement.jobId().toString()))
          .isFalse();

      var olderDecision = coordinator.register(tiedOlder);
      assertThat(olderDecision.replacementSuperseded()).isFalse();
      assertThat(olderDecision.jobsToCancel())
          .extracting(cancellation -> cancellation.jobId())
          .containsExactly(tiedOlder.jobId());
      assertThat(
              redis
                  .opsForHash()
                  .hasKey(
                      layout.queuedPayload(USER_A, tiedOlder.jobId()).redisKey(),
                      tiedOlder.jobId().toString()))
          .isFalse();
      assertThat(redis.opsForHash().get(layout.replacementRequestKey(USER_A), "requestId"))
          .isEqualTo(REQUEST_C.toString());
      assertThat(redis.opsForList().range(layout.replacementJobsKey(USER_A), 0, -1))
          .containsExactly(tiedNewer.jobId().toString());

      redis.delete(layout.replacementRequestKey(USER_A));
      var incoming =
          job(
              USER_A,
              UUID.fromString("550e8400-e29b-41d4-a716-446655449154"),
              Instant.parse("2026-08-21T10:03:02Z"));
      var incomingDecision = coordinator.register(incoming);
      assertThat(incomingDecision.replacementQueued()).isTrue();
      assertThat(incomingDecision.replacementSuperseded()).isFalse();
      assertThat(redis.opsForList().range(layout.replacementJobsKey(USER_A), 0, -1))
          .containsExactly(incoming.jobId().toString());
      assertThat(
              redis
                  .opsForHash()
                  .hasKey(
                      layout.queuedPayload(USER_A, tiedNewer.jobId()).redisKey(),
                      tiedNewer.jobId().toString()))
          .isFalse();
      assertThat(
              redis
                  .opsForHash()
                  .hasKey(
                      layout.queuedPayload(USER_A, incoming.jobId()).redisKey(),
                      incoming.jobId().toString()))
          .isTrue();
    }
  }

  @Test
  void clusterCleanup_promotesBacklogAndReplacementThenResetsAnEmptyController() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var redis = cluster.stringTemplate();
      var properties = properties();
      properties.setMaxConcurrentSubjectsPerJob(1);
      var coordinator = coordinator(redis, properties, layout);

      var backlogActive = job(USER_A, REQUEST_A, Instant.parse("2026-08-21T10:04:00Z"));
      var backlog = job(USER_A, REQUEST_A, Instant.parse("2026-08-21T10:04:01Z"));
      assertThat(coordinator.register(backlogActive).action()).isEqualTo(Action.START_NOW);
      assertThat(coordinator.register(backlog).action()).isEqualTo(Action.QUEUE_ONLY);
      var cleanupBacklog = coordinator.cleanupAfterRegistrationFailure(backlogActive.jobId());
      assertThat(cleanupBacklog.jobsToStart())
          .extracting(HistoricalTracksJobSubmission::jobId)
          .containsExactly(backlog.jobId());
      assertThat(redis.opsForValue().get(layout.activeRequestKey(USER_A)))
          .isEqualTo(REQUEST_A.toString());
      assertThat(redis.opsForSet().members(layout.activeRunningKey(USER_A)))
          .containsExactly(backlog.jobId().toString());
      assertThat(
              redis
                  .opsForHash()
                  .hasKey(
                      layout.queuedPayload(USER_A, backlog.jobId()).redisKey(),
                      backlog.jobId().toString()))
          .isFalse();

      var replacementActive = job(USER_B, REQUEST_A, Instant.parse("2026-08-21T10:04:02Z"));
      var replacement = job(USER_B, REQUEST_B, Instant.parse("2026-08-21T10:04:03Z"));
      assertThat(coordinator.register(replacementActive).action()).isEqualTo(Action.START_NOW);
      assertThat(coordinator.register(replacement).action()).isEqualTo(Action.QUEUE_ONLY);
      var cleanupReplacement =
          coordinator.cleanupAfterRegistrationFailure(replacementActive.jobId());
      assertThat(cleanupReplacement.jobsToStart())
          .extracting(HistoricalTracksJobSubmission::jobId)
          .containsExactly(replacement.jobId());
      assertThat(redis.opsForValue().get(layout.activeRequestKey(USER_B)))
          .isEqualTo(REQUEST_B.toString());
      assertThat(redis.opsForSet().members(layout.activeRunningKey(USER_B)))
          .containsExactly(replacement.jobId().toString());
      assertThat(redis.hasKey(layout.replacementRequestKey(USER_B))).isFalse();
      assertThat(redis.hasKey(layout.replacementJobsKey(USER_B))).isFalse();
      assertThat(
              redis
                  .opsForHash()
                  .hasKey(
                      layout.queuedPayload(USER_B, replacement.jobId()).redisKey(),
                      replacement.jobId().toString()))
          .isFalse();

      var resetUser = UUID.fromString("550e8400-e29b-41d4-a716-446655449155");
      var resetJob = job(resetUser, REQUEST_A, Instant.parse("2026-08-21T10:04:04Z"));
      assertThat(coordinator.register(resetJob).action()).isEqualTo(Action.START_NOW);
      assertThat(coordinator.cleanupAfterRegistrationFailure(resetJob.jobId()).jobsToStart())
          .isEmpty();
      assertThat(redis.hasKey(layout.activeRequestKey(resetUser))).isFalse();
      assertThat(redis.hasKey(layout.activeRunningKey(resetUser))).isFalse();
      assertThat(redis.hasKey(layout.activeBacklogKey(resetUser))).isFalse();
      assertThat(redis.hasKey(layout.replacementRequestKey(resetUser))).isFalse();
      assertThat(redis.hasKey(layout.replacementJobsKey(resetUser))).isFalse();
      assertThat(redis.hasKey(layout.jobMeta(resetJob.jobId()))).isFalse();
      assertThat(redis.hasKey(layout.queuedPayload(resetUser, resetJob.jobId()).redisKey()))
          .isFalse();
    }
  }

  @Test
  void clusterPayloadHash_prunesMalformedAndExpiredNeighborsAndHonorsExpiredDeadlines() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var redis = cluster.stringTemplate();
      var properties = properties();
      properties.setMaxConcurrentSubjectsPerJob(1);
      var coordinator = coordinator(redis, properties, layout);

      var active = job(USER_A, REQUEST_A, Instant.parse("2026-08-21T10:05:00Z"));
      assertThat(coordinator.register(active).action()).isEqualTo(Action.START_NOW);
      var payloadKey = layout.queuedPayload(USER_A, active.jobId()).redisKey();
      var expiredId = UUID.fromString("550e8400-e29b-41d4-a716-446655449156").toString();
      var malformedId = UUID.fromString("550e8400-e29b-41d4-a716-446655449157").toString();
      var validId = UUID.fromString("550e8400-e29b-41d4-a716-446655449158").toString();
      redis.opsForHash().put(payloadKey, expiredId, "expired-neighbor");
      redis.opsForHash().put(payloadKey, expiredId + ":expiresAt", "1");
      redis.opsForHash().put(payloadKey, malformedId, "malformed-neighbor");
      redis.opsForHash().put(payloadKey, malformedId + ":expiresAt", "not-a-deadline");
      redis.opsForHash().put(payloadKey, validId, "valid-neighbor");
      redis
          .opsForHash()
          .put(
              payloadKey,
              validId + ":expiresAt",
              Long.toString(System.currentTimeMillis() + 60000));

      var queued = job(USER_A, REQUEST_A, Instant.parse("2026-08-21T10:05:01Z"));
      assertThat(coordinator.register(queued).action()).isEqualTo(Action.QUEUE_ONLY);
      assertThat(redis.opsForHash().hasKey(payloadKey, expiredId)).isFalse();
      assertThat(redis.opsForHash().hasKey(payloadKey, expiredId + ":expiresAt")).isFalse();
      assertThat(redis.opsForHash().hasKey(payloadKey, malformedId)).isFalse();
      assertThat(redis.opsForHash().hasKey(payloadKey, malformedId + ":expiresAt")).isFalse();
      assertThat(redis.opsForHash().get(payloadKey, validId)).isEqualTo("valid-neighbor");
      assertThat(redis.opsForHash().get(payloadKey, validId + ":expiresAt")).isNotNull();

      var deadlineProperties = properties();
      deadlineProperties.setMaxConcurrentSubjectsPerJob(1);
      var deadlineCoordinator = coordinator(redis, deadlineProperties, layout);
      var deadlineUser = UUID.fromString("550e8400-e29b-41d4-a716-446655449159");
      var deadlineActive = job(deadlineUser, REQUEST_A, Instant.parse("2026-08-21T10:05:02Z"));
      var expiringQueued = job(deadlineUser, REQUEST_A, Instant.parse("2026-08-21T10:05:03Z"));
      assertThat(deadlineCoordinator.register(deadlineActive).action()).isEqualTo(Action.START_NOW);
      assertThat(deadlineCoordinator.register(expiringQueued).action())
          .isEqualTo(Action.QUEUE_ONLY);
      var expiringPayload = layout.queuedPayload(deadlineUser, expiringQueued.jobId());
      var originalDeadline =
          Long.parseLong(
              (String)
                  redis
                      .opsForHash()
                      .get(expiringPayload.redisKey(), expiringPayload.deadlineField()));
      // Give the older payload a distinct future deadline so a sibling write cannot silently
      // refresh it, even when both registrations happen within the same millisecond.
      var preservedDeadline = Long.toString(originalDeadline - Duration.ofMinutes(1).toMillis());
      redis
          .opsForHash()
          .put(expiringPayload.redisKey(), expiringPayload.deadlineField(), preservedDeadline);
      var refreshQueued = job(deadlineUser, REQUEST_A, Instant.parse("2026-08-21T10:05:04Z"));
      assertThat(deadlineCoordinator.register(refreshQueued).action()).isEqualTo(Action.QUEUE_ONLY);
      assertThat(
              redis.opsForHash().get(expiringPayload.redisKey(), expiringPayload.deadlineField()))
          .isEqualTo(preservedDeadline);
      assertThat(redis.hasKey(expiringPayload.redisKey())).isTrue();
      assertThat(redis.getExpire(expiringPayload.redisKey(), TimeUnit.MILLISECONDS))
          .isGreaterThan(0L);
      // Mark only the older payload logically expired; the HASH and fresh sibling retain their TTL.
      redis.opsForHash().put(expiringPayload.redisKey(), expiringPayload.deadlineField(), "1");

      var expiredDecision = deadlineCoordinator.recordTerminal(deadlineActive.jobId());
      assertThat(expiredDecision.jobsToCancel())
          .extracting(cancellation -> cancellation.jobId())
          .containsExactly(expiringQueued.jobId());
      assertThat(expiredDecision.jobsToStart())
          .extracting(HistoricalTracksJobSubmission::jobId)
          .containsExactly(refreshQueued.jobId());
      assertThat(
              redis
                  .opsForHash()
                  .hasKey(expiringPayload.redisKey(), expiringQueued.jobId().toString()))
          .isFalse();
      assertThat(
              redis
                  .opsForHash()
                  .hasKey(expiringPayload.redisKey(), refreshQueued.jobId().toString()))
          .isFalse();
    }
  }

  @Test
  void clusterAdmission_handlesMissingMalformedOwnerInvalidJsonAndScriptFailure() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var redis = cluster.stringTemplate();
      var properties = properties();
      properties.setMaxConcurrentSubjectsPerJob(1);
      var coordinator = coordinator(redis, properties, layout);

      var missingOwnerJob =
          job(
              UUID.fromString("550e8400-e29b-41d4-a716-446655449160"),
              REQUEST_A,
              Instant.parse("2026-08-21T10:06:00Z"));
      var missingDecision = coordinator.recordTerminal(missingOwnerJob.jobId());
      assertThat(missingDecision.action()).isEqualTo(Action.NOOP);
      assertThat(missingDecision.jobsToStart()).isEmpty();
      assertThat(missingDecision.jobsToCancel()).isEmpty();

      var malformedOwnerJob =
          job(
              UUID.fromString("550e8400-e29b-41d4-a716-446655449161"),
              REQUEST_A,
              Instant.parse("2026-08-21T10:06:01Z"));
      redis.opsForHash().put(layout.jobMeta(malformedOwnerJob.jobId()), "userId", "not-a-uuid");
      assertThat(coordinator.recordTerminal(malformedOwnerJob.jobId()).action())
          .isEqualTo(Action.NOOP);
      assertThat(redis.hasKey(layout.jobMeta(malformedOwnerJob.jobId()))).isFalse();

      var active = job(USER_A, REQUEST_A, Instant.parse("2026-08-21T10:06:02Z"));
      var invalid = job(USER_A, REQUEST_B, Instant.parse("2026-08-21T10:06:03Z"));
      assertThat(coordinator.register(active).action()).isEqualTo(Action.START_NOW);
      assertThat(coordinator.register(invalid).action()).isEqualTo(Action.QUEUE_ONLY);
      var invalidPayload = layout.queuedPayload(USER_A, invalid.jobId());
      redis
          .opsForHash()
          .put(invalidPayload.redisKey(), invalidPayload.payloadField(), "{broken-json");
      var invalidDecision = coordinator.recordTerminal(active.jobId());
      assertThat(invalidDecision.jobsToStart()).isEmpty();
      assertThat(invalidDecision.jobsToCancel())
          .extracting(cancellation -> cancellation.jobId())
          .containsExactly(invalid.jobId());
      assertThat(
              redis.opsForHash().hasKey(invalidPayload.redisKey(), invalidPayload.payloadField()))
          .isFalse();

      var failingProperties = properties();
      failingProperties.setAdmissionTtl(Duration.ZERO);
      var failingCoordinator = coordinator(redis, failingProperties, layout);
      var failedRegistration =
          job(
              UUID.fromString("550e8400-e29b-41d4-a716-446655449162"),
              REQUEST_A,
              Instant.parse("2026-08-21T10:06:04Z"));
      assertThatThrownBy(() -> failingCoordinator.register(failedRegistration))
          .hasStackTraceContaining("invalid admission TTL");
      assertThat(redis.hasKey(layout.activeRequestKey(failedRegistration.requestedBy()))).isFalse();
      assertThat(redis.hasKey(layout.activeRunningKey(failedRegistration.requestedBy()))).isFalse();
      assertThat(redis.hasKey(layout.jobMeta(failedRegistration.jobId()))).isFalse();
      assertThat(
              redis.hasKey(
                  layout
                      .queuedPayload(failedRegistration.requestedBy(), failedRegistration.jobId())
                      .redisKey()))
          .isFalse();
    }
  }

  @Test
  void clusterAdmission_raceHasOneStartOneQueuedAndAConcretePromotion() throws Exception {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var redis = cluster.stringTemplate();
      var properties = properties();
      properties.setMaxConcurrentSubjectsPerJob(1);
      var firstCoordinator = coordinator(redis, properties, layout);
      var secondCoordinator = coordinator(redis, properties, layout);
      var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655449163");
      var first = job(userId, REQUEST_A, Instant.parse("2026-08-21T10:07:00Z"));
      var second = job(userId, REQUEST_B, Instant.parse("2026-08-21T10:07:01Z"));
      var gate = new CountDownLatch(1);

      HistoricalJobAdmissionDecision firstDecision;
      HistoricalJobAdmissionDecision secondDecision;
      try (var pool = Executors.newFixedThreadPool(2)) {
        var firstFuture =
            pool.submit(
                () -> {
                  gate.await(2, TimeUnit.SECONDS);
                  return firstCoordinator.register(first);
                });
        var secondFuture =
            pool.submit(
                () -> {
                  gate.await(2, TimeUnit.SECONDS);
                  return secondCoordinator.register(second);
                });
        gate.countDown();
        firstDecision = firstFuture.get(2, TimeUnit.SECONDS);
        secondDecision = secondFuture.get(2, TimeUnit.SECONDS);
      }

      var decisions = List.of(firstDecision, secondDecision);
      assertThat(decisions.stream().filter(d -> d.action() == Action.START_NOW).count())
          .isEqualTo(1L);
      assertThat(decisions.stream().filter(d -> d.action() == Action.QUEUE_ONLY).count())
          .isEqualTo(1L);
      var started = firstDecision.action() == Action.START_NOW ? first : second;
      var queued = firstDecision.action() == Action.QUEUE_ONLY ? first : second;
      var queuedDecision =
          firstDecision.action() == Action.QUEUE_ONLY ? firstDecision : secondDecision;
      assertThat(queuedDecision.queueRole()).isEqualTo("QUEUED_REPLACEMENT");
      var queuedPayload = layout.queuedPayload(userId, queued.jobId());
      assertThat(redis.opsForHash().hasKey(queuedPayload.redisKey(), queuedPayload.payloadField()))
          .isTrue();
      assertThat(redis.opsForSet().members(layout.activeRunningKey(userId)))
          .containsExactly(started.jobId().toString());

      var promotion = firstCoordinator.recordTerminal(started.jobId());
      assertThat(promotion.jobsToStart())
          .extracting(HistoricalTracksJobSubmission::jobId)
          .containsExactly(queued.jobId());
      assertThat(redis.opsForHash().hasKey(queuedPayload.redisKey(), queuedPayload.payloadField()))
          .isFalse();
    }
  }

  @Test
  void clusterScripts_preflightEveryDeclaredWrongTypePositionBeforeMutation() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var redis = cluster.stringTemplate();
      var properties = properties();
      var coordinator = coordinator(redis, properties, layout);
      var scripts = new ProcessorHistoricalRedisScripts();

      for (int wrongPosition = 0; wrongPosition < 7; wrongPosition++) {
        var userId = UUID.randomUUID();
        var job = job(userId, REQUEST_A, Instant.parse("2026-08-21T10:08:00Z"));
        var registerKeys =
            List.of(
                layout.activeRequestKey(userId),
                layout.activeRunningKey(userId),
                layout.activeBacklogKey(userId),
                layout.replacementRequestKey(userId),
                layout.replacementJobsKey(userId),
                layout.jobMeta(job.jobId()),
                layout.queuedPayload(userId, job.jobId()).redisKey());
        seedAdmissionKeys(redis, registerKeys, job.jobId());
        setWrongType(redis, registerKeys.get(wrongPosition), wrongPosition);
        assertThatThrownBy(() -> coordinator.register(job)).hasStackTraceContaining("WRONGTYPE");
        assertSeededExcept(redis, registerKeys, job.jobId(), wrongPosition);
      }

      for (int wrongPosition = 0; wrongPosition < 7; wrongPosition++) {
        var userId = UUID.randomUUID();
        var jobId = UUID.randomUUID();
        var cleanupKeys =
            List.of(
                layout.activeRequestKey(userId),
                layout.activeRunningKey(userId),
                layout.activeBacklogKey(userId),
                layout.replacementRequestKey(userId),
                layout.replacementJobsKey(userId),
                layout.queuedPayloadKey(userId, jobId.toString()),
                layout.jobMeta(jobId));
        seedAdmissionKeys(redis, cleanupKeys, jobId);
        setWrongType(redis, cleanupKeys.get(wrongPosition), wrongPosition);
        assertThatThrownBy(
                () ->
                    redis.execute(
                        scripts.admissionCleanupStateCluster(),
                        cleanupKeys,
                        jobId.toString(),
                        "3600000",
                        "1"))
            .hasStackTraceContaining("WRONGTYPE");
        assertSeededExcept(redis, cleanupKeys, jobId, wrongPosition);
      }

      var readUser = UUID.randomUUID();
      var readJobId = UUID.randomUUID();
      var readKey = layout.queuedPayloadKey(readUser, readJobId.toString());
      redis.opsForValue().set(readKey, "wrong-payload-type");
      assertThatThrownBy(
              () ->
                  redis.execute(
                      scripts.admissionReadQueuedPayloadCluster(),
                      List.of(readKey),
                      readJobId.toString()))
          .hasStackTraceContaining("WRONGTYPE");
      assertThat(redis.opsForValue().get(readKey)).isEqualTo("wrong-payload-type");
    }
  }

  private static RedisHistoricalJobAdmissionGateway coordinator(
      StringRedisTemplate redis,
      HistoricalExecutionProperties properties,
      HistoricalJobControlKeyLayout layout) {
    return RedisHistoricalJobAdmissionGateway.create(
        redis, new ObjectMapper(), properties, new ProcessorHistoricalRedisScripts(), layout);
  }

  private static void setWrongType(StringRedisTemplate redis, String key, int position) {
    assertThat(redis.delete(key)).isTrue();
    assertThat(redis.hasKey(key)).isFalse();
    switch (position) {
      case 0 -> redis.opsForList().rightPush(key, "wrong");
      case 1, 2, 3, 4, 5, 6 -> redis.opsForValue().set(key, "wrong");
      default -> throw new IllegalArgumentException("Unexpected key position " + position);
    }
  }

  private static void seedAdmissionKeys(StringRedisTemplate redis, List<String> keys, UUID jobId) {
    redis.opsForValue().set(keys.get(0), "baseline-request");
    redis.opsForSet().add(keys.get(1), "baseline-running");
    redis.opsForList().rightPush(keys.get(2), "baseline-backlog");
    redis.opsForHash().put(keys.get(3), "requestId", "baseline-replacement");
    redis.opsForHash().put(keys.get(3), "requestedAt", "baseline-time");
    redis.opsForList().rightPush(keys.get(4), "baseline-replacement-job");
    redis.opsForHash().put(keys.get(5), "jobId", jobId.toString());
    redis.opsForHash().put(keys.get(5), "requestId", "baseline-meta-request");
    redis.opsForHash().put(keys.get(5), "requestedAt", "baseline-meta-time");
    redis.opsForHash().put(keys.get(5), "userId", "baseline-meta-user");
    redis.opsForHash().put(keys.get(6), jobId.toString(), "baseline-payload");
    redis
        .opsForHash()
        .put(keys.get(6), jobId + ":expiresAt", Long.toString(System.currentTimeMillis() + 60000));
  }

  private static void assertSeededExcept(
      StringRedisTemplate redis, List<String> keys, UUID jobId, int wrongPosition) {
    var expectedTypes =
        List.of(
            DataType.STRING,
            DataType.SET,
            DataType.LIST,
            DataType.HASH,
            DataType.LIST,
            DataType.HASH,
            DataType.HASH);
    var wrongTypes =
        List.of(
            DataType.LIST,
            DataType.STRING,
            DataType.STRING,
            DataType.STRING,
            DataType.STRING,
            DataType.STRING,
            DataType.STRING);
    for (int index = 0; index < keys.size(); index++) {
      assertThat(redis.type(keys.get(index)))
          .isEqualTo(index == wrongPosition ? wrongTypes.get(index) : expectedTypes.get(index));
      if (index == wrongPosition) {
        continue;
      }
      switch (index) {
        case 0 ->
            assertThat(redis.opsForValue().get(keys.get(index))).isEqualTo("baseline-request");
        case 1 ->
            assertThat(redis.opsForSet().members(keys.get(index)))
                .containsExactly("baseline-running");
        case 2 ->
            assertThat(redis.opsForList().range(keys.get(index), 0, -1))
                .containsExactly("baseline-backlog");
        case 3 ->
            assertThat(redis.opsForHash().entries(keys.get(index)))
                .containsEntry("requestId", "baseline-replacement")
                .containsEntry("requestedAt", "baseline-time");
        case 4 ->
            assertThat(redis.opsForList().range(keys.get(index), 0, -1))
                .containsExactly("baseline-replacement-job");
        case 5 ->
            assertThat(redis.opsForHash().entries(keys.get(index)))
                .containsEntry("jobId", jobId.toString())
                .containsEntry("requestId", "baseline-meta-request")
                .containsEntry("requestedAt", "baseline-meta-time")
                .containsEntry("userId", "baseline-meta-user");
        case 6 ->
            assertThat(redis.opsForHash().get(keys.get(index), jobId.toString()))
                .isEqualTo("baseline-payload");
        default -> throw new IllegalArgumentException("Unexpected key position " + index);
      }
    }
  }

  private static HistoricalExecutionProperties properties() {
    var properties = new HistoricalExecutionProperties();
    properties.setJobStateTtl(Duration.ofHours(24));
    properties.setAdmissionTtl(Duration.ofHours(1));
    properties.setMaxConcurrentSubjectsPerJob(2);
    return properties;
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
