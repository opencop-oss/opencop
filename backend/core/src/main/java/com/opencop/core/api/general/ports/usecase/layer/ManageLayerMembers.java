// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.layer;

import com.opencop.core.sdk.model.iam.layer.UpdateLayerRoleRequest;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Changes who holds which role on a layer.
 *
 * <p>Every method is a per-id upsert against the stored membership rather than a replacement of it,
 * so an id the call does not name keeps whatever it had.
 */
public interface ManageLayerMembers {

  /** Grants the named roles. Naming an owner requires the caller to be one. */
  void addMembers(UUID layerId, Map<UUID, LayerRole> request, OpenCopPrincipal caller);

  /** Moves the named ids to the roles the request buckets them into. */
  void updateMemberRole(UUID layerId, UpdateLayerRoleRequest request, OpenCopPrincipal caller);

  /** Revokes access for the named ids; ids that held none are skipped. */
  void removeMembers(UUID layerId, Set<UUID> userIds, OpenCopPrincipal caller);
}
