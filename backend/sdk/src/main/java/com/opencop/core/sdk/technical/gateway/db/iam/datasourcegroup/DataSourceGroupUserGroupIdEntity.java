// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DataSourceGroupUserGroupIdEntity implements Serializable {

  @Column(name = "data_source_group_id", columnDefinition = "uuid", nullable = false)
  private UUID dataSourceGroupId;

  @Column(name = "user_group_id", columnDefinition = "uuid", nullable = false)
  private UUID userGroupId;

  public DataSourceGroupUserGroupIdEntity() {}

  public DataSourceGroupUserGroupIdEntity(UUID dataSourceGroupId, UUID userGroupId) {
    this.dataSourceGroupId = dataSourceGroupId;
    this.userGroupId = userGroupId;
  }

  public UUID getDataSourceGroupId() {
    return dataSourceGroupId;
  }

  public UUID getUserGroupId() {
    return userGroupId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DataSourceGroupUserGroupIdEntity that)) {
      return false;
    }
    return Objects.equals(dataSourceGroupId, that.dataSourceGroupId)
        && Objects.equals(userGroupId, that.userGroupId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dataSourceGroupId, userGroupId);
  }
}
