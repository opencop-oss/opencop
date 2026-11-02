// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class LayerDataSourceGroupIdEntity implements Serializable {

  @Column(name = "layer_id", columnDefinition = "uuid", nullable = false)
  private UUID layerId;

  @Column(name = "data_source_group_id", columnDefinition = "uuid", nullable = false)
  private UUID dataSourceGroupId;

  public LayerDataSourceGroupIdEntity() {}

  public LayerDataSourceGroupIdEntity(UUID layerId, UUID dataSourceGroupId) {
    this.layerId = layerId;
    this.dataSourceGroupId = dataSourceGroupId;
  }

  public UUID getLayerId() {
    return layerId;
  }

  public UUID getDataSourceGroupId() {
    return dataSourceGroupId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LayerDataSourceGroupIdEntity that)) {
      return false;
    }
    return Objects.equals(layerId, that.layerId)
        && Objects.equals(dataSourceGroupId, that.dataSourceGroupId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(layerId, dataSourceGroupId);
  }
}
