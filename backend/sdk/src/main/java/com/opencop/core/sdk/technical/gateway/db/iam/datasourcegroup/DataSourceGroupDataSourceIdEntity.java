// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DataSourceGroupDataSourceIdEntity implements Serializable {

  @Column(name = "data_source_group_id", columnDefinition = "uuid", nullable = false)
  private UUID dataSourceGroupId;

  @Column(name = "data_source_id", columnDefinition = "uuid", nullable = false)
  private UUID dataSourceId;

  public DataSourceGroupDataSourceIdEntity() {}

  public DataSourceGroupDataSourceIdEntity(UUID dataSourceGroupId, UUID dataSourceId) {
    this.dataSourceGroupId = dataSourceGroupId;
    this.dataSourceId = dataSourceId;
  }

  public UUID getDataSourceGroupId() {
    return dataSourceGroupId;
  }

  public UUID getDataSourceId() {
    return dataSourceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DataSourceGroupDataSourceIdEntity that)) {
      return false;
    }
    return Objects.equals(dataSourceGroupId, that.dataSourceGroupId)
        && Objects.equals(dataSourceId, that.dataSourceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dataSourceGroupId, dataSourceId);
  }
}
