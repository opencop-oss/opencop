// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.server.user.entity;

import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

@Entity
@Table(
    name = "server_user_access",
    schema = "iam",
    indexes = {
      @Index(name = "idx_sua_by_server", columnList = "server_id"),
      @Index(name = "idx_sua_by_principal", columnList = "principal"),
      @Index(name = "idx_sua_server_role", columnList = "server_id,role")
    })
public class ServerUserAccessEntity {

  @EmbeddedId private ServerUserAccessIdEntity id;

  @CreationTimestamp
  @Column(name = "granted_at", nullable = false, updatable = false)
  private Instant grantedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, columnDefinition = "iam.server_role")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private ServerRole role;

  public ServerUserAccessEntity() {}

  public ServerUserAccessEntity(UUID serverId, UUID userId, ServerRole role) {
    this.id = new ServerUserAccessIdEntity(serverId, userId);
    this.grantedAt = Instant.now();
    this.role = role;
  }

  public ServerUserAccessIdEntity getId() {
    return id;
  }

  public UUID getServerId() {
    return id != null ? id.getServerId() : null;
  }

  public UUID getUserId() {
    return id != null ? id.getUserId() : null;
  }

  public void setRole(ServerRole role) {
    this.role = role;
  }

  public ServerRole getRole() {
    return role;
  }

  public Instant getGrantedAt() {
    return grantedAt;
  }
}
