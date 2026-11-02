// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.layer;

import com.opencop.core.api.iam.ports.gateway.mapper.RepositoryMapper;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.member.LayerMemberEntity;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class LayerMapper implements RepositoryMapper<LayerEntity, Layer> {

  private final RepositoryMapper<LayerMemberEntity, LayerMember> layerMemberMapper;

  public LayerMapper(RepositoryMapper<LayerMemberEntity, LayerMember> layerMemberMapper) {
    this.layerMemberMapper = layerMemberMapper;
  }

  @Override
  public Layer toModel(LayerEntity dbEntity) {
    return new Layer.Builder()
        .withId(dbEntity.getId())
        .withName(dbEntity.getName())
        .withAccessScope(dbEntity.getAccessScope())
        //                .withMembers(mapLayerMembers(dbEntity.getMembers()))
        //                .withServers(mapLayerMembers(dbEntity.getServers()))
        .withCreatedAt(dbEntity.getCreatedAt())
        .withCreatedBy(dbEntity.getCreatedBy())
        .withUpdatedAt(dbEntity.getUpdatedAt())
        .withUpdatedBy(dbEntity.getUpdatedBy())
        .withArchivedAt(dbEntity.getArchivedAt())
        .withArchivedBy(dbEntity.getArchivedBy())
        .withLifecycleState(dbEntity.getLifecycleState())
        .withDefaultVisible(dbEntity.isDefaultVisible())
        .build();
  }

  private Set<LayerMember> mapLayerMembers(Set<LayerMemberEntity> layerMemberEntitySet) {
    Set<LayerMember> set = new HashSet<>();
    if (layerMemberEntitySet == null) {
      return set;
    }
    return layerMemberEntitySet.stream()
        .map(layerMemberMapper::toSanitizedModel)
        .collect(Collectors.toSet());
  }

  private Set<LayerMember> mapLayerServers(Set<LayerMemberEntity> layerMemberEntitySet) {
    Set<LayerMember> set = new HashSet<>();
    if (layerMemberEntitySet == null) {
      return set;
    }
    return layerMemberEntitySet.stream()
        .map(layerMemberMapper::toSanitizedModel)
        .collect(Collectors.toSet());
  }

  @Override
  public Layer toSanitizedModel(LayerEntity dbEntity) {
    return new Layer.Builder()
        .withId(dbEntity.getId())
        .withName(dbEntity.getName())
        .withAccessScope(dbEntity.getAccessScope())
        .withCreatedAt(dbEntity.getCreatedAt())
        .withCreatedBy(dbEntity.getCreatedBy())
        .withUpdatedAt(dbEntity.getUpdatedAt())
        .withUpdatedBy(dbEntity.getUpdatedBy())
        .withArchivedAt(dbEntity.getArchivedAt())
        .withArchivedBy(dbEntity.getArchivedBy())
        .withLifecycleState(dbEntity.getLifecycleState())
        .withDefaultVisible(dbEntity.isDefaultVisible())
        .build();
  }
}
