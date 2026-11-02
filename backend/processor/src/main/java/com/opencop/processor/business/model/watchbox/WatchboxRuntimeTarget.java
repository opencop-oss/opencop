// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.watchbox;

import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusReason;
import java.util.Objects;
import java.util.UUID;
import org.locationtech.jts.geom.prep.PreparedGeometry;

public record WatchboxRuntimeTarget(
    UUID watchboxId,
    long configEpoch,
    WatchboxDirection direction,
    PreparedGeometry geometry,
    WatchboxFilter filter,
    WatchboxStatusReason frozenReason) {

  public WatchboxRuntimeTarget {
    Objects.requireNonNull(watchboxId, "watchboxId");
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(geometry, "geometry");
    Objects.requireNonNull(filter, "filter");
    if (configEpoch < 1) {
      throw new IllegalArgumentException("configEpoch must be positive");
    }
  }

  public boolean frozen() {
    return frozenReason != null;
  }
}
