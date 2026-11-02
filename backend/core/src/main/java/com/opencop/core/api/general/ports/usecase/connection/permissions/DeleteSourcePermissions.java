// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.permissions;

import java.util.UUID;

/** Port for deleting source permissions when a data source is deleted. */
public interface DeleteSourcePermissions {

  /**
   * Deletes all permissions (user and layer access) for a data source.
   *
   * @param sourceId the data source being deleted
   */
  void deletePermissions(UUID sourceId);
}
