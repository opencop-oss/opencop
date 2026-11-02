// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

@Entity
@Table(
    name = "data_source_group_user_group",
    schema = "iam",
    indexes = {
      @Index(name = "idx_data_source_group_user_group_user_group_id", columnList = "user_group_id")
    })
public class DataSourceGroupUserGroupEntity {

  @EmbeddedId private DataSourceGroupUserGroupIdEntity id;

  @MapsId("dataSourceGroupId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "data_source_group_id", nullable = false)
  private DataSourceGroupEntity dataSourceGroup;

  @MapsId("userGroupId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_group_id", nullable = false)
  private GroupEntity userGroup;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, columnDefinition = "iam.data_source_group_role")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private DataSourceGroupRole role;

  @CreationTimestamp
  @Column(name = "assigned_at", nullable = false, updatable = false)
  private Instant assignedAt;

  public DataSourceGroupUserGroupEntity() {}

  public DataSourceGroupUserGroupEntity(
      DataSourceGroupEntity dataSourceGroup, GroupEntity userGroup, DataSourceGroupRole role) {
    this.dataSourceGroup = dataSourceGroup;
    this.userGroup = userGroup;
    this.id = new DataSourceGroupUserGroupIdEntity(dataSourceGroup.getId(), userGroup.getId());
    this.role = role;
  }

  public DataSourceGroupUserGroupIdEntity getId() {
    return id;
  }

  public void setId(DataSourceGroupUserGroupIdEntity id) {
    this.id = id;
  }

  public DataSourceGroupEntity getDataSourceGroup() {
    return dataSourceGroup;
  }

  public void setDataSourceGroup(DataSourceGroupEntity dataSourceGroup) {
    this.dataSourceGroup = dataSourceGroup;
  }

  public GroupEntity getUserGroup() {
    return userGroup;
  }

  public void setUserGroup(GroupEntity userGroup) {
    this.userGroup = userGroup;
  }

  public DataSourceGroupRole getRole() {
    return role;
  }

  public void setRole(DataSourceGroupRole role) {
    this.role = role;
  }

  public UUID getUserGroupId() {
    return id != null ? id.getUserGroupId() : null;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }
}
