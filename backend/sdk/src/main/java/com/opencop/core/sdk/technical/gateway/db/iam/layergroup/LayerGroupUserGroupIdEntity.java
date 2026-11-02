// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class LayerGroupUserGroupIdEntity implements Serializable {

  @Column(name = "layer_group_id", columnDefinition = "uuid", nullable = false)
  private UUID layerGroupId;

  @Column(name = "user_group_id", columnDefinition = "uuid", nullable = false)
  private UUID userGroupId;

  public LayerGroupUserGroupIdEntity() {}

  public LayerGroupUserGroupIdEntity(UUID layerGroupId, UUID userGroupId) {
    this.layerGroupId = layerGroupId;
    this.userGroupId = userGroupId;
  }

  public UUID getLayerGroupId() {
    return layerGroupId;
  }

  public UUID getUserGroupId() {
    return userGroupId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LayerGroupUserGroupIdEntity that)) {
      return false;
    }
    return Objects.equals(layerGroupId, that.layerGroupId)
        && Objects.equals(userGroupId, that.userGroupId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(layerGroupId, userGroupId);
  }
}
