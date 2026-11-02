// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class LayerGroupLayerIdEntity implements Serializable {

  @Column(name = "layer_group_id", columnDefinition = "uuid", nullable = false)
  private UUID layerGroupId;

  @Column(name = "layer_id", columnDefinition = "uuid", nullable = false)
  private UUID layerId;

  public LayerGroupLayerIdEntity() {}

  public LayerGroupLayerIdEntity(UUID layerGroupId, UUID layerId) {
    this.layerGroupId = layerGroupId;
    this.layerId = layerId;
  }

  public UUID getLayerGroupId() {
    return layerGroupId;
  }

  public UUID getLayerId() {
    return layerId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LayerGroupLayerIdEntity that)) {
      return false;
    }
    return Objects.equals(layerGroupId, that.layerGroupId) && Objects.equals(layerId, that.layerId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(layerGroupId, layerId);
  }
}
