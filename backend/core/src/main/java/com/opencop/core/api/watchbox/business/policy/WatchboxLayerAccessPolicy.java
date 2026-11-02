// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.policy;

import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED;
import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_NOT_FOUND;

import com.opencop.core.api.watchbox.business.model.WatchboxLayerRole;
import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxLayerAccess;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class WatchboxLayerAccessPolicy {

  private final WatchboxLayerAccess layerAccess;

  public WatchboxLayerAccessPolicy(WatchboxLayerAccess layerAccess) {
    this.layerAccess = layerAccess;
  }

  public LayerAccessDecision decide(Set<UUID> layerIds, WatchboxCaller caller) {
    return contextFor(layerIds, caller).decide(layerIds);
  }

  /**
   * Resolves the caller's roles for {@code layerIds} with one gateway read and answers later
   * per-watchbox decisions from that snapshot in memory. Build it once per request from the union
   * of every layer id the request will decide on; it is a per-request object, never a cache.
   */
  public LayerAccessContext contextFor(Set<UUID> layerIds, WatchboxCaller caller) {
    if (caller.globalAdmin()) {
      return new LayerAccessContext(true, Map.of());
    }
    Set<UUID> requestedLayerIds = Set.copyOf(layerIds);
    Map<UUID, WatchboxLayerRole> roles =
        requestedLayerIds.isEmpty()
            ? Map.of()
            : layerAccess.rolesForLayers(requestedLayerIds, caller);
    return new LayerAccessContext(false, roles);
  }

  public void assertVisible(Set<UUID> layerIds, WatchboxCaller caller) {
    if (!decide(layerIds, caller).fullyVisible()) {
      throw new WatchboxException(
          WATCHBOX_NOT_FOUND, "Watchbox not found", HttpStatus.NOT_FOUND, List.of(), List.of());
    }
  }

  public void assertCanManage(Set<UUID> layerIds, WatchboxCaller caller) {
    if (!decide(layerIds, caller).managesAll()) {
      throw new WatchboxException(
          WATCHBOX_MANAGEMENT_ROLE_REQUIRED,
          "Watchbox management role is required",
          HttpStatus.FORBIDDEN,
          List.of("layerIds"));
    }
  }

  private static boolean canManage(WatchboxLayerRole role) {
    return role == WatchboxLayerRole.OWNER || role == WatchboxLayerRole.ADMIN;
  }

  public record LayerAccessDecision(boolean fullyVisible, boolean managesAll) {}

  /**
   * Caller-scoped role snapshot. {@code roles} may cover a superset of any one watchbox's layers,
   * so decisions look up exactly the ids asked about, never the whole map.
   */
  public record LayerAccessContext(boolean globalAdmin, Map<UUID, WatchboxLayerRole> roles) {

    public LayerAccessContext {
      roles = Map.copyOf(roles);
    }

    public LayerAccessDecision decide(Set<UUID> layerIds) {
      if (globalAdmin) {
        return new LayerAccessDecision(true, true);
      }
      boolean fullyVisible = !layerIds.isEmpty() && roles.keySet().containsAll(layerIds);
      boolean managesAll =
          fullyVisible
              && layerIds.stream().map(roles::get).allMatch(WatchboxLayerAccessPolicy::canManage);
      return new LayerAccessDecision(fullyVisible, managesAll);
    }
  }
}
