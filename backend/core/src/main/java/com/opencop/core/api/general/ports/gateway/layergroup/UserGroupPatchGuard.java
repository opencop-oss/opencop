// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.layergroup;

import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import java.util.Map;
import java.util.UUID;

/**
 * Callback invoked after the parent row lock is acquired and the current assignments are read, but
 * before writes are applied. Throwing aborts the transaction so the patch is not persisted.
 */
@FunctionalInterface
public interface UserGroupPatchGuard {
  void check(
      Map<UUID, LayerGroupRole> currentUsers,
      Map<UUID, LayerGroupRole> currentUserGroups,
      Map<UUID, LayerGroupRole> projectedUserGroups)
      throws PersistenceException;
}
