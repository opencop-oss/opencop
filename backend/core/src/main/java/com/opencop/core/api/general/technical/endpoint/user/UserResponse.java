// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.user;

import com.opencop.core.api.general.business.model.user.InternalUser;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String idpUserId,
    String idpUsername,
    String issuer,
    String username,
    String email,
    String firstName,
    String lastName,
    String displayName,
    boolean active,
    boolean admin,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {

  static UserResponse from(InternalUser user) {
    return new UserResponse(
        user.id(),
        user.idpUserId(),
        user.idpUsername(),
        user.issuer(),
        user.username(),
        user.email(),
        user.firstName(),
        user.lastName(),
        user.displayName(),
        user.active(),
        user.admin(),
        user.createdAt(),
        user.updatedAt(),
        user.deletedAt());
  }
}
