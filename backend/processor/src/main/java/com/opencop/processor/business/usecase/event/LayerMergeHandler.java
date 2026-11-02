// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import com.opencop.core.sdk.model.job.LayerMergeJobSubmission;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperation;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperationPhase;
import com.opencop.processor.ports.business.usecase.event.JobHandler;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import com.opencop.processor.ports.technical.gateway.db.GeoCacheHydrationGateway;
import com.opencop.processor.ports.technical.gateway.db.LayerMergeGateway;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LayerMergeHandler implements JobHandler<LayerMergeJobSubmission> {

  private static final Logger log = LoggerFactory.getLogger(LayerMergeHandler.class);
  private static final int CACHE_HYDRATION_PAGE_SIZE = 1000;

  private final LayerMergeGateway layerMergeGateway;
  private final GeoCacheHydrationGateway geoCacheHydrationGateway;
  private final GeoCacheGateway geoCacheGateway;

  public LayerMergeHandler(
      LayerMergeGateway layerMergeGateway,
      GeoCacheHydrationGateway geoCacheHydrationGateway,
      GeoCacheGateway geoCacheGateway) {
    this.layerMergeGateway = layerMergeGateway;
    this.geoCacheHydrationGateway = geoCacheHydrationGateway;
    this.geoCacheGateway = geoCacheGateway;
  }

  @Override
  public Class<LayerMergeJobSubmission> jobType() {
    return LayerMergeJobSubmission.class;
  }

  @Override
  public void handle(LayerMergeJobSubmission job) {
    log.info(
        "Received layer merge job {} for target {} from {} source layers initiated by {}",
        job.jobId(),
        job.targetLayerId(),
        job.sourceLayerIds().size(),
        job.issuer());
    var operation =
        layerMergeGateway
            .findMergeOperation(job.targetLayerId())
            .orElseThrow(() -> missingOperation(job));
    validateOperationIdentity(job, operation);

    if (operation.phase() == LayerMergeOperationPhase.FAILED) {
      log.warn(
          "Layer merge job {} for target {} has terminal FAILED operation state; skipping mutation",
          job.jobId(),
          job.targetLayerId());
      return;
    }
    if (operation.phase() == LayerMergeOperationPhase.FINALIZED) {
      retryFinalizedCleanup(job);
      return;
    }

    runPendingSteps(job, operation);

    try {
      evictSources(job);
    } catch (RuntimeException e) {
      log.error(
          "Layer merge job {} completed DB finalization for target {}, but source Redis eviction failed for source layers {}. Merge lifecycle remains successful; rethrowing so job replay retries cleanup.",
          job.jobId(),
          job.targetLayerId(),
          job.sourceLayerIds(),
          e);
      throw new IllegalStateException(
          "Layer merge job "
              + job.jobId()
              + " finalized target "
              + job.targetLayerId()
              + " but failed source Redis eviction",
          e);
    }
    log.info(
        "Layer merge job {} completed for target {} with source Redis eviction complete",
        job.jobId(),
        job.targetLayerId());
  }

  private void validateOperationIdentity(
      LayerMergeJobSubmission job, LayerMergeOperation operation) {
    if (Objects.equals(job.jobId(), operation.jobId())
        && Objects.equals(job.sourceLayerIds(), operation.sourceLayerIds())) {
      return;
    }
    var reason = "OPERATION_IDENTITY_CONFLICT";
    if (operation.phase() == LayerMergeOperationPhase.PENDING) {
      layerMergeGateway.markMergeOperationFailed(
          job.targetLayerId(), LayerMergeOperationPhase.PENDING, reason);
    } else {
      log.warn(
          "Layer merge job {} conflicts with accepted operation for target {} in phase {}; preserving accepted operation state",
          job.jobId(),
          job.targetLayerId(),
          operation.phase());
    }
    throw new IllegalStateException(
        "Layer merge job "
            + job.jobId()
            + " conflicts with accepted operation for target "
            + job.targetLayerId());
  }

  private IllegalStateException missingOperation(LayerMergeJobSubmission job) {
    return new IllegalStateException(
        "Missing layer merge operation for target "
            + job.targetLayerId()
            + " and job "
            + job.jobId());
  }

  private void retryFinalizedCleanup(LayerMergeJobSubmission job) {
    log.info(
        "Layer merge job {} for target {} is already FINALIZED; retrying source Redis eviction only",
        job.jobId(),
        job.targetLayerId());
    evictSources(job);
  }

  private void runPendingSteps(LayerMergeJobSubmission job, LayerMergeOperation operation) {
    var currentPhase = operation.phase();
    if (operation.phase() == LayerMergeOperationPhase.PENDING) {
      log.info(
          "Layer merge job {}: materializing target {} from source layers {}",
          job.jobId(),
          job.targetLayerId(),
          job.sourceLayerIds());
      try {
        layerMergeGateway.mergeLayersAndAdvanceOperation(
            job.sourceLayerIds(), job.targetLayerId(), LayerMergeOperationPhase.MATERIALIZED);
      } catch (RuntimeException e) {
        log.warn(
            "Layer merge job {} failed before successful materialization; marking operation and target failed and unlocking source layers {}",
            job.jobId(),
            job.sourceLayerIds(),
            e);
        rollbackFailedPendingMerge(job, e);
        throw new IllegalStateException(
            "Layer merge job "
                + job.jobId()
                + " failed materializing target "
                + job.targetLayerId(),
            e);
      }
      currentPhase = LayerMergeOperationPhase.MATERIALIZED;
    }
    if (currentPhase == LayerMergeOperationPhase.MATERIALIZED) {
      log.info(
          "Layer merge job {}: hydrating target layer {} into Redis",
          job.jobId(),
          job.targetLayerId());
      hydrateTarget(job.targetLayerId());
      layerMergeGateway.advanceMergeOperation(
          job.targetLayerId(),
          LayerMergeOperationPhase.MATERIALIZED,
          LayerMergeOperationPhase.HYDRATED);
      currentPhase = LayerMergeOperationPhase.HYDRATED;
    }
    if (currentPhase == LayerMergeOperationPhase.HYDRATED) {
      log.info(
          "Layer merge job {}: finalizing successful lifecycle transition for target {}",
          job.jobId(),
          job.targetLayerId());
      layerMergeGateway.finalizeMergeSuccessAndAdvanceOperation(
          job.targetLayerId(),
          job.sourceLayerIds(),
          UUID.fromString(job.issuer().id()),
          LayerMergeOperationPhase.FINALIZED);
    }
  }

  private void hydrateTarget(UUID targetLayerId) {
    var stats =
        geoCacheHydrationGateway.hydrateSnapshot(
            targetLayerId, CACHE_HYDRATION_PAGE_SIZE, page -> geoCacheGateway.write(page, true));
    log.info(
        "Hydrated target layer {} into Redis: pages={}, features={}, lastObjectId={}",
        targetLayerId,
        stats.pageCount(),
        stats.featureCount(),
        stats.lastObjectId());
  }

  private void rollbackFailedPendingMerge(
      LayerMergeJobSubmission job, RuntimeException originalFailure) {
    try {
      layerMergeGateway.finalizeMergeFailureAndSetOperationPhase(
          job.targetLayerId(),
          job.sourceLayerIds(),
          LayerMergeOperationPhase.FAILED,
          "MATERIALIZATION_FAILED");
      log.info(
          "Layer merge job {}: marked target {} as failed and unlocked source layers {}",
          job.jobId(),
          job.targetLayerId(),
          job.sourceLayerIds());
    } catch (RuntimeException rollbackFailure) {
      originalFailure.addSuppressed(rollbackFailure);
      log.error(
          "Layer merge job {}: failed to mark target {} as failed and unlock source layers {}",
          job.jobId(),
          job.targetLayerId(),
          job.sourceLayerIds(),
          rollbackFailure);
    }

    try {
      geoCacheGateway.clearSource(job.targetLayerId());
      log.info(
          "Layer merge job {}: removed target {} Redis data after failed merge",
          job.jobId(),
          job.targetLayerId());
    } catch (RuntimeException targetEvictionFailure) {
      originalFailure.addSuppressed(targetEvictionFailure);
      log.error(
          "Layer merge job {}: failed to remove target {} Redis data after failed merge",
          job.jobId(),
          job.targetLayerId(),
          targetEvictionFailure);
    }
  }

  private void evictSources(LayerMergeJobSubmission job) {
    log.info(
        "Layer merge job {}: evicting source layer Redis data for {}",
        job.jobId(),
        job.sourceLayerIds());
    for (UUID sourceLayerId : job.sourceLayerIds()) {
      geoCacheGateway.clearSource(sourceLayerId);
      log.info("Evicted source layer {} Redis data for merge finalization", sourceLayerId);
    }
  }
}
