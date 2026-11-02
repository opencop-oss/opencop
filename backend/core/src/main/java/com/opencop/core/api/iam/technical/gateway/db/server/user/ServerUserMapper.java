// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.server.user;

import com.opencop.core.api.iam.ports.gateway.mapper.RepositoryMapper;
import com.opencop.core.api.iam.technical.gateway.db.server.user.entity.ServerUserAccessEntity;
import com.opencop.core.sdk.business.model.iam.access.SourceUser;

public class ServerUserMapper implements RepositoryMapper<ServerUserAccessEntity, SourceUser> {

  @Override
  public SourceUser toModel(ServerUserAccessEntity dbEntity) {
    return new SourceUser.Builder()
        .withServerId(dbEntity.getServerId())
        .withUserId(dbEntity.getUserId())
        .withRole(dbEntity.getRole())
        .withGrantedAt(dbEntity.getGrantedAt())
        .build();
  }

  @Override
  public SourceUser toSanitizedModel(ServerUserAccessEntity dbEntity) {
    return toModel(dbEntity);
  }
}
