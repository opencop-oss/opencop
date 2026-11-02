// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.observability.history.HistoricalObservability.JobStage;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class HistoricalJobExecutionSupervisor {

  private static final Logger log = LoggerFactory.getLogger(HistoricalJobExecutionSupervisor.class);
  private static final Duration MIN_STAGE_DURATION = Duration.ofNanos(1L);

  private final HistoricalJobStateGateway jobStateGateway;
  private final ExecutorService historicalParentJobExecutor;
  private final ExecutorService historicalLaneJobExecutor;
  private final ScheduledExecutorService historicalJobScheduler;
  private final HistoricalExecutionProperties properties;
  private final HistoricalObservability historicalObservability;
  private final Map<UUID, TrackedExecutionHandle> trackedExecutions = new ConcurrentHashMap<>();

  public HistoricalJobExecutionSupervisor(
      HistoricalJobStateGateway jobStateGateway,
      JobStatusNotificationPublisher notifications,
      HistoryEventPublisher historyEventPublisher,
      @Qualifier("historicalJobExecutor") ExecutorService historicalJobExecutor,
      @Qualifier("historicalLaneJobExecutor") ExecutorService historicalLaneJobExecutor,
      @Qualifier("historicalJobScheduler") ScheduledExecutorService historicalJobScheduler,
      HistoricalExecutionProperties properties,
      HistoricalObservability historicalObservability) {
    this.jobStateGateway = jobStateGateway;
    this.historicalParentJobExecutor = historicalJobExecutor;
    this.historicalLaneJobExecutor = historicalLaneJobExecutor;
    this.historicalJobScheduler = historicalJobScheduler;
    this.properties = properties;
    this.historicalObservability = historicalObservability;
  }

  void submitParent(
      HistoricalSubjectExecutionMeta laneMetadata,
      LaneExecution laneExecution,
      LaneCompletionHandler completionHandler,
      LaneCancellationBeforeStartHandler cancellationBeforeStartHandler) {
    submit(
        historicalParentJobExecutor,
        laneMetadata,
        laneExecution,
        completionHandler,
        cancellationBeforeStartHandler);
  }

  Future<?> submitLane(
      HistoricalSubjectExecutionMeta laneMetadata,
      LaneExecution laneExecution,
      LaneCompletionHandler completionHandler,
      LaneCancellationBeforeStartHandler cancellationBeforeStartHandler) {
    return submit(
        historicalLaneJobExecutor,
        laneMetadata,
        laneExecution,
        completionHandler,
        cancellationBeforeStartHandler);
  }

  private Future<?> submit(
      ExecutorService executor,
      HistoricalSubjectExecutionMeta laneMetadata,
      LaneExecution laneExecution,
      LaneCompletionHandler completionHandler,
      LaneCancellationBeforeStartHandler cancellationBeforeStartHandler) {
    var trackedExecution =
        new TrackedLaneExecution(laneMetadata, completionHandler, cancellationBeforeStartHandler);
    FutureTask<Void> future =
        new FutureTask<>(
            () -> {
              try {
                if (trackedExecution.markStarted()) {
                  laneExecution.execute(laneMetadata);
                }
                return null;
              } finally {
                trackedExecution.completeAfterRun();
              }
            });
    trackedExecution.attachWorkerFuture(future);
    track(laneMetadata, trackedExecution);
    try {
      executor.execute(future);
      return future;
    } catch (RejectedExecutionException e) {
      cancelTracking(laneMetadata.laneJobId());
      throw e;
    }
  }

  boolean isTracking(UUID jobId) {
    return trackedExecutions.containsKey(jobId);
  }

  void cancelBeforeStartIfTracked(UUID jobId) {
    TrackedExecutionHandle trackedExecution = trackedExecutions.get(jobId);
    if (trackedExecution == null) {
      return;
    }
    trackedExecution.workerFuture().cancel(true);
    trackedExecution.completeIfCancelledBeforeStart();
  }

  private void track(
      HistoricalSubjectExecutionMeta laneMetadata, TrackedLaneExecution trackedExecution) {
    ScheduledFuture<?> refreshFuture =
        historicalJobScheduler.scheduleAtFixedRate(
            () -> jobStateGateway.refreshActiveTtl(laneMetadata.laneJobId()),
            properties.getTtlRefreshInterval().toMillis(),
            properties.getTtlRefreshInterval().toMillis(),
            TimeUnit.MILLISECONDS);
    ScheduledFuture<?> timeoutFuture =
        historicalJobScheduler.schedule(
            () -> failTimedOut(laneMetadata, trackedExecution),
            properties.getMaxRuntime().toMillis(),
            TimeUnit.MILLISECONDS);
    trackedExecution.attachScheduledFutures(refreshFuture, timeoutFuture);
    trackedExecutions.put(laneMetadata.laneJobId(), trackedExecution);
  }

  private void failTimedOut(
      HistoricalSubjectExecutionMeta laneMetadata, TrackedLaneExecution trackedExecution) {
    long timeoutStartNanos = System.nanoTime();
    String reason =
        "Historical job exceeded max runtime of " + properties.getMaxRuntime().toSeconds() + "s";
    if (jobStateGateway.markFailed(laneMetadata.laneJobId(), HistoricalJobStage.TIMEOUT, reason)) {
      historicalObservability.recordJobStage(
          JobStage.TIMEOUT, Outcome.ERROR, elapsedSince(timeoutStartNanos));
      log.error(
          "Historical job {} timed out after {}",
          laneMetadata.laneJobId(),
          properties.getMaxRuntime());
    } else {
      log.error(
          "Historical job {} timed out but timeout state transition was not persisted: {}",
          laneMetadata.laneJobId(),
          reason);
    }
    trackedExecution.workerFuture().cancel(true);
    trackedExecution.completeIfCancelledBeforeStart();
  }

  private void cancelTracking(UUID jobId) {
    TrackedExecutionHandle trackedExecution = trackedExecutions.remove(jobId);
    if (trackedExecution == null) {
      return;
    }
    trackedExecution.cancelScheduledFutures();
  }

  private static Duration elapsedSince(long startNanos) {
    long elapsedNanos = System.nanoTime() - startNanos;
    if (elapsedNanos <= 0L) {
      return MIN_STAGE_DURATION;
    }
    return Duration.ofNanos(elapsedNanos);
  }

  @FunctionalInterface
  interface LaneExecution {
    void execute(HistoricalSubjectExecutionMeta laneMetadata);
  }

  @FunctionalInterface
  interface LaneCompletionHandler {
    void complete(HistoricalSubjectExecutionMeta laneMetadata);
  }

  @FunctionalInterface
  interface LaneCancellationBeforeStartHandler {
    void cancel(HistoricalSubjectExecutionMeta laneMetadata);
  }

  private interface TrackedExecutionHandle {
    Future<?> workerFuture();

    void completeIfCancelledBeforeStart();

    void cancelScheduledFutures();
  }

  private final class TrackedLaneExecution implements TrackedExecutionHandle {
    private final HistoricalSubjectExecutionMeta laneMetadata;
    private final LaneCompletionHandler completionHandler;
    private final LaneCancellationBeforeStartHandler cancellationBeforeStartHandler;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private Future<?> workerFuture;
    private ScheduledFuture<?> refreshFuture;
    private ScheduledFuture<?> timeoutFuture;

    private TrackedLaneExecution(
        HistoricalSubjectExecutionMeta laneMetadata,
        LaneCompletionHandler completionHandler,
        LaneCancellationBeforeStartHandler cancellationBeforeStartHandler) {
      this.laneMetadata = laneMetadata;
      this.completionHandler = completionHandler;
      this.cancellationBeforeStartHandler = cancellationBeforeStartHandler;
    }

    private void attachWorkerFuture(Future<?> workerFuture) {
      this.workerFuture = workerFuture;
    }

    private void attachScheduledFutures(
        ScheduledFuture<?> refreshFuture, ScheduledFuture<?> timeoutFuture) {
      this.refreshFuture = refreshFuture;
      this.timeoutFuture = timeoutFuture;
    }

    @Override
    public Future<?> workerFuture() {
      return workerFuture;
    }

    private boolean markStarted() {
      if (completed.get()) {
        return false;
      }
      started.set(true);
      return !completed.get();
    }

    private void completeAfterRun() {
      if (!completed.compareAndSet(false, true)) {
        return;
      }
      finishCompletion();
    }

    @Override
    public void completeIfCancelledBeforeStart() {
      if (started.get() || !completed.compareAndSet(false, true)) {
        return;
      }
      cancellationBeforeStartHandler.cancel(laneMetadata);
      finishCompletion();
    }

    private void finishCompletion() {
      trackedExecutions.remove(laneMetadata.laneJobId(), this);
      cancelScheduledFutures();
      completionHandler.complete(laneMetadata);
    }

    @Override
    public void cancelScheduledFutures() {
      if (refreshFuture != null) {
        refreshFuture.cancel(false);
      }
      if (timeoutFuture != null) {
        timeoutFuture.cancel(false);
      }
    }
  }
}
