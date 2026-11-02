// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.watchbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Last evaluated position of one (watchbox, epoch, source, object). Two booleans carry two
 * different facts: {@code lastGeometryInside} is the raw spatial sub-fact (the point was inside the
 * polygon), while {@code lastInside} is the effective state — geometry AND the watchbox filter
 * matched, i.e. "in the watchbox" as the product defines it. Filter rules are evaluated fresh on
 * every observation and are never stored here. The transition engine classifies by which fact
 * flipped: a geometry flip is motion (ENTER/LEAVE); an effective flip with geometry unchanged is
 * the filter re-classifying an object already in the polygon (FILTER_MATCH_GAINED/LOST).
 */
public record WatchboxStateRow(
    WatchboxStateKey key,
    boolean lastInside,
    boolean lastGeometryInside,
    UUID lastEventId,
    Instant lastEffectiveEventTime,
    Instant lastObservedAt) {

  public WatchboxStateRow {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(lastEventId, "lastEventId");
    Objects.requireNonNull(lastEffectiveEventTime, "lastEffectiveEventTime");
    Objects.requireNonNull(lastObservedAt, "lastObservedAt");
    if (lastInside && !lastGeometryInside) {
      throw new IllegalArgumentException("lastInside requires lastGeometryInside");
    }
  }
}
