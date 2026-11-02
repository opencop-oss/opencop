// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.tile;

import com.opencop.core.sdk.business.model.map.tile.MapTileConfig;
import com.opencop.core.sdk.technical.gateway.db.iam.MapTileAccessScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "map_tile")
public class MapTileEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "is_primary", nullable = false)
  private boolean isPrimary = false;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  @Enumerated(EnumType.STRING)
  @Column(name = "access_scope", nullable = false, columnDefinition = "iam.map_tile_access_scope")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private MapTileAccessScope accessScope = MapTileAccessScope.PRIVATE;

  @Column(name = "name", nullable = false, unique = true)
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "url", nullable = false)
  private String url;

  @Column(name = "kind", nullable = false)
  private String kind;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "config", columnDefinition = "jsonb", nullable = false)
  private MapTileConfig config;

  @Column(name = "token_ref")
  private String tokenRef;

  @Column(name = "attribution")
  private String attribution;

  protected MapTileEntity() {}

  public MapTileEntity(
      UUID id,
      boolean isPrimary,
      boolean enabled,
      String name,
      String description,
      String url,
      String kind,
      MapTileConfig config,
      String tokenRef) {

    this.id = id;
    this.isPrimary = isPrimary;
    this.enabled = enabled;
    this.name = name;
    this.description = description;
    this.url = url;
    this.kind = kind;
    this.config = config;
    this.tokenRef = tokenRef;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public boolean isPrimary() {
    return isPrimary;
  }

  public void setPrimary(boolean primary) {
    isPrimary = primary;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public MapTileAccessScope getAccessScope() {
    return accessScope;
  }

  public void setAccessScope(MapTileAccessScope accessScope) {
    this.accessScope = accessScope;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getAttribution() {
    return attribution;
  }

  public void setAttribution(String attribution) {
    this.attribution = attribution;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public MapTileConfig getConfig() {
    return config;
  }

  public void setConfig(MapTileConfig config) {
    this.config = config;
  }

  public String getTokenRef() {
    return tokenRef;
  }

  public void setTokenRef(String tokenRef) {
    this.tokenRef = tokenRef;
  }
}
