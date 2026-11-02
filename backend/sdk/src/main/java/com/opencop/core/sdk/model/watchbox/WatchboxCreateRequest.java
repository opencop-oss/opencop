// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import java.util.Set;
import java.util.UUID;
import org.geojson.Polygon;

public record WatchboxCreateRequest(
    String name,
    String description,
    Polygon polygon,
    WatchboxDirection direction,
    Set<UUID> layerIds,
    WatchboxFilter filter,
    Boolean enabled) {}
