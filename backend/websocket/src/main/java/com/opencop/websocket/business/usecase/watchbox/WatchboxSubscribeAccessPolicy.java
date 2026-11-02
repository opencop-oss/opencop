// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.watchbox;

import com.opencop.websocket.ports.business.usecase.watchbox.WatchboxSubscribeAuthorizer;
import com.opencop.websocket.ports.gateway.watchbox.WatchboxAccessGateway;
import java.util.UUID;

/**
 * WebSocket-side mirror of core's {@code WatchboxLayerAccessPolicy} visibility rule
 * (all-layers-or-nothing): a subscriber must be a current active global administrator or hold a
 * role on every selected layer of an active watchbox. Fail-closed for completed domain outcomes: an
 * unknown or archived watchbox id, an empty layer set, and any unreachable layer all deny —
 * including for administrators on nonexistent watchboxes, so no ghost destination is ever
 * registered. Failures while querying those facts propagate rather than becoming denials. Selected
 * layers need no separate lifecycle check: V37's triggers block archiving a layer while a watchbox
 * references it.
 *
 * <p>The mirror holds at admission time, then is re-asserted on the delivery path: {@code
 * WatchboxSubscriptionReauthorizer} re-runs this policy for a destination's registered sessions
 * before delivering an alert whenever the audience's last full check is older than the configured
 * interval ({@code opencop.watchbox.subscription-reauth-interval}). Both drift vectors — the
 * watchbox's layer composition edited under its stable id, and a subscriber's layer role revoked
 * mid-session — are therefore corrected within one interval of the next delivered alert, and a
 * quiet watchbox delivers nothing through its stale registrations. That bound is deliberately
 * stronger than this service's other destination families, which authorize once at SUBSCRIBE for
 * the session's lifetime. Archive/disable is NOT a drift vector (evaluation stops at the source,
 * and V37's triggers block archiving a layer while a watchbox references it).
 */
public class WatchboxSubscribeAccessPolicy implements WatchboxSubscribeAuthorizer {

  private final WatchboxAccessGateway gateway;

  public WatchboxSubscribeAccessPolicy(WatchboxAccessGateway gateway) {
    this.gateway = gateway;
  }

  @Override
  public boolean userCanSubscribe(UUID userId, UUID watchboxId) {
    var layerIds = gateway.activeWatchboxLayerIds(watchboxId);
    if (layerIds.isEmpty() || layerIds.get().isEmpty()) {
      return false;
    }
    if (gateway.userIsActiveAdministrator(userId)) {
      return true;
    }
    return layerIds.get().stream().allMatch(layerId -> gateway.userHasLayerAccess(userId, layerId));
  }
}
