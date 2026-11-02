// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.tile.access;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MapTileMemberIdEntity implements Serializable {

  @Column(name = "map_tile_id", nullable = false)
  private UUID mapTileId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  public MapTileMemberIdEntity() {}

  public MapTileMemberIdEntity(UUID mapTileId, UUID userId) {
    this.mapTileId = mapTileId;
    this.userId = userId;
  }

  public UUID getMapTileId() {
    return mapTileId;
  }

  public UUID getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MapTileMemberIdEntity that)) {
      return false;
    }
    return Objects.equals(mapTileId, that.mapTileId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mapTileId, userId);
  }
}
