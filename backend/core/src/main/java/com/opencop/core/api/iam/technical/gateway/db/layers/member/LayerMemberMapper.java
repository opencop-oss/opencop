// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.member;

import com.opencop.core.api.iam.ports.gateway.mapper.RepositoryMapper;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.technical.gateway.db.iam.member.LayerMemberEntity;

public class LayerMemberMapper implements RepositoryMapper<LayerMemberEntity, LayerMember> {
  @Override
  public LayerMember toModel(LayerMemberEntity dbEntity) {
    return new LayerMember.Builder()
        .withLayerId(dbEntity.getId().getLayerId())
        .withUserId(dbEntity.getUserId())
        .withRole(dbEntity.getRole())
        .withJoinedAt(dbEntity.getJoinedAt())
        .build();
  }

  @Override
  public LayerMember toSanitizedModel(LayerMemberEntity dbEntity) {
    return new LayerMember.Builder()
        .withLayerId(dbEntity.getId().getLayerId())
        .withUserId(dbEntity.getUserId())
        .withRole(dbEntity.getRole())
        .withJoinedAt(dbEntity.getJoinedAt())
        .build();
  }
}
