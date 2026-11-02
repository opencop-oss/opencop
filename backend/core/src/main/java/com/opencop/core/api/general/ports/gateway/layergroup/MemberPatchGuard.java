// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.layergroup;

import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import java.util.Map;
import java.util.UUID;

/**
 * Callback invoked by {@link LayerGroupService#patchMembers} after the parent row lock has been
 * acquired and the current direct/user-group membership state has been read, but before any writes
 * are applied. Throwing aborts the transaction so the patch is not persisted.
 */
@FunctionalInterface
public interface MemberPatchGuard {
  void check(
      Map<UUID, LayerGroupRole> currentUsers,
      Map<UUID, LayerGroupRole> projectedUsers,
      Map<UUID, LayerGroupRole> currentUserGroups)
      throws PersistenceException;
}
