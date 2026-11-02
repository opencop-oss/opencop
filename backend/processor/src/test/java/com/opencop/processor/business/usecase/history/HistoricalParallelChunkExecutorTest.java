// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.policy.history.HistoricalChunkFailurePolicy;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class HistoricalParallelChunkExecutorTest {

  private static final String ABORT_REASON_FRAGMENT = "failure ratio exceeded threshold";

  @Test
  void execute_processesChunksWithBoundedParallelismAndReturnsSummary() {
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    try {
      var progressPublisher = new RecordingHistoryEventPublisher();
      var active = new AtomicInteger();
      var maxActive = new AtomicInteger();
      var firstTwoStarted = new CountDownLatch(2);
      var operationExecutor =
          operationExecutor(
              chunk -> {
                int activeNow = active.incrementAndGet();
                maxActive.updateAndGet(current -> Math.max(current, activeNow));
                firstTwoStarted.countDown();
                await(firstTwoStarted);
                try {
                  if (chunk.chunkIndex() == 1) {
                    return HistoricalChunkExecutionResult.REUSED_FROM_CACHE;
                  }
                  if (chunk.chunkIndex() == 2) {
                    return HistoricalChunkExecutionResult.FAILED;
                  }
                  return HistoricalChunkExecutionResult.COMPUTED;
                } finally {
                  active.decrementAndGet();
                }
              });
      var executor =
          executor(
              executorService, 2, Integer.MAX_VALUE, 1.0, progressPublisher, operationExecutor);
      var metadata = laneMetadata(planEntries(5));
      var plan = HistoricalChunkPlan.forSubject(metadata, metadata.windowPlan());

      HistoricalChunkExecutionSummary summary = executor.execute(metadata, plan);

      assertEquals(new HistoricalChunkExecutionSummary(3, 1), summary);
      assertTrue(maxActive.get() <= 2, "parallelism exceeded configured bound");
      assertEquals(5, progressPublisher.events.size());
      assertEquals("exec-progress:3:0:1", progressPublisher.events.getLast());
    } finally {
      executorService.shutdownNow();
    }
  }

  @Test
  void execute_createsLaneLocalExecutorsAndEnforcesPerLaneConcurrency() throws Exception {
    var factory = new LaneLocalExecutorFactory();
    var tracker = new LaneConcurrencyTracker();
    var parallelExecutor =
        executor(
            factory::next,
            2,
            Integer.MAX_VALUE,
            1.0,
            new RecordingHistoryEventPublisher(),
            operationExecutorWithConcurrencyTracking(tracker));
    var firstMetadata = laneMetadata(planEntries(4));
    var secondMetadata =
        new HistoricalSubjectExecutionMeta(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440220"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440221"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440222"),
            JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440223"),
            Instant.parse("2026-04-07T10:00:00Z"),
            Instant.parse("2026-04-06T10:02:17Z"),
            Instant.parse("2026-04-06T12:13:02Z"),
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T10:04:00Z"),
            HistoricalResolution.MINUTE_1,
            4,
            List.of(),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440224"),
            new HistoricalChunkWindowPlan(
                Instant.parse("2026-04-06T10:00:00Z"),
                Instant.parse("2026-04-06T10:04:00Z"),
                4,
                HistoricalResolution.MINUTE_1,
                planEntries(4)));
    var firstPlan = HistoricalChunkPlan.forSubject(firstMetadata, firstMetadata.windowPlan());
    var secondPlan = HistoricalChunkPlan.forSubject(secondMetadata, secondMetadata.windowPlan());

    var runner = Executors.newFixedThreadPool(2);
    try {
      Future<HistoricalChunkExecutionSummary> firstResult =
          runner.submit(() -> parallelExecutor.execute(firstMetadata, firstPlan));
      Future<HistoricalChunkExecutionSummary> secondResult =
          runner.submit(() -> parallelExecutor.execute(secondMetadata, secondPlan));

      assertTrue(
          tracker.awaitPeakAtLeast(4, 2_000),
          "expected lane-local executors to execute more than one lane's chunk pool concurrently");
      tracker.release();

      assertEquals(new HistoricalChunkExecutionSummary(4, 0), firstResult.get(3, TimeUnit.SECONDS));
      assertEquals(
          new HistoricalChunkExecutionSummary(4, 0), secondResult.get(3, TimeUnit.SECONDS));
      assertEquals(2, factory.created.size());
      for (ExecutorService perLaneExecutor : factory.created) {
        assertTrue(perLaneExecutor.isShutdown());
      }
    } finally {
      runner.shutdownNow();
    }
  }

  @Test
  void execute_abortsWhenFailureRatioThresholdExceeded() {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    try {
      var observedChunkIndexes = new CopyOnWriteArrayList<Integer>();
      var operationExecutor =
          operationExecutor(
              chunk -> {
                observedChunkIndexes.add(chunk.chunkIndex());
                if (Set.of(0, 2, 4).contains(chunk.chunkIndex())) {
                  return HistoricalChunkExecutionResult.FAILED;
                }
                return HistoricalChunkExecutionResult.COMPUTED;
              });
      var executor =
          executor(
              executorService,
              1,
              Integer.MAX_VALUE,
              0.10,
              new RecordingHistoryEventPublisher(),
              operationExecutor);
      var metadata = laneMetadata(planEntries(20));
      var plan = HistoricalChunkPlan.forSubject(metadata, metadata.windowPlan());

      IllegalStateException thrown =
          assertThrows(IllegalStateException.class, () -> executor.execute(metadata, plan));

      assertTrue(thrown.getMessage().contains(ABORT_REASON_FRAGMENT), thrown.getMessage());
      assertEquals(List.of(0, 1, 2, 3, 4), observedChunkIndexes);
    } finally {
      executorService.shutdownNow();
    }
  }

  @Test
  void execute_cancelsOutstandingFuturesOnThresholdTrip() {
    ExecutorService executorService = Executors.newFixedThreadPool(4);
    try {
      var sleeperStarted = new CountDownLatch(1);
      var sleeperFinished = new CountDownLatch(1);
      var interrupted = new AtomicBoolean();
      var operationExecutor =
          operationExecutor(
              chunk -> {
                if (chunk.chunkIndex() == 0) {
                  sleeperStarted.countDown();
                  try {
                    Thread.sleep(Duration.ofSeconds(5));
                  } catch (InterruptedException e) {
                    interrupted.set(true);
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", e);
                  } finally {
                    sleeperFinished.countDown();
                  }
                  return HistoricalChunkExecutionResult.COMPUTED;
                }
                await(sleeperStarted);
                return HistoricalChunkExecutionResult.FAILED;
              });
      var executor =
          executor(
              executorService, 4, 4, 1.0, new RecordingHistoryEventPublisher(), operationExecutor);
      var metadata = laneMetadata(planEntries(6));
      var plan = HistoricalChunkPlan.forSubject(metadata, metadata.windowPlan());

      assertThrows(IllegalStateException.class, () -> executor.execute(metadata, plan));

      await(sleeperFinished);
      assertTrue(interrupted.get(), "expected outstanding sleeper to be interrupted on trip");
    } finally {
      executorService.shutdownNow();
    }
  }

  @Test
  void execute_propagatesErrorUnwrapped() {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    try {
      var error = new OutOfMemoryError("simulated OOM");
      var operationExecutor =
          operationExecutor(
              chunk -> {
                throw error;
              });
      var executor =
          executor(
              executorService,
              1,
              Integer.MAX_VALUE,
              1.0,
              new RecordingHistoryEventPublisher(),
              operationExecutor);
      var metadata = laneMetadata(planEntries(1));
      var plan = HistoricalChunkPlan.forSubject(metadata, metadata.windowPlan());

      OutOfMemoryError thrown =
          assertThrows(OutOfMemoryError.class, () -> executor.execute(metadata, plan));

      assertSame(error, thrown);
    } finally {
      executorService.shutdownNow();
    }
  }

  @Test
  void execute_whenPlanIsEmpty_returnsZeroSummaryWithoutProgress() {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    try {
      var progressPublisher = new RecordingHistoryEventPublisher();
      var operationExecutor =
          operationExecutor(
              chunk -> {
                throw new AssertionError("empty plan must not execute chunks");
              });
      var executor = executor(executorService, 2, 5, 0.10, progressPublisher, operationExecutor);
      var metadata =
          new HistoricalSubjectExecutionMeta(
              UUID.fromString("550e8400-e29b-41d4-a716-446655440230"),
              UUID.fromString("550e8400-e29b-41d4-a716-446655440231"),
              UUID.fromString("550e8400-e29b-41d4-a716-446655440232"),
              JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
              UUID.fromString("550e8400-e29b-41d4-a716-446655440233"),
              Instant.parse("2026-04-07T10:00:00Z"),
              Instant.parse("2026-04-06T10:02:17Z"),
              Instant.parse("2026-04-06T12:13:02Z"),
              Instant.parse("2026-04-06T10:00:00Z"),
              Instant.parse("2026-04-06T10:00:00Z"),
              HistoricalResolution.MINUTE_1,
              0,
              List.of(),
              SubjectType.SOURCE,
              UUID.fromString("550e8400-e29b-41d4-a716-446655440234"),
              new HistoricalChunkWindowPlan(
                  Instant.parse("2026-04-06T10:00:00Z"),
                  Instant.parse("2026-04-06T10:00:00Z"),
                  0,
                  HistoricalResolution.MINUTE_1,
                  List.of()));
      var plan = HistoricalChunkPlan.forSubject(metadata, metadata.windowPlan());

      HistoricalChunkExecutionSummary summary = executor.execute(metadata, plan);

      assertEquals(new HistoricalChunkExecutionSummary(0, 0), summary);
      assertTrue(progressPublisher.events.isEmpty());
    } finally {
      executorService.shutdownNow();
    }
  }

  @Test
  void execute_processesChunksAndReturnsSummary() {
    ExecutorService executorService = Executors.newFixedThreadPool(2);
    try {
      var progressPublisher = new RecordingHistoryEventPublisher();
      var operationExecutor =
          operationExecutor(
              chunk ->
                  chunk.chunkIndex() == 2
                      ? HistoricalChunkExecutionResult.FAILED
                      : HistoricalChunkExecutionResult.COMPUTED);
      var executor =
          executor(
              executorService, 2, Integer.MAX_VALUE, 1.0, progressPublisher, operationExecutor);
      var metadata = laneMetadata();
      var plan = HistoricalChunkPlan.forSubject(metadata, metadata.windowPlan());

      HistoricalChunkExecutionSummary summary = executor.execute(metadata, plan);

      assertEquals(new HistoricalChunkExecutionSummary(3, 1), summary);
      assertEquals(4, progressPublisher.events.size());
      assertEquals("exec-progress:3:0:1", progressPublisher.events.getLast());
    } finally {
      executorService.shutdownNow();
    }
  }

  private static HistoricalChunkOperationExecutor operationExecutor(
      Function<HistoricalChunkPlanEntry, HistoricalChunkExecutionResult> behavior) {
    HistoricalChunkOperationExecutor mock = mock(HistoricalChunkOperationExecutor.class);
    when(mock.execute(any(), any(), any()))
        .thenAnswer(invocation -> behavior.apply(invocation.getArgument(2)));
    return mock;
  }

  private static HistoricalParallelChunkExecutor executor(
      ExecutorService executorService,
      int maxConcurrentChunksPerJob,
      int maxConsecutiveChunkFailures,
      double maxFailureRatio,
      RecordingHistoryEventPublisher historyEventPublisher,
      HistoricalChunkOperationExecutor chunkOperationExecutor) {
    return executor(
        () -> executorService,
        maxConcurrentChunksPerJob,
        maxConsecutiveChunkFailures,
        maxFailureRatio,
        historyEventPublisher,
        chunkOperationExecutor);
  }

  private static HistoricalParallelChunkExecutor executor(
      Supplier<ExecutorService> executorFactory,
      int maxConcurrentChunksPerJob,
      int maxConsecutiveChunkFailures,
      double maxFailureRatio,
      RecordingHistoryEventPublisher historyEventPublisher,
      HistoricalChunkOperationExecutor chunkOperationExecutor) {
    var computeKeepalive = mock(HistoricalChunkComputeKeepalive.class);
    when(computeKeepalive.execute(any(), any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              var operation =
                  (HistoricalChunkExecutionTelemetry.ChunkOperation) invocation.getArgument(4);
              return operation.execute();
            });
    return new HistoricalParallelChunkExecutor(
        executorFactory,
        maxConcurrentChunksPerJob,
        new HistoricalChunkFailurePolicy(maxConsecutiveChunkFailures, maxFailureRatio),
        new HistoricalChunkCancellationGuard(new NoCancellationJobStateGateway(), null, null),
        new HistoricalChunkDeliveryProgressReporter(historyEventPublisher, null),
        chunkOperationExecutor,
        computeKeepalive);
  }

  private static HistoricalChunkOperationExecutor operationExecutorWithConcurrencyTracking(
      LaneConcurrencyTracker tracker) {
    HistoricalChunkOperationExecutor mock = mock(HistoricalChunkOperationExecutor.class);
    when(mock.execute(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              var metadata = invocation.getArgument(1, HistoricalSubjectExecutionMeta.class);
              tracker.startChunk(metadata.laneJobId());
              try {
                assertTrue(tracker.awaitRelease(2000));
                return HistoricalChunkExecutionResult.COMPUTED;
              } finally {
                tracker.endChunk(metadata.laneJobId());
              }
            });
    return mock;
  }

  private static List<HistoricalChunkPlanEntry> planEntries(int count) {
    List<HistoricalChunkPlanEntry> entries = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      Instant from = Instant.parse("2026-04-06T10:00:00Z").plusSeconds(60L * index);
      entries.add(
          new HistoricalChunkPlanEntry(
              index,
              "chunk-" + index,
              "payload-" + index,
              "state-" + index,
              HistoricalChunkCacheStatus.MISSING,
              from,
              from.plusSeconds(60)));
    }
    return entries;
  }

  private static HistoricalSubjectExecutionMeta laneMetadata() {
    return laneMetadata(planEntries(4));
  }

  private static HistoricalSubjectExecutionMeta laneMetadata(
      List<HistoricalChunkPlanEntry> entries) {
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440200"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440201"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440202"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440203"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        entries.getFirst().from(),
        entries.getLast().to(),
        HistoricalResolution.MINUTE_1,
        entries.size(),
        List.of(),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440204"),
        new HistoricalChunkWindowPlan(
            entries.getFirst().from(),
            entries.getLast().to(),
            entries.size(),
            HistoricalResolution.MINUTE_1,
            entries));
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(2, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static final class LaneLocalExecutorFactory {
    private final List<ExecutorService> created = new ArrayList<>();

    private synchronized ExecutorService next() {
      var executor = Executors.newFixedThreadPool(2);
      created.add(executor);
      return executor;
    }
  }

  private static final class LaneConcurrencyTracker {
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger maxActive = new AtomicInteger();
    private final Map<UUID, AtomicInteger> activeBy = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> maxBy = new ConcurrentHashMap<>();
    private final CountDownLatch release = new CountDownLatch(1);

    private void startChunk(UUID jobId) {
      int now = active.incrementAndGet();
      maxActive.updateAndGet(previous -> Math.max(previous, now));

      int perLaneNow = activeBy.computeIfAbsent(jobId, _ -> new AtomicInteger()).incrementAndGet();
      maxBy.compute(
          jobId,
          (_, currentMax) -> {
            if (currentMax == null) {
              return new AtomicInteger(perLaneNow);
            }
            currentMax.updateAndGet(previous -> Math.max(previous, perLaneNow));
            return currentMax;
          });
    }

    private void endChunk(UUID jobId) {
      active.decrementAndGet();
      AtomicInteger laneActive = activeBy.get(jobId);
      if (laneActive != null) {
        laneActive.decrementAndGet();
      }
    }

    private void release() {
      release.countDown();
    }

    private boolean awaitRelease(long timeoutMillis) throws InterruptedException {
      return release.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    private boolean awaitPeakAtLeast(int expected, long timeoutMillis) {
      long timeoutAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
      while (maxActive.get() < expected && System.nanoTime() < timeoutAt) {
        Thread.onSpinWait();
      }
      return maxActive.get() >= expected;
    }
  }

  private static final class RecordingHistoryEventPublisher implements HistoryEventPublisher {
    private final List<String> events = new CopyOnWriteArrayList<>();

    @Override
    public void publishPlan(
        HistoricalChunkPlan plan, HistoricalExecutionContext executionContext) {}

    @Override
    public void publishProgress(
        HistoricalChunkPlan plan,
        HistoricalTracksJobSubmission job,
        long computedChunks,
        long deliveredChunks,
        long failedChunks) {}

    @Override
    public void publishExecutionProgress(
        HistoricalChunkPlan plan,
        HistoricalExecutionContext executionContext,
        long computedChunks,
        long failedChunks) {
      events.add("exec-progress:%d:%d:%d".formatted(computedChunks, 0, failedChunks));
    }

    @Override
    public void publishCompleted(
        HistoricalChunkPlan plan,
        HistoricalTracksJobSubmission job,
        long computedChunks,
        long deliveredChunks,
        long failedChunks) {}

    @Override
    public void publishFailed(
        HistoricalTracksJobSubmission job, HistoricalJobStage stage, String failureReason) {}
  }

  private static final class NoCancellationJobStateGateway implements HistoricalJobStateGateway {
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
}
