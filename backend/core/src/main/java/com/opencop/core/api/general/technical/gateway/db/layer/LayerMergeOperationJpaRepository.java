// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.layer;

import com.opencop.core.sdk.model.layermerge.LayerMergeOperationPhase;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LayerMergeOperationJpaRepository
    extends JpaRepository<LayerMergeOperationEntity, UUID> {

  @Modifying
  @Query(
      """
      update LayerMergeOperationEntity operation
      set operation.phase = :failedPhase,
          operation.failureReason = :failureReason,
          operation.updatedAt = CURRENT_TIMESTAMP
      where operation.targetLayerId = :targetLayerId
        and operation.phase = :pendingPhase
      """)
  int markFailed(
      @Param("targetLayerId") UUID targetLayerId,
      @Param("failureReason") String failureReason,
      @Param("pendingPhase") LayerMergeOperationPhase pendingPhase,
      @Param("failedPhase") LayerMergeOperationPhase failedPhase);
}
