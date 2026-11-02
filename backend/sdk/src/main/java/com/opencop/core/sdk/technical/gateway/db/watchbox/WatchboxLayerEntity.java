// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.watchbox;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "watchbox_layer", schema = "watchbox")
public class WatchboxLayerEntity {

  @EmbeddedId private WatchboxLayerIdEntity id;

  public WatchboxLayerEntity() {}

  public WatchboxLayerEntity(WatchboxLayerIdEntity id) {
    this.id = id;
  }

  public WatchboxLayerIdEntity getId() {
    return id;
  }

  public void setId(WatchboxLayerIdEntity id) {
    this.id = id;
  }
}
