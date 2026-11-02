// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layergroup;

import com.opencop.core.api.general.business.model.audit.AuditChange;
import com.opencop.core.api.general.business.model.audit.AuditDeltas;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The safe projection of a layer group change.
 *
 * <p>A layer group carries no {@code updated_at} or {@code updated_by} of its own, so for these
 * resources the ledger is the only record of who changed what.
 */
@Component
public class LayerGroupAuditProjection {

  /** Creation, including the owner the group starts with. */
  public AuditChange create(LayerGroupResponse created, UUID ownerId) {
    return AuditChange.builder()
        .field("name", null, created.name())
        .field("description", null, created.description())
        .field("accessScope", null, created.accessScope())
        .delta(new AuditDeltas.Delta<>(Map.of(ownerId, LayerGroupRole.OWNER), Set.of(), Map.of()))
        .build();
  }

  /** The fields a patch actually moved, or empty when it moved none. */
  public Optional<AuditChange> patch(LayerGroupResponse before, LayerGroupResponse after) {
    var change =
        AuditChange.builder()
            .moved("name", before.name(), after.name())
            .moved("description", before.description(), after.description())
            .moved("accessScope", before.accessScope(), after.accessScope());
    return change.isEmpty() ? Optional.empty() : Optional.of(change.build());
  }

  /**
   * A hard delete, with what went with it.
   *
   * <p>Deleting a group cascades its members, layers and user groups away, and the drawer that
   * would show this history disappears with it. The counts are captured here because afterwards
   * there is nothing left to count.
   */
  public AuditChange delete(LayerGroupResponse before, int members, int layers, int userGroups) {
    return AuditChange.builder()
        .detail("accessScope", before == null ? null : before.accessScope())
        .detail("removedMemberCount", members)
        .detail("removedLayerCount", layers)
        .detail("removedUserGroupCount", userGroups)
        .build();
  }

  /**
   * A change to who holds a role in the group, from the delta between the roles read before and
   * after the write. Used for members, user groups and layers alike: each is an id-to-role set.
   */
  public AuditChange roleSetChanged(AuditDeltas.Delta<?> delta) {
    return AuditChange.ofDelta(delta);
  }
}
