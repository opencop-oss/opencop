// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.watchbox;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class WatchboxLayerIdEntity implements Serializable {

  @Column(name = "watchbox_id", nullable = false)
  private UUID watchboxId;

  @Column(name = "layer_id", nullable = false)
  private UUID layerId;

  public WatchboxLayerIdEntity() {}

  public WatchboxLayerIdEntity(UUID watchboxId, UUID layerId) {
    this.watchboxId = watchboxId;
    this.layerId = layerId;
  }

  public UUID getWatchboxId() {
    return watchboxId;
  }

  public UUID getLayerId() {
    return layerId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WatchboxLayerIdEntity that)) return false;
    return Objects.equals(watchboxId, that.watchboxId) && Objects.equals(layerId, that.layerId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(watchboxId, layerId);
  }
}
