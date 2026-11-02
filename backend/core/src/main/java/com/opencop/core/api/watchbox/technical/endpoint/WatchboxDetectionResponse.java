// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import com.opencop.core.api.watchbox.business.model.WatchboxDetection;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import java.time.Instant;
import java.util.UUID;

public record WatchboxDetectionResponse(
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
  public static WatchboxDetectionResponse from(WatchboxDetection d) {
    return new WatchboxDetectionResponse(
        d.id(),
        d.watchboxId(),
        d.configEpoch(),
        d.sourceId(),
        d.objectId(),
        d.eventId(),
        d.effectiveEventTime(),
        d.kind(),
        d.objectDisplayName(),
        d.detectedAt());
  }
}
