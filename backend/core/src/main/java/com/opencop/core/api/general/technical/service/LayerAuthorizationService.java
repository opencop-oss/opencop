// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.service;

import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;

/**
 * Shared layer authorization logic. Checks whether a user is a global admin or has one of the
 * required roles on a given layer.
 *
 * <p>Extracted from {@code LayersEndpoint} so that other layer-scoped endpoints (e.g., layer media)
 * can reuse the same authorization check.
 */
public class LayerAuthorizationService {

  private static final Logger log = LoggerFactory.getLogger(LayerAuthorizationService.class);
  private final LayerManager<Layer, LayerMember> layerManager;

  public LayerAuthorizationService(LayerManager<Layer, LayerMember> layerManager) {
    this.layerManager = layerManager;
  }

  public void assertLayerRole(
      UUID layerId, OpenCopPrincipal authentication, LayerRole... allowedRoles) {
    assertLayerRole(
        layerId,
        authentication,
        "You are not authorized for the specified change to this layer",
        allowedRoles);
  }

  public void assertLayerRole(
      UUID layerId,
      OpenCopPrincipal authentication,
      String errorMessage,
      LayerRole... allowedRoles) {
    if (authentication.isGlobalAdmin()) {
      return;
    }

    var allowed = Set.of(allowedRoles);
    var role = this.layerManager.getMemberRole(layerId, authentication.internalUserId());
    if (role.isPresent() && allowed.contains(role.get())) {
      return;
    }

    log.warn(
        "User {} (role={}) denied access to layer {} (required: {})",
        authentication.internalUserId(),
        role.orElse(null),
        layerId,
        allowed);
    throw new AccessDeniedException(errorMessage);
  }

  /**
   * Checks that the user is a member of the layer (any role) or a global admin. Throws
   * AccessDeniedException otherwise.
   */
  public void assertLayerMember(UUID layerId, OpenCopPrincipal authentication) {
    assertLayerRole(
        layerId,
        authentication,
        "You are not a member of this layer",
        LayerRole.OWNER,
        LayerRole.ADMIN,
        LayerRole.EDITOR,
        LayerRole.MEMBER);
  }
}
