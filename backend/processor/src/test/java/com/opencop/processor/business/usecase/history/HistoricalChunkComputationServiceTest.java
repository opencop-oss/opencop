// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPayloadBuildResult;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPayloadBuilder;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkSampledResult;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.policy.history.HistoricalCacheIdentity;
import com.opencop.processor.business.policy.history.V1LatestInBucketSampler;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPayloadCacheGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPersistenceGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkReader;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkReadyNotifier;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkState;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class HistoricalChunkComputationServiceTest {

  private final RecordingReadGateway readGateway = new RecordingReadGateway();
  private final RecordingSampler sampler = new RecordingSampler();
  private final RecordingPayloadBuilder payloadBuilder = new RecordingPayloadBuilder();
  private final RecordingPayloadPersistence payloadPersistence = new RecordingPayloadPersistence();
  private final RecordingChunkStateGateway chunkStateGateway = new RecordingChunkStateGateway();
  private final RecordingChunkReadyNotifier chunkReadyNotifier = new RecordingChunkReadyNotifier();
  private final HistoricalChunkAvailabilityHandler availabilityHandler =
      new HistoricalChunkAvailabilityHandler(
          payloadPersistence,
          new NoopPayloadCacheGateway(),
          chunkStateGateway,
          new NoopJobStateGateway(),
          chunkReadyNotifier,
          new HistoricalCacheIdentity(new tools.jackson.databind.ObjectMapper()));
  private final HistoricalChunkComputationService service =
      new HistoricalChunkComputationService(
          readGateway,
          sampler,
          payloadBuilder,
          payloadPersistence,
          chunkStateGateway,
          availabilityHandler);

  @Test
  void metadataComputeMissingChunk_readsAllPagesSamplesLatestEventAndPersistsPayload() {
    var job = job();
    var chunk = chunk("chunk-paged", "payload-paged", "state-paged");
    var plan = plan(job, List.of(chunk));
    var early = event("obj-1", "2026-04-06T10:02:03Z");
    var later = event("obj-1", "2026-04-06T10:02:55Z");
    readGateway.rows = List.of(early, later);

    var executionContext = HistoricalExecutionContext.from(job);
    service.computeMissingChunk(executionContext, plan, chunk);

    assertEquals(1, readGateway.calls);
    assertEquals(1, sampler.sampleCalls);
    assertEquals(2, sampler.acceptedEvents);
    assertEquals(1, payloadBuilder.buildCalls);
    assertEquals(1, payloadBuilder.lastSampledChunk.pointCount());
    assertEquals(1, payloadBuilder.lastSampledChunk.objectCount());
    assertEquals(later.eventId(), payloadBuilder.lastSampledChunk.points().getFirst().eventId());
    assertEquals(1, payloadPersistence.persistCalls);
    assertEquals(executionContext, payloadPersistence.persistedContext);
    assertSame(chunk, payloadPersistence.persistedChunk);
    assertEquals(List.of(HistoricalChunkStateStatus.COMPUTING), chunkStateGateway.transitions);
    assertEquals(
        List.of(new NotificationRecord(job.jobId(), "chunk-paged", false, 1)),
        chunkReadyNotifier.notifications);
  }

  @Test
  void computeMissingChunk_includesCarriedForwardActiveObjectWhenNoInChunkEventExists() {
    var job = job();
    var chunk = chunk("chunk-carried-forward", "payload-carried-forward", "state-carried-forward");
    var plan = plan(job, List.of(chunk));
    var carriedForward = event("obj-1", "2026-04-06T10:01:40Z");
    readGateway.rows = List.of(carriedForward);

    service.computeMissingChunk(HistoricalExecutionContext.from(job), plan, chunk);

    assertEquals(1, readGateway.calls);
    assertEquals(1, payloadBuilder.buildCalls);
    assertEquals(1, payloadBuilder.lastSampledChunk.pointCount());
    assertEquals(1, payloadBuilder.lastSampledChunk.objectCount());
    assertEquals(
        carriedForward.eventId(), payloadBuilder.lastSampledChunk.points().getFirst().eventId());
    assertEquals(
        Instant.parse("2026-04-06T10:01:00Z"),
        payloadBuilder.lastSampledChunk.points().getFirst().bucketStart());
  }

  @Test
  void computeMissingChunk_marksChunkFailedAndRethrowsRuntimeFailure() {
    var job = job();
    var chunk = chunk("chunk-fails", "payload-fails", "state-fails");
    var plan = plan(job, List.of(chunk));
    readGateway.rows = List.of(event("obj-1", "2026-04-06T10:02:03Z"));
    payloadBuilder.failNextBuild = true;

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> service.computeMissingChunk(HistoricalExecutionContext.from(job), plan, chunk));

    assertEquals("simulated build failure", thrown.getMessage());
    assertEquals(
        List.of(HistoricalChunkStateStatus.COMPUTING, HistoricalChunkStateStatus.FAILED),
        chunkStateGateway.transitions);
    assertEquals(List.of(), chunkReadyNotifier.notifications);
  }

  @Test
  void computeMissingChunk_readsAllPagesSamplesLatestEventAndPersistsPayload() {
    var laneMetadata = laneMetadata();
    var chunk = chunk("chunk-lane-paged", "payload-lane-paged", "state-lane-paged");
    var plan = HistoricalChunkPlan.forSubject(laneMetadata, laneWindowPlan(List.of(chunk)));
    var early = event("obj-1", "2026-04-06T10:02:03Z");
    var later = event("obj-1", "2026-04-06T10:02:55Z");
    readGateway.rows = List.of(early, later);

    var executionContext = HistoricalExecutionContext.from(laneMetadata);
    service.computeMissingChunk(executionContext, plan, chunk);

    assertEquals(1, readGateway.calls);
    assertEquals(executionContext, readGateway.lastReadContext);
    assertEquals(1, sampler.sampleCalls);
    assertEquals(2, sampler.acceptedEvents);
    assertEquals(1, payloadBuilder.buildCalls);
    assertEquals(1, payloadBuilder.lastSampledChunk.pointCount());
    assertEquals(1, payloadBuilder.lastSampledChunk.objectCount());
    assertEquals(later.eventId(), payloadBuilder.lastSampledChunk.points().getFirst().eventId());
    assertEquals(1, payloadPersistence.persistCalls);
    assertEquals(executionContext, payloadPersistence.persistedContext);
    assertSame(chunk, payloadPersistence.persistedChunk);
    assertEquals(
        List.of(new NotificationRecord(laneMetadata.laneJobId(), chunk.chunkKey(), false, 1)),
        chunkReadyNotifier.notifications);
  }

  private static HistoricalChunkPlanEntry chunk(
      String chunkKey, String payloadKey, String chunkStateKey) {
    return new HistoricalChunkPlanEntry(
        0,
        chunkKey,
        payloadKey,
        chunkStateKey,
        HistoricalChunkCacheStatus.MISSING,
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T10:03:00Z"));
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

  private static HistoricalChunkPlan plan(
      HistoricalTracksJobSubmission job, List<HistoricalChunkPlanEntry> entries) {
    return HistoricalChunkPlan.forSubject(laneMetadata(job, entries), laneWindowPlan(entries));
  }

  private static final UUID TEST_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-4466554400aa");

  private static HistoricalSourceEvent event(String objectId, String playbackAt) {
    Instant playback = Instant.parse(playbackAt);
    return new HistoricalSourceEvent(
        UUID.randomUUID(),
        TEST_SOURCE_ID,
        objectId,
        playback,
        playback,
        playback,
        playback.plusSeconds(60),
        """
        {
          "type": "Feature",
          "geometry": { "type": "Point", "coordinates": [-73.5, 45.5] },
          "properties": {
            "opencop": {
              "id": "550e8400-e29b-41d4-a716-446655440001",
              "objectId": "%s",
              "source": "550e8400-e29b-41d4-a716-4466554400aa",
              "displayName": "Object %s",
              "timestampz": { "time": %s, "start": %s, "stale": %s },
              "element": { "type": "a-f-G-U-C", "speed": 12.5, "remarks": "alpha" }
            }
          }
        }
        """
            .formatted(
                objectId,
                objectId,
                playback.toEpochMilli(),
                playback.toEpochMilli(),
                playback.toEpochMilli() + 60000));
  }

  private record NotificationRecord(UUID jobId, String chunkKey, boolean cached, int partCount) {}

  private static final class RecordingReadGateway implements HistoricalChunkReader {
    private int calls;
    private HistoricalExecutionContext lastReadContext;
    private List<HistoricalSourceEvent> rows = List.of();

    @Override
    public void readChunk(
        HistoricalExecutionContext executionContext,
        HistoricalChunkPlanEntry chunk,
        Consumer<HistoricalSourceEvent> rowConsumer) {
      calls++;
      lastReadContext = executionContext;
      rows.forEach(rowConsumer);
    }
  }

  private static final class RecordingSampler extends V1LatestInBucketSampler {
    private int sampleCalls;
    private int acceptedEvents;

    @Override
    protected void onAccumulatorCreated(
        HistoricalChunkPlanEntry chunk, HistoricalResolution resolution) {
      sampleCalls++;
    }

    @Override
    protected void onEventAccepted(HistoricalSourceEvent event) {
      acceptedEvents++;
    }
  }

  private static final class RecordingPayloadBuilder extends HistoricalChunkPayloadBuilder {
    private int buildCalls;
    private boolean failNextBuild;
    private HistoricalChunkSampledResult lastSampledChunk;

    private RecordingPayloadBuilder() {
      super(
          new tools.jackson.databind.ObjectMapper(),
          new HistoricalCacheIdentity(new tools.jackson.databind.ObjectMapper()),
          131_072,
          524_288);
    }

    @Override
    public HistoricalChunkPayloadBuildResult buildPayload(
        HistoricalChunkSampledResult sampledChunk, String basePayloadKey) {
      buildCalls++;
      lastSampledChunk = sampledChunk;
      if (failNextBuild) {
        failNextBuild = false;
        throw new IllegalStateException("simulated build failure");
      }
      return super.buildPayload(sampledChunk, basePayloadKey);
    }
  }

  private static final class RecordingPayloadPersistence
      implements HistoricalChunkPersistenceGateway {
    private int persistCalls;
    private HistoricalExecutionContext persistedContext;
    private HistoricalChunkPlanEntry persistedChunk;

    @Override
    public void persistComputedChunk(
        HistoricalExecutionContext executionContext,
        HistoricalChunkPlanEntry chunk,
        HistoricalChunkPayloadBuildResult payload) {
      persistCalls++;
      persistedContext = executionContext;
      persistedChunk = chunk;
    }

    @Override
    public void markPayloadMissingForRecovery(
        HistoricalChunkPlanEntry chunk, boolean recoveryPossible) {}
  }

  private static final class NoopPayloadCacheGateway implements HistoricalChunkPayloadCacheGateway {

    @Override
    public Map<String, Optional<Duration>> remainingPayloadTtl(Set<String> payloadKeys) {
      return Map.of();
    }

    @Override
    public void put(String payloadKey, byte[] payloadBytes, Duration ttl) {}

    @Override
    public Optional<byte[]> find(String payloadKey) {
      return Optional.empty();
    }

    @Override
    public Map<String, Optional<byte[]>> findAll(List<String> payloadKeys) {
      return Map.of();
    }
  }

  private static final class RecordingChunkStateGateway implements HistoricalChunkStateGateway {
    private final List<HistoricalChunkStateStatus> transitions = new ArrayList<>();

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
    public void updatePartCount(String chunkStateKey, int partCount) {}
  }

  private static final class NoopJobStateGateway implements HistoricalJobStateGateway {

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
      return true;
    }

    @Override
    public Optional<HistoricalJobState> findByJobId(UUID jobId) {
      return Optional.empty();
    }
  }

  private static final class RecordingChunkReadyNotifier implements HistoricalChunkReadyNotifier {
    private final List<NotificationRecord> notifications = new ArrayList<>();

    @Override
    public void notifyChunkReady(
        HistoricalExecutionContext executionContext,
        HistoricalChunkPlanEntry chunk,
        boolean cached,
        int partCount) {
      notifications.add(
          new NotificationRecord(executionContext.jobId(), chunk.chunkKey(), cached, partCount));
    }
  }

  private static HistoricalSubjectExecutionMeta laneMetadata() {
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440299"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440299"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        0L,
        List.of(),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440121"),
        new HistoricalChunkWindowPlan(
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            1,
            HistoricalResolution.MINUTE_1,
            List.of()));
  }

  private static HistoricalSubjectExecutionMeta laneMetadata(
      HistoricalTracksJobSubmission job, List<HistoricalChunkPlanEntry> entries) {
    var subject = job.subjects().getFirst();
    return new HistoricalSubjectExecutionMeta(
        subject.jobId(),
        job.jobId(),
        job.requestId(),
        job.issuer(),
        job.requestedBy(),
        job.requestedAt(),
        job.requestedFrom(),
        job.requestedTo(),
        job.effectiveFrom(),
        job.effectiveTo(),
        job.resolution(),
        job.plannedChunks(),
        job.filters(),
        subject.subjectType(),
        subject.subjectId(),
        laneWindowPlan(entries));
  }

  private static HistoricalChunkWindowPlan laneWindowPlan(List<HistoricalChunkPlanEntry> entries) {
    return new HistoricalChunkWindowPlan(
        entries.getFirst().from(),
        entries.getLast().to(),
        entries.size(),
        HistoricalResolution.MINUTE_1,
        entries);
  }
}
