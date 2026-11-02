// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.model;

import com.opencop.core.api.event.business.model.EventDetails;
import java.util.Objects;
import java.util.Optional;

/** Empty embeds are normal when a feature is absent or its source is not visible. */
public record WatchboxDetectionDetail(
    WatchboxDetection detection,
    Optional<EventDetails> triggeringEvent,
    Optional<EventDetails> latestEvent) {
  public WatchboxDetectionDetail {
    Objects.requireNonNull(detection);
    Objects.requireNonNull(triggeringEvent);
    Objects.requireNonNull(latestEvent);
  }
}
