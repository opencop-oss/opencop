// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalSubjectLaneExecutionGateway;
import com.opencop.processor.technical.gateway.history.execution.HistoricalJobExecutionOutcomeHandler;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobStatus;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public final class ProcessorHistoricalSubjectLaneExecutionGateway
    implements HistoricalSubjectLaneExecutionGateway {

  private final HistoricalJobStateGateway jobStateGateway;
  private final HistoricalJobExecutionSupervisor executionSupervisor;
  private final HistoricalJobExecutionOutcomeHandler executionOutcomeHandler;

  public ProcessorHistoricalSubjectLaneExecutionGateway(
      HistoricalJobStateGateway jobStateGateway,
      HistoricalJobExecutionSupervisor executionSupervisor,
      HistoricalJobExecutionOutcomeHandler executionOutcomeHandler) {
    this.jobStateGateway = jobStateGateway;
    this.executionSupervisor = executionSupervisor;
    this.executionOutcomeHandler = executionOutcomeHandler;
  }

  @Override
  public void execute(HistoricalSubjectExecutionMeta laneMetadata, Runnable laneStarted) {
    Objects.requireNonNull(laneMetadata, "laneMetadata");
    Objects.requireNonNull(laneStarted, "laneStarted");
    if (!jobStateGateway.createReceivedIfAbsent(laneMetadata)) {
      laneStarted.run();
      return;
    }
    if (!jobStateGateway.markQueued(laneMetadata.laneJobId())) {
      laneStarted.run();
      return;
    }

    CountDownLatch laneCompleted = new CountDownLatch(1);
    AtomicReference<RuntimeException> laneFailure = new AtomicReference<>();
    try {
      Future<?> laneWorker =
          executionSupervisor.submitLane(
              laneMetadata,
              execution -> {
                laneStarted.run();
                if (isTerminal(laneMetadata)) {
                  throw new IllegalStateException(
                      "Historical subject lane reached terminal state before execution started");
                }
                try {
                  executionOutcomeHandler.execute(execution);
                } catch (RuntimeException e) {
                  laneFailure.compareAndSet(null, e);
                  throw e;
                }
              },
              ignored -> laneCompleted.countDown(),
              ignored -> {
                laneFailure.compareAndSet(
                    null,
                    new IllegalStateException(
                        "Historical subject lane was cancelled before execution started"));
                laneCompleted.countDown();
              });
      awaitLaneCompletion(laneMetadata, laneCompleted, laneWorker);
      RuntimeException failure = laneFailure.get();
      if (failure != null) {
        throw failure;
      }
      throwIfTerminalFailure(laneMetadata);
    } catch (RuntimeException e) {
      laneStarted.run();
      throw e;
    }
  }

  private void awaitLaneCompletion(
      HistoricalSubjectExecutionMeta laneMetadata,
      CountDownLatch laneCompleted,
      Future<?> laneWorker) {
    try {
      laneCompleted.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      laneWorker.cancel(true);
      jobStateGateway.markCancelled(
          laneMetadata.laneJobId(), "cancelled after parent lane execution stopped");
      throw new IllegalStateException("Interrupted while waiting for historical subject lane", e);
    }
  }

  private boolean isTerminal(HistoricalSubjectExecutionMeta laneMetadata) {
    return jobStateGateway
        .findByJobId(laneMetadata.laneJobId())
        .map(state -> state.status().isTerminal())
        .orElse(false);
  }

  private void throwIfTerminalFailure(HistoricalSubjectExecutionMeta laneMetadata) {
    var currentState = jobStateGateway.findByJobId(laneMetadata.laneJobId());
    if (currentState.isEmpty()) {
      return;
    }
    var state = currentState.get();
    if (state.status() == HistoricalJobStatus.FAILED) {
      throw laneTerminalFailure(
          state.stage(),
          state.failureReason() == null || state.failureReason().isBlank()
              ? "Historical subject lane failed"
              : state.failureReason());
    }
    if (state.status() == HistoricalJobStatus.CANCELLED) {
      throw new IllegalStateException("Historical subject lane was cancelled");
    }
  }

  private RuntimeException laneTerminalFailure(HistoricalJobStage stage, String reason) {
    if (stage == HistoricalJobStage.TIMEOUT
        || stage == HistoricalJobStage.EXECUTION_FAILED_TIMEOUT) {
      return new IllegalStateException(reason, new TimeoutException(reason));
    }
    return new IllegalStateException(reason);
  }
}
