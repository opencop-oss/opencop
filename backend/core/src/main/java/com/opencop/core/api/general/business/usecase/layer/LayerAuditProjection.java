// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import com.opencop.core.api.general.business.model.audit.AuditChange;
import com.opencop.core.api.general.business.model.audit.AuditDeltas;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.model.iam.layer.LayerPatchRequest;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The safe projection of a layer change. Id lists are bounded by {@link AuditDeltas}. */
@Component
public class LayerAuditProjection {

  /** Creation, carrying whoever the layer started with so the row stands alone. */
  public AuditChange create(Layer created, Map<UUID, LayerRole> initialMembers) {
    return AuditChange.builder()
        .field("name", null, created.getName())
        .field("accessScope", null, created.getAccessScope())
        .field("defaultVisible", null, created.isDefaultVisible())
        .delta(granted(initialMembers))
        .build();
  }

  /** The fields a patch actually moved, or empty when it moved none. */
  public Optional<AuditChange> patch(Layer before, LayerPatchRequest request) {
    var change = AuditChange.builder();
    if (request.name() != null) {
      change.moved("name", before.getName(), request.name());
    }
    if (request.accessScope() != null) {
      change.moved("accessScope", before.getAccessScope(), request.accessScope());
    }
    if (request.defaultVisible() != null) {
      change.moved("defaultVisible", before.isDefaultVisible(), request.defaultVisible());
    }
    return change.isEmpty() ? Optional.empty() : Optional.of(change.build());
  }

  /** One row per request, whatever the mix of additions, removals and role moves it carried. */
  public AuditChange roleSetChanged(AuditDeltas.Delta<?> delta) {
    return AuditChange.ofDelta(delta);
  }

  /** Archiving and restoring move no field, so the row carries only the state it left. */
  public AuditChange lifecycle(Layer before) {
    return AuditChange.builder().detail("accessScope", before.getAccessScope()).build();
  }

  /** Written on the layer that was copied, pointing at the copy. */
  public AuditChange clonedTo(Layer clone) {
    return AuditChange.builder()
        .detail("cloneId", clone.getId())
        .detail("cloneName", clone.getName())
        .build();
  }

  /** Written on the copy, pointing back at the original and listing who it started with. */
  public AuditChange createdFromClone(Layer source, Map<UUID, LayerRole> initialMembers) {
    return AuditChange.builder()
        .detail("clonedFromId", source.getId())
        .detail("clonedFromName", source.getName())
        .delta(granted(initialMembers))
        .build();
  }

  /**
   * Written on the merge target at request time.
   *
   * <p>A merge finishes in the processor service, which has no user behind it, so this records that
   * a merge was requested rather than that one completed. The outcome stays visible on the layer's
   * own lifecycle state.
   */
  public AuditChange mergedFrom(List<Layer> sources, String resultName) {
    return AuditChange.builder()
        .detail("resultName", resultName)
        .idList("mergedFrom", sources.stream().map(Layer::getId).toList())
        .detail("mergedFromNames", sources.stream().map(Layer::getName).toList())
        .build();
  }

  /** Written on each source layer at request time, pointing at what it was merged into. */
  public AuditChange mergedInto(Layer target, String resultName) {
    return AuditChange.builder()
        .detail("mergedIntoId", target.getId())
        .detail("resultName", resultName)
        .build();
  }

  /** An export, recorded when it is requested rather than when the file is fetched. */
  public AuditChange exported(String format, UUID exportId, int subjectCount) {
    return AuditChange.exported(format, exportId, subjectCount);
  }

  /**
   * An import that was accepted, not one that finished.
   *
   * <p>The mapping runs asynchronously in the processor and there is no import status table, so the
   * ledger cannot honestly say what landed.
   */
  public AuditChange importRequested(String filename, String format, UUID mapperId, UUID importId) {
    return AuditChange.builder()
        .detail("filename", AuditDeltas.truncate(filename))
        .detail("format", format)
        .detail("mapperId", mapperId)
        .detail("importId", importId)
        .build();
  }

  /** The initial membership, as the delta a reader sees on every other membership row. */
  private static AuditDeltas.Delta<LayerRole> granted(Map<UUID, LayerRole> members) {
    return new AuditDeltas.Delta<>(members, Set.of(), Map.of());
  }
}
