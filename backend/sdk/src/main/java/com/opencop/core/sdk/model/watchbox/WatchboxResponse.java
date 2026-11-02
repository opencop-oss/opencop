// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.geojson.Polygon;

public record WatchboxResponse(
    UUID id,
    String name,
    String description,
    boolean enabled,
    WatchboxDirection direction,
    Polygon polygon,
    WatchboxFilter filter,
    Set<UUID> layerIds,
    long configEpoch,
    WatchboxAlertingStatus alertingStatus,
    Set<WatchboxStatusReason> alertingReasons,
    List<WatchboxSourceStatusResponse> sourceStatuses,
    WatchboxCapabilities capabilities,
    Instant createdAt,
    Instant updatedAt) {}
