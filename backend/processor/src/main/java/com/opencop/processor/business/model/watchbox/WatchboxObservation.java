// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.watchbox;

import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WatchboxObservation(
    WatchboxStateKey key,
    UUID eventId,
    Instant effectiveEventTime,
    boolean geometryInside,
    boolean inside,
    WatchboxDirection direction,
    String objectDisplayName) {

  public WatchboxObservation {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(effectiveEventTime, "effectiveEventTime");
    Objects.requireNonNull(direction, "direction");
    if (inside && !geometryInside) {
      throw new IllegalArgumentException("inside requires geometryInside");
    }
  }
}
