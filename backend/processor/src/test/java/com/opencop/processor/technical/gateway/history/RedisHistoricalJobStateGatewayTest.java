// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobStatus;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisHistoricalJobStateGatewayTest {

  private final HistoricalExecutionProperties properties = new HistoricalExecutionProperties();
  private final Clock clock = Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);

  private RedisHistoricalJobStateGateway repository;
  private RecordingStringRedisTemplate redisTemplate;

  @BeforeEach
  void setUp() {
    redisTemplate = new RecordingStringRedisTemplate();
    properties.setJobStateTtl(Duration.ofHours(24));
    repository =
        new RedisHistoricalJobStateGateway(
            redisTemplate, properties, clock, new ProcessorHistoricalRedisScripts());
  }

  @Test
  void createReceived_persistsInitialStateWithTtl() {
    var job = job();

    repository.createReceived(job);

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.RECEIVED, state.status());
    assertEquals(HistoricalJobStage.INGRESS_RECEIVED, state.stage());
    assertEquals(job.requestId(), state.requestId());
    assertEquals("HISTORICAL_TRACKS", state.jobType());
    assertNotNull(state.expiresAt());
    assertEquals(
        Duration.ofHours(24),
        redisTemplate.expirations.get(
            HistoricalJobControlKeyLayout.STANDALONE.jobState(job.jobId())));
  }

  @Test
  void clusterLayout_routesStateReadsWritesAndLifecycleScriptsToTheTaggedControlKey() {
    var clusterRepository =
        new RedisHistoricalJobStateGateway(
            redisTemplate,
            HistoricalJobControlKeyLayout.CLUSTER,
            properties,
            clock,
            new ProcessorHistoricalRedisScripts());
    var job = job();
    var clusterKey = HistoricalJobControlKeyLayout.CLUSTER.jobState(job.jobId());

    clusterRepository.createReceived(job);
    assertTrue(redisTemplate.hashes.containsKey(clusterKey));
    assertFalse(
        redisTemplate.hashes.containsKey(
            HistoricalJobControlKeyLayout.STANDALONE.jobState(job.jobId())));

    assertTrue(clusterRepository.markRunning(job.jobId(), "history-job-1"));
    assertTrue(clusterRepository.markCancellationRequested(job.jobId(), "new replacement"));
    assertEquals(
        HistoricalJobStage.CANCELLATION_REQUESTED.name(),
        redisTemplate.hashes.get(clusterKey).get("stage"));
  }

  @Test
  void createReceivedIfAbsent_whenCalledTwiceForSameJob_keepsFirstRequestId() {
    var first = job();
    var secondRequestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440123");
    var second =
        new HistoricalTracksJobSubmission(
            first.jobId(),
            secondRequestId,
            first.issuer(),
            List.of(
                new HistoricalTracksSubject(
                    first.jobId(),
                    first.subjects().getFirst().subjectType(),
                    first.subjects().getFirst().subjectId())),
            first.requestedBy(),
            first.requestedAt(),
            first.requestedFrom(),
            first.requestedTo(),
            first.effectiveFrom(),
            first.effectiveTo(),
            first.resolution(),
            first.plannedChunks(),
            first.filters());

    boolean firstCreated = repository.createReceivedIfAbsent(first);
    boolean secondCreated = repository.createReceivedIfAbsent(second);

    assertTrue(firstCreated);
    assertFalse(secondCreated);
    var persisted = repository.findByJobId(first.jobId()).orElseThrow();
    assertEquals(first.requestId(), persisted.requestId());
    assertNull(
        redisTemplate.partialHashReadFailure,
        "duplicate readers must not be able to observe a partially written received state");
  }

  @Test
  void createReceivedIfAbsent_executesResourceBackedCreateScript() {
    var job = job();

    assertTrue(repository.createReceivedIfAbsent(job));

    assertNotNull(redisTemplate.lastCreateScriptSource);
    assertTrue(
        redisTemplate.lastCreateScriptSource.contains("-- Script: job_create_received_if_absent"));
  }

  @Test
  void stateTransitions_updateDurableStateUntilTerminal() {
    var job = job();
    repository.createReceived(job);

    assertTrue(repository.markQueued(job.jobId()));
    assertTrue(repository.markRunning(job.jobId(), "history-job-1"));
    assertTrue(repository.refreshActiveTtl(job.jobId()));
    assertTrue(repository.markCompleted(job.jobId()));

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.COMPLETED, state.status());
    assertEquals(HistoricalJobStage.EXECUTION_COMPLETED, state.stage());
    assertEquals("history-job-1", state.workerId());
    assertFalse(
        repository.markFailed(job.jobId(), HistoricalJobStage.EXECUTION_FAILED_UNKNOWN, "boom"));
    assertNotNull(redisTemplate.lastGuardedUpdateScriptSource);
    assertTrue(
        redisTemplate.lastGuardedUpdateScriptSource.contains("-- Script: job_update_with_guards"));
  }

  @Test
  void markFailed_recordsFailureReasonAndTerminalState() {
    var job = job();
    repository.createReceived(job);
    repository.markQueued(job.jobId());

    assertTrue(
        repository.markFailed(job.jobId(), HistoricalJobStage.INGRESS_REJECTED, "queue full"));

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.FAILED, state.status());
    assertEquals(HistoricalJobStage.INGRESS_REJECTED, state.stage());
    assertEquals("queue full", state.failureReason());
    assertFalse(repository.refreshActiveTtl(job.jobId()));
  }

  @Test
  void refreshActiveTtl_whenStateIsCancelledConcurrently_refusesAndPreservesTerminalState() {
    var job = job();
    repository.createReceived(job);
    repository.markQueued(job.jobId());
    repository.markRunning(job.jobId(), "history-job-1");
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(job.jobId());
    // Simulate a concurrent guarded transition (cancellation, failure, completion, timeout) that
    // landed after the caller decided to schedule a TTL refresh: Redis already shows the terminal
    // status. With the old read-then-persistWithTtl pattern, the refresh would write the stale
    // active snapshot back over the terminal fields. The Lua-script refresh must refuse instead.
    redisTemplate.hashes.get(key).put("status", HistoricalJobStatus.CANCELLED.name());
    redisTemplate.hashes.get(key).put("stage", HistoricalJobStage.CANCELLED.name());
    redisTemplate.hashes.get(key).put("failureReason", "operator cancellation");
    var expireCallsBefore = redisTemplate.expireCalls.getOrDefault(key, 0);

    boolean refreshed = repository.refreshActiveTtl(job.jobId());

    assertFalse(refreshed);
    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.CANCELLED, state.status());
    assertEquals(HistoricalJobStage.CANCELLED, state.stage());
    assertEquals("operator cancellation", state.failureReason());
    assertEquals(
        expireCallsBefore,
        redisTemplate.expireCalls.getOrDefault(key, 0),
        "TTL must not be extended once the state has reached a terminal status");
  }

  @Test
  void refreshActiveTtl_whenStateIsActive_updatesOnlyUpdatedAtAndExpiresAt() {
    var job = job();
    repository.createReceived(job);
    repository.markQueued(job.jobId());
    repository.markRunning(job.jobId(), "history-job-1");
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(job.jobId());
    var statusBefore = redisTemplate.hashes.get(key).get("status");
    var stageBefore = redisTemplate.hashes.get(key).get("stage");
    var workerBefore = redisTemplate.hashes.get(key).get("workerId");
    var startedAtBefore = redisTemplate.hashes.get(key).get("startedAt");

    boolean refreshed = repository.refreshActiveTtl(job.jobId());

    assertTrue(refreshed);
    var hashAfter = redisTemplate.hashes.get(key);
    assertEquals(statusBefore, hashAfter.get("status"));
    assertEquals(stageBefore, hashAfter.get("stage"));
    assertEquals(workerBefore, hashAfter.get("workerId"));
    assertEquals(startedAtBefore, hashAfter.get("startedAt"));
    assertNotNull(redisTemplate.lastRefreshActiveTtlScriptSource);
    assertTrue(
        redisTemplate.lastRefreshActiveTtlScriptSource.contains(
            "-- Script: job_refresh_active_ttl"));
  }

  @Test
  void markCancellationRequested_setsCancellationRequestedStageAndRefreshesTtl() {
    var job = job();
    repository.createReceived(job);
    repository.markQueued(job.jobId());
    repository.markRunning(job.jobId(), "history-job-1");
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(job.jobId());
    var expireCallsBefore = redisTemplate.expireCalls.getOrDefault(key, 0);

    assertTrue(repository.markCancellationRequested(job.jobId(), "new replacement accepted"));

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.RUNNING, state.status());
    assertEquals(HistoricalJobStage.CANCELLATION_REQUESTED, state.stage());
    assertEquals("new replacement accepted", state.failureReason());
    assertEquals(Duration.ofHours(24), redisTemplate.expirations.get(key));
    assertEquals(expireCallsBefore + 1, redisTemplate.expireCalls.getOrDefault(key, 0));
  }

  @Test
  void markCancellationRequested_preservesRedisStatusAndWorkerWhenSnapshotIsStale() {
    var job = job();
    repository.createReceived(job);
    repository.markQueued(job.jobId());
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(job.jobId());
    redisTemplate.beforeGuardedUpdate =
        hash -> {
          hash.put("status", HistoricalJobStatus.RUNNING.name());
          hash.put("stage", HistoricalJobStage.EXECUTION_RUNNING.name());
          hash.put("workerId", "history-job-1");
        };

    assertTrue(repository.markCancellationRequested(job.jobId(), "new replacement accepted"));

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.RUNNING, state.status());
    assertEquals("history-job-1", state.workerId());
    assertEquals(HistoricalJobStage.CANCELLATION_REQUESTED, state.stage());
    assertEquals("new replacement accepted", state.failureReason());
  }

  @Test
  void
      markCancellationRequested_whenAlreadyCancellationRequested_preservesFirstReasonAndUpdatedAt() {
    var job = job();
    repository.createReceived(job);
    repository.markQueued(job.jobId());
    repository.markRunning(job.jobId(), "history-job-1");

    assertTrue(repository.markCancellationRequested(job.jobId(), "first"));
    var first = repository.findByJobId(job.jobId()).orElseThrow();

    assertTrue(repository.markCancellationRequested(job.jobId(), "second"));
    var second = repository.findByJobId(job.jobId()).orElseThrow();

    assertEquals(HistoricalJobStage.CANCELLATION_REQUESTED, second.stage());
    assertEquals("first", second.failureReason());
    assertEquals(first.updatedAt(), second.updatedAt());
    assertNotNull(redisTemplate.lastMarkCancellationRequestedScriptSource);
    assertTrue(
        redisTemplate.lastMarkCancellationRequestedScriptSource.contains(
            "-- Script: job_mark_cancellation_requested"));
  }

  @Test
  void markCancelled_doesNotOverwriteTerminalStateWhenSnapshotIsStale() {
    var job = job();
    repository.createReceived(job);
    assertTrue(repository.markQueued(job.jobId()));
    assertTrue(repository.markRunning(job.jobId(), "history-job-1"));

    redisTemplate.beforeGuardedUpdate =
        hash -> {
          hash.put("status", HistoricalJobStatus.COMPLETED.name());
          hash.put("stage", HistoricalJobStage.EXECUTION_COMPLETED.name());
          hash.put("failureReason", "");
        };
    assertFalse(repository.markCancelled(job.jobId(), "replacement"));
    var afterCancelAttempt = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.COMPLETED, afterCancelAttempt.status());
    assertEquals(HistoricalJobStage.EXECUTION_COMPLETED, afterCancelAttempt.stage());
  }

  @Test
  void markFailed_doesNotOverwriteTerminalStateWhenSnapshotIsStale() {
    var job = job();
    repository.createReceived(job);
    assertTrue(repository.markQueued(job.jobId()));
    assertTrue(repository.markRunning(job.jobId(), "history-job-1"));

    redisTemplate.beforeGuardedUpdate =
        hash -> {
          hash.put("status", HistoricalJobStatus.COMPLETED.name());
          hash.put("stage", HistoricalJobStage.EXECUTION_COMPLETED.name());
          hash.put("failureReason", "");
        };
    assertFalse(
        repository.markFailed(job.jobId(), HistoricalJobStage.EXECUTION_FAILED_UNKNOWN, "boom"));
    var afterFailAttempt = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.COMPLETED, afterFailAttempt.status());
    assertEquals(HistoricalJobStage.EXECUTION_COMPLETED, afterFailAttempt.stage());
  }

  @Test
  void markCancelled_setsCancelledTerminalStateWithoutRefreshingTtl() {
    var job = job();
    repository.createReceived(job);
    repository.markQueued(job.jobId());
    repository.markRunning(job.jobId(), "history-job-1");
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(job.jobId());
    var expireCallsBefore = redisTemplate.expireCalls.getOrDefault(key, 0);

    assertTrue(repository.markCancelled(job.jobId(), "superseded replacement"));

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.CANCELLED, state.status());
    assertEquals(HistoricalJobStage.CANCELLED, state.stage());
    assertEquals("superseded replacement", state.failureReason());
    assertTrue(state.isTerminal());
    assertEquals(expireCallsBefore, redisTemplate.expireCalls.getOrDefault(key, 0));
  }

  @Test
  void normalMutators_doNotAdvanceCancellationRequestedState() {
    var job = job();
    repository.createReceived(job);
    repository.markQueued(job.jobId());
    repository.markRunning(job.jobId(), "history-job-1");
    repository.markCancellationRequested(job.jobId(), "new replacement accepted");

    assertFalse(repository.markQueued(job.jobId()));
    assertFalse(repository.markRunning(job.jobId(), "history-job-2"));
    assertFalse(repository.markPlanning(job.jobId()));
    assertFalse(repository.markPlanned(job.jobId()));
    assertFalse(repository.markDeliveryPending(job.jobId()));
    assertFalse(repository.markCompleted(job.jobId()));
    assertFalse(repository.markDeliveredCompleted(job.jobId()));

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.RUNNING, state.status());
    assertEquals(HistoricalJobStage.CANCELLATION_REQUESTED, state.stage());
  }

  @Test
  void markQueued_defaultsQueueRoleToDispatchReady() {
    var job = job();
    repository.createReceived(job);

    assertTrue(repository.markQueued(job.jobId()));

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.QUEUED, state.status());
    assertEquals("DISPATCH_READY", state.queueRole());
  }

  @Test
  void explicitQueuedStateRolePersistsWhenRead() {
    var now = Instant.parse("2026-04-07T12:00:00Z");
    var queuedFromCompatibilityConstructor =
        new HistoricalJobState(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "HISTORICAL_TRACKS",
            SubjectType.SOURCE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            now.minusSeconds(60),
            now.minusSeconds(120),
            now.minusSeconds(30),
            now.minusSeconds(120),
            now.minusSeconds(30),
            HistoricalResolution.MINUTE_1,
            1L,
            HistoricalJobStatus.QUEUED,
            HistoricalJobStage.INGRESS_QUEUED,
            null,
            now,
            now.plusSeconds(3600),
            null,
            null,
            0L,
            0L,
            "DISPATCH_READY");

    redisTemplate
        .opsForHash()
        .putAll(
            HistoricalJobControlKeyLayout.STANDALONE.jobState(
                queuedFromCompatibilityConstructor.jobId()),
            queuedFromCompatibilityConstructor.toRedisHash());
    var state = repository.findByJobId(queuedFromCompatibilityConstructor.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.QUEUED, state.status());
    assertEquals("DISPATCH_READY", state.queueRole());
  }

  @Test
  void nonQueuedMutators_clearQueueRoleFromQueuedStates() {
    assertQueueRoleClearedAfterTransition(
        "DISPATCH_READY", id -> repository.markRunning(id, "worker-1"));
    assertQueueRoleClearedAfterTransition("ACTIVE_REQUEST_BACKLOG", repository::markPlanning);
    assertQueueRoleClearedAfterTransition("QUEUED_REPLACEMENT", repository::markPlanned);
    assertQueueRoleClearedAfterTransition("DISPATCH_READY", repository::markDeliveryPending);
    assertQueueRoleClearedAfterTransition("ACTIVE_REQUEST_BACKLOG", repository::markCompleted);
    assertQueueRoleClearedAfterTransition(
        "QUEUED_REPLACEMENT",
        id -> repository.markFailed(id, HistoricalJobStage.EXECUTION_FAILED_UNKNOWN, "failed"));
    assertQueueRoleClearedAfterTransition(
        "DISPATCH_READY", id -> repository.markCancelled(id, "cancelled"));
  }

  @Test
  void findByJobId_treatsMissingQueueRoleAsEmpty() {
    var job = job();
    repository.createReceived(job);
    assertTrue(repository.markQueued(job.jobId()));
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(job.jobId());
    redisTemplate.hashes.get(key).remove("queueRole");

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals("", state.queueRole());
  }

  @Test
  void findByJobId_legacyStateWithoutSequenceFieldsDefaultsToZero() {
    var job = job();
    repository.createReceived(job);
    var key = HistoricalJobControlKeyLayout.STANDALONE.jobState(job.jobId());
    redisTemplate.hashes.get(key).remove("userSequence");
    redisTemplate.hashes.get(key).remove("requestSequence");

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(0L, state.userSequence());
    assertEquals(0L, state.requestSequence());
  }

  @Test
  void markCancelled_allowsTerminalCancellationFromCancellationRequestedState() {
    var job = job();
    repository.createReceived(job);
    repository.markQueued(job.jobId());
    repository.markRunning(job.jobId(), "history-job-1");
    repository.markCancellationRequested(job.jobId(), "new replacement accepted");

    assertTrue(repository.markCancelled(job.jobId(), "superseded replacement"));

    var state = repository.findByJobId(job.jobId()).orElseThrow();
    assertEquals(HistoricalJobStatus.CANCELLED, state.status());
    assertEquals(HistoricalJobStage.CANCELLED, state.stage());
    assertEquals("superseded replacement", state.failureReason());
  }

  private void assertQueueRoleClearedAfterTransition(
      String queuedRole, Function<UUID, Boolean> transition) {
    var queuedJob = job();
    repository.createReceived(queuedJob);
    assertTrue(repository.markQueued(queuedJob.jobId(), queuedRole));

    assertTrue(transition.apply(queuedJob.jobId()));

    var transitioned = repository.findByJobId(queuedJob.jobId()).orElseThrow();
    assertEquals("", transitioned.queueRole());
  }

  private HistoricalTracksJobSubmission job() {
    return HistoricalTracksJobSubmission.create(
        UUID.randomUUID(),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        SubjectType.SOURCE,
        UUID.randomUUID(),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        HistoricalResolution.MINUTE_1,
        118,
        List.of());
  }

  private static final class RecordingStringRedisTemplate extends StringRedisTemplate {

    private final Map<String, Map<Object, Object>> hashes = new HashMap<>();
    private final Map<String, Duration> expirations = new HashMap<>();
    private final Map<String, Integer> expireCalls = new HashMap<>();
    private String lastCreateScriptSource;
    private String lastGuardedUpdateScriptSource;
    private String lastMarkCancellationRequestedScriptSource;
    private String lastRefreshActiveTtlScriptSource;
    private Throwable partialHashReadFailure;
    private java.util.function.Consumer<Map<Object, Object>> beforeGuardedUpdate;

    @Override
    @SuppressWarnings("unchecked")
    public org.springframework.data.redis.core.HashOperations<String, Object, Object> opsForHash() {
      return (org.springframework.data.redis.core.HashOperations<String, Object, Object>)
          java.lang.reflect.Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class<?>[] {org.springframework.data.redis.core.HashOperations.class},
              (proxy, method, args) -> {
                return switch (method.getName()) {
                  case "putAll" -> {
                    hashes.put((String) args[0], new HashMap<>((Map<?, ?>) args[1]));
                    yield null;
                  }
                  case "entries" -> new HashMap<>(hashes.getOrDefault((String) args[0], Map.of()));
                  case "put" -> {
                    hashes
                        .computeIfAbsent((String) args[0], ignored -> new HashMap<>())
                        .put(args[1], args[2]);
                    yield null;
                  }
                  case "putIfAbsent" -> {
                    var key = (String) args[0];
                    var field = args[1];
                    var map = hashes.computeIfAbsent(key, ignored -> new HashMap<>());
                    if (map.containsKey(field)) {
                      yield false;
                    }
                    map.put(field, args[2]);
                    try {
                      HistoricalJobState.fromRedisHash(new HashMap<>(map));
                    } catch (Throwable throwable) {
                      partialHashReadFailure = throwable;
                    }
                    yield true;
                  }
                  case "hasKey" ->
                      hashes.containsKey((String) args[0])
                          && hashes.get((String) args[0]).containsKey(args[1]);
                  case "get" -> hashes.getOrDefault((String) args[0], Map.of()).get(args[1]);
                  case "delete" -> 0L;
                  case "size" -> (long) hashes.getOrDefault((String) args[0], Map.of()).size();
                  default -> throw new UnsupportedOperationException(method.getName());
                };
              });
    }

    @Override
    public Boolean expire(String key, Duration timeout) {
      expirations.put(key, timeout);
      expireCalls.put(key, expireCalls.getOrDefault(key, 0) + 1);
      return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
      String key = keys.get(0);
      String source = script.getScriptAsString();
      if (source.contains("if redis.call('EXISTS', KEYS[1]) == 1")) {
        lastCreateScriptSource = source;
        if (hashes.containsKey(key) && !hashes.get(key).isEmpty()) {
          return (T) Long.valueOf(0L);
        }

        Map<Object, Object> hash = hashes.computeIfAbsent(key, ignored -> new HashMap<>());
        int ttlIndex = args.length - 3;
        for (int index = 0; index < ttlIndex; index += 2) {
          hash.put(args[index], args[index + 1]);
        }
        hash.put("userSequence", "1");
        hash.put("requestSequence", "1");

        expirations.put(key, Duration.ofMillis(Long.parseLong(args[ttlIndex].toString())));
        return (T) Long.valueOf(1L);
      }

      if (source.contains("-- Script: job_refresh_active_ttl")) {
        lastRefreshActiveTtlScriptSource = source;
        Map<Object, Object> hash = hashes.get(key);
        if (hash == null || hash.isEmpty()) {
          return (T) Long.valueOf(0L);
        }
        String currentStatus = hash.getOrDefault("status", "").toString();
        for (int i = 3; i < args.length; i++) {
          if (currentStatus.equals(args[i].toString())) {
            return (T) Long.valueOf(0L);
          }
        }
        hash.put("updatedAt", args[0]);
        hash.put("expiresAt", args[1]);
        long ttlMillis = Long.parseLong(args[2].toString());
        expirations.put(key, Duration.ofMillis(ttlMillis));
        expireCalls.put(key, expireCalls.getOrDefault(key, 0) + 1);
        return (T) Long.valueOf(1L);
      }

      if (!source.contains("local blockedStatusCount = tonumber(ARGV[index])")) {
        if (!source.contains("redis.call('HSET', KEYS[1], 'stage'")) {
          throw new UnsupportedOperationException("Unsupported script: " + source);
        }
        lastMarkCancellationRequestedScriptSource = source;
        Map<Object, Object> hash = hashes.get(key);
        if (hash == null || hash.isEmpty()) {
          return (T) Long.valueOf(0L);
        }
        if (beforeGuardedUpdate != null) {
          beforeGuardedUpdate.accept(hash);
          beforeGuardedUpdate = null;
        }
        int index = 0;
        int blockedStatusCount = Integer.parseInt(args[index++].toString());
        String currentStatus = hash.getOrDefault("status", "").toString();
        for (int i = 0; i < blockedStatusCount; i++) {
          if (currentStatus.equals(args[index++].toString())) {
            return (T) Long.valueOf(0L);
          }
        }
        String cancellationRequestedStage = args[index++].toString();
        String currentStage = hash.getOrDefault("stage", "").toString();
        if (currentStage.equals(cancellationRequestedStage)) {
          return (T) Long.valueOf(1L);
        }
        hash.put("stage", cancellationRequestedStage);
        hash.put("failureReason", args[index++]);
        hash.put("updatedAt", args[index++]);
        hash.put("expiresAt", args[index++]);
        long ttlMillis = Long.parseLong(args[index].toString());
        expirations.put(key, Duration.ofMillis(ttlMillis));
        expireCalls.put(key, expireCalls.getOrDefault(key, 0) + 1);
        return (T) Long.valueOf(1L);
      }

      lastGuardedUpdateScriptSource = source;
      Map<Object, Object> hash = hashes.get(key);
      if (hash == null || hash.isEmpty()) {
        return (T) Long.valueOf(0L);
      }
      if (beforeGuardedUpdate != null) {
        beforeGuardedUpdate.accept(hash);
        beforeGuardedUpdate = null;
      }
      int index = 0;
      int blockedStatusCount = Integer.parseInt(args[index++].toString());
      String currentStatus = hash.getOrDefault("status", "").toString();
      for (int i = 0; i < blockedStatusCount; i++) {
        if (currentStatus.equals(args[index++].toString())) {
          return (T) Long.valueOf(0L);
        }
      }
      int blockedStageCount = Integer.parseInt(args[index++].toString());
      String currentStage = hash.getOrDefault("stage", "").toString();
      for (int i = 0; i < blockedStageCount; i++) {
        if (currentStage.equals(args[index++].toString())) {
          return (T) Long.valueOf(0L);
        }
      }
      String ttlMillis = args[index++].toString();
      for (int i = index; i < args.length; i += 2) {
        hash.put(args[i], args[i + 1]);
      }
      if (!ttlMillis.isBlank()) {
        expirations.put(key, Duration.ofMillis(Long.parseLong(ttlMillis)));
        expireCalls.put(key, expireCalls.getOrDefault(key, 0) + 1);
      }
      return (T) Long.valueOf(1L);
    }
  }
}
