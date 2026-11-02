// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisHistoricalTracksAdmissionGatewayIT {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
  private static final UUID OTHER_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440999");

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private RedisHistoricalTracksAdmissionGateway gateway;

  @BeforeEach
  void setUp() {
    connectionFactory =
        new LettuceConnectionFactory(LocalRedisSupport.host(), LocalRedisSupport.port());
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    gateway =
        new RedisHistoricalTracksAdmissionGateway(redisTemplate, new CoreHistoricalRedisScripts());
  }

  @AfterEach
  void tearDown() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    connectionFactory.destroy();
  }

  @Test
  void requestCancellationForActiveJobs_updatesOnlySameUserHashes() {
    var replacementRequestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440778");
    var userJobId = UUID.randomUUID();
    var completedJobId = UUID.randomUUID();
    var otherUserJobId = UUID.randomUUID();
    var userKey = HistoricalJobControlKeyLayout.STANDALONE.jobState(userJobId);
    var otherUserKey = HistoricalJobControlKeyLayout.STANDALONE.jobState(otherUserJobId);
    var completedUserKey = HistoricalJobControlKeyLayout.STANDALONE.jobState(completedJobId);
    var userUpdatedAtBefore = "2026-05-21T11:00:00Z";
    var otherUpdatedAtBefore = "2026-05-21T11:10:00Z";
    var completedUpdatedAtBefore = "2026-05-21T11:20:00Z";
    var otherFailureReasonBefore = "existing reason";
    var completedFailureReasonBefore = "completed reason";

    redisTemplate
        .opsForHash()
        .putAll(
            userKey,
            Map.of(
                "requestedBy",
                USER_ID.toString(),
                "status",
                "RUNNING",
                "stage",
                HistoricalJobStage.EXECUTION_RUNNING.name(),
                "failureReason",
                "before",
                "updatedAt",
                userUpdatedAtBefore));
    redisTemplate
        .opsForHash()
        .putAll(
            otherUserKey,
            Map.of(
                "requestedBy",
                OTHER_USER_ID.toString(),
                "status",
                "RUNNING",
                "stage",
                HistoricalJobStage.EXECUTION_RUNNING.name(),
                "failureReason",
                otherFailureReasonBefore,
                "updatedAt",
                otherUpdatedAtBefore));
    redisTemplate
        .opsForHash()
        .putAll(
            completedUserKey,
            Map.of(
                "requestedBy",
                USER_ID.toString(),
                "status",
                "COMPLETED",
                "stage",
                HistoricalJobStage.EXECUTION_COMPLETED.name(),
                "failureReason",
                completedFailureReasonBefore,
                "updatedAt",
                completedUpdatedAtBefore));
    redisTemplate
        .opsForZSet()
        .add(
            HistoricalJobControlKeyLayout.STANDALONE.userJobIndex(USER_ID),
            userJobId.toString(),
            1.0);
    redisTemplate
        .opsForZSet()
        .add(
            HistoricalJobControlKeyLayout.STANDALONE.userJobIndex(USER_ID),
            completedJobId.toString(),
            2.0);
    redisTemplate
        .opsForZSet()
        .add(
            HistoricalJobControlKeyLayout.STANDALONE.userJobIndex(USER_ID),
            otherUserJobId.toString(),
            3.0);

    int changedJobs = gateway.requestCancellationForActiveJobs(USER_ID, replacementRequestId);

    var expectedReason = "Superseded by request " + replacementRequestId;
    assertEquals(1, changedJobs);
    assertEquals(
        HistoricalJobStage.CANCELLATION_REQUESTED.name(),
        redisTemplate.opsForHash().get(userKey, "stage"));
    assertEquals(expectedReason, redisTemplate.opsForHash().get(userKey, "failureReason"));
    assertNotEquals(userUpdatedAtBefore, redisTemplate.opsForHash().get(userKey, "updatedAt"));
    Instant.parse(redisTemplate.opsForHash().get(userKey, "updatedAt").toString());

    assertEquals(
        HistoricalJobStage.EXECUTION_RUNNING.name(),
        redisTemplate.opsForHash().get(otherUserKey, "stage"));
    assertEquals(
        otherFailureReasonBefore, redisTemplate.opsForHash().get(otherUserKey, "failureReason"));
    assertEquals(otherUpdatedAtBefore, redisTemplate.opsForHash().get(otherUserKey, "updatedAt"));

    assertEquals("COMPLETED", redisTemplate.opsForHash().get(completedUserKey, "status"));
    assertEquals(
        HistoricalJobStage.EXECUTION_COMPLETED.name(),
        redisTemplate.opsForHash().get(completedUserKey, "stage"));
    assertEquals(
        completedFailureReasonBefore,
        redisTemplate.opsForHash().get(completedUserKey, "failureReason"));
    assertEquals(
        completedUpdatedAtBefore, redisTemplate.opsForHash().get(completedUserKey, "updatedAt"));
  }

  @Test
  void requestCancellationForActiveJobs_doesNotMutateTerminalStatuses() {
    var replacementRequestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440779");
    var completedJobId = UUID.randomUUID();
    var failedJobId = UUID.randomUUID();
    var cancelledJobId = UUID.randomUUID();
    var completedKey = HistoricalJobControlKeyLayout.STANDALONE.jobState(completedJobId);
    var failedKey = HistoricalJobControlKeyLayout.STANDALONE.jobState(failedJobId);
    var cancelledKey = HistoricalJobControlKeyLayout.STANDALONE.jobState(cancelledJobId);
    var completedUpdatedAt = "2026-05-21T11:00:00Z";
    var failedUpdatedAt = "2026-05-21T11:10:00Z";
    var cancelledUpdatedAt = "2026-05-21T11:20:00Z";

    redisTemplate
        .opsForHash()
        .putAll(
            completedKey,
            Map.of(
                "requestedBy",
                USER_ID.toString(),
                "status",
                "COMPLETED",
                "stage",
                HistoricalJobStage.EXECUTION_COMPLETED.name(),
                "failureReason",
                "",
                "updatedAt",
                completedUpdatedAt));
    redisTemplate
        .opsForHash()
        .putAll(
            failedKey,
            Map.of(
                "requestedBy",
                USER_ID.toString(),
                "status",
                "FAILED",
                "stage",
                HistoricalJobStage.EXECUTION_FAILED_UNKNOWN.name(),
                "failureReason",
                "existing failure",
                "updatedAt",
                failedUpdatedAt));
    redisTemplate
        .opsForHash()
        .putAll(
            cancelledKey,
            Map.of(
                "requestedBy",
                USER_ID.toString(),
                "status",
                "CANCELLED",
                "stage",
                HistoricalJobStage.CANCELLED.name(),
                "failureReason",
                "already cancelled",
                "updatedAt",
                cancelledUpdatedAt));
    redisTemplate
        .opsForZSet()
        .add(
            HistoricalJobControlKeyLayout.STANDALONE.userJobIndex(USER_ID),
            completedJobId.toString(),
            1.0);
    redisTemplate
        .opsForZSet()
        .add(
            HistoricalJobControlKeyLayout.STANDALONE.userJobIndex(USER_ID),
            failedJobId.toString(),
            2.0);
    redisTemplate
        .opsForZSet()
        .add(
            HistoricalJobControlKeyLayout.STANDALONE.userJobIndex(USER_ID),
            cancelledJobId.toString(),
            3.0);

    int changedJobs = gateway.requestCancellationForActiveJobs(USER_ID, replacementRequestId);

    assertEquals(0, changedJobs);
    assertEquals("COMPLETED", redisTemplate.opsForHash().get(completedKey, "status"));
    assertEquals(
        HistoricalJobStage.EXECUTION_COMPLETED.name(),
        redisTemplate.opsForHash().get(completedKey, "stage"));
    assertEquals(completedUpdatedAt, redisTemplate.opsForHash().get(completedKey, "updatedAt"));

    assertEquals("FAILED", redisTemplate.opsForHash().get(failedKey, "status"));
    assertEquals(
        HistoricalJobStage.EXECUTION_FAILED_UNKNOWN.name(),
        redisTemplate.opsForHash().get(failedKey, "stage"));
    assertEquals(failedUpdatedAt, redisTemplate.opsForHash().get(failedKey, "updatedAt"));

    assertEquals("CANCELLED", redisTemplate.opsForHash().get(cancelledKey, "status"));
    assertEquals(
        HistoricalJobStage.CANCELLED.name(), redisTemplate.opsForHash().get(cancelledKey, "stage"));
    assertEquals(cancelledUpdatedAt, redisTemplate.opsForHash().get(cancelledKey, "updatedAt"));
    assertNotEquals(
        "Superseded by request " + replacementRequestId,
        redisTemplate.opsForHash().get(completedKey, "failureReason"));
    assertNotEquals(
        "Superseded by request " + replacementRequestId,
        redisTemplate.opsForHash().get(failedKey, "failureReason"));
    assertNotEquals(
        "Superseded by request " + replacementRequestId,
        redisTemplate.opsForHash().get(cancelledKey, "failureReason"));
  }

  @Test
  void requestCancellationForActiveJobs_removesMissingIndexedMembers() {
    var replacementRequestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440780");
    var missingJobId = UUID.randomUUID();
    var activeJobId = UUID.randomUUID();
    var missingStatusKey = HistoricalJobControlKeyLayout.STANDALONE.jobState(missingJobId);
    var activeKey = HistoricalJobControlKeyLayout.STANDALONE.jobState(activeJobId);
    redisTemplate
        .opsForHash()
        .putAll(
            activeKey,
            Map.of(
                "requestedBy",
                USER_ID.toString(),
                "status",
                "RUNNING",
                "stage",
                HistoricalJobStage.EXECUTION_RUNNING.name(),
                "failureReason",
                "",
                "updatedAt",
                "2026-05-21T11:41:00Z"));
    redisTemplate
        .opsForZSet()
        .add(
            HistoricalJobControlKeyLayout.STANDALONE.userJobIndex(USER_ID),
            missingJobId.toString(),
            1.0);
    redisTemplate
        .opsForZSet()
        .add(
            HistoricalJobControlKeyLayout.STANDALONE.userJobIndex(USER_ID),
            activeJobId.toString(),
            2.0);

    int changedJobs = gateway.requestCancellationForActiveJobs(USER_ID, replacementRequestId);

    assertEquals(1, changedJobs);
    assertEquals(false, redisTemplate.hasKey(missingStatusKey));
    assertEquals(
        HistoricalJobStage.CANCELLATION_REQUESTED.name(),
        redisTemplate.opsForHash().get(activeKey, "stage"));
    assertEquals(
        1L,
        redisTemplate
            .opsForZSet()
            .size(HistoricalJobControlKeyLayout.STANDALONE.userJobIndex(USER_ID)));
  }
}
