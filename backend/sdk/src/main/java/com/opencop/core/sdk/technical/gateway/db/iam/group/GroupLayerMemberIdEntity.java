// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.group;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class GroupLayerMemberIdEntity implements Serializable {

  @Column(name = "group_id", columnDefinition = "uuid", nullable = false)
  private UUID groupId;

  @Column(name = "layer_id", columnDefinition = "uuid", nullable = false)
  private UUID layerId;

  public GroupLayerMemberIdEntity() {}

  public GroupLayerMemberIdEntity(UUID groupId, UUID layerId) {
    this.groupId = groupId;
    this.layerId = layerId;
  }

  public UUID getGroupId() {
    return groupId;
  }

  public UUID getLayerId() {
    return layerId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GroupLayerMemberIdEntity that)) {
      return false;
    }
    return Objects.equals(groupId, that.groupId) && Objects.equals(layerId, that.layerId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupId, layerId);
  }
}
