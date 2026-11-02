// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.cache;

import java.util.List;
import java.util.UUID;
import org.geojson.Feature;

public interface GeoCacheGateway {

  void write(List<Feature> inputs, boolean publishUpdates);

  void clearSource(UUID sourceId);

  void evictObject(UUID sourceId, String objectId, long ifNotNewerThanMs);
}
