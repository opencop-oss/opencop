// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPayloadBuildResult;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPayloadPart;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPayloadCacheGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPersistenceGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkState;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkPayload;
import org.junit.jupiter.api.Test;

class HistoricalChunkPayloadPersistenceTest {

  private final RecordingPayloadCacheGateway payloadCacheGateway =
      new RecordingPayloadCacheGateway();
  private final RecordingChunkStateGateway chunkStateGateway = new RecordingChunkStateGateway();
  private final RecordingJobStateGateway jobStateGateway = new RecordingJobStateGateway();
  private final HistoricalExecutionProperties properties = new HistoricalExecutionProperties();
  private final HistoricalChunkPersistenceGateway persistence =
      new HistoricalChunkPayloadPersistence(
          payloadCacheGateway, chunkStateGateway, jobStateGateway, properties);

  HistoricalChunkPayloadPersistenceTest() {
    properties.setPayloadTtl(Duration.ofMinutes(5));
    properties.setJobStateTtl(Duration.ofHours(24));
  }

  @Test
  void persistComputedChunk_writesPayloadAndAdvancesDurableState() {
    var job = job();
    var chunk = chunk();
    var payloadBytes = new byte[] {9, 8, 7};
    var payload =
        new HistoricalChunkPayloadBuildResult(
            List.of(
                new HistoricalChunkPayloadPart(
                    chunk.payloadKey(),
                    HistoricalChunkPayload.newBuilder().setChunkKey(chunk.chunkKey()).build(),
                    payloadBytes,
                    payloadBytes.length,
                    0,
                    1,
                    true)));

    persistence.persistComputedChunk(HistoricalExecutionContext.from(job), chunk, payload);

    assertArrayEquals(payloadBytes, payloadCacheGateway.payloads.get(chunk.payloadKey()));
    assertEquals(Duration.ofMinutes(5), payloadCacheGateway.ttls.get(chunk.payloadKey()));
    assertEquals(
        List.of(
            HistoricalChunkStateStatus.CACHED, HistoricalChunkStateStatus.AVAILABLE_FOR_DELIVERY),
        chunkStateGateway.transitions);
    assertEquals(List.of(1), chunkStateGateway.partCounts);
    assertTrue(jobStateGateway.refreshCalled);
  }

  @Test
  void markPayloadMissingForRecovery_prefersRecomputeNeeded() {
    persistence.markPayloadMissingForRecovery(chunk(), true);

    assertEquals(
        List.of(HistoricalChunkStateStatus.RECOMPUTE_NEEDED), chunkStateGateway.transitions);
  }

  @Test
  void markPayloadMissingForRecovery_canFailExplicitly() {
    persistence.markPayloadMissingForRecovery(chunk(), false);

    assertEquals(List.of(HistoricalChunkStateStatus.FAILED), chunkStateGateway.transitions);
  }

  private static HistoricalTracksJobSubmission job() {
    return HistoricalTracksJobSubmission.create(
        UUID.randomUUID(),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440121"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        132,
        List.of());
  }

  private static HistoricalChunkPlanEntry chunk() {
    return new HistoricalChunkPlanEntry(
        0,
        "opencop:history:chunk:SOURCE:test:MINUTE_1:v1:2026-04-06T10:02:00Z",
        "opencop:history:payload:SOURCE:test:MINUTE_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:02:00Z",
        "opencop:history:chunk-state:SOURCE:test:MINUTE_1:v1:v1_latest_in_bucket:filters:2026-04-06T10:02:00Z",
        null,
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T10:03:00Z"));
  }

  private static final class RecordingPayloadCacheGateway
      implements HistoricalChunkPayloadCacheGateway {
    private final Map<String, byte[]> payloads = new java.util.HashMap<>();
    private final Map<String, Duration> ttls = new java.util.HashMap<>();

    @Override
    public Map<String, Optional<Duration>> remainingPayloadTtl(Set<String> payloadKeys) {
      return Map.of();
    }

    @Override
    public void put(String payloadKey, byte[] payloadBytes, Duration ttl) {
      payloads.put(payloadKey, payloadBytes);
      ttls.put(payloadKey, ttl);
    }

    @Override
    public Optional<byte[]> find(String payloadKey) {
      return Optional.ofNullable(payloads.get(payloadKey));
    }

    @Override
    public Map<String, Optional<byte[]>> findAll(List<String> payloadKeys) {
      return payloadKeys.stream()
          .collect(
              java.util.stream.Collectors.toMap(
                  key -> key, this::find, (left, right) -> right, java.util.LinkedHashMap::new));
    }
  }

  private static final class RecordingChunkStateGateway implements HistoricalChunkStateGateway {
    private final List<HistoricalChunkStateStatus> transitions = new java.util.ArrayList<>();
    private final List<Integer> partCounts = new java.util.ArrayList<>();

    @Override
    public void initialize(HistoricalChunkPlan plan) {}

    @Override
    public Optional<HistoricalChunkState> findByChunkStateKey(String chunkStateKey) {
      return Optional.empty();
    }

    @Override
    public void transition(String chunkStateKey, HistoricalChunkStateStatus status) {
      transitions.add(status);
    }

    @Override
    public void updatePartCount(String chunkStateKey, int partCount) {
      partCounts.add(partCount);
    }
  }

  private static final class RecordingJobStateGateway implements HistoricalJobStateGateway {
    private boolean refreshCalled;

    @Override
    public void createReceived(HistoricalTracksJobSubmission job) {}

    @Override
    public boolean markQueued(UUID jobId, String queueRole) {
      return true;
    }

    @Override
    public boolean markRunning(UUID jobId, String workerId) {
      return true;
    }

    @Override
    public boolean markPlanning(UUID jobId) {
      return true;
    }

    @Override
    public boolean markPlanned(UUID jobId) {
      return true;
    }

    @Override
    public boolean markCompleted(UUID jobId) {
      return true;
    }

    @Override
    public boolean markDeliveredCompleted(UUID jobId) {
      return true;
    }

    @Override
    public boolean markDeliveryPending(UUID jobId) {
      return true;
    }

    @Override
    public boolean markDeliveryFailed(UUID jobId, String failureReason) {
      return true;
    }

    @Override
    public boolean markFailed(UUID jobId, HistoricalJobStage stage, String failureReason) {
      return true;
    }

    @Override
    public boolean markCancellationRequested(UUID jobId, String reason) {
      return true;
    }

    @Override
    public boolean markCancelled(UUID jobId, String reason) {
      return true;
    }

    @Override
    public boolean refreshActiveTtl(UUID jobId) {
      refreshCalled = true;
      return true;
    }

    @Override
    public Optional<HistoricalJobState> findByJobId(UUID jobId) {
      return Optional.empty();
    }
  }
}
