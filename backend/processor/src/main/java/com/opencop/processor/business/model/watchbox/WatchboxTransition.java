// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.watchbox;

import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WatchboxTransition(
    WatchboxStateKey key,
    UUID eventId,
    Instant effectiveEventTime,
    WatchboxTransitionKind kind,
    String objectDisplayName) {

  public WatchboxTransition {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(effectiveEventTime, "effectiveEventTime");
    Objects.requireNonNull(kind, "kind");
  }
}
