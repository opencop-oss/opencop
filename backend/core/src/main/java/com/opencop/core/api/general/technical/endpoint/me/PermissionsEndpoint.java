// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.me;

import com.opencop.core.api.general.business.model.me.MyLayerResponse;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupMemberManagement;
import com.opencop.core.api.general.ports.usecase.me.RetrieveUserDataSourceGroups;
import com.opencop.core.api.general.ports.usecase.me.RetrieveUserLayers;
import com.opencop.core.api.general.ports.usecase.me.RetrieveUserSources;
import com.opencop.core.api.general.technical.model.datasourcegroup.MyDataSourceGroupResponse;
import com.opencop.core.api.general.technical.model.layergroup.MyLayerGroupResponse;
import com.opencop.core.sdk.model.iam.source.SourceInfo;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PermissionsEndpoint {

  private final RetrieveUserLayers userLayers;
  private final RetrieveUserSources userSources;
  private final RetrieveUserDataSourceGroups userDataSourceGroups;
  private final LayerGroupMemberManagement layerGroupMembers;

  PermissionsEndpoint(
      final RetrieveUserLayers userLayers,
      final RetrieveUserSources userSources,
      final RetrieveUserDataSourceGroups userDataSourceGroups,
      final LayerGroupMemberManagement layerGroupMembers) {
    this.userLayers = userLayers;
    this.userSources = userSources;
    this.userDataSourceGroups = userDataSourceGroups;
    this.layerGroupMembers = layerGroupMembers;
  }

  @GetMapping("/v1/me/layers")
  public List<MyLayerResponse> getMyLayers(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @RequestParam(defaultValue = "ACTIVE") RecordVisibility recordVisibility) {
    return this.userLayers.get(authentication.internalUserId(), recordVisibility);
  }

  @GetMapping("/v1/me/sources")
  public List<SourceInfo> getMySources(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @RequestParam(defaultValue = "ACTIVE") RecordVisibility recordVisibility) {
    return this.userSources.get(authentication.internalUserId(), recordVisibility);
  }

  @GetMapping("/v1/me/data-source-groups")
  public List<MyDataSourceGroupResponse> getMyDataSourceGroups(
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return userDataSourceGroups.get(authentication.internalUserId());
  }

  @GetMapping("/v1/me/layer-groups")
  public List<MyLayerGroupResponse> getMyLayerGroups(
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return layerGroupMembers.listMyLayerGroups(authentication);
  }

  @GetMapping("/v1/users/{userId}/layers")
  public List<MyLayerResponse> getUserLayers(
      @PathVariable UUID userId,
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @RequestParam(defaultValue = "ACTIVE") RecordVisibility recordVisibility) {
    assertCanReadUserPermissions(userId, authentication);
    return this.userLayers.get(userId, recordVisibility);
  }

  @GetMapping("/v1/users/{userId}/sources")
  public List<SourceInfo> getUserSources(
      @PathVariable UUID userId,
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @RequestParam(defaultValue = "ACTIVE") RecordVisibility recordVisibility) {
    assertCanReadUserPermissions(userId, authentication);
    return this.userSources.get(userId, recordVisibility);
  }

  private static void assertCanReadUserPermissions(
      UUID requestedUserId, OpenCopPrincipal authentication) {
    if (authentication != null
        && (authentication.isGlobalAdmin()
            || authentication.internalUserId().equals(requestedUserId))) {
      return;
    }
    throw new AccessDeniedException("Cannot read another user's permissions");
  }
}
