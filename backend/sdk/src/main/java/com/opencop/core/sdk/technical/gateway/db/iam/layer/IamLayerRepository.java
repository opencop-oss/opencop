// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layer;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IamLayerRepository
    extends JpaRepository<LayerEntity, UUID>, JpaSpecificationExecutor<LayerEntity> {

  Optional<LayerEntity> findByIdAndArchivedAtIsNull(UUID layerId);

  /**
   * Archives a layer only when it still holds {@code expectedState}, returning the number of rows
   * affected so the caller can tell a won transition from a lost one. A read-then-save pair cannot:
   * two callers can both read the same state and both write, and the second write silently
   * overwrites the first — including overwriting an archive whose caller already acted on its
   * success.
   *
   * <p>Archived-at and archived-by are set together with the state so the row never carries archive
   * metadata that disagrees with its lifecycle.
   *
   * <p>Updated-at and updated-by are written explicitly rather than left to the generated timestamp
   * on {@link LayerEntity}: a bulk update bypasses the entity lifecycle, so Hibernate never stamps
   * them. They carry the archive instant and actor, since archiving is the change being recorded.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update LayerEntity layer
         set layer.lifecycleState = :targetState,
             layer.archivedAt = :archivedAt,
             layer.archivedBy = :archivedBy,
             layer.updatedAt = :archivedAt,
             layer.updatedBy = :archivedBy
       where layer.id = :layerId
         and layer.lifecycleState = :expectedState
      """)
  int archiveIfInState(
      UUID layerId,
      LayerLifecycleState expectedState,
      LayerLifecycleState targetState,
      Instant archivedAt,
      UUID archivedBy);

  /**
   * Restores a layer only when it still holds {@code expectedState}, clearing the archive metadata
   * alongside the state. Counterpart to {@link #archiveIfInState}; the nulls are written as query
   * literals so the clearing write carries no untyped parameters.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update LayerEntity layer
         set layer.lifecycleState = :targetState,
             layer.archivedAt = null,
             layer.archivedBy = null
       where layer.id = :layerId
         and layer.lifecycleState = :expectedState
      """)
  int unarchiveIfInState(
      UUID layerId, LayerLifecycleState expectedState, LayerLifecycleState targetState);
}
