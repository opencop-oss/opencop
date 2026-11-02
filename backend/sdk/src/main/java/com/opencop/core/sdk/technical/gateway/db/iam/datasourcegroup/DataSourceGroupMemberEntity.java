// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

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
    name = "data_source_group_member",
    schema = "iam",
    indexes = {@Index(name = "idx_data_source_group_member_user_id", columnList = "user_id")})
public class DataSourceGroupMemberEntity {

  @EmbeddedId private DataSourceGroupMemberIdEntity id;

  @MapsId("dataSourceGroupId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "data_source_group_id", nullable = false)
  private DataSourceGroupEntity group;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, columnDefinition = "iam.data_source_group_role")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private DataSourceGroupRole role;

  @CreationTimestamp
  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  public DataSourceGroupMemberEntity() {}

  public DataSourceGroupMemberEntity(
      DataSourceGroupEntity group, UUID userId, DataSourceGroupRole role) {
    this.group = group;
    this.id = new DataSourceGroupMemberIdEntity(group.getId(), userId);
    this.role = role;
  }

  public DataSourceGroupMemberIdEntity getId() {
    return id;
  }

  public void setId(DataSourceGroupMemberIdEntity id) {
    this.id = id;
  }

  public DataSourceGroupEntity getGroup() {
    return group;
  }

  public void setGroup(DataSourceGroupEntity group) {
    this.group = group;
  }

  public DataSourceGroupRole getRole() {
    return role;
  }

  public void setRole(DataSourceGroupRole role) {
    this.role = role;
  }

  public UUID getUserId() {
    return id != null ? id.getUserId() : null;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }
}
