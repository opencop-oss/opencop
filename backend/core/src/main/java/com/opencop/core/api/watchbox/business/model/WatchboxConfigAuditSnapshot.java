// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.model;

import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.geojson.Polygon;

/**
 * Versioned, allowlisted event-time snapshot for Watchbox configuration history.
 *
 * <p>The persisted v1 JSON shape and enum vocabulary are a migration-owned durable contract. An
 * incompatible change after release requires a new schema version and migration rather than a
 * silent reinterpretation of existing rows.
 */
public record WatchboxConfigAuditSnapshot(
    int schemaVersion,
    UUID id,
    String name,
    String description,
    boolean enabled,
    WatchboxDirection direction,
    Polygon polygon,
    WatchboxFilter filter,
    List<UUID> layerIds,
    long configEpoch,
    Instant createdAt,
    Instant updatedAt) {

  public static final int CURRENT_SCHEMA_VERSION = 1;

  public WatchboxConfigAuditSnapshot {
    if (schemaVersion != CURRENT_SCHEMA_VERSION) {
      throw new IllegalArgumentException("Unsupported Watchbox audit snapshot schema version");
    }
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(polygon, "polygon");
    Objects.requireNonNull(filter, "filter");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    layerIds = List.copyOf(Objects.requireNonNull(layerIds, "layerIds"));
  }

  public static WatchboxConfigAuditSnapshot from(WatchboxConfig config) {
    Objects.requireNonNull(config, "config");
    return new WatchboxConfigAuditSnapshot(
        CURRENT_SCHEMA_VERSION,
        config.id(),
        config.name(),
        config.description(),
        config.enabled(),
        config.direction(),
        config.polygon(),
        config.filter(),
        config.layerIds().stream().sorted(Comparator.comparing(UUID::toString)).toList(),
        config.configEpoch(),
        config.createdAt(),
        config.updatedAt());
  }
}
