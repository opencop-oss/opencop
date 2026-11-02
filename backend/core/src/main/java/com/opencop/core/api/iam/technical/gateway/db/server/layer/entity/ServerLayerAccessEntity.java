// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.server.layer.entity;

import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
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
    name = "server_layer_access",
    schema = "iam",
    indexes = {
      @Index(name = "idx_sta_by_server", columnList = "server_id"),
      @Index(name = "idx_sta_by_layer", columnList = "layer_id"),
      @Index(name = "idx_sta_server_role", columnList = "server_id,role")
    })
public class ServerLayerAccessEntity {

  @EmbeddedId private ServerLayerAccessIdEntity id;

  @MapsId("layerId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "layer_id", nullable = false)
  private LayerEntity layer;

  @CreationTimestamp
  @Column(name = "granted_at", nullable = false, updatable = false)
  private Instant grantedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, columnDefinition = "iam.server_role")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private ServerRole role;

  public ServerLayerAccessEntity() {}

  public ServerLayerAccessEntity(UUID serverId, LayerEntity layer, ServerRole role) {
    this.layer = layer;
    this.id = new ServerLayerAccessIdEntity(serverId, layer.getId());
    this.role = role;
  }

  public ServerLayerAccessIdEntity getId() {
    return id;
  }

  public UUID getServerId() {
    return id != null ? id.getServerId() : null;
  }

  public LayerEntity getLayer() {
    return layer;
  }

  public ServerRole getRole() {
    return role;
  }

  public void setRole(ServerRole role) {
    this.role = role;
  }

  public Instant getGrantedAt() {
    return grantedAt;
  }
}
