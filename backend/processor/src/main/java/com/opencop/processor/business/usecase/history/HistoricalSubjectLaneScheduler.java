// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.policy.history.HistoricalChunkPlanner;
import com.opencop.processor.ports.technical.gateway.history.HistoricalSubjectLaneExecutionGateway;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class HistoricalSubjectLaneScheduler {

  private final HistoricalSubjectLaneExecutionGateway laneExecutionGateway;
  private final HistoricalSubjectJobFactory subjectJobFactory;
  private final HistoricalChunkPlanner chunkPlanner;
  private final int maxConcurrentSubjectsPerJob;

  public HistoricalSubjectLaneScheduler(
      HistoricalSubjectLaneExecutionGateway laneExecutionGateway,
      HistoricalSubjectJobFactory subjectJobFactory,
      HistoricalChunkPlanner chunkPlanner,
      HistoricalExecutionProperties historicalExecutionProperties) {
    int maxConcurrentSubjectsPerJob =
        historicalExecutionProperties.getMaxConcurrentSubjectsPerJob();
    if (maxConcurrentSubjectsPerJob < 1) {
      throw new IllegalArgumentException("maxConcurrentSubjectsPerJob must be >= 1");
    }
    this.laneExecutionGateway = laneExecutionGateway;
    this.subjectJobFactory = subjectJobFactory;
    this.chunkPlanner = chunkPlanner;
    this.maxConcurrentSubjectsPerJob = maxConcurrentSubjectsPerJob;
  }

  public void execute(HistoricalTracksJobSubmission historicalSubmission) {
    HistoricalChunkWindowPlan windowPlan = chunkPlanner.planWindow(historicalSubmission);
    List<HistoricalSubjectExecutionMeta> laneSubmissions =
        subjectJobFactory.create(historicalSubmission, windowPlan);
    if (laneSubmissions.isEmpty()) {
      return;
    }

    ExecutorService laneExecutor =
        Executors.newFixedThreadPool(Math.max(maxConcurrentSubjectsPerJob, 1));
    LaneStartOrderCoordinator laneStartOrderCoordinator = new LaneStartOrderCoordinator();
    AtomicBoolean stopStartingLanes = new AtomicBoolean();
    AtomicReference<RuntimeException> laneFailure = new AtomicReference<>();
    List<CompletableFuture<Void>> activeLanes = new ArrayList<>();
    int nextLaneIndex = 0;

    try {
      throwIfInterrupted();
      while (nextLaneIndex < laneSubmissions.size()
          && activeLanes.size() < maxConcurrentSubjectsPerJob) {
        LaneExecution laneExecution =
            start(
                laneSubmissions.get(nextLaneIndex),
                nextLaneIndex,
                stopStartingLanes,
                laneFailure,
                laneExecutor,
                laneStartOrderCoordinator);
        nextLaneIndex++;
        activeLanes.add(laneExecution.completion);
      }

      while (!activeLanes.isEmpty()) {
        waitForNextLaneToFinish(activeLanes);
        throwIfInterrupted();
        activeLanes.removeIf(CompletableFuture::isDone);
        if (laneFailure.get() != null) {
          stopStartingLanes.set(true);
          activeLanes.forEach(activeLane -> activeLane.cancel(true));
          activeLanes.clear();
          break;
        }
        while (nextLaneIndex < laneSubmissions.size()
            && !stopStartingLanes.get()
            && activeLanes.size() < maxConcurrentSubjectsPerJob) {
          throwIfInterrupted();
          LaneExecution laneExecution =
              start(
                  laneSubmissions.get(nextLaneIndex),
                  nextLaneIndex,
                  stopStartingLanes,
                  laneFailure,
                  laneExecutor,
                  laneStartOrderCoordinator);
          nextLaneIndex++;
          activeLanes.add(laneExecution.completion);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      stopStartingLanes.set(true);
      activeLanes.forEach(activeLane -> activeLane.cancel(true));
      throw new IllegalStateException("Historical subject lane scheduling was interrupted", e);
    } finally {
      laneExecutor.shutdownNow();
    }

    RuntimeException firstFailure = laneFailure.get();
    if (firstFailure != null) {
      throw new IllegalStateException("Historical subject lane execution failed", firstFailure);
    }
  }

  private static final class LaneExecution {
    private final CompletableFuture<Void> completion;

    private LaneExecution(CompletableFuture<Void> completion) {
      this.completion = completion;
    }
  }

  private LaneExecution start(
      HistoricalSubjectExecutionMeta laneMetadata,
      int laneIndex,
      AtomicBoolean stopStartingLanes,
      AtomicReference<RuntimeException> laneFailure,
      ExecutorService laneExecutor,
      LaneStartOrderCoordinator laneStartOrderCoordinator) {
    CompletableFuture<Void> completion =
        CompletableFuture.runAsync(
            () -> {
              run(
                  laneMetadata,
                  laneIndex,
                  stopStartingLanes,
                  laneFailure,
                  laneStartOrderCoordinator);
            },
            laneExecutor);
    return new LaneExecution(completion);
  }

  private static void waitForNextLaneToFinish(List<CompletableFuture<Void>> activeLanes)
      throws InterruptedException {
    if (activeLanes.isEmpty()) {
      return;
    }
    while (activeLanes.stream().noneMatch(CompletableFuture::isDone)) {
      CompletableFuture<Object> nextCompleted =
          CompletableFuture.anyOf(activeLanes.toArray(new CompletableFuture[0]));
      try {
        nextCompleted.get(100, TimeUnit.MILLISECONDS);
      } catch (TimeoutException ignored) {
        throwIfInterrupted();
      } catch (ExecutionException ignored) {
        // Failure is tracked in laneFailure; this method only coordinates launch timing.
      }
    }
  }

  private static void throwIfInterrupted() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException();
    }
  }

  private void run(
      HistoricalSubjectExecutionMeta laneMetadata,
      int laneIndex,
      AtomicBoolean stopStartingLanes,
      AtomicReference<RuntimeException> laneFailure,
      LaneStartOrderCoordinator laneStartOrderCoordinator) {
    AtomicBoolean laneAcknowledged = new AtomicBoolean();
    try {
      laneStartOrderCoordinator.waitForTurn(laneIndex);

      Runnable laneStarted =
          () -> {
            if (laneAcknowledged.compareAndSet(false, true)) {
              laneStartOrderCoordinator.acknowledgeLaneStart(laneIndex);
            }
          };

      laneExecutionGateway.execute(laneMetadata, laneStarted);

      if (!laneAcknowledged.get()) {
        RuntimeException missedCallback =
            new IllegalStateException(
                "Lane execution gateway did not acknowledge lane start for lane " + laneIndex);
        laneFailure.compareAndSet(null, missedCallback);
        stopStartingLanes.set(true);
        throw missedCallback;
      }
    } catch (RuntimeException e) {
      if (!laneAcknowledged.getAndSet(true)) {
        laneStartOrderCoordinator.acknowledgeLaneStart(laneIndex);
      }
      laneFailure.compareAndSet(null, e);
      stopStartingLanes.set(true);
      throw e;
    } catch (InterruptedException e) {
      RuntimeException interrupted =
          new IllegalStateException(
              "Lane execution was interrupted while waiting for invocation order", e);
      if (!laneAcknowledged.getAndSet(true)) {
        laneStartOrderCoordinator.acknowledgeLaneStart(laneIndex);
      }
      laneFailure.compareAndSet(null, interrupted);
      stopStartingLanes.set(true);
      throw interrupted;
    }
  }

  private static final class LaneStartOrderCoordinator {
    private int nextLaneIndex;
    private final Object monitor = new Object();

    private void waitForTurn(int laneIndex) throws InterruptedException {
      synchronized (monitor) {
        while (laneIndex != nextLaneIndex) {
          monitor.wait();
        }
      }
    }

    private void acknowledgeLaneStart(int laneIndex) {
      synchronized (monitor) {
        if (laneIndex == nextLaneIndex) {
          nextLaneIndex++;
          monitor.notifyAll();
        }
      }
    }
  }
}
