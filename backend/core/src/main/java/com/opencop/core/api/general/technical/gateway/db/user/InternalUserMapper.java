// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.user;

import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;

public class InternalUserMapper {

  private InternalUserMapper() {}

  public static InternalUser fromEntity(InternalUserEntity entity) {
    return new InternalUser(
        entity.getId(),
        entity.getIdpUserId(),
        entity.getIdpUsername(),
        entity.getIdpIssuer(),
        entity.getUsername(),
        entity.getEmail(),
        entity.getFirstName(),
        entity.getLastName(),
        entity.getDisplayName(),
        entity.isActive(),
        entity.isAdministrator(),
        entity.getSessionGeneration(),
        entity.getAccountAccessRevision(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getDeletedAt());
  }

  public static InternalUserEntity fromDto(InternalUser user) {
    var entity = new InternalUserEntity();

    entity.setId(user.id());
    entity.setIdpUserId(user.idpUserId());
    entity.setIdpUsername(user.idpUsername());
    entity.setIdpIssuer(user.issuer());
    entity.setUsername(user.username());
    entity.setEmail(user.email());
    entity.setFirstName(user.firstName());
    entity.setLastName(user.lastName());
    entity.setDisplayName(user.displayName());
    entity.setActive(user.active());
    entity.setAdministrator(user.admin());
    entity.setSessionGeneration(user.sessionGeneration());
    entity.setAccountAccessRevision(user.accountAccessRevision());
    entity.setCreatedAt(user.createdAt());
    entity.setUpdatedAt(user.updatedAt());
    entity.setDeletedAt(user.deletedAt());

    return entity;
  }
}
