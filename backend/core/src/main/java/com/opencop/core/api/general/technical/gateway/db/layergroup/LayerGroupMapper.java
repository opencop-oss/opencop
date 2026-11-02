// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.layergroup;

import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMemberResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerSummary;
import com.opencop.core.api.general.technical.model.layergroup.MyLayerGroupResponse;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupLayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupUserGroupEntity;
import java.util.List;

public final class LayerGroupMapper {

  private LayerGroupMapper() {}

  public static LayerGroupResponse toResponse(LayerGroupEntity e) {
    return new LayerGroupResponse(
        e.getId(), e.getName(), e.getDescription(), e.getAccessScope(), e.getCreatedAt());
  }

  public static LayerGroupMemberResponse toMemberResponse(
      LayerGroupMemberEntity e, InternalUserEntity user) {
    return new LayerGroupMemberResponse(
        e.getUserId(),
        user != null ? user.getUsername() : null,
        user != null ? user.getFirstName() : null,
        user != null ? user.getLastName() : null,
        user != null ? user.getEmail() : null,
        e.getRole(),
        e.getJoinedAt());
  }

  public static MyLayerGroupResponse toMyResponse(LayerGroupMemberEntity e) {
    LayerGroupEntity group = e.getGroup();
    return new MyLayerGroupResponse(
        group.getId(),
        group.getName(),
        group.getDescription(),
        e.getRole(),
        group.getAccessScope(),
        group.getCreatedAt(),
        List.of());
  }

  public static MyLayerGroupResponse toMyResponse(LayerGroupEntity group, LayerGroupRole role) {
    return new MyLayerGroupResponse(
        group.getId(),
        group.getName(),
        group.getDescription(),
        role,
        group.getAccessScope(),
        group.getCreatedAt(),
        List.of());
  }

  public static MyLayerGroupResponse toMyResponse(LayerGroupUserGroupEntity e) {
    return toMyResponse(e.getLayerGroup(), e.getRole());
  }

  public static MyLayerGroupResponse withLayers(MyLayerGroupResponse r, List<LayerSummary> layers) {
    return new MyLayerGroupResponse(
        r.id(),
        r.name(),
        r.description(),
        r.containerRole(),
        r.accessScope(),
        r.createdAt(),
        layers);
  }

  public static LayerSummary toLayerSummary(LayerGroupLayerEntity e) {
    var layer = e.getLayer();
    return new LayerSummary(layer.getId(), layer.getName(), layer.getAccessScope(), e.getRole());
  }
}
