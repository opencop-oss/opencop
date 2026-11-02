// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.media;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "layer_media")
public class LayerMediaEntity {

  @EmbeddedId private LayerMediaId id;

  public LayerMediaEntity() {}

  public LayerMediaEntity(LayerMediaId id) {
    this.id = id;
  }

  public LayerMediaId getId() {
    return id;
  }

  public void setId(LayerMediaId id) {
    this.id = id;
  }
}
