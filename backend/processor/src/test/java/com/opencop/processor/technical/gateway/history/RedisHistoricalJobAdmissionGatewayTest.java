// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static com.opencop.processor.technical.gateway.history.HistoricalJobAdmissionRedisKeys.activeBacklogKey;
import static com.opencop.processor.technical.gateway.history.HistoricalJobAdmissionRedisKeys.activeRequestKey;
import static com.opencop.processor.technical.gateway.history.HistoricalJobAdmissionRedisKeys.activeRunningKey;
import static com.opencop.processor.technical.gateway.history.HistoricalJobAdmissionRedisKeys.jobMetaKey;
import static com.opencop.processor.technical.gateway.history.HistoricalJobAdmissionRedisKeys.replacementJobsKey;
import static com.opencop.processor.technical.gateway.history.HistoricalJobAdmissionRedisKeys.replacementRequestKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.LayerCopyJobSubmission;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision.Action;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class RedisHistoricalJobAdmissionGatewayTest {
  private static final String PAYLOAD_RESTORE_REASON =
      "Historical job payload could not be restored from Redis";
  private static final String SUPERSEDED_BY_REPLACEMENT_REASON =
      "Historical job superseded by replacement";

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redisTemplate;
  private HistoricalExecutionProperties properties;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    connectionFactory =
        new LettuceConnectionFactory(LocalRedisSupport.host(), LocalRedisSupport.port());
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    properties = new HistoricalExecutionProperties();
    properties.setJobStateTtl(Duration.ofHours(24));
    properties.setMaxConcurrentSubjectsPerJob(2);
    objectMapper = new ObjectMapper();
  }

  @AfterEach
  void tearDown() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    connectionFactory.destroy();
  }

  @Test
  void register_startsImmediatelyForIdleUser() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var job =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));

    var decision = coordinator().register(job);

    assertEquals(Action.START_NOW, decision.action());
  }

  @Test
  void register_standaloneWritesOwnerBeforeSerializationFailure() throws Exception {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440014");
    var job =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var failingMapper = mock(ObjectMapper.class);
    when(failingMapper.writeValueAsString(job)).thenThrow(new RuntimeException("serialize"));
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, failingMapper, properties, new ProcessorHistoricalRedisScripts());

    assertThrows(IllegalStateException.class, () -> localCoordinator.register(job));

    assertEquals(
        user.toString(), redisTemplate.opsForHash().get(jobMetaKey(job.jobId()), "userId"));
    assertFalse(redisTemplate.hasKey(activeRequestKey(user)));
  }

  @Test
  void register_standaloneRetainsOwnerWhenAdmissionScriptRejectsWrongType() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440015");
    var job =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    redisTemplate.opsForValue().set(activeRunningKey(user), "wrong-type");

    assertThrows(RuntimeException.class, () -> localCoordinator.register(job));

    assertEquals(
        user.toString(), redisTemplate.opsForHash().get(jobMetaKey(job.jobId()), "userId"));
    assertEquals(
        job.requestId().toString(), redisTemplate.opsForValue().get(activeRequestKey(user)));
    assertEquals("wrong-type", redisTemplate.opsForValue().get(activeRunningKey(user)));
  }

  @Test
  void register_usesAdmissionTtlForAdmissionKeysInsteadOfJobStateTtl() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    localProperties.setAdmissionTtl(Duration.ofMinutes(45));
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var job =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));

    localCoordinator.register(job);

    long activeRequestTtlSeconds =
        redisTemplate.getExpire(activeRequestKey(user), TimeUnit.SECONDS);
    long activeRunningTtlSeconds =
        redisTemplate.getExpire(activeRunningKey(user), TimeUnit.SECONDS);
    long jobMetaTtlSeconds = redisTemplate.getExpire(jobMetaKey(job.jobId()), TimeUnit.SECONDS);
    assertTrue(activeRequestTtlSeconds > 2_600L && activeRequestTtlSeconds <= 2_700L);
    assertTrue(activeRunningTtlSeconds > 2_600L && activeRunningTtlSeconds <= 2_700L);
    assertTrue(jobMetaTtlSeconds > 2_600L && jobMetaTtlSeconds <= 2_700L);
  }

  @Test
  void recordExecutionExit_promotesBacklogJob() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    localProperties.setMaxConcurrentSubjectsPerJob(1);
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var request = UUID.randomUUID();
    var active = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var backlog = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    assertEquals(Action.START_NOW, localCoordinator.register(active).action());
    assertEquals(Action.QUEUE_ONLY, localCoordinator.register(backlog).action());

    var decision = localCoordinator.recordExecutionExit(active.jobId());

    assertEquals(1, decision.jobsToStart().size());
    assertEquals(backlog.jobId(), decision.jobsToStart().getFirst().jobId());
  }

  @Test
  void register_startsFirstUnifiedPayloadAndQueuesSecondForRestorationOnExecutionExit() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    localProperties.setMaxConcurrentSubjectsPerJob(1);
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440020");
    var request = UUID.fromString("550e8400-e29b-41d4-a716-446655440021");

    var first =
        job(
            request,
            user,
            List.of(
                subject(SubjectType.SOURCE, UUID.randomUUID()),
                subject(SubjectType.LAYER, UUID.randomUUID())),
            Instant.parse("2026-04-07T10:00:00Z"));
    var second =
        job(
            request,
            user,
            List.of(subject(SubjectType.LAYER, UUID.randomUUID())),
            Instant.parse("2026-04-07T10:00:01Z"));

    assertEquals(Action.START_NOW, localCoordinator.register(first).action());
    assertEquals(Action.QUEUE_ONLY, localCoordinator.register(second).action());
    assertTrue(redisTemplate.hasKey(payloadKey(second.jobId())));

    var decision = localCoordinator.recordExecutionExit(first.jobId());
    var promoted = decision.jobsToStart().getFirst();
    assertEquals(1, decision.jobsToStart().size());
    assertEquals(second.jobId(), promoted.jobId());
    assertEquals(2, first.subjects().size());
    assertEquals(1, promoted.subjects().size());
    assertEquals(
        second.subjects().getFirst().subjectType(), promoted.subjects().getFirst().subjectType());
    assertEquals(
        second.subjects().getFirst().subjectId(), promoted.subjects().getFirst().subjectId());
    assertEquals(second.subjects().getFirst().jobId(), promoted.subjects().getFirst().jobId());
    assertFalse(redisTemplate.hasKey(payloadKey(second.jobId())));
  }

  @Test
  void register_doesNotPersistHistoricalJobStateForRequest() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440025");
    var request = UUID.fromString("550e8400-e29b-41d4-a716-446655440026");
    var unified =
        job(
            request,
            user,
            List.of(subject(SubjectType.SOURCE, UUID.randomUUID())),
            Instant.parse("2026-04-07T10:05:00Z"));

    coordinator().register(unified);

    assertFalse(
        redisTemplate.keys("opencop:history:job:*").stream()
            .anyMatch(key -> key.startsWith("opencop:history:job:")));
  }

  @Test
  void recordTerminal_promotesReplacementOnLastTerminal() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    localProperties.setMaxConcurrentSubjectsPerJob(1);
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var active = job(requestA, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var replacement = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    localCoordinator.register(active);
    localCoordinator.register(replacement);

    var decision = localCoordinator.recordTerminal(active.jobId());

    assertEquals(1, decision.jobsToStart().size());
    assertEquals(replacement.jobId(), decision.jobsToStart().getFirst().jobId());
  }

  @Test
  void recoverTerminalActiveJobs_releaseFailurePropagatesAfterScriptPartialEffect() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    localProperties.setMaxConcurrentSubjectsPerJob(1);
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440016");
    var request = UUID.randomUUID();
    var active = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var replacement = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    localCoordinator.register(active);
    localCoordinator.register(replacement);
    var stateGateway = mock(HistoricalJobStateGateway.class);
    var activeState =
        HistoricalJobState.received(
                active, Instant.parse("2026-04-07T10:00:02Z"), Duration.ofHours(24))
            .completed(Instant.parse("2026-04-07T10:00:03Z"), Duration.ofHours(24));
    when(stateGateway.findByJobId(active.jobId()))
        .thenAnswer(
            ignored -> {
              redisTemplate.opsForValue().set(activeBacklogKey(user), "wrong-backlog-type");
              return Optional.of(activeState);
            });

    assertThrows(
        RuntimeException.class, () -> localCoordinator.recoverTerminalActiveJobs(stateGateway));

    assertFalse(
        redisTemplate.opsForSet().isMember(activeRunningKey(user), active.jobId().toString()));
    assertEquals("wrong-backlog-type", redisTemplate.opsForValue().get(activeBacklogKey(user)));
    assertEquals(request.toString(), redisTemplate.opsForValue().get(activeRequestKey(user)));
    assertTrue(redisTemplate.hasKey(payloadKey(replacement.jobId())));
  }

  @Test
  void cleanupAfterRegistrationFailure_removesBacklogJob() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    localProperties.setMaxConcurrentSubjectsPerJob(1);
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var request = UUID.randomUUID();
    var active = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var backlog = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    localCoordinator.register(active);
    localCoordinator.register(backlog);

    var decision = localCoordinator.cleanupAfterRegistrationFailure(backlog.jobId());

    assertTrue(decision.jobsToStart().isEmpty());
    assertFalse(
        redisTemplate
            .opsForList()
            .range(activeBacklogKey(user), 0, -1)
            .contains(backlog.jobId().toString()));
  }

  @Test
  void register_raceAcrossReplicas_onlyOneStartsAndTerminalPromotesQueuedReplacement()
      throws Exception {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var a1 = job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var b1 = job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var c1 = coordinator();
    var c2 = coordinator();
    var gate = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var f1 =
          pool.submit(
              () -> {
                gate.await();
                return c1.register(a1);
              });
      var f2 =
          pool.submit(
              () -> {
                gate.await();
                return c2.register(b1);
              });
      gate.countDown();
      var d1 = f1.get();
      var d2 = f2.get();

      int startNowCount =
          (d1.action() == Action.START_NOW ? 1 : 0) + (d2.action() == Action.START_NOW ? 1 : 0);
      int queueOnlyCount =
          (d1.action() == Action.QUEUE_ONLY ? 1 : 0) + (d2.action() == Action.QUEUE_ONLY ? 1 : 0);
      assertEquals(1, startNowCount);
      assertEquals(1, queueOnlyCount);
      var queuedDecision = d1.action() == Action.QUEUE_ONLY ? d1 : d2;
      var startedJob = d1.action() == Action.START_NOW ? a1 : b1;
      var queuedJob = d1.action() == Action.QUEUE_ONLY ? a1 : b1;
      assertEquals("QUEUED_REPLACEMENT", queuedDecision.queueRole());

      var terminal = coordinator().recordTerminal(startedJob.jobId());
      assertEquals(1, terminal.jobsToStart().size());
      assertEquals(queuedJob.jobId(), terminal.jobsToStart().getFirst().jobId());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void register_backlogRespectsParallelismAndExecutionExitPromotesFromRedisPayload() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var request = UUID.fromString("550e8400-e29b-41d4-a716-446655440011");
    var a1 = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var a2 = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var a3 = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:02Z"));
    var c1 = coordinator();
    var c2 = coordinator();

    assertEquals(Action.START_NOW, c1.register(a1).action());
    assertEquals(Action.START_NOW, c2.register(a2).action());
    var third = c1.register(a3);
    assertEquals(Action.QUEUE_ONLY, third.action());
    assertEquals("ACTIVE_REQUEST_BACKLOG", third.queueRole());
    assertTrue(redisTemplate.hasKey(payloadKey(a3.jobId())));

    var promoted = c2.recordExecutionExit(a1.jobId());
    assertEquals(1, promoted.jobsToStart().size());
    var restored = promoted.jobsToStart().getFirst();
    assertEquals(a3.jobId(), restored.jobId());
    assertEquals(a3.requestId(), restored.requestId());
    assertEquals(a3.requestedBy(), restored.requestedBy());
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(a3.jobId()))));
  }

  @Test
  void
      register_mixedLayerAndSourceSubjectsRespectMaxConcurrentRequestsAndPreserveQueuedSubjectType() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440012");
    var request = UUID.fromString("550e8400-e29b-41d4-a716-446655440013");
    var l1 =
        job(
            request,
            user,
            SubjectType.LAYER,
            UUID.randomUUID(),
            Instant.parse("2026-04-07T10:00:00Z"));
    var s1 =
        job(
            request,
            user,
            SubjectType.SOURCE,
            UUID.randomUUID(),
            Instant.parse("2026-04-07T10:00:01Z"));
    var l2 =
        job(
            request,
            user,
            SubjectType.LAYER,
            UUID.randomUUID(),
            Instant.parse("2026-04-07T10:00:02Z"));
    var l3 =
        job(
            request,
            user,
            SubjectType.LAYER,
            UUID.randomUUID(),
            Instant.parse("2026-04-07T10:00:03Z"));
    var s2 =
        job(
            request,
            user,
            SubjectType.SOURCE,
            UUID.randomUUID(),
            Instant.parse("2026-04-07T10:00:04Z"));
    var coordinator = coordinator();

    var d1 = coordinator.register(l1);
    var d2 = coordinator.register(s1);
    var d3 = coordinator.register(l2);
    var d4 = coordinator.register(l3);
    var d5 = coordinator.register(s2);

    assertEquals(
        2,
        List.of(d1, d2, d3, d4, d5).stream()
            .mapToInt(decision -> decision.action() == Action.START_NOW ? 1 : 0)
            .sum());
    assertEquals(
        3,
        List.of(d1, d2, d3, d4, d5).stream()
            .mapToInt(decision -> decision.action() == Action.QUEUE_ONLY ? 1 : 0)
            .sum());
    assertEquals("ACTIVE_REQUEST_BACKLOG", d3.queueRole().isBlank() ? "" : d3.queueRole());
    assertEquals("ACTIVE_REQUEST_BACKLOG", d4.queueRole().isBlank() ? "" : d4.queueRole());
    assertEquals("ACTIVE_REQUEST_BACKLOG", d5.queueRole().isBlank() ? "" : d5.queueRole());
    assertTrue(redisTemplate.hasKey(payloadKey(l3.jobId())));

    var promoted = coordinator.recordExecutionExit(l1.jobId());
    assertEquals(1, promoted.jobsToStart().size());
    assertEquals(l2.jobId(), promoted.jobsToStart().getFirst().jobId());
    assertEquals(
        SubjectType.LAYER, promoted.jobsToStart().getFirst().subjects().getFirst().subjectType());
  }

  @Test
  void recordExecutionExit_duplicateCallbackWithRemainingBacklogPromotesOnlyOneJob() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var request = UUID.fromString("550e8400-e29b-41d4-a716-446655440011");
    var a1 = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var a2 = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var a3 = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:02Z"));
    var a4 = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:03Z"));
    var c1 = coordinator();
    var c2 = coordinator();

    assertEquals(Action.START_NOW, c1.register(a1).action());
    assertEquals(Action.START_NOW, c2.register(a2).action());
    assertEquals(Action.QUEUE_ONLY, c1.register(a3).action());
    assertEquals(Action.QUEUE_ONLY, c2.register(a4).action());

    var first = c1.recordExecutionExit(a1.jobId());
    assertEquals(1, first.jobsToStart().size());
    assertEquals(a3.jobId(), first.jobsToStart().getFirst().jobId());

    var duplicate = c2.recordExecutionExit(a1.jobId());
    assertTrue(duplicate.jobsToStart().isEmpty());
    assertTrue(redisTemplate.hasKey(payloadKey(a4.jobId())));
  }

  @Test
  void register_replacementOrderingHonorsRequestedAtThenLexicographicRequestId() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var active =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var c1 =
        job(
            UUID.fromString("0f000000-0000-0000-0000-000000000001"),
            user,
            UUID.randomUUID(),
            Instant.parse("2026-04-07T10:00:03Z"));
    var b2 = job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:02Z"));
    var d1 =
        job(
            UUID.fromString("f0000000-0000-0000-0000-000000000001"),
            user,
            UUID.randomUUID(),
            Instant.parse("2026-04-07T10:00:03Z"));
    var coordinator = coordinator();

    coordinator.register(active);
    coordinator.register(c1);
    var older = coordinator.register(b2);
    assertEquals(1, older.jobsToCancel().size());
    assertEquals(b2.jobId(), older.jobsToCancel().getFirst().jobId());
    assertEquals(SUPERSEDED_BY_REPLACEMENT_REASON, older.jobsToCancel().getFirst().reason());
    assertTrue(redisTemplate.hasKey(payloadKey(c1.jobId())));
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(b2.jobId()))));
    assertTrue(older.jobsToStart().isEmpty());

    var newer = coordinator.register(d1);
    assertEquals(1, newer.jobsToCancel().size());
    assertEquals(c1.jobId(), newer.jobsToCancel().getFirst().jobId());
    assertEquals(SUPERSEDED_BY_REPLACEMENT_REASON, newer.jobsToCancel().getFirst().reason());
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(c1.jobId()))));
    assertTrue(redisTemplate.hasKey(payloadKey(d1.jobId())));
    assertTrue(newer.jobsToStart().isEmpty());
  }

  @Test
  void recordTerminal_waitsForLastActiveSubjectThenPromotesQueuedReplacement() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = job(requestA, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var a2 = job(requestA, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var b1 = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var c1 = coordinator();
    var c2 = coordinator();

    c1.register(a1);
    c2.register(a2);
    c1.register(b1);

    var firstTerminal = c1.recordTerminal(a1.jobId());
    assertTrue(firstTerminal.jobsToStart().isEmpty());
    assertTrue(redisTemplate.hasKey(payloadKey(b1.jobId())));

    var secondTerminal = c2.recordTerminal(a2.jobId());
    assertEquals(1, secondTerminal.jobsToStart().size());
    assertEquals(b1.jobId(), secondTerminal.jobsToStart().getFirst().jobId());
    assertTrue(secondTerminal.replacementStarted());
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(b1.jobId()))));
  }

  @Test
  void register_sameReplacementRequestKeepsAllSiblingSubjectsQueuedWithoutCancellation() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var active = job(requestA, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var b1 = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var b2 = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var b3 = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var coordinator = coordinator();

    assertEquals(Action.START_NOW, coordinator.register(active).action());
    var first = coordinator.register(b1);
    var second = coordinator.register(b2);
    var third = coordinator.register(b3);

    assertTrue(first.jobsToCancel().isEmpty());
    assertTrue(second.jobsToCancel().isEmpty());
    assertTrue(third.jobsToCancel().isEmpty());
    assertTrue(redisTemplate.hasKey(payloadKey(b1.jobId())));
    assertTrue(redisTemplate.hasKey(payloadKey(b2.jobId())));
    assertTrue(redisTemplate.hasKey(payloadKey(b3.jobId())));

    var promoted = coordinator.recordTerminal(active.jobId());
    assertEquals(2, promoted.jobsToStart().size());
    assertTrue(
        promoted.jobsToStart().stream()
            .map(HistoricalTracksJobSubmission::jobId)
            .anyMatch(id -> id.equals(b1.jobId())));
    assertTrue(
        promoted.jobsToStart().stream()
            .map(HistoricalTracksJobSubmission::jobId)
            .anyMatch(id -> id.equals(b2.jobId()) || id.equals(b3.jobId())));
  }

  @Test
  void recordTerminal_promotesAtMostConfiguredConcurrentSubjectsAndKeepsBacklogPayload() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var a1 = job(requestA, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var a2 = job(requestA, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var b1 = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var b2 = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var b3 = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var coordinator = coordinator();

    coordinator.register(a1);
    coordinator.register(a2);
    coordinator.register(b1);
    coordinator.register(b2);
    coordinator.register(b3);

    assertTrue(coordinator.recordTerminal(a1.jobId()).jobsToStart().isEmpty());
    var promoted = coordinator.recordTerminal(a2.jobId());
    assertEquals(2, promoted.jobsToStart().size());

    var thirdJobNotStarted =
        List.of(b1.jobId(), b2.jobId(), b3.jobId()).stream()
            .filter(
                jobId ->
                    promoted.jobsToStart().stream()
                        .noneMatch(promotedJob -> promotedJob.jobId().equals(jobId)))
            .findFirst()
            .orElseThrow();
    assertTrue(redisTemplate.hasKey(payloadKey(thirdJobNotStarted)));

    var promotedJobToExit = promoted.jobsToStart().getFirst();
    var backlogPromotion = coordinator.recordExecutionExit(promotedJobToExit.jobId());
    assertEquals(1, backlogPromotion.jobsToStart().size());
    assertEquals(thirdJobNotStarted, backlogPromotion.jobsToStart().getFirst().jobId());
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(thirdJobNotStarted))));
  }

  @Test
  void register_replacementOrderingTreatsNanoLaterTimestampAsNewer() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var activeRequest = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var requestC = UUID.randomUUID();
    var active = job(activeRequest, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var b1 = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var c1 =
        job(requestC, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00.000000001Z"));
    var coordinator = coordinator();

    coordinator.register(active);
    assertTrue(coordinator.register(b1).jobsToCancel().isEmpty());
    var supersede = coordinator.register(c1);

    assertEquals(1, supersede.jobsToCancel().size());
    assertEquals(b1.jobId(), supersede.jobsToCancel().getFirst().jobId());
    assertEquals(SUPERSEDED_BY_REPLACEMENT_REASON, supersede.jobsToCancel().getFirst().reason());
    assertTrue(redisTemplate.hasKey(payloadKey(c1.jobId())));
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(b1.jobId()))));
  }

  @Test
  void recordTerminal_malformedPayloadCancelsAndCleansQueues() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var a1 = job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var b1 = job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var coordinator = coordinator();

    coordinator.register(a1);
    coordinator.register(b1);
    redisTemplate
        .opsForValue()
        .set(payloadKey(b1.jobId()), "{broken-json", properties.getJobStateTtl());

    var decision = coordinator.recordTerminal(a1.jobId());
    assertTrue(decision.jobsToStart().isEmpty());
    assertEquals(1, decision.jobsToCancel().size());
    assertEquals(b1.jobId(), decision.jobsToCancel().getFirst().jobId());
    assertEquals(PAYLOAD_RESTORE_REASON, decision.jobsToCancel().getFirst().reason());
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(b1.jobId()))));
  }

  @Test
  void recordTerminal_wrongTypePayloadCancelsAndCleansQueues() throws Exception {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var activeOne =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var replacementOne =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var coordinatorOne = coordinator();
    coordinatorOne.register(activeOne);
    coordinatorOne.register(replacementOne);
    var wrongTypePayload =
        objectMapper.writeValueAsString(
            LayerCopyJobSubmission.create(
                JobIssuer.user(user), UUID.randomUUID(), UUID.randomUUID(), true, true));
    redisTemplate
        .opsForValue()
        .set(payloadKey(replacementOne.jobId()), wrongTypePayload, properties.getJobStateTtl());

    var wrongType = coordinatorOne.recordTerminal(activeOne.jobId());
    assertTrue(wrongType.jobsToStart().isEmpty());
    assertEquals(1, wrongType.jobsToCancel().size());
    assertEquals(replacementOne.jobId(), wrongType.jobsToCancel().getFirst().jobId());
    assertEquals(PAYLOAD_RESTORE_REASON, wrongType.jobsToCancel().getFirst().reason());
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(replacementOne.jobId()))));
  }

  @Test
  void recordTerminal_missingPayloadCancelsAndRemovesQueuedJob() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var activeTwo =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:10:00Z"));
    var replacementTwo =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:10:01Z"));
    var coordinatorTwo = coordinator();
    coordinatorTwo.register(activeTwo);
    coordinatorTwo.register(replacementTwo);
    redisTemplate.delete(payloadKey(replacementTwo.jobId()));

    var missing = coordinatorTwo.recordTerminal(activeTwo.jobId());
    assertTrue(missing.jobsToStart().isEmpty());
    assertEquals(1, missing.jobsToCancel().size());
    assertEquals(replacementTwo.jobId(), missing.jobsToCancel().getFirst().jobId());
    assertEquals(PAYLOAD_RESTORE_REASON, missing.jobsToCancel().getFirst().reason());
  }

  @Test
  void recordTerminal_skipsMalformedReplacementMemberAndRestoresValidJob() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var active =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var replacement =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var coordinator = coordinator();
    coordinator.register(active);
    coordinator.register(replacement);
    redisTemplate.opsForList().leftPush(replacementJobsKey(user), "bad-uuid");

    var decision = coordinator.recordTerminal(active.jobId());

    assertEquals(1, decision.jobsToStart().size());
    assertEquals(replacement.jobId(), decision.jobsToStart().getFirst().jobId());
    assertFalse(
        redisTemplate.opsForList().range(replacementJobsKey(user), 0, -1).contains("bad-uuid"));
  }

  @Test
  void recordTerminal_missingReplacementPayloadCancelsAndPromotesNextEligibleJob() {
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var active = job(requestA, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:00Z"));
    var missingPayloadJob =
        job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var validPayloadJob =
        job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:00:01Z"));
    var coordinator = coordinator();
    coordinator.register(active);
    coordinator.register(missingPayloadJob);
    coordinator.register(validPayloadJob);
    redisTemplate.delete(payloadKey(missingPayloadJob.jobId()));

    var decision = coordinator.recordTerminal(active.jobId());

    assertEquals(1, decision.jobsToCancel().size());
    assertEquals(missingPayloadJob.jobId(), decision.jobsToCancel().getFirst().jobId());
    assertEquals(1, decision.jobsToStart().size());
    assertEquals(validPayloadJob.jobId(), decision.jobsToStart().getFirst().jobId());
    String activeRequest = redisTemplate.opsForValue().get(activeRequestKey(user));
    assertNotNull(activeRequest);
    assertEquals(requestB.toString(), activeRequest);
  }

  @Test
  void recordTerminal_missingPayloadWithConcurrencyOneSurfacesCleanupPromotedBacklogJob() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    localProperties.setMaxConcurrentSubjectsPerJob(1);
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var active = job(requestA, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:20:00Z"));
    var missingPayloadJob =
        job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:20:01Z"));
    var validPayloadJob =
        job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:20:02Z"));

    localCoordinator.register(active);
    localCoordinator.register(missingPayloadJob);
    localCoordinator.register(validPayloadJob);
    redisTemplate.delete(payloadKey(missingPayloadJob.jobId()));

    var decision = localCoordinator.recordTerminal(active.jobId());

    assertEquals(1, decision.jobsToCancel().size());
    assertEquals(missingPayloadJob.jobId(), decision.jobsToCancel().getFirst().jobId());
    assertEquals(1, decision.jobsToStart().size());
    assertEquals(validPayloadJob.jobId(), decision.jobsToStart().getFirst().jobId());
    assertFalse(
        redisTemplate
            .opsForSet()
            .isMember(
                "opencop:history:admission:user:" + user + ":active-running",
                active.jobId().toString()));
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(validPayloadJob.jobId()))));
  }

  @Test
  void recordTerminal_malformedSelectedReplacementWithConcurrencyOnePromotesNextValidJob() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    localProperties.setMaxConcurrentSubjectsPerJob(1);
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var active = job(requestA, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:25:00Z"));
    var validPayloadJob =
        job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:25:01Z"));

    localCoordinator.register(active);
    localCoordinator.register(validPayloadJob);
    redisTemplate.opsForList().leftPush(replacementJobsKey(user), "bad-uuid");

    var decision = localCoordinator.recordTerminal(active.jobId());

    assertTrue(decision.jobsToCancel().isEmpty());
    assertEquals(1, decision.jobsToStart().size());
    assertEquals(validPayloadJob.jobId(), decision.jobsToStart().getFirst().jobId());
    assertFalse(
        redisTemplate.opsForList().range(replacementJobsKey(user), 0, -1).contains("bad-uuid"));
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(validPayloadJob.jobId()))));
  }

  @Test
  void cleanupAfterRegistrationFailure_removesActiveBacklogMembershipAndPayload() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    localProperties.setMaxConcurrentSubjectsPerJob(1);
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var request = UUID.randomUUID();
    var running = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:30:00Z"));
    var backlog = job(request, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:30:01Z"));

    localCoordinator.register(running);
    localCoordinator.register(backlog);
    assertTrue(
        redisTemplate
            .opsForList()
            .range(activeBacklogKey(user), 0, -1)
            .contains(backlog.jobId().toString()));
    assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(backlog.jobId()))));

    var cleanupDecision = localCoordinator.cleanupAfterRegistrationFailure(backlog.jobId());

    assertTrue(cleanupDecision.jobsToStart().isEmpty());
    assertFalse(
        redisTemplate
            .opsForList()
            .range(activeBacklogKey(user), 0, -1)
            .contains(backlog.jobId().toString()));
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(backlog.jobId()))));
  }

  @Test
  void cleanupAfterRegistrationFailure_removesReplacementMembershipAndPayload() {
    var localCoordinator = coordinator();
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var active = job(requestA, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:40:00Z"));
    var replacement = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:40:01Z"));

    localCoordinator.register(active);
    localCoordinator.register(replacement);
    assertTrue(
        redisTemplate
            .opsForList()
            .range(replacementJobsKey(user), 0, -1)
            .contains(replacement.jobId().toString()));
    assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(replacement.jobId()))));

    var cleanupDecision = localCoordinator.cleanupAfterRegistrationFailure(replacement.jobId());

    assertTrue(cleanupDecision.jobsToStart().isEmpty());
    assertFalse(
        redisTemplate
            .opsForList()
            .range(replacementJobsKey(user), 0, -1)
            .contains(replacement.jobId().toString()));
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(payloadKey(replacement.jobId()))));
  }

  @Test
  void cleanupAfterRegistrationFailure_promotesReplacementSubjectsUpToConcurrencyLimit() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    localProperties.setMaxConcurrentSubjectsPerJob(2);
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var active = job(requestA, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:45:00Z"));
    var b1 = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:45:01Z"));
    var b2 = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:45:02Z"));
    var b3 = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:45:03Z"));
    var b4 = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:45:04Z"));

    localCoordinator.register(active);
    localCoordinator.register(b1);
    localCoordinator.register(b2);
    localCoordinator.register(b3);
    localCoordinator.register(b4);

    var cleanupDecision = localCoordinator.cleanupAfterRegistrationFailure(active.jobId());

    assertEquals(2, cleanupDecision.jobsToStart().size());
    assertEquals(
        List.of(b1.jobId(), b2.jobId()),
        cleanupDecision.jobsToStart().stream().map(HistoricalTracksJobSubmission::jobId).toList());
    assertTrue(redisTemplate.opsForSet().isMember(activeRunningKey(user), b1.jobId().toString()));
    assertTrue(redisTemplate.opsForSet().isMember(activeRunningKey(user), b2.jobId().toString()));
    assertEquals(
        List.of(b3.jobId().toString(), b4.jobId().toString()),
        redisTemplate.opsForList().range(activeBacklogKey(user), 0, -1));
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(replacementRequestKey(user))));
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(replacementJobsKey(user))));
  }

  @Test
  void
      cleanupAfterRegistrationFailure_clearsOrphanedReplacementRequestWhileActiveJobStillRunning() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    localProperties.setMaxConcurrentSubjectsPerJob(1);
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var requestA = UUID.randomUUID();
    var requestB = UUID.randomUUID();
    var active = job(requestA, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:50:00Z"));
    var replacement = job(requestB, user, UUID.randomUUID(), Instant.parse("2026-04-07T10:50:01Z"));

    localCoordinator.register(active);
    localCoordinator.register(replacement);

    var cleanupDecision = localCoordinator.cleanupAfterRegistrationFailure(replacement.jobId());
    assertTrue(cleanupDecision.jobsToStart().isEmpty());
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(replacementRequestKey(user))));

    var terminalDecision = localCoordinator.recordTerminal(active.jobId());
    assertTrue(terminalDecision.jobsToStart().isEmpty());
    assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(activeRequestKey(user))));
  }

  @Test
  void recoverTerminalActiveJobs_removesMalformedActiveRunningMemberBeforePromotion() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    localProperties.setMaxConcurrentSubjectsPerJob(1);
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var active =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T11:00:00Z"));
    var replacement =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T11:00:01Z"));
    localCoordinator.register(active);
    localCoordinator.register(replacement);
    redisTemplate.opsForSet().add(activeRunningKey(user), "not-a-uuid");
    var stateGateway = mock(HistoricalJobStateGateway.class);
    when(stateGateway.findByJobId(active.jobId()))
        .thenReturn(
            Optional.of(
                HistoricalJobState.received(
                        active, Instant.parse("2026-04-07T11:00:02Z"), Duration.ofHours(24))
                    .completed(Instant.parse("2026-04-07T11:00:03Z"), Duration.ofHours(24))));

    var decisions = localCoordinator.recoverTerminalActiveJobs(stateGateway);

    assertFalse(
        Boolean.TRUE.equals(
            redisTemplate.opsForSet().isMember(activeRunningKey(user), "not-a-uuid")));
    assertEquals(1, decisions.size());
    assertEquals(1, decisions.getFirst().jobsToStart().size());
    assertEquals(replacement.jobId(), decisions.getFirst().jobsToStart().getFirst().jobId());
  }

  @Test
  void recoverTerminalActiveJobs_skipsMalformedJobHashAndContinuesRecovery() {
    var localProperties = new HistoricalExecutionProperties();
    localProperties.setJobStateTtl(Duration.ofHours(24));
    localProperties.setMaxConcurrentSubjectsPerJob(1);
    var localCoordinator =
        RedisHistoricalJobAdmissionGateway.create(
            redisTemplate, objectMapper, localProperties, new ProcessorHistoricalRedisScripts());
    var stateGateway =
        new RedisHistoricalJobStateGateway(
            redisTemplate,
            localProperties,
            Clock.fixed(Instant.parse("2026-04-07T11:05:00Z"), ZoneOffset.UTC),
            new ProcessorHistoricalRedisScripts());
    var user = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var badJobId = UUID.randomUUID();
    var active =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T11:05:00Z"));
    var replacement =
        job(UUID.randomUUID(), user, UUID.randomUUID(), Instant.parse("2026-04-07T11:05:01Z"));
    localCoordinator.register(active);
    localCoordinator.register(replacement);
    redisTemplate
        .opsForHash()
        .putAll(
            HistoricalJobControlKeyLayout.STANDALONE.jobState(active.jobId()),
            HistoricalJobState.received(
                    active, Instant.parse("2026-04-07T11:05:02Z"), Duration.ofHours(24))
                .completed(Instant.parse("2026-04-07T11:05:03Z"), Duration.ofHours(24))
                .toRedisHash());
    redisTemplate.opsForSet().add(activeRunningKey(user), badJobId.toString());
    redisTemplate
        .opsForHash()
        .put(
            HistoricalJobControlKeyLayout.STANDALONE.jobState(badJobId), "requestId", "not-a-uuid");

    var decisions = localCoordinator.recoverTerminalActiveJobs(stateGateway);

    assertFalse(redisTemplate.opsForSet().isMember(activeRunningKey(user), badJobId.toString()));
    assertEquals(1, decisions.size());
    assertEquals(1, decisions.getFirst().jobsToStart().size());
    assertEquals(replacement.jobId(), decisions.getFirst().jobsToStart().getFirst().jobId());
  }

  private RedisHistoricalJobAdmissionGateway coordinator() {
    return RedisHistoricalJobAdmissionGateway.create(
        redisTemplate, objectMapper, properties, new ProcessorHistoricalRedisScripts());
  }

  private static HistoricalTracksJobSubmission job(
      UUID requestId, UUID requestedBy, UUID subjectId, Instant requestedAt) {
    return job(requestId, requestedBy, SubjectType.SOURCE, subjectId, requestedAt);
  }

  private static HistoricalTracksJobSubmission job(
      UUID requestId,
      UUID requestedBy,
      SubjectType subjectType,
      UUID subjectId,
      Instant requestedAt) {
    return job(requestId, requestedBy, List.of(subject(subjectType, subjectId)), requestedAt);
  }

  private static HistoricalTracksJobSubmission job(
      UUID requestId,
      UUID requestedBy,
      List<HistoricalTracksSubject> subjects,
      Instant requestedAt) {
    return new HistoricalTracksJobSubmission(
        UUID.randomUUID(),
        requestId,
        JobIssuer.user(requestedBy),
        List.copyOf(subjects),
        requestedBy,
        requestedAt,
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        HistoricalResolution.MINUTE_1,
        118,
        List.of());
  }

  private static HistoricalTracksSubject subject(SubjectType subjectType, UUID subjectId) {
    return new HistoricalTracksSubject(UUID.randomUUID(), subjectType, subjectId);
  }

  private static String payloadKey(UUID jobId) {
    return "opencop:history:job-payload:" + jobId;
  }
}
