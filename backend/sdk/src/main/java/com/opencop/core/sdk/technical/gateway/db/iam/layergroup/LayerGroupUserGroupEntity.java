// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

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
    name = "layer_group_user_group",
    schema = "iam",
    indexes = {
      @Index(name = "idx_layer_group_user_group_user_group_id", columnList = "user_group_id")
    })
public class LayerGroupUserGroupEntity {

  @EmbeddedId private LayerGroupUserGroupIdEntity id;

  @MapsId("layerGroupId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "layer_group_id", nullable = false)
  private LayerGroupEntity layerGroup;

  @MapsId("userGroupId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_group_id", nullable = false)
  private GroupEntity userGroup;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, columnDefinition = "iam.layer_group_role")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private LayerGroupRole role;

  @CreationTimestamp
  @Column(name = "assigned_at", nullable = false, updatable = false)
  private Instant assignedAt;

  public LayerGroupUserGroupEntity() {}

  public LayerGroupUserGroupEntity(
      LayerGroupEntity layerGroup, GroupEntity userGroup, LayerGroupRole role) {
    this.layerGroup = layerGroup;
    this.userGroup = userGroup;
    this.id = new LayerGroupUserGroupIdEntity(layerGroup.getId(), userGroup.getId());
    this.role = role;
  }

  public LayerGroupUserGroupIdEntity getId() {
    return id;
  }

  public void setId(LayerGroupUserGroupIdEntity id) {
    this.id = id;
  }

  public LayerGroupEntity getLayerGroup() {
    return layerGroup;
  }

  public void setLayerGroup(LayerGroupEntity layerGroup) {
    this.layerGroup = layerGroup;
  }

  public GroupEntity getUserGroup() {
    return userGroup;
  }

  public void setUserGroup(GroupEntity userGroup) {
    this.userGroup = userGroup;
  }

  public LayerGroupRole getRole() {
    return role;
  }

  public void setRole(LayerGroupRole role) {
    this.role = role;
  }

  public UUID getUserGroupId() {
    return id != null ? id.getUserGroupId() : null;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }
}
