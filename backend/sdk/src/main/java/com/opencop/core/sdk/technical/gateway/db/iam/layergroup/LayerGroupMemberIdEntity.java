// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class LayerGroupMemberIdEntity implements Serializable {

  @Column(name = "layer_group_id", columnDefinition = "uuid", nullable = false)
  private UUID layerGroupId;

  @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
  private UUID userId;

  public LayerGroupMemberIdEntity() {}

  public LayerGroupMemberIdEntity(UUID layerGroupId, UUID userId) {
    this.layerGroupId = layerGroupId;
    this.userId = userId;
  }

  public UUID getLayerGroupId() {
    return layerGroupId;
  }

  public UUID getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LayerGroupMemberIdEntity that)) {
      return false;
    }
    return Objects.equals(layerGroupId, that.layerGroupId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(layerGroupId, userId);
  }
}
