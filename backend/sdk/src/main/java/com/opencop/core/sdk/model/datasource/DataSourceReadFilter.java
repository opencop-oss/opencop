// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.datasource;

import com.opencop.core.sdk.model.visibility.RecordVisibility;
import java.util.Set;
import java.util.UUID;

public record DataSourceReadFilter(
    UUID id,
    Set<UUID> ids,
    RecordVisibility recordVisibility,
    String brokerProvider,
    Boolean enabled,
    String assignedInstance) {

  public DataSourceReadFilter {
    ids = ids == null ? Set.of() : Set.copyOf(ids);
  }

  public RecordVisibility recordVisibilityOrDefault() {
    return recordVisibility == null ? RecordVisibility.ACTIVE : recordVisibility;
  }

  public boolean hasIds() {
    return !ids.isEmpty();
  }
}
