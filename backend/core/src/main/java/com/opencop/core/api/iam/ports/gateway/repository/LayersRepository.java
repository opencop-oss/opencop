// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.gateway.repository;

import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import java.util.List;
import java.util.UUID;

public interface LayersRepository {

  Layer create(String name, LayerAccessScope accessScope, UUID createdBy)
      throws RepositoryException;

  Layer createPendingMerge(String name, UUID createdBy) throws RepositoryException;

  Layer get(UUID layerId) throws RepositoryException;

  Layer get(UUID layerId, RecordVisibility recordVisibility) throws RepositoryException;

  List<Layer> list();

  List<Layer> list(LayerReadFilter filter);

  void update(
      UUID layerId,
      String name,
      LayerAccessScope accessScope,
      Boolean defaultVisible,
      UUID updatedBy)
      throws RepositoryException;

  /**
   * Takes the layer's row lock for the rest of the transaction, so a read of its state that follows
   * cannot be overtaken by a concurrent writer. Must be called inside a transaction.
   */
  void lock(UUID layerId) throws RepositoryException;

  void archive(UUID layerId, UUID archivedBy) throws RepositoryException;

  /**
   * Clear the archive markers set by {@link #archive} and return the layer to ACTIVE. Compensating
   * action for a post-archive step that failed and left resources only the layer id can reach.
   */
  void unarchive(UUID layerId) throws RepositoryException;

  /**
   * Locks source layers for a merge.
   *
   * @param lockedBy the user who requested the merge — locking is part of their action, so the
   *     transition is attributed to them rather than left with the previous editor
   */
  void lockMergeSources(List<UUID> layerIds, UUID lockedBy) throws RepositoryException;

  void finalizeMergeSuccess(UUID targetLayerId, List<UUID> sourceLayerIds, UUID archivedBy)
      throws RepositoryException;

  void finalizeMergeFailure(UUID targetLayerId, List<UUID> sourceLayerIds)
      throws RepositoryException;
}
