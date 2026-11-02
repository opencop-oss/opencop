// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
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
    name = "data_source_group_data_source",
    schema = "iam",
    indexes = {
      @Index(
          name = "idx_data_source_group_data_source_data_source_id",
          columnList = "data_source_id"),
      @Index(
          name = "idx_data_source_group_data_source_group_id",
          columnList = "data_source_group_id")
    })
public class DataSourceGroupDataSourceEntity {

  @EmbeddedId private DataSourceGroupDataSourceIdEntity id;

  @MapsId("dataSourceGroupId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "data_source_group_id", nullable = false)
  private DataSourceGroupEntity group;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, columnDefinition = "iam.server_role")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private ServerRole role = ServerRole.VIEWER;

  @CreationTimestamp
  @Column(name = "assigned_at", nullable = false, updatable = false)
  private Instant assignedAt;

  public DataSourceGroupDataSourceEntity() {}

  public DataSourceGroupDataSourceEntity(
      DataSourceGroupEntity group, UUID dataSourceId, ServerRole role) {
    this.group = group;
    this.id = new DataSourceGroupDataSourceIdEntity(group.getId(), dataSourceId);
    this.role = role == null ? ServerRole.VIEWER : role;
  }

  public DataSourceGroupDataSourceIdEntity getId() {
    return id;
  }

  public void setId(DataSourceGroupDataSourceIdEntity id) {
    this.id = id;
  }

  public DataSourceGroupEntity getGroup() {
    return group;
  }

  public void setGroup(DataSourceGroupEntity group) {
    this.group = group;
  }

  public UUID getDataSourceId() {
    return id != null ? id.getDataSourceId() : null;
  }

  public ServerRole getRole() {
    return role;
  }

  public void setRole(ServerRole role) {
    this.role = role;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }
}
