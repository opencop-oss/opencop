// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.media;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class LayerMediaId implements Serializable {

  @Column(name = "layer_id", nullable = false)
  private UUID layerId;

  @Column(name = "media_id", nullable = false)
  private UUID mediaId;

  public LayerMediaId() {}

  public LayerMediaId(UUID layerId, UUID mediaId) {
    this.layerId = layerId;
    this.mediaId = mediaId;
  }

  public UUID getLayerId() {
    return layerId;
  }

  public UUID getMediaId() {
    return mediaId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LayerMediaId that)) {
      return false;
    }
    return Objects.equals(layerId, that.layerId) && Objects.equals(mediaId, that.mediaId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(layerId, mediaId);
  }
}
