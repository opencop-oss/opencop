// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

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
    name = "layer_group_member",
    schema = "iam",
    indexes = {@Index(name = "idx_layer_group_member_user_id", columnList = "user_id")})
public class LayerGroupMemberEntity {

  @EmbeddedId private LayerGroupMemberIdEntity id;

  @MapsId("layerGroupId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "layer_group_id", nullable = false)
  private LayerGroupEntity group;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, columnDefinition = "iam.layer_group_role")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private LayerGroupRole role;

  @CreationTimestamp
  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  public LayerGroupMemberEntity() {}

  public LayerGroupMemberEntity(LayerGroupEntity group, UUID userId, LayerGroupRole role) {
    this.group = group;
    this.id = new LayerGroupMemberIdEntity(group.getId(), userId);
    this.role = role;
  }

  public LayerGroupMemberIdEntity getId() {
    return id;
  }

  public LayerGroupEntity getGroup() {
    return group;
  }

  public LayerGroupRole getRole() {
    return role;
  }

  public void setRole(LayerGroupRole role) {
    this.role = role;
  }

  public UUID getUserId() {
    return id != null ? id.getUserId() : null;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public void setId(LayerGroupMemberIdEntity id) {
    this.id = id;
  }

  public void setGroup(LayerGroupEntity group) {
    this.group = group;
  }
}
