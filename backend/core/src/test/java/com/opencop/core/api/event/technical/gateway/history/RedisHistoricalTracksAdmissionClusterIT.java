// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.gateway.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.component.redis.LocalRedisClusterSupport;
import com.opencop.core.sdk.component.redis.RedisClusterCandidateContext;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import io.lettuce.core.cluster.SlotHash;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisHistoricalTracksAdmissionClusterIT {

  private static final UUID USER_A = UUID.fromString("550e8400-e29b-41d4-a716-446655449150");
  private static final UUID USER_B = UUID.fromString("550e8400-e29b-41d4-a716-446655449141");

  @Test
  void requestCancellationForActiveJobs_usesSelectedClusterStateKeysAndPreservesOtherUsers() {
    try (var fixture = LocalRedisClusterSupport.start();
        var cluster = RedisClusterCandidateContext.start(fixture)) {
      var layout = cluster.bean(HistoricalJobControlKeyLayout.class);
      assertThat(layout).isEqualTo(HistoricalJobControlKeyLayout.CLUSTER);
      var redis = cluster.stringTemplate();
      var gateway =
          new RedisHistoricalTracksAdmissionGateway(
              redis, layout, new CoreHistoricalRedisScripts());
      var replacementRequestId = UUID.fromString("550e8400-e29b-41d4-a716-446655449778");
      var userJobId = UUID.randomUUID();
      var completedJobId = UUID.randomUUID();
      var otherJobId = UUID.randomUUID();
      var userJobKey = layout.jobState(userJobId);
      var completedJobKey = layout.jobState(completedJobId);
      var otherJobKey = layout.jobState(otherJobId);
      var userIndexKey = layout.userJobIndex(USER_A);

      assertThat(SlotHash.getSlot(userJobKey)).isEqualTo(SlotHash.getSlot(userIndexKey));
      assertThat(SlotHash.getSlot(userJobKey))
          .isEqualTo(SlotHash.getSlot(layout.jobState(otherJobId)));

      redis
          .opsForHash()
          .putAll(
              userJobKey,
              Map.of(
                  "requestedBy",
                  USER_A.toString(),
                  "status",
                  "RUNNING",
                  "stage",
                  HistoricalJobStage.EXECUTION_RUNNING.name(),
                  "failureReason",
                  "before",
                  "updatedAt",
                  "2026-05-21T11:00:00Z"));
      redis
          .opsForHash()
          .putAll(
              completedJobKey,
              Map.of(
                  "requestedBy",
                  USER_A.toString(),
                  "status",
                  "COMPLETED",
                  "stage",
                  HistoricalJobStage.EXECUTION_COMPLETED.name(),
                  "failureReason",
                  "keep-completed",
                  "updatedAt",
                  "2026-05-21T11:10:00Z"));
      redis
          .opsForHash()
          .putAll(
              otherJobKey,
              Map.of(
                  "requestedBy",
                  USER_B.toString(),
                  "status",
                  "RUNNING",
                  "stage",
                  HistoricalJobStage.EXECUTION_RUNNING.name(),
                  "failureReason",
                  "keep-other",
                  "updatedAt",
                  "2026-05-21T11:20:00Z"));
      redis.opsForZSet().add(userIndexKey, userJobId.toString(), 1.0);
      redis.opsForZSet().add(userIndexKey, completedJobId.toString(), 2.0);
      redis.opsForZSet().add(userIndexKey, "not-a-uuid", 3.0);
      redis.opsForZSet().add(layout.userJobIndex(USER_B), otherJobId.toString(), 1.0);

      int changedJobs = gateway.requestCancellationForActiveJobs(USER_A, replacementRequestId);

      assertThat(changedJobs).isEqualTo(1);
      assertThat(redis.opsForHash().get(userJobKey, "stage"))
          .isEqualTo(HistoricalJobStage.CANCELLATION_REQUESTED.name());
      assertThat(redis.opsForHash().get(userJobKey, "failureReason"))
          .isEqualTo("Superseded by request " + replacementRequestId);
      assertThat(redis.opsForHash().get(completedJobKey, "stage"))
          .isEqualTo(HistoricalJobStage.EXECUTION_COMPLETED.name());
      assertThat(redis.opsForHash().get(otherJobKey, "stage"))
          .isEqualTo(HistoricalJobStage.EXECUTION_RUNNING.name());
      assertThat(redis.opsForZSet().range(userIndexKey, 0, -1))
          .containsExactly(userJobId.toString(), completedJobId.toString());
      assertThat(redis.hasKey(HistoricalJobControlKeyLayout.STANDALONE.jobState(userJobId)))
          .isFalse();
    }
  }
}
