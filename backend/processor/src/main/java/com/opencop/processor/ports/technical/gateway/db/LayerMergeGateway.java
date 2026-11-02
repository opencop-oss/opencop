// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.db;

import com.opencop.core.sdk.model.layermerge.LayerMergeOperation;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperationPhase;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LayerMergeGateway {

  // TODO: Migrate layer merge operation persistence to Spring Data JPA.
  Optional<LayerMergeOperation> findMergeOperation(UUID targetLayerId);

  void advanceMergeOperation(
      UUID targetLayerId,
      LayerMergeOperationPhase expectedPhase,
      LayerMergeOperationPhase nextPhase);

  void markMergeOperationFailed(
      UUID targetLayerId, LayerMergeOperationPhase expectedPhase, String failureReason);

  boolean isMergeTargetActive(UUID targetLayerId);

  boolean isMergeTargetPending(UUID targetLayerId);

  void mergeLayers(List<UUID> sourceLayerIds, UUID targetLayerId);

  void mergeLayersAndAdvanceOperation(
      List<UUID> sourceLayerIds, UUID targetLayerId, LayerMergeOperationPhase nextPhase);

  void finalizeMergeSuccess(UUID targetLayerId, List<UUID> sourceLayerIds, UUID archivedBy);

  void finalizeMergeSuccessAndAdvanceOperation(
      UUID targetLayerId,
      List<UUID> sourceLayerIds,
      UUID archivedBy,
      LayerMergeOperationPhase nextPhase);

  void finalizeMergeFailure(UUID targetLayerId, List<UUID> sourceLayerIds);

  void finalizeMergeFailureAndSetOperationPhase(
      UUID targetLayerId,
      List<UUID> sourceLayerIds,
      LayerMergeOperationPhase phase,
      String failureReason);
}
