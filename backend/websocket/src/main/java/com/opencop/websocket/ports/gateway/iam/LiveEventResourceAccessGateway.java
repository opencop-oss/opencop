// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.ports.gateway.iam;

import java.util.UUID;

public interface LiveEventResourceAccessGateway {

  boolean userHasLayerAccess(UUID userId, UUID layerId);

  boolean userHasSourceAccess(UUID userId, UUID sourceId);

  boolean layerIsActive(UUID layerId);

  boolean sourceIsActive(UUID sourceId);
}
