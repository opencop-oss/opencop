// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.ObjectMapper;

final class HistoricalJobExecutionSupervisorFixture {
  final RecordingHistoricalJobStateGateway repository = new RecordingHistoricalJobStateGateway();
  final RecordingJobStatusNotificationPublisher notifications =
      new RecordingJobStatusNotificationPublisher();
  final RecordingHistoryEventPublisher historyEventPublisher = new RecordingHistoryEventPublisher();
  final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private final RecordingScheduledExecutorService scheduler =
      new RecordingScheduledExecutorService();
  private final ExecutorService executor;
  private final HistoricalJobExecutionSupervisor supervisor;
  private final AtomicInteger executions = new AtomicInteger();
  private final AtomicInteger completions = new AtomicInteger();
  private final AtomicInteger cancellations = new AtomicInteger();

  private HistoricalJobExecutionSupervisorFixture(ExecutorService executor) {
    this.executor = executor;
    this.supervisor =
        new HistoricalJobExecutionSupervisor(
            repository,
            notifications,
            historyEventPublisher,
            executor,
            executor,
            scheduler,
            properties(),
            new HistoricalObservability(meterRegistry));
  }

  static HistoricalJobExecutionSupervisorFixture withDirectExecutor() {
    return new HistoricalJobExecutionSupervisorFixture(new DirectExecutorService());
  }

  static HistoricalJobExecutionSupervisorFixture withRejectingExecutor() {
    return new HistoricalJobExecutionSupervisorFixture(new RejectingExecutorService());
  }

  static HistoricalJobExecutionSupervisorFixture withManualExecutor() {
    return new HistoricalJobExecutionSupervisorFixture(new ManualQueuedExecutorService());
  }

  static HistoricalJobExecutionSupervisorFixture withSingleThreadExecutor() {
    return new HistoricalJobExecutionSupervisorFixture(
        java.util.concurrent.Executors.newSingleThreadExecutor());
  }

  void submit(HistoricalSubjectExecutionMeta laneMetadata) {
    supervisor.submitLane(
        laneMetadata,
        ignored -> executions.incrementAndGet(),
        ignored -> completions.incrementAndGet(),
        ignored -> cancellations.incrementAndGet());
  }

  void submitNoop(HistoricalSubjectExecutionMeta laneMetadata) {
    supervisor.submitLane(laneMetadata, ignored -> {}, ignored -> {}, ignored -> {});
  }

  void submitBlocking(
      HistoricalSubjectExecutionMeta laneMetadata,
      CountDownLatch started,
      CountDownLatch interrupted,
      CountDownLatch completed) {
    supervisor.submitLane(
        laneMetadata,
        ignored -> {
          started.countDown();
          try {
            Thread.sleep(5_000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            interrupted.countDown();
          }
        },
        ignored -> {
          completions.incrementAndGet();
          completed.countDown();
        },
        ignored -> {});
  }

  boolean isTracking(HistoricalSubjectExecutionMeta laneMetadata) {
    return supervisor.isTracking(laneMetadata.laneJobId());
  }

  void cancelBeforeStart(HistoricalSubjectExecutionMeta laneMetadata) {
    supervisor.cancelBeforeStartIfTracked(laneMetadata.laneJobId());
  }

  void runQueuedWorker() {
    ((ManualQueuedExecutorService) executor).runNext();
  }

  void runRefresh() {
    scheduler.runRefresh();
  }

  void runTimeout() {
    scheduler.runTimeout();
  }

  int executions() {
    return executions.get();
  }

  int completions() {
    return completions.get();
  }

  int cancellations() {
    return cancellations.get();
  }

  void assertScheduledTasksCancelled() {
    assertTrue(scheduler.refreshFuture.cancelled);
    assertTrue(scheduler.timeoutFuture.cancelled);
  }

  void shutdownExecutor() {
    executor.shutdownNow();
  }

  private static HistoricalExecutionProperties properties() {
    var properties = new HistoricalExecutionProperties();
    properties.setJobStateTtl(Duration.ofHours(24));
    properties.setMaxRuntime(Duration.ofMillis(50));
    properties.setTtlRefreshInterval(Duration.ofMillis(10));
    return properties;
  }

  static final class RecordingHistoricalJobStateGateway implements HistoricalJobStateGateway {
    UUID lastRefreshJobId;
    int refreshCount;
    HistoricalJobStage failedStage;
    String failureReason;

    @Override
    public void createReceived(HistoricalTracksJobSubmission job) {}

    @Override
    public boolean createReceivedIfAbsent(HistoricalTracksJobSubmission job) {
      return true;
    }

    @Override
    public boolean markQueued(UUID jobId) {
      return true;
    }

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
      this.failedStage = stage;
      this.failureReason = failureReason;
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
      this.lastRefreshJobId = jobId;
      refreshCount++;
      return true;
    }

    @Override
    public Optional<HistoricalJobState> findByJobId(UUID jobId) {
      return Optional.empty();
    }
  }

