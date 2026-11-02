// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase;

import java.util.UUID;
import org.geojson.Feature;

public interface EventBroadcaster<O> {

  Feature create(UUID userId, UUID containerId, Feature event);

  Feature patch(UUID userId, UUID containerId, O objectId, Feature event);

  void delete(UUID userId, UUID containerId, O objectId);
}
