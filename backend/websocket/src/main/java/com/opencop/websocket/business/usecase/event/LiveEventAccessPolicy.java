// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.event;

import com.opencop.websocket.ports.business.usecase.event.LiveEventAccessAuthorizer;
import com.opencop.websocket.ports.gateway.iam.LiveEventResourceAccessGateway;
import java.util.UUID;

public class LiveEventAccessPolicy implements LiveEventAccessAuthorizer {

  private final LiveEventResourceAccessGateway gateway;

  public LiveEventAccessPolicy(LiveEventResourceAccessGateway gateway) {
    this.gateway = gateway;
  }

  @Override
  public boolean userCanSubscribe(UUID userId, UUID resourceId) {
    if (gateway.userHasLayerAccess(userId, resourceId)) {
      return gateway.layerIsActive(resourceId);
    }

    if (!gateway.userHasSourceAccess(userId, resourceId)) {
      return false;
    }

    return gateway.sourceIsActive(resourceId);
  }
}
