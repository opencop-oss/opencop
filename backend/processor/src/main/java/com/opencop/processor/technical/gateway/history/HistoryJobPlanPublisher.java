// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoryEvent;
import com.opencop.core.sdk.model.history.HistoryJobCompletedEvent;
import com.opencop.core.sdk.model.history.HistoryJobFailedEvent;
import com.opencop.core.sdk.model.history.HistoryJobPlanEvent;
import com.opencop.core.sdk.model.history.HistoryJobProgressEvent;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuerType;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobSubjectIdentity;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HistoryJobPlanPublisher implements HistoryEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(HistoryJobPlanPublisher.class);

  private final CachePublisher<HistoryEvent> historyEventsPublisher;

  public HistoryJobPlanPublisher(CachePublisher<HistoryEvent> historyEventsPublisher) {
    this.historyEventsPublisher = historyEventsPublisher;
  }

  @Override
  public void publishPlan(HistoricalChunkPlan plan, HistoricalExecutionContext executionContext) {
    if (!shouldPublish(executionContext)) {
      return;
    }

    publishPlanEvent(
        plan,
        executionContext.requestedBy(),
        executionContext.requestId(),
        executionContext.jobId(),
        executionContext.subjectType(),
        executionContext.subjectId(),
        executionContext.jobId());
  }

  @Override
  public void publishProgress(
      HistoricalChunkPlan plan,
      HistoricalTracksJobSubmission job,
      long computedChunks,
      long deliveredChunks,
      long failedChunks) {
    if (!shouldPublish(job)) {
      return;
    }

    publishProgress(
        plan,
        job.requestedBy(),
        job.requestId(),
        job.jobId(),
        plan.subjectType(),
        plan.subjectId(),
        plan.chunks(),
        plan.cachedChunks(),
        computedChunks,
        deliveredChunks,
        failedChunks,
        job);
  }

  @Override
  public void publishExecutionProgress(
      HistoricalChunkPlan plan,
      HistoricalExecutionContext executionContext,
      long computedChunks,
      long failedChunks) {
    if (!shouldPublish(executionContext)) {
      return;
    }

    publishProgress(
        plan,
        executionContext.requestedBy(),
        executionContext.requestId(),
        executionContext.jobId(),
        executionContext.subjectType(),
        executionContext.subjectId(),
        plan.chunks(),
        plan.cachedChunks(),
        computedChunks,
        0L,
        failedChunks,
        null);
  }

  @Override
  public void publishCompleted(
      HistoricalChunkPlan plan,
      HistoricalTracksJobSubmission job,
      long computedChunks,
      long deliveredChunks,
      long failedChunks) {
    if (!shouldPublish(job)) {
      return;
    }

    publishSafely(
        new HistoryJobCompletedEvent(
            job.requestedBy(),
            plan.requestId(),
            plan.jobId(),
            plan.subjectType(),
            plan.subjectId(),
            plan.chunks(),
            plan.cachedChunks(),
            computedChunks,
            deliveredChunks,
            failedChunks),
        "history.job.completed",
        job);
  }

  @Override
  public void publishFailed(
      HistoricalTracksJobSubmission job, HistoricalJobStage stage, String failureReason) {
    if (!shouldPublish(job)) {
      return;
    }

    HistoricalJobSubjectIdentity subjectIdentity = HistoricalJobSubjectIdentity.fromParentJob(job);
    publishSafely(
        new HistoryJobFailedEvent(
            job.requestedBy(),
            job.requestId(),
            job.jobId(),
            subjectIdentity.subjectType(),
            subjectIdentity.subjectId(),
            stage.name(),
            failureReason),
        "history.job.failed",
        job);
  }

  private boolean shouldPublish(HistoricalExecutionContext executionContext) {
    return executionContext != null && executionContext.userInitiated();
  }

  private boolean shouldPublish(HistoricalTracksJobSubmission job) {
    return job.issuer().type() == JobIssuerType.USER;
  }

  private void publishSafely(
      HistoryEvent event, String eventName, HistoricalTracksJobSubmission job) {
    publishSafely(event, eventName, job.jobId());
  }

  private void publishSafely(HistoryEvent event, String eventName, UUID jobId) {
    try {
      historyEventsPublisher.publish(event);
    } catch (Exception e) {
      log.error("Failed to publish {} event for job {}", eventName, jobId, e);
    }
  }

  private void publishProgress(
      HistoricalChunkPlan plan,
      UUID requestedBy,
      UUID requestId,
      UUID jobId,
      SubjectType subjectType,
      UUID subjectId,
      long chunks,
      long cachedChunks,
      long computedChunks,
      long deliveredChunks,
      long failedChunks,
      HistoricalTracksJobSubmission jobForFailureLogs) {
    long remainingChunks = Math.max(0L, chunks - deliveredChunks - failedChunks);
    int percentComplete =
        chunks == 0 ? 100 : (int) Math.min(100L, (deliveredChunks * 100L) / chunks);

    publishSafely(
        new HistoryJobProgressEvent(
            requestedBy,
            requestId,
            jobId,
            subjectType,
            subjectId,
            chunks,
            cachedChunks,
            computedChunks,
            deliveredChunks,
            failedChunks,
            remainingChunks,
            percentComplete),
        "history.job.progress",
        jobForFailureLogs == null ? jobId : jobForFailureLogs.jobId());
  }

  private void publishPlanEvent(
      HistoricalChunkPlan plan,
      UUID requestedBy,
      UUID requestId,
      UUID jobId,
      SubjectType subjectType,
      UUID subjectId,
      UUID loggedJobId) {
    try {
      historyEventsPublisher.publish(
          new HistoryJobPlanEvent(
              requestedBy,
              requestId,
              jobId,
              subjectType,
              subjectId,
              requestedBy,
              plan.requestedFrom(),
              plan.requestedTo(),
              plan.effectiveFrom(),
              plan.effectiveTo(),
              plan.resolution(),
              plan.requestedAt(),
              plan.chunks(),
              plan.cachedChunks(),
              plan.chunksToCompute()));
    } catch (Exception e) {
      log.error("Failed to publish history.job.plan event for job {}", loggedJobId, e);
    }
  }
}
