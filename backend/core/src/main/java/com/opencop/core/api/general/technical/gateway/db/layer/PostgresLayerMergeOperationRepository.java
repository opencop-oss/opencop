// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.layer;

import com.opencop.core.api.general.ports.gateway.layer.LayerMergeOperationRepository;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperation;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperationPhase;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresLayerMergeOperationRepository implements LayerMergeOperationRepository {

  private final LayerMergeOperationJpaRepository jpaRepository;

  public PostgresLayerMergeOperationRepository(LayerMergeOperationJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public void createPending(LayerMergeOperation operation) {
    if (operation.phase() != LayerMergeOperationPhase.PENDING) {
      throw new IllegalArgumentException("operation phase must be PENDING");
    }
    jpaRepository.save(entity(operation));
  }

  @Override
  @Transactional
  public void markFailed(UUID targetLayerId, String failureReason) {
    int updated =
        jpaRepository.markFailed(
            targetLayerId,
            failureReason,
            LayerMergeOperationPhase.PENDING,
            LayerMergeOperationPhase.FAILED);
    if (updated != 1) {
      throw new IllegalStateException(
          "Expected to fail one pending layer merge operation for target " + targetLayerId);
    }
  }

  private static LayerMergeOperationEntity entity(LayerMergeOperation operation) {
    var entity = new LayerMergeOperationEntity();
    entity.setTargetLayerId(operation.targetLayerId());
    entity.setJobId(operation.jobId());
    entity.setSourceLayerIds(sourceLayerIds(operation.sourceLayerIds()));
    entity.setPhase(operation.phase());
    entity.setFailureReason(operation.failureReason());
    return entity;
  }

  private static UUID[] sourceLayerIds(List<UUID> sourceLayerIds) {
    return sourceLayerIds.toArray(UUID[]::new);
  }
}
