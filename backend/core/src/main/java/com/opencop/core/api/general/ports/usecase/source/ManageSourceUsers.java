// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.source;

import com.opencop.core.sdk.model.iam.source.UpdateSourcePermissionsRequest;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Set;
import java.util.UUID;

/** Changes who may read or manage a data source. */
public interface ManageSourceUsers {

  /**
   * Grants or changes roles for the users the request names.
   *
   * <p>A per-id upsert, not a replacement of the whole permission set: ids the request does not
   * name keep whatever they had, and ids that cannot be resolved are skipped.
   */
  void updateUsers(UUID sourceId, UpdateSourcePermissionsRequest request, OpenCopPrincipal caller);

  /** Revokes access for the named ids; ids that held none are skipped. */
  void deleteUsers(UUID sourceId, Set<UUID> userIds, OpenCopPrincipal caller);
}
