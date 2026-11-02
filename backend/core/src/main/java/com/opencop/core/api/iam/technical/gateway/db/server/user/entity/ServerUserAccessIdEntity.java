// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.server.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ServerUserAccessIdEntity implements Serializable {
  @Column(name = "server_id", nullable = false)
  private UUID serverId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  public ServerUserAccessIdEntity() {}

  public ServerUserAccessIdEntity(UUID serverId, UUID userId) {
    this.serverId = serverId;
    this.userId = userId;
  }

  public UUID getServerId() {
    return serverId;
  }

  public UUID getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ServerUserAccessIdEntity that)) {
      return false;
    }
    return Objects.equals(serverId, that.serverId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(serverId, userId);
  }
}
