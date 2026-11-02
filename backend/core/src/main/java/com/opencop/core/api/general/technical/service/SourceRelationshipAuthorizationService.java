// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.service;

import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.iam.source.UpdateSourcePermissionsRequest;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Interim source relationship authorization that mirrors the capabilities exposed by the
 * first-party UI. The final product ownership rule remains intentionally separate from this
 * compatibility policy.
 */
@Component
public class SourceRelationshipAuthorizationService {

  private static final String DENIED_MESSAGE =
      "Insufficient permissions to manage source relationships";

  private final UserSpecificManager userManager;

  public SourceRelationshipAuthorizationService(UserSpecificManager userManager) {
    this.userManager = userManager;
  }

  public void assertCanAdd(
      OpenCopPrincipal principal, UUID sourceId, UpdateSourcePermissionsRequest request) {
    if (principal.isGlobalAdmin()) {
      return;
    }

    var userId = principal.internalUserId();
    var effectiveLayers = userManager.getUserLayers(userId, RecordVisibility.ALL);
    var memberLayerIds =
        effectiveLayers.stream().map(LayerInfo::layerId).collect(Collectors.toSet());
    var managedLayerIds = managedLayerIds(effectiveLayers);
    var effectiveSourceOwner = isEffectiveSourceOwner(userId, sourceId);
    var nonOwnerLayerIds = new HashSet<UUID>();
    nonOwnerLayerIds.addAll(request.viewers());
    nonOwnerLayerIds.addAll(request.admins());

    var nonOwnerRolesAllowed =
        nonOwnerLayerIds.stream()
            .allMatch(
                layerId ->
                    managedLayerIds.contains(layerId)
                        || (effectiveSourceOwner && memberLayerIds.contains(layerId)));
    var ownerRoleAllowed =
        request.owners().isEmpty()
            || (effectiveSourceOwner && memberLayerIds.containsAll(request.owners()));
    if (!nonOwnerRolesAllowed || !ownerRoleAllowed) {
      throw denied();
    }
  }

  public void assertCanRemove(
      OpenCopPrincipal principal, UUID sourceId, Collection<UUID> targetLayerIds) {
    if (principal.isGlobalAdmin()) {
      return;
    }

    var userId = principal.internalUserId();
    if (isEffectiveSourceOwner(userId, sourceId)) {
      return;
    }

    var managedLayerIds = managedLayerIds(userManager.getUserLayers(userId, RecordVisibility.ALL));
    if (!managedLayerIds.containsAll(targetLayerIds)) {
      throw denied();
    }
  }

  public void assertCanManageSource(OpenCopPrincipal principal, UUID sourceId) {
    if (principal.isGlobalAdmin() || isEffectiveSourceOwner(principal.internalUserId(), sourceId)) {
      return;
    }
    throw denied();
  }

  /**
   * Reading a source requires only that the caller can see it — any effective role, direct or
   * layer-derived, exactly as {@code GET /v1/me/sources} reports. Reads are deliberately wider than
   * {@link #assertCanManageSource}: a caller who finds a source in their own list must be able to
   * fetch its detail.
   *
   * <p>Rejects with 404 rather than 403 so an invisible source and a nonexistent one are
   * indistinguishable — a 403 here would confirm that an id the caller may not read exists.
   */
  public void assertCanReadSource(OpenCopPrincipal principal, UUID sourceId) {
    if (canReadSource(principal.internalUserId(), sourceId, principal.isGlobalAdmin())) {
      return;
    }
    throw new GlobalOpenCopException("Data source not found: " + sourceId, HttpStatus.NOT_FOUND);
  }

  /**
   * Whether the user can see the source: a global admin, or any effective role on it, direct or
   * layer-derived. The one rule every read of a source applies.
   */
  public boolean canReadSource(UUID userId, UUID sourceId, boolean isGlobalAdmin) {
    return isGlobalAdmin
        || userManager.getUserServers(userId).stream()
            .anyMatch(source -> source.serverId().equals(sourceId));
  }

  private boolean isEffectiveSourceOwner(UUID userId, UUID sourceId) {
    return userManager.getUserServers(userId).stream()
        .anyMatch(
            source -> source.serverId().equals(sourceId) && source.role() == ServerRole.OWNER);
  }

  private static Set<UUID> managedLayerIds(Collection<LayerInfo> effectiveLayers) {
    return effectiveLayers.stream()
        .filter(layer -> layer.role().isAtLeast(LayerRole.ADMIN))
        .map(LayerInfo::layerId)
        .collect(Collectors.toSet());
  }

  private static GlobalOpenCopException denied() {
    return new GlobalOpenCopException(DENIED_MESSAGE, HttpStatus.FORBIDDEN);
  }
}
