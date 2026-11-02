// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import java.util.Optional;
import java.util.UUID;

public interface HistoricalJobStateGateway {

  void createReceived(HistoricalTracksJobSubmission job);

  default boolean createReceivedIfAbsent(HistoricalTracksJobSubmission job) {
    createReceived(job);
    return true;
  }

  default void createReceived(HistoricalSubjectExecutionMeta laneMetadata) {
    throw new UnsupportedOperationException("Lane state creation is not implemented");
  }

  default boolean createReceivedIfAbsent(HistoricalSubjectExecutionMeta laneMetadata) {
    createReceived(laneMetadata);
    return true;
  }

  default boolean markQueued(UUID jobId) {
    return markQueued(jobId, HistoricalJobState.QUEUE_ROLE_DISPATCH_READY);
  }

  boolean markQueued(UUID jobId, String queueRole);

  boolean markRunning(UUID jobId, String workerId);

  boolean markPlanning(UUID jobId);

  boolean markPlanned(UUID jobId);

  boolean markCompleted(UUID jobId);

  boolean markDeliveredCompleted(UUID jobId);

  boolean markDeliveryPending(UUID jobId);

  boolean markDeliveryFailed(UUID jobId, String failureReason);

  boolean markFailed(UUID jobId, HistoricalJobStage stage, String failureReason);

  boolean markCancellationRequested(UUID jobId, String reason);

  boolean markCancelled(UUID jobId, String reason);

  boolean refreshActiveTtl(UUID jobId);

  Optional<HistoricalJobState> findByJobId(UUID jobId);
}
