// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.policy.history.HistoricalChunkPlanner;
import com.opencop.processor.ports.technical.gateway.history.HistoricalSubjectLaneExecutionGateway;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HistoricalSubjectLaneSchedulerTest {

  private static final UUID REQUEST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440200");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440201");
  private static final UUID REQUESTED_BY = UUID.fromString("550e8400-e29b-41d4-a716-446655440202");
  private static final Instant REQUESTED_AT = Instant.parse("2026-04-06T10:00:00Z");
  private static final Instant REQUESTED_FROM = Instant.parse("2026-04-06T10:05:00Z");
  private static final Instant REQUESTED_TO = Instant.parse("2026-04-06T11:00:00Z");
  private static final Instant EFFECTIVE_FROM = Instant.parse("2026-04-06T10:05:00Z");
  private static final Instant EFFECTIVE_TO = Instant.parse("2026-04-06T11:00:00Z");
  private static final long PLANNED_CHUNKS = 55L;

  @Test
  void scheduleRespectsActiveSubjectConcurrencyAndStartsBacklogLanesInOrder()
      throws InterruptedException {
    var job = job(subjects());
    HistoricalChunkWindowPlan sharedWindowPlan = sharedWindowPlan(job);
    TrackingChunkPlanner planner = new TrackingChunkPlanner(sharedWindowPlan);
    ControlledLaneExecutionGateway laneExecutionGateway =
        new ControlledLaneExecutionGateway(job.subjects());
    HistoricalSubjectLaneScheduler scheduler = scheduler(laneExecutionGateway, planner, 2);

    var execution =
        CompletableFuture.runAsync(
            () -> {
              assertDoesNotThrow(() -> scheduler.execute(job));
            });
    awaitCountDown(laneExecutionGateway.startCountDownLatch);
    assertEquals(2, laneExecutionGateway.maxActiveObserved());
    laneExecutionGateway.release(0);
    awaitCompletion(0, laneExecutionGateway.completedLatches);
    awaitStarted(3, laneExecutionGateway.startOrder);
    assertEquals(0, laneExecutionGateway.completionOrder.get(0));
    assertEquals(2, laneExecutionGateway.startOrder.get(2));

    laneExecutionGateway.releaseAll();
    execution.join();

    assertEquals(List.of(0, 1, 2, 3, 4), laneExecutionGateway.startOrder);
    assertEquals(2, laneExecutionGateway.maxActiveObserved());
    assertFalse(laneExecutionGateway.hadFailure());
    assertEquals(1, planner.windowsPlanned());
    assertSubmittedMetadataMatchesSubjects(
        job, sharedWindowPlan, laneExecutionGateway.submittedMetadata());
  }

  @Test
  void scheduleStopsStartingQueuedLanesAfterFailure() throws InterruptedException {
    var job = job(subjects());
    ControlledLaneExecutionGateway laneExecutionGateway =
        new ControlledLaneExecutionGateway(job.subjects());
    laneExecutionGateway.failAt(1);
    HistoricalSubjectLaneScheduler scheduler = scheduler(laneExecutionGateway, 2);

    AtomicReference<RuntimeException> executionFailure = new AtomicReference<>();
    var execution =
        CompletableFuture.runAsync(
            () -> {
              try {
                scheduler.execute(job);
              } catch (RuntimeException e) {
                executionFailure.set(e);
              }
            });

    awaitCountDown(laneExecutionGateway.startCountDownLatch);
    laneExecutionGateway.release(1);
    // Lane 1 fails while lane 0 is still blocked, so the scheduler tears itself down
    // (cancelling and interrupting the remaining lane) rather than draining the backlog.
    // Releasing lane 0 here would let it complete normally and free a slot before the
    // failure is observed, which legitimately admits lane 2 and races the assertion.
    execution.join();
    assertEquals(2, laneExecutionGateway.maxActiveObserved());
    assertEquals(IllegalArgumentException.class, executionFailure.get().getCause().getClass());
    assertTrue(executionFailure.get().getMessage().contains("lane execution failed"));
    assertEquals(List.of(0, 1), laneExecutionGateway.startOrder);
  }

  @Test
  void scheduleExitsPromptlyWhenInterruptedWhileWaitingForActiveLane() throws InterruptedException {
    var job = job(List.of(subjects().getFirst()));
    ControlledLaneExecutionGateway laneExecutionGateway =
        new ControlledLaneExecutionGateway(job.subjects());
    HistoricalSubjectLaneScheduler scheduler = scheduler(laneExecutionGateway, 1);
    AtomicReference<RuntimeException> failure = new AtomicReference<>();

    Thread schedulerThread =
        new Thread(
            () -> {
              try {
                scheduler.execute(job);
              } catch (RuntimeException e) {
                failure.set(e);
              }
            });
    schedulerThread.start();

    awaitCountDown(laneExecutionGateway.startCountDownLatch);
    schedulerThread.interrupt();
    schedulerThread.join(1_000);

    assertFalse(schedulerThread.isAlive(), "scheduler should exit after interruption");
    assertNotNull(failure.get(), "interrupted scheduler should report cancellation failure");
  }

  private static HistoricalSubjectLaneScheduler scheduler(
      HistoricalSubjectLaneExecutionGateway gateway, int maxConcurrentSubjects) {
    return scheduler(gateway, new HistoricalChunkPlanner(), maxConcurrentSubjects);
  }

  private static HistoricalSubjectLaneScheduler scheduler(
      HistoricalSubjectLaneExecutionGateway gateway,
      HistoricalChunkPlanner planner,
      int maxConcurrentSubjects) {
    HistoricalExecutionProperties properties = new HistoricalExecutionProperties();
    properties.setMaxConcurrentSubjectsPerJob(maxConcurrentSubjects);
    return new HistoricalSubjectLaneScheduler(
        gateway, new HistoricalSubjectJobFactory(), planner, properties);
  }

  private static HistoricalTracksJobSubmission job(List<HistoricalTracksSubject> subjects) {
    return new HistoricalTracksJobSubmission(
        UUID.randomUUID(),
        REQUEST_ID,
        JobIssuer.user(USER_ID),
        subjects,
        REQUESTED_BY,
        REQUESTED_AT,
        REQUESTED_FROM,
        REQUESTED_TO,
        EFFECTIVE_FROM,
        EFFECTIVE_TO,
        HistoricalResolution.MINUTE_1,
        PLANNED_CHUNKS,
        List.of());
  }

  private static List<HistoricalTracksSubject> subjects() {
    return List.of(
        subject(
            "550e8400-e29b-41d4-a716-446655440210",
            "550e8400-e29b-41d4-a716-446655440001",
            SubjectType.SOURCE),
        subject(
            "550e8400-e29b-41d4-a716-446655440220",
            "550e8400-e29b-41d4-a716-446655440002",
            SubjectType.LAYER),
        subject(
            "550e8400-e29b-41d4-a716-446655440230",
            "550e8400-e29b-41d4-a716-446655440003",
            SubjectType.SOURCE),
        subject(
            "550e8400-e29b-41d4-a716-446655440240",
            "550e8400-e29b-41d4-a716-446655440004",
            SubjectType.LAYER),
        subject(
            "550e8400-e29b-41d4-a716-446655440250",
            "550e8400-e29b-41d4-a716-446655440005",
            SubjectType.SOURCE));
  }

  private static HistoricalTracksSubject subject(
      String jobId, String subjectId, SubjectType subjectType) {
    return new HistoricalTracksSubject(
        UUID.fromString(jobId), subjectType, UUID.fromString(subjectId));
  }

  private static void assertCountDown(CountDownLatch latch) throws InterruptedException {
    assertTrue(latch.await(3, TimeUnit.SECONDS));
  }

  private static void awaitCountDown(CountDownLatch latch) throws InterruptedException {
    assertCountDown(latch);
  }

  private static void awaitCompletion(int laneIndex, List<CountDownLatch> latches)
      throws InterruptedException {
    assertCountDown(latches.get(laneIndex));
  }

  private static void awaitStarted(int expectedStartCount, List<Integer> startOrder)
      throws InterruptedException {
    long timeoutNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (System.nanoTime() < timeoutNanos) {
      if (startOrder.size() >= expectedStartCount) {
        return;
      }
      Thread.sleep(10);
    }
    throw new AssertionError(
        "Expected at least %d lanes to have started, observed %d"
            .formatted(expectedStartCount, startOrder.size()));
  }

  private static HistoricalChunkWindowPlan sharedWindowPlan(
      HistoricalTracksJobSubmission parentSubmission) {
    return new HistoricalChunkWindowPlan(
        parentSubmission.effectiveFrom(),
        parentSubmission.effectiveTo(),
        parentSubmission.plannedChunks(),
        parentSubmission.resolution(),
        List.of(
            new HistoricalChunkPlanEntry(
                0,
                "chunk-key",
                UUID.randomUUID().toString(),
                null,
                null,
                parentSubmission.effectiveFrom(),
                parentSubmission.effectiveFrom().plusSeconds(60))));
  }

  private static void assertSubmittedMetadataMatchesSubjects(
      HistoricalTracksJobSubmission parentSubmission,
      HistoricalChunkWindowPlan expectedWindowPlan,
      List<HistoricalSubjectExecutionMeta> submittedMetadata) {
    assertEquals(parentSubmission.subjects().size(), submittedMetadata.size());
    for (int index = 0; index < parentSubmission.subjects().size(); index++) {
      HistoricalTracksSubject sourceSubject = parentSubmission.subjects().get(index);
      HistoricalSubjectExecutionMeta meta = submittedMetadata.get(index);
      assertEquals(sourceSubject.jobId(), meta.laneJobId());
      assertEquals(parentSubmission.jobId(), meta.parentJobId());
      assertEquals(parentSubmission.requestId(), meta.requestId());
      assertEquals(parentSubmission.issuer(), meta.issuer());
      assertEquals(parentSubmission.requestedBy(), meta.requestedBy());
      assertEquals(parentSubmission.requestedAt(), meta.requestedAt());
      assertEquals(parentSubmission.requestedFrom(), meta.requestedFrom());
      assertEquals(parentSubmission.requestedTo(), meta.requestedTo());
      assertEquals(parentSubmission.effectiveFrom(), meta.effectiveFrom());
      assertEquals(parentSubmission.effectiveTo(), meta.effectiveTo());
      assertEquals(parentSubmission.resolution(), meta.resolution());
      assertEquals(parentSubmission.plannedChunks(), meta.plannedChunks());
      assertEquals(parentSubmission.filters(), meta.filters());
      assertEquals(sourceSubject.subjectType(), meta.subjectType());
      assertEquals(sourceSubject.subjectId(), meta.subjectId());
      assertEquals(expectedWindowPlan, meta.windowPlan());
    }
  }

  private static final class ControlledLaneExecutionGateway
      implements HistoricalSubjectLaneExecutionGateway {

    private final List<UUID> orderedSubjectJobIds;
    private final List<CountDownLatch> runLatches;
    private final List<CountDownLatch> completedLatches;
    private int failingIndex;
    private final AtomicInteger activeLanes = new AtomicInteger();
    private final AtomicInteger maxActive = new AtomicInteger();
    private final List<Integer> startOrder = new CopyOnWriteArrayList<>();
    private final List<Integer> completionOrder = new CopyOnWriteArrayList<>();
    private final List<HistoricalSubjectExecutionMeta> submittedMetadata =
        new CopyOnWriteArrayList<>();
    private volatile boolean failed;
    private final CountDownLatch startCountDownLatch;

    private ControlledLaneExecutionGateway(List<HistoricalTracksSubject> orderedSubjects) {
      this.orderedSubjectJobIds =
          orderedSubjects.stream().map(HistoricalTracksSubject::jobId).toList();
      this.failingIndex = -1;
      int size = orderedSubjects.size();
      this.startCountDownLatch = new CountDownLatch(Math.min(2, size));
      this.runLatches = new ArrayList<>(size);
      this.completedLatches = new ArrayList<>(size);
      for (int index = 0; index < size; index++) {
        runLatches.add(new CountDownLatch(1));
        completedLatches.add(new CountDownLatch(1));
      }
    }

    void failAt(int index) {
      this.failingIndex = index;
    }

    void release(int index) {
      runLatches.get(index).countDown();
    }

    void releaseAll() {
      runLatches.forEach(CountDownLatch::countDown);
    }

    int maxActiveObserved() {
      return maxActive.get();
    }

    boolean hadFailure() {
      return failed;
    }

    List<HistoricalSubjectExecutionMeta> submittedMetadata() {
      return submittedMetadata;
    }

    @Override
    public void execute(HistoricalSubjectExecutionMeta laneMetadata, Runnable laneStarted) {
      int subjectIndex = orderedSubjectJobIds.indexOf(laneMetadata.laneJobId());
      if (subjectIndex < 0) {
        throw new IllegalArgumentException("Unknown metadata lane in test gateway");
      }
      synchronized (this) {
        startOrder.add(subjectIndex);
      }
      submittedMetadata.add(laneMetadata);
      int active = activeLanes.incrementAndGet();
      maxActive.updateAndGet(value -> Math.max(value, active));
      laneStarted.run();
      startCountDownLatch.countDown();
      try {
        runLatches.get(subjectIndex).await();
        if (subjectIndex == failingIndex) {
          failed = true;
          throw new IllegalArgumentException("simulated lane failure");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("lane interrupted");
      } finally {
        completionOrder.add(subjectIndex);
        completedLatches.get(subjectIndex).countDown();
        activeLanes.decrementAndGet();
      }
    }
  }

  private static final class TrackingChunkPlanner extends HistoricalChunkPlanner {
    private final AtomicInteger windowsPlanned = new AtomicInteger();
    private final HistoricalChunkWindowPlan sharedWindowPlan;

    private TrackingChunkPlanner(HistoricalChunkWindowPlan sharedWindowPlan) {
      this.sharedWindowPlan = sharedWindowPlan;
    }

    @Override
    public HistoricalChunkWindowPlan planWindow(
        HistoricalTracksJobSubmission historicalSubmission) {
      windowsPlanned.incrementAndGet();
      return sharedWindowPlan;
    }

    int windowsPlanned() {
      return windowsPlanned.get();
    }
  }
}
