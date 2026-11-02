// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.db;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.geojson.Feature;

public interface GeoCacheHydrationGateway {

  HydrationSnapshotStats hydrateSnapshot(
      UUID sourceId, int pageSize, Consumer<List<Feature>> pageConsumer);

  record HydrationSnapshotStats(int pageCount, int featureCount, String lastObjectId) {}
}
