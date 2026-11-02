// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.layer;

import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import java.util.Set;
import java.util.UUID;

public record LayerReadFilter(
    RecordVisibility recordVisibility,
    LayerAccessScope accessScope,
    String name,
    UUID id,
    Set<UUID> ids) {

  public LayerReadFilter(
      RecordVisibility recordVisibility, LayerAccessScope accessScope, String name) {
    this(recordVisibility, accessScope, name, null, Set.of());
  }

  public LayerReadFilter {
    ids = ids == null ? Set.of() : Set.copyOf(ids);
  }

  public RecordVisibility recordVisibilityOrDefault() {
    return recordVisibility == null ? RecordVisibility.ACTIVE : recordVisibility;
  }

  public boolean hasIds() {
    return !ids.isEmpty();
  }
}
