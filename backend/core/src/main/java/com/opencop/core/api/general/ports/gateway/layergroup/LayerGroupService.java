// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.layergroup;

import com.opencop.core.api.general.technical.model.layergroup.LayerGroupCreateRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMemberResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.api.general.technical.model.layergroup.MyLayerGroupResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.List;
import java.util.UUID;

public interface LayerGroupService {

  List<LayerGroupResponse> listGroups();

  LayerGroupResponse createGroup(LayerGroupCreateRequest request) throws PersistenceException;

  LayerGroupResponse getGroup(UUID groupId) throws PersistenceException;

  LayerGroupResponse patchGroup(UUID groupId, LayerGroupPatchRequest request)
      throws PersistenceException;

  void deleteGroup(UUID groupId) throws PersistenceException;

  /**
   * Takes the group's row lock until the enclosing transaction ends, so state read afterwards
   * cannot be overtaken by a concurrent writer. Must be called inside a transaction.
   */
  void lockGroup(UUID groupId) throws PersistenceException;

  List<LayerGroupMemberResponse> getMembers(UUID layerGroupId) throws PersistenceException;

  /**
   * Atomically applies the member patch. Acquires a row-level write lock on the parent layer-group
   * row to serialize concurrent patches, reads the current member list, invokes {@code guard} (if
   * non-null) with the current and projected member maps so the caller can enforce invariants, and
   * then persists the adds/removes — all in a single transaction.
   */
  void patchMembers(
      UUID layerGroupId, LayerGroupMembersPatchRequest request, MemberPatchGuard guard)
      throws PersistenceException;

  List<MyLayerGroupResponse> listMyLayerGroups(UUID userId);
}
