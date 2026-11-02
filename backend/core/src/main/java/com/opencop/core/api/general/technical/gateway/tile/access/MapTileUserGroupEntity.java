// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.tile.access;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** A grant of tile visibility to every member of a user group. */
@Entity
@Table(
    name = "map_tile_user_group",
    schema = "iam",
    indexes = {@Index(name = "idx_map_tile_user_group_group_id", columnList = "user_group_id")})
public class MapTileUserGroupEntity {

  @EmbeddedId private MapTileUserGroupIdEntity id;

  @CreationTimestamp
  @Column(name = "granted_at", nullable = false, updatable = false)
  private Instant grantedAt;

  public MapTileUserGroupEntity() {}

  public MapTileUserGroupEntity(UUID mapTileId, UUID userGroupId) {
    this.id = new MapTileUserGroupIdEntity(mapTileId, userGroupId);
  }

  public MapTileUserGroupIdEntity getId() {
    return id;
  }

  public UUID getMapTileId() {
    return id != null ? id.getMapTileId() : null;
  }

  public UUID getUserGroupId() {
    return id != null ? id.getUserGroupId() : null;
  }

  public Instant getGrantedAt() {
    return grantedAt;
  }

  public void setId(MapTileUserGroupIdEntity id) {
    this.id = id;
  }
}
