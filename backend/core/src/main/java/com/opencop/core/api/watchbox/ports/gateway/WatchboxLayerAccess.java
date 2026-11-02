// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.ports.gateway;

import com.opencop.core.api.watchbox.business.model.WatchboxLayerRole;
import com.opencop.core.api.watchbox.business.model.WatchboxLayerState;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface WatchboxLayerAccess {

  WatchboxLayerState activeLayerState(UUID layerId);

  Map<UUID, WatchboxLayerRole> rolesForLayers(Set<UUID> layerIds, WatchboxCaller caller);
}
