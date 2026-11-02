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

/** A direct grant of tile visibility to one user. */
@Entity
@Table(
    name = "map_tile_member",
    schema = "iam",
    indexes = {@Index(name = "idx_map_tile_member_user_id", columnList = "user_id")})
public class MapTileMemberEntity {

  @EmbeddedId private MapTileMemberIdEntity id;

  @CreationTimestamp
  @Column(name = "granted_at", nullable = false, updatable = false)
  private Instant grantedAt;

  public MapTileMemberEntity() {}

  public MapTileMemberEntity(UUID mapTileId, UUID userId) {
    this.id = new MapTileMemberIdEntity(mapTileId, userId);
  }

  public MapTileMemberIdEntity getId() {
    return id;
  }

  public UUID getMapTileId() {
    return id != null ? id.getMapTileId() : null;
  }

  public UUID getUserId() {
    return id != null ? id.getUserId() : null;
  }

  public Instant getGrantedAt() {
    return grantedAt;
  }

  public void setId(MapTileMemberIdEntity id) {
    this.id = id;
  }
}
