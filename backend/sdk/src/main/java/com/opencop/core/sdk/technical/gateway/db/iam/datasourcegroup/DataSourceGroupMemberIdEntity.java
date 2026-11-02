// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DataSourceGroupMemberIdEntity implements Serializable {

  @Column(name = "data_source_group_id", columnDefinition = "uuid", nullable = false)
  private UUID dataSourceGroupId;

  @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
  private UUID userId;

  public DataSourceGroupMemberIdEntity() {}

  public DataSourceGroupMemberIdEntity(UUID dataSourceGroupId, UUID userId) {
    this.dataSourceGroupId = dataSourceGroupId;
    this.userId = userId;
  }

  public UUID getDataSourceGroupId() {
    return dataSourceGroupId;
  }

  public UUID getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DataSourceGroupMemberIdEntity that)) {
      return false;
    }
    return Objects.equals(dataSourceGroupId, that.dataSourceGroupId)
        && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dataSourceGroupId, userId);
  }
}
