// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.component.redis.LocalRedisClusterSupport;
import com.opencop.core.sdk.component.redis.RedisClusterCandidateContext;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.technical.gateway.history.ProcessorHistoricalRedisScripts;
import com.opencop.processor.technical.gateway.history.RedisHistoricalJobStateGateway;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.lettuce.core.cluster.SlotHash;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Positive cluster characterization: selected cluster state keys now share one slot and the
 * production state gateway can execute its existing six-key creation script.
 */
class RedisHistoricalAdmissionClusterCharacterizationIT {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449150");
  private static final UUID REQUEST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449151");
  private static final UUID JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449152");

  @Test
  void selectedClusterLayout_writesExistingSixKeyStateAtomically() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var redis = cluster.stringTemplate();
      var properties = new HistoricalExecutionProperties();
      properties.setJobStateTtl(Duration.ofHours(24));
      var gateway =
          new RedisHistoricalJobStateGateway(
              redis,
              layout,
              properties,
              Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC),
              new ProcessorHistoricalRedisScripts());
      var job = job();

      assertThat(SlotHash.getSlot(layout.jobState(JOB_ID)))
          .isEqualTo(SlotHash.getSlot(layout.userJobIndex(USER_ID)));
      assertThat(SlotHash.getSlot(layout.userJobIndex(USER_ID)))
          .isEqualTo(SlotHash.getSlot(layout.userRequestIndex(USER_ID)));
      assertThat(gateway.createReceivedIfAbsent(job)).isTrue();
      assertThat(gateway.findByJobId(JOB_ID)).isPresent();
      assertThat(redis.hasKey(HistoricalJobControlKeyLayout.STANDALONE.jobState(JOB_ID))).isFalse();
    }
  }

  @Test
  void selectedClusterLayout_concurrentDuplicateCreationHasOneWinnerAndConsistentIndexes()
      throws Exception {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var redis = cluster.stringTemplate();
      var properties = new HistoricalExecutionProperties();
      properties.setJobStateTtl(Duration.ofHours(24));
      var first = job();
      var second =
          new HistoricalTracksJobSubmission(
              first.jobId(),
              UUID.fromString("550e8400-e29b-41d4-a716-446655449155"),
              first.issuer(),
              first.subjects(),
              first.requestedBy(),
              first.requestedAt(),
              first.requestedFrom(),
              first.requestedTo(),
              first.effectiveFrom(),
              first.effectiveTo(),
              first.resolution(),
              first.plannedChunks(),
              first.filters());
      var firstGateway =
          new RedisHistoricalJobStateGateway(
              redis,
              layout,
              properties,
              Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC),
              new ProcessorHistoricalRedisScripts());
      var secondGateway =
          new RedisHistoricalJobStateGateway(
              redis,
              layout,
              properties,
              Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC),
              new ProcessorHistoricalRedisScripts());
      var gate = new CountDownLatch(1);

      boolean firstCreated;
      boolean secondCreated;
      try (var pool = Executors.newFixedThreadPool(2)) {
        var firstFuture =
            pool.submit(
                () -> {
                  gate.await(2, TimeUnit.SECONDS);
                  return firstGateway.createReceivedIfAbsent(first);
                });
        var secondFuture =
            pool.submit(
                () -> {
                  gate.await(2, TimeUnit.SECONDS);
                  return secondGateway.createReceivedIfAbsent(second);
                });
        gate.countDown();
        firstCreated = firstFuture.get(2, TimeUnit.SECONDS);
        secondCreated = secondFuture.get(2, TimeUnit.SECONDS);
      }

      assertThat(firstCreated ^ secondCreated).isTrue();
      var persisted = firstGateway.findByJobId(first.jobId()).orElseThrow();
      assertThat(persisted.requestId())
          .isEqualTo(firstCreated ? first.requestId() : second.requestId());
      assertThat(persisted.userSequence()).isEqualTo(1L);
      assertThat(persisted.requestSequence()).isEqualTo(1L);
      assertThat(redis.opsForZSet().range(layout.userJobIndex(first.requestedBy()), 0, -1))
          .containsExactly(first.jobId().toString());
      assertThat(
              redis
                  .opsForZSet()
                  .range(
                      layout.userRequestJobIndex(first.requestedBy(), persisted.requestId()),
                      0,
                      -1))
          .containsExactly(first.jobId().toString());
      assertThat(redis.opsForZSet().range(layout.userRequestIndex(first.requestedBy()), 0, -1))
          .containsExactly(persisted.requestId().toString());
      assertThat(redis.opsForValue().get(layout.userJobSequence(first.requestedBy())))
          .isEqualTo("1");
      assertThat(
              redis
                  .opsForValue()
                  .get(layout.userRequestJobSequence(first.requestedBy(), persisted.requestId())))
          .isEqualTo("1");
      assertThat(redis.hasKey(HistoricalJobControlKeyLayout.STANDALONE.jobState(first.jobId())))
          .isFalse();
    }
  }

  private static HistoricalTracksJobSubmission job() {
    return new HistoricalTracksJobSubmission(
        JOB_ID,
        REQUEST_ID,
        JobIssuer.user(USER_ID),
        List.of(
            new HistoricalTracksSubject(
                UUID.fromString("550e8400-e29b-41d4-a716-446655449153"),
                SubjectType.SOURCE,
                UUID.fromString("550e8400-e29b-41d4-a716-446655449154"))),
        USER_ID,
        Instant.parse("2026-08-21T10:00:00Z"),
        Instant.parse("2026-08-21T09:00:00Z"),
        Instant.parse("2026-08-21T10:00:00Z"),
        Instant.parse("2026-08-21T09:00:00Z"),
        Instant.parse("2026-08-21T10:00:00Z"),
        HistoricalResolution.MINUTE_1,
        60,
        List.of());
  }
}
