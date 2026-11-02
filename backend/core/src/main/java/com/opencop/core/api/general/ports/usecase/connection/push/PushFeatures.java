// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.push;

import java.util.UUID;
import org.geojson.FeatureCollection;

public interface PushFeatures {
  void execute(UUID sourceId, UUID userId, FeatureCollection features);
}