  static final class RecordingHistoryEventPublisher implements HistoryEventPublisher {
    HistoricalTracksJobSubmission failedJob;
    HistoricalJobStage failedStage;

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
        long failedChunks) {}

    @Override
    public void publishCompleted(
        HistoricalChunkPlan plan,
        HistoricalTracksJobSubmission job,
        long computedChunks,
        long deliveredChunks,
        long failedChunks) {}

    @Override
    public void publishFailed(
        HistoricalTracksJobSubmission job, HistoricalJobStage stage, String reason) {
      this.failedJob = job;
      this.failedStage = stage;
    }
  }

  static final class RecordingJobStatusNotificationPublisher
      extends JobStatusNotificationPublisher {
    JobSubmission failureJob;
    Throwable failureException;

    private RecordingJobStatusNotificationPublisher() {
      super(null, new ObjectMapper());
    }

    @Override
    public void notifyFailure(JobSubmission job, Throwable exception) {
      this.failureJob = job;
      this.failureException = exception;
    }
  }

  private static final class DirectExecutorService extends AbstractExecutorService {
    private boolean shutdown;

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown = true;
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(Runnable command) {
      command.run();
    }
  }

  private static final class RejectingExecutorService extends AbstractExecutorService {

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(Runnable command) {
      throw new RejectedExecutionException("rejected");
    }
  }

  private static final class ManualQueuedExecutorService extends AbstractExecutorService {
    private final LinkedBlockingQueue<Runnable> commands = new LinkedBlockingQueue<>();

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      commands.clear();
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(Runnable command) {
      commands.add(command);
    }

    private void runNext() {
      Runnable command = commands.poll();
      assertNotNull(command);
      command.run();
    }
  }

  private static final class RecordingScheduledExecutorService extends AbstractExecutorService
      implements ScheduledExecutorService {
    private RecordingScheduledFuture refreshFuture;
    private RecordingScheduledFuture timeoutFuture;

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      timeoutFuture = new RecordingScheduledFuture(command);
      return timeoutFuture;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable command, long initialDelay, long period, TimeUnit unit) {
      refreshFuture = new RecordingScheduledFuture(command);
      return refreshFuture;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(Runnable command) {
      command.run();
    }

    private void runRefresh() {
      refreshFuture.run();
    }

    private void runTimeout() {
      timeoutFuture.run();
    }
  }

  private static final class RecordingScheduledFuture implements RunnableScheduledFuture<Object> {
    private final Runnable command;
    private boolean cancelled;
    private boolean done;

    private RecordingScheduledFuture(Runnable command) {
      this.command = command;
    }

    @Override
    public boolean isPeriodic() {
      return false;
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return 0;
    }

    @Override
    public int compareTo(Delayed other) {
      return 0;
    }

    @Override
    public void run() {
      if (cancelled) {
        return;
      }
      command.run();
      done = true;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      cancelled = true;
      return true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public boolean isDone() {
      return done || cancelled;
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
      return null;
    }

    @Override
    public Object get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return null;
    }
  }
}
