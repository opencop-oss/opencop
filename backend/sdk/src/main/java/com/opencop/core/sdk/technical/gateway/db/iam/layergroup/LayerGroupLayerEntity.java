// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

@Entity
@Table(
    name = "layer_group_layer",
    schema = "iam",
    indexes = {
      @Index(name = "idx_layer_group_layer_layer_id", columnList = "layer_id"),
      @Index(name = "idx_layer_group_layer_group_id", columnList = "layer_group_id")
    })
public class LayerGroupLayerEntity {

  @EmbeddedId private LayerGroupLayerIdEntity id;

  @MapsId("layerGroupId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "layer_group_id", nullable = false)
  private LayerGroupEntity group;

  @MapsId("layerId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "layer_id", nullable = false)
  private LayerEntity layer;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, columnDefinition = "iam.layer_role")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private LayerRole role = LayerRole.MEMBER;

  @CreationTimestamp
  @Column(name = "assigned_at", nullable = false, updatable = false)
  private Instant assignedAt;

  public LayerGroupLayerEntity() {}

  public LayerGroupLayerEntity(LayerGroupEntity group, LayerEntity layer, LayerRole role) {
    this.group = group;
    this.layer = layer;
    this.id = new LayerGroupLayerIdEntity(group.getId(), layer.getId());
    this.role = role == null ? LayerRole.MEMBER : role;
  }

  public LayerGroupLayerIdEntity getId() {
    return id;
  }

  public LayerGroupEntity getGroup() {
    return group;
  }

  public LayerEntity getLayer() {
    return layer;
  }

  public LayerRole getRole() {
    return role;
  }

  public void setRole(LayerRole role) {
    this.role = role;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }
}
