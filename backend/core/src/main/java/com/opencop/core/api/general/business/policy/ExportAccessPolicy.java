// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ExportAccessPolicy {

  private final UserSpecificManager userManager;

  public ExportAccessPolicy(UserSpecificManager userManager) {
    this.userManager = userManager;
  }

  public void assertCanExportLayer(OpenCopPrincipal principal, UUID layerId) {
    assertCanExportLayers(principal, Set.of(layerId));
  }

  public void assertCanExportLayers(OpenCopPrincipal principal, Collection<UUID> layerIds) {
    if (principal.isGlobalAdmin()) {
      return;
    }
    var authorizedLayerIds =
        userManager.getUserLayers(principal.internalUserId()).stream()
            .filter(
                layer ->
                    layerIds.contains(layer.layerId()) && layer.role().isAtLeast(LayerRole.ADMIN))
            .map(layer -> layer.layerId())
            .collect(Collectors.toSet());
    if (!authorizedLayerIds.containsAll(layerIds)) {
      throw new GlobalOpenCopException("Insufficient permissions", HttpStatus.FORBIDDEN);
    }
  }

  public void assertCanExportSource(OpenCopPrincipal principal, UUID sourceId) {
    assertCanExportSources(principal, Set.of(sourceId));
  }

  public void assertCanExportSources(OpenCopPrincipal principal, Collection<UUID> sourceIds) {
    if (principal.isGlobalAdmin()) {
      return;
    }
    var authorizedSourceIds =
        userManager.getUserServers(principal.internalUserId()).stream()
            .filter(
                source ->
                    sourceIds.contains(source.serverId())
                        && source.role().isAtLeast(ServerRole.ADMIN))
            .map(source -> source.serverId())
            .collect(Collectors.toSet());
    if (!authorizedSourceIds.containsAll(sourceIds)) {
      throw new GlobalOpenCopException("Insufficient permissions", HttpStatus.FORBIDDEN);
    }
  }
}
