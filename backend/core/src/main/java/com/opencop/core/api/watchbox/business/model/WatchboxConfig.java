// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.model;

import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.geojson.Polygon;

public record WatchboxConfig(
    UUID id,
    String name,
    String description,
    boolean enabled,
    WatchboxDirection direction,
    Polygon polygon,
    WatchboxFilter filter,
    Set<UUID> layerIds,
    long configEpoch,
    Instant createdAt,
    Instant updatedAt) {

  public WatchboxConfig {
    layerIds = layerIds == null ? Set.of() : Set.copyOf(layerIds);
    filter = filter == null ? WatchboxFilter.empty() : filter;
  }
}
