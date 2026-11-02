// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.runtimefeature;

import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureBusyException;
import java.util.Set;
import java.util.UUID;

public interface RuntimeFeatureUserGroupAccess {

  /**
   * Stabilizes current target-group existence for the surrounding audience replacement and returns
   * the IDs that are not currently present.
   *
   * @throws RuntimeFeatureBusyException when a stable view cannot be acquired without waiting
   */
  Set<UUID> stabilizeAndFindMissingTargets(Set<UUID> userGroupIds);

  /** Returns true iff the user is a current direct member of any listed group. */
  boolean hasMembership(UUID userId, Set<UUID> userGroupIds);
}
