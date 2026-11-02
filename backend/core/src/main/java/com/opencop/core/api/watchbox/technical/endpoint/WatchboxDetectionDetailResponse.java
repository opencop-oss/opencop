// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencop.core.api.watchbox.business.model.WatchboxDetectionDetail;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record WatchboxDetectionDetailResponse(
    UUID id,
    UUID watchboxId,
    long configEpoch,
    UUID sourceId,
    String objectId,
    UUID eventId,
    Instant effectiveEventTime,
    WatchboxTransitionKind kind,
    String objectDisplayName,
    Instant detectedAt,
    EventDetailsResponse triggeringEvent,
    EventDetailsResponse latestEvent) {
  public static WatchboxDetectionDetailResponse from(WatchboxDetectionDetail details) {
    var d = details.detection();
    return new WatchboxDetectionDetailResponse(
        d.id(),
        d.watchboxId(),
        d.configEpoch(),
        d.sourceId(),
        d.objectId(),
        d.eventId(),
        d.effectiveEventTime(),
        d.kind(),
        d.objectDisplayName(),
        d.detectedAt(),
        details.triggeringEvent().map(EventDetailsResponse::from).orElse(null),
        details.latestEvent().map(EventDetailsResponse::from).orElse(null));
  }
}
