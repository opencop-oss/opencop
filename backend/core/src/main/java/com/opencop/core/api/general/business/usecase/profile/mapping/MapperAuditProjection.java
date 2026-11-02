// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.profile.mapping;

import com.opencop.core.api.general.business.model.audit.AuditChange;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The safe projection of a mapper change.
 *
 * <p>Records which fields moved and whether the decoding rules moved, never the rules themselves
 * and never the sample payload: both are already kept in full by the mapper's own version history,
 * and a sample payload is arbitrary customer data.
 */
@Component
public class MapperAuditProjection {

  /** The initial state, with every recorded field moving from absent to its first value. */
  public AuditChange create(MapperProfile created) {
    return AuditChange.builder()
        .field("name", null, created.name())
        .field("description", null, created.description())
        .field("editable", null, created.editable())
        .detail("rulesChanged", true)
        .detail("versionAfter", created.version())
        .build();
  }

  /**
   * The difference between two versions, or empty when the save would change nothing.
   *
   * <p>An empty result means the whole save is a no-op: the caller writes neither a new version nor
   * a history row.
   */
  public Optional<AuditChange> patch(
      MapperProfile before, MapperProfile after, boolean payloadChanged) {
    var rulesChanged = !Objects.equals(before.dynamicMapper(), after.dynamicMapper());
    var change =
        AuditChange.builder()
            .moved("name", before.name(), after.name())
            .moved("description", before.description(), after.description())
            .moved("editable", before.editable(), after.editable());

    if (change.isEmpty() && !rulesChanged && !payloadChanged) {
      return Optional.empty();
    }
    return Optional.of(
        change
            .detail("rulesChanged", rulesChanged)
            // The sample payload is never stored, so its movement is reported as a bare fact.
            .detail("samplePayloadChanged", payloadChanged)
            .detail("versionBefore", before.version())
            .detail("versionAfter", before.version() == null ? null : before.version() + 1)
            .build());
  }

  /** Archiving moves no field, so the row carries only the version it archived. */
  public AuditChange archive(MapperProfile before) {
    return AuditChange.builder().detail("versionArchived", before.version()).build();
  }
}
