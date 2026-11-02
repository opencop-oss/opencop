// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.member;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class LayerMemberIdEntity implements Serializable {

  @Column(name = "layer_id", columnDefinition = "uuid", nullable = false)
  private UUID layerId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  public LayerMemberIdEntity() {}

  public LayerMemberIdEntity(UUID layerId, UUID userId) {
    this.layerId = layerId;
    this.userId = userId;
  }

  public UUID getLayerId() {
    return layerId;
  }

  public UUID getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LayerMemberIdEntity that)) {
      return false;
    }
    return Objects.equals(layerId, that.layerId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(layerId, userId);
  }
}
