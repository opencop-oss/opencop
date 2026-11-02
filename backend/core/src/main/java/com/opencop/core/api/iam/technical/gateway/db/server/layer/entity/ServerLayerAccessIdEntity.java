// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.server.layer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ServerLayerAccessIdEntity implements Serializable {

  @Column(name = "server_id", nullable = false)
  private UUID serverId;

  @Column(name = "layer_id", columnDefinition = "uuid", nullable = false)
  private UUID layerId;

  public ServerLayerAccessIdEntity() {}

  public ServerLayerAccessIdEntity(UUID serverId, UUID layerId) {
    this.serverId = serverId;
    this.layerId = layerId;
  }

  public UUID getServerId() {
    return serverId;
  }

  public UUID getLayerId() {
    return layerId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ServerLayerAccessIdEntity that)) {
      return false;
    }
    return Objects.equals(serverId, that.serverId) && Objects.equals(layerId, that.layerId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(serverId, layerId);
  }
}
