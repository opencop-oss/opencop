// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.authorization;

import java.util.UUID;

public interface ConnectionAuthorization {

  /**
   * Requires the user to see the connection: any effective role on it, direct or derived from a
   * layer. A connection the user cannot see is reported exactly like a missing one, with 404, so
   * the answer never confirms that it exists.
   */
  void assertCanRead(UUID sourceId, UUID userId, boolean isGlobalAdmin);

  /**
   * Whether the user may administer the connection: a global admin, or a direct OWNER of it. The
   * same rule {@link #assertDirectOwnerOrAdmin} enforces, for callers that only need to know.
   */
  boolean isDirectOwnerOrAdmin(UUID sourceId, UUID userId, boolean isGlobalAdmin);

  void assertDirectOwnerOrAdmin(UUID sourceId, UUID userId, boolean isGlobalAdmin);

  void assertDirectOwnerAdminOrGlobalAdmin(UUID sourceId, UUID userId, boolean isGlobalAdmin);
}
