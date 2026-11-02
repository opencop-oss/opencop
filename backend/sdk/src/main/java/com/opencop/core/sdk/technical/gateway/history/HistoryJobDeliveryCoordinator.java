// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoryEvent;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.model.history.HistoricalJobDeliverySnapshot;
import com.opencop.core.sdk.technical.model.history.HistoryJobDeliveryEvents;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates Redis-backed delivery metrics and terminal {@code history.job.*} publications (Slice
 * 14).
 */
public class HistoryJobDeliveryCoordinator {

  private static final Logger log = LoggerFactory.getLogger(HistoryJobDeliveryCoordinator.class);

  private final RedisHistoricalJobDeliveryRepository repository;
  private final HistoricalJobLifecycleUpdater lifecycleUpdater;
  private final CachePublisher<HistoryEvent> historyEventsPublisher;
  private final Duration stateTtl;

  public HistoryJobDeliveryCoordinator(
      RedisHistoricalJobDeliveryRepository repository,
      HistoricalJobLifecycleUpdater lifecycleUpdater,
      CachePublisher<HistoryEvent> historyEventsPublisher,
      Duration stateTtl) {
    this.repository = repository;
    this.lifecycleUpdater = lifecycleUpdater;
    this.historyEventsPublisher = historyEventsPublisher;
    this.stateTtl = stateTtl;
  }

  public void onExecutionStarted(
      UUID jobId,
      UUID targetUserId,
      UUID requestId,
      SubjectType subjectType,
      UUID subjectId,
      long totalChunks,
      long cachedChunks) {
    repository.ensureInitialized(
        jobId,
        targetUserId,
        requestId,
        subjectType,
        subjectId,
        totalChunks,
        cachedChunks,
        true,
        stateTtl);
  }

  public void recordExecutionProgress(UUID jobId, long computedChunks, long failedChunks) {
    repository.recordExecutionProgress(jobId, computedChunks, failedChunks, stateTtl);
  }

  public void onExecutionFinished(UUID jobId, long computedChunks, long failedChunks) {
    repository.markExecutionComplete(jobId, computedChunks, failedChunks, stateTtl);
    tryPublishFailure(jobId);
    tryPublishTerminal(jobId);
  }

  /** After websocket transport has finished sending one logical chunk. */
  public void onChunkDelivered(UUID jobId) {
    if (repository.incrementDeliveredIfTracked(jobId, stateTtl) < 0) {
      return;
    }
    repository
        .loadSnapshot(jobId)
        .filter(HistoricalJobDeliverySnapshot::userLifecycle)
        .ifPresent(
            snap ->
                publishSafely(
                    HistoryJobDeliveryEvents.progress(snap), "history.job.progress", jobId));
    tryPublishTerminal(jobId);
  }

  public void onChunkDeliveryFailed(UUID jobId, String failureReason) {
    if (repository.incrementFailedIfTracked(jobId, stateTtl, failureReason) < 0) {
      return;
    }
    repository
        .loadSnapshot(jobId)
        .filter(HistoricalJobDeliverySnapshot::userLifecycle)
        .ifPresent(
            snap ->
                publishSafely(
                    HistoryJobDeliveryEvents.progress(snap), "history.job.progress", jobId));
    tryPublishFailure(jobId);
  }

  private void tryPublishTerminal(UUID jobId) {
    var snapOpt = repository.loadSnapshot(jobId);
    if (snapOpt.isEmpty() || !snapOpt.get().userLifecycle()) {
      return;
    }
    HistoricalJobDeliverySnapshot s = snapOpt.get();
    if (!s.executionComplete()) {
      return;
    }
    if (s.deliveredChunks() + s.failedChunks() != s.totalChunks()) {
      return;
    }
    if (repository.hasFailurePublicationClaim(jobId) || repository.hasDeliveryFailure(jobId)) {
      return;
    }
    if (!repository.tryClaimCompletionPublication(jobId, stateTtl)) {
      return;
    }
    if (!lifecycleUpdater.markCompleted(jobId, stateTtl)) {
      log.error(
          "Failed to persist completed historical job state for job {} after delivery completion",
          jobId);
      return;
    }
    publishSafely(HistoryJobDeliveryEvents.progress(s), "history.job.progress", jobId);
    publishSafely(HistoryJobDeliveryEvents.completed(s), "history.job.completed", jobId);
  }

  private void tryPublishFailure(UUID jobId) {
    var snapOpt = repository.loadSnapshot(jobId);
    if (snapOpt.isEmpty() || !snapOpt.get().userLifecycle()) {
      return;
    }
    HistoricalJobDeliverySnapshot s = snapOpt.get();
    if (!s.executionComplete() || !repository.hasDeliveryFailure(jobId)) {
      return;
    }
    if (!repository.tryClaimFailurePublication(jobId, stateTtl)) {
      return;
    }
    if (!lifecycleUpdater.markFailed(jobId, s.failureReason(), stateTtl)) {
      log.error(
          "Failed to persist failed historical job state for job {} after delivery failure: {}",
          jobId,
          s.failureReason());
      return;
    }
    publishSafely(
        HistoryJobDeliveryEvents.failed(s, "DELIVERY_FAILED"), "history.job.failed", jobId);
  }

  private void publishSafely(HistoryEvent event, String eventName, UUID jobId) {
    try {
      historyEventsPublisher.publish(event);
    } catch (Exception e) {
      log.error("Failed to publish {} event for job {}", eventName, jobId, e);
    }
  }
}
