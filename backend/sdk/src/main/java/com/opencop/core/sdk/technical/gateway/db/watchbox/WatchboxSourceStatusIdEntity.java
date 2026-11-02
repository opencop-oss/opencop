// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.watchbox;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class WatchboxSourceStatusIdEntity implements Serializable {

  @Column(name = "watchbox_id", nullable = false)
  private UUID watchboxId;

  @Column(name = "config_epoch", nullable = false)
  private long configEpoch;

  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  public WatchboxSourceStatusIdEntity() {}

  public WatchboxSourceStatusIdEntity(UUID watchboxId, long configEpoch, UUID sourceId) {
    this.watchboxId = watchboxId;
    this.configEpoch = configEpoch;
    this.sourceId = sourceId;
  }

  public UUID getWatchboxId() {
    return watchboxId;
  }

  public long getConfigEpoch() {
    return configEpoch;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WatchboxSourceStatusIdEntity that)) return false;
    return configEpoch == that.configEpoch
        && Objects.equals(watchboxId, that.watchboxId)
        && Objects.equals(sourceId, that.sourceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(watchboxId, configEpoch, sourceId);
  }
}
