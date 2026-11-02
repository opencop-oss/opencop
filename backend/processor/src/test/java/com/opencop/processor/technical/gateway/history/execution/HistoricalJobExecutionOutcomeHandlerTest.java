// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.business.policy.history.HistoricalExecutionFailureClassifier;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.business.usecase.history.HistoricalJobCancelledException;
import com.opencop.processor.business.usecase.history.HistoricalSubjectLaneJobHandler;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import io.lettuce.core.RedisCommandTimeoutException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

class HistoricalJobExecutionOutcomeHandlerTest {

  private final HistoricalJobStateGateway jobStateGateway =
      Mockito.mock(HistoricalJobStateGateway.class);
  private final HistoricalSubjectLaneJobHandler laneJobHandler =
      Mockito.mock(HistoricalSubjectLaneJobHandler.class);
  private final JobStatusNotificationPublisher notifications =
      Mockito.mock(JobStatusNotificationPublisher.class);
  private final HistoryEventPublisher historyEventPublisher =
      Mockito.mock(HistoryEventPublisher.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final HistoricalObservability historicalObservability =
      new HistoricalObservability(meterRegistry);
  private final HistoricalJobExecutionOutcomeHandler outcomeHandler =
      new HistoricalJobExecutionOutcomeHandler(
          jobStateGateway,
          laneJobHandler,
          notifications,
          historyEventPublisher,
          historicalObservability,
          new HistoricalExecutionFailureClassifier());

  @Test
  void execute_whenUserLaneSucceeds_marksDeliveryPendingAndUsesLaneHandler() {
    var laneMetadata = userLaneMetadata();
    when(jobStateGateway.markDeliveryPending(laneMetadata.laneJobId())).thenReturn(true);

    outcomeHandler.execute(laneMetadata);

    verify(laneJobHandler).handle(laneMetadata);
    verify(jobStateGateway).markDeliveryPending(laneMetadata.laneJobId());
    verify(jobStateGateway, never()).markCompleted(laneMetadata.laneJobId());
    verify(notifications, never()).notifySuccess(any());
    assertEquals(1.0d, completedCounter());
  }

  @Test
  void execute_whenSystemLaneSucceeds_marksCompleted() {
    var laneMetadata = systemLaneMetadata();
    when(jobStateGateway.markCompleted(laneMetadata.laneJobId())).thenReturn(true);

    outcomeHandler.execute(laneMetadata);

    verify(laneJobHandler).handle(laneMetadata);
    verify(jobStateGateway).markCompleted(laneMetadata.laneJobId());
    verify(jobStateGateway, never()).markDeliveryPending(laneMetadata.laneJobId());
  }

  @Test
  void execute_whenLaneHandlerThrowsHistoricalJobCancelledException_marksCancelled() {
    var laneMetadata = userLaneMetadata();
    var cancellation = new HistoricalJobCancelledException(laneMetadata.laneJobId(), "cancelled");
    when(jobStateGateway.markCancelled(laneMetadata.laneJobId(), "cancelled")).thenReturn(true);
    doThrow(cancellation).when(laneJobHandler).handle(laneMetadata);

    assertThrows(HistoricalJobCancelledException.class, () -> outcomeHandler.execute(laneMetadata));

    verify(jobStateGateway).markCancelled(laneMetadata.laneJobId(), "cancelled");
    verify(notifications, never()).notifyFailure(any(), any());
  }

  @Test
  void execute_whenLaneHandlerFails_marksUnexpectedFailureJobId() {
    var laneMetadata = userLaneMetadata();
    var failure = new IllegalStateException("boom");
    when(jobStateGateway.markFailed(
            laneMetadata.laneJobId(), HistoricalJobStage.EXECUTION_FAILED_UNEXPECTED, "boom"))
        .thenReturn(true);
    doThrow(failure).when(laneJobHandler).handle(laneMetadata);

    assertThrows(IllegalStateException.class, () -> outcomeHandler.execute(laneMetadata));

    verify(laneJobHandler).handle(laneMetadata);
    verify(jobStateGateway)
        .markFailed(
            laneMetadata.laneJobId(), HistoricalJobStage.EXECUTION_FAILED_UNEXPECTED, "boom");
    verify(notifications, never()).notifyFailure(any(), any());
    assertEquals(1.0d, executionFailedCounter());
  }

  @Test
  void execute_whenLaneHandlerThrowsStorageException_marksStorageStage() {
    var laneMetadata = userLaneMetadata();
    var failure = new DuplicateKeyException("postgres unique violation");
    when(jobStateGateway.markFailed(
            laneMetadata.laneJobId(),
            HistoricalJobStage.EXECUTION_FAILED_STORAGE,
            "postgres unique violation"))
        .thenReturn(true);
    doThrow(failure).when(laneJobHandler).handle(laneMetadata);

    assertThrows(DuplicateKeyException.class, () -> outcomeHandler.execute(laneMetadata));

    verify(jobStateGateway)
        .markFailed(
            laneMetadata.laneJobId(),
            HistoricalJobStage.EXECUTION_FAILED_STORAGE,
            "postgres unique violation");
  }

  @Test
  void execute_whenLaneHandlerThrowsWrappedRedisTimeout_marksStorageStage() {
    var laneMetadata = userLaneMetadata();
    var failure =
        new IllegalStateException(
            "chunk-ready notify failed", new RedisCommandTimeoutException("redis timed out"));
    when(jobStateGateway.markFailed(
            laneMetadata.laneJobId(),
            HistoricalJobStage.EXECUTION_FAILED_STORAGE,
            "chunk-ready notify failed"))
        .thenReturn(true);
    doThrow(failure).when(laneJobHandler).handle(laneMetadata);

    assertThrows(IllegalStateException.class, () -> outcomeHandler.execute(laneMetadata));

    verify(jobStateGateway)
        .markFailed(
            laneMetadata.laneJobId(),
            HistoricalJobStage.EXECUTION_FAILED_STORAGE,
            "chunk-ready notify failed");
  }

  private double completedCounter() {
    return meterRegistry
        .find("opencop_history_job_stage_total")
        .tags("stage", "COMPLETED", "outcome", "success")
        .counter()
        .count();
  }

  private double executionFailedCounter() {
    return meterRegistry
        .find("opencop_history_job_stage_total")
        .tags("stage", "EXECUTION_FAILED", "outcome", "error")
        .counter()
        .count();
  }

  private static HistoricalSubjectExecutionMeta userLaneMetadata() {
    return laneMetadata(JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")));
  }

  private static HistoricalSubjectExecutionMeta systemLaneMetadata() {
    return laneMetadata(JobIssuer.system("history-test"));
  }

  private static HistoricalSubjectExecutionMeta laneMetadata(JobIssuer issuer) {
    Instant requestedFrom = Instant.parse("2026-04-06T10:02:17Z");
    Instant requestedTo = Instant.parse("2026-04-06T12:00:00Z");
    Instant effectiveFrom = Instant.parse("2026-04-06T10:02:00Z");
    Instant effectiveTo = Instant.parse("2026-04-06T12:00:00Z");
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440121"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440122"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440123"),
        issuer,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440124"),
        Instant.parse("2026-04-07T10:00:00Z"),
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        HistoricalResolution.MINUTE_1,
        118,
        List.of(),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        new HistoricalChunkWindowPlan(
            effectiveFrom,
            effectiveTo,
            118,
            HistoricalResolution.MINUTE_1,
            List.of(
                new HistoricalChunkPlanEntry(
                    0,
                    "chunk-key",
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440125").toString(),
                    null,
                    null,
                    effectiveFrom,
                    effectiveFrom.plusSeconds(60)))));
  }
}
