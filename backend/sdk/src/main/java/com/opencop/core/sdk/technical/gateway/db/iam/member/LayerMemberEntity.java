// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.member;

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
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

@Entity
@Table(
    name = "layer_member",
    schema = "iam",
    indexes = {
      @Index(name = "idx_layer_member_principal", columnList = "principal"),
      @Index(name = "idx_layer_member_layer_role", columnList = "layer_id,role")
    })
public class LayerMemberEntity {

  @EmbeddedId private LayerMemberIdEntity id;

  @MapsId("layerId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "layer_id", nullable = false)
  private LayerEntity layer;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, columnDefinition = "iam.layer_role")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private LayerRole role = LayerRole.MEMBER;

  @CreationTimestamp
  @Column(name = "joined_at", nullable = false, updatable = false)
  private Instant joinedAt;

  public LayerMemberEntity() {}

  public LayerMemberEntity(LayerEntity layer, UUID userId, LayerRole role) {
    this.layer = layer;
    this.id = new LayerMemberIdEntity(layer.getId(), userId);
    this.role = role == null ? LayerRole.MEMBER : role;
  }

  public LayerMemberIdEntity getId() {
    return id;
  }

  public UUID getUserId() {
    return id != null ? id.getUserId() : null;
  }

  public LayerEntity getLayer() {
    return layer;
  }

  public LayerRole getRole() {
    return role;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public void setId(LayerMemberIdEntity id) {
    this.id = id;
  }

  public void setLayer(LayerEntity layer) {
    this.layer = layer;
  }

  public void setRole(LayerRole role) {
    this.role = role;
  }

  public void setJoinedAt(Instant joinedAt) {
    this.joinedAt = joinedAt;
  }
}
