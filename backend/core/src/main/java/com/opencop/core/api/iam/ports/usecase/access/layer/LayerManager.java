// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.usecase.access.layer;

import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.model.iam.layer.LayerCreateRequest;
import com.opencop.core.sdk.model.iam.layer.LayerPatchRequest;
import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.iam.layer.UpdateLayerRoleRequest;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface LayerManager<T, U> {

  T create(LayerCreateRequest request, UUID createdBy);

  T get(UUID layerId);

  List<T> list();

  List<T> list(LayerReadFilter filter);

  void update(UUID layerId, LayerPatchRequest request, UUID updatedBy);

  /** Locks the layer's row until the enclosing transaction ends; see the repository's contract. */
  void lock(UUID layerId);

  void archive(UUID layerId, UUID archivedBy);

  /** Reverses {@link #archive}, returning an archived layer to ACTIVE. */
  void unarchive(UUID layerId);

  List<U> listLayerMembers(UUID layerId);

  /** Single-row lookup: returns the user's role on a layer without fetching all members. */
  Optional<LayerRole> getMemberRole(UUID layerId, UUID userId);

  void updateLayerMemberRole(UUID layerId, UpdateLayerRoleRequest request);

  /**
   * @return each affected id mapped to the role it ended with, which may be higher than asked.
   */
  Map<UUID, LayerRole> addLayerMembers(UUID layerId, Map<UUID, LayerRole> userIds);

  void removeLayerMembers(UUID layerId, Set<UUID> userIds);

  Set<SourceLayer> listLayerServers(UUID layerId);
  //    void addServerLayers(String serverId, List<String> layersId);
  //    void deleteServerLayers(String serverId, List<String> layersId);
}
