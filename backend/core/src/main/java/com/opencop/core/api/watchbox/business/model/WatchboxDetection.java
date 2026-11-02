// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.model;

import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One retained detection; detectedAt is created_at. Identity names match the live alert. */
public record WatchboxDetection(
    UUID id,
    UUID watchboxId,
    long configEpoch,
    UUID sourceId,
    String objectId,
    UUID eventId,
    Instant effectiveEventTime,
    WatchboxTransitionKind kind,
    String objectDisplayName,
    Instant detectedAt) {
  public WatchboxDetection {
    Objects.requireNonNull(id);
    Objects.requireNonNull(watchboxId);
    Objects.requireNonNull(sourceId);
    Objects.requireNonNull(objectId);
    Objects.requireNonNull(eventId);
    Objects.requireNonNull(effectiveEventTime);
    Objects.requireNonNull(kind);
    Objects.requireNonNull(detectedAt);
  }
}
