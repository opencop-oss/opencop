// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Watchbox-scoped live alert: one detected transition, published as JSON on {@code
 * CacheChannel.WATCHBOX_ALERTS} and forwarded verbatim to the {@code /topic/watchbox/{watchboxId}}
 * WebSocket destination.
 *
 * <p>{@code detectionId} and {@code objectDisplayName} are optional handles, absent ({@code null})
 * for rows recorded before they were stored. The six identity fields remain the deduplication
 * contract. {@code detectionId} is the id served by {@code GET
 * /v1/watchbox-detections/{detectionId}}; {@code objectDisplayName} is at most 255 characters,
 * capped at capture.
 *
 * <p>{@code objectId} is the producer-supplied object identity (bounded to 255 chars upstream); it
 * is a correlation key, not trusted attribution. {@code sourceId} is validated against {@code
 * iam.server_layer_access} before an emission exists, so only watched sources appear here.
 */
public record WatchboxAlertEvent(
    UUID watchboxId,
    long configEpoch,
    UUID sourceId,
    String objectId,
    UUID eventId,
    Instant effectiveEventTime,
    WatchboxTransitionKind kind,
    UUID detectionId,
    String objectDisplayName) {

  public WatchboxAlertEvent {
    Objects.requireNonNull(watchboxId, "watchboxId");
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(objectId, "objectId");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(effectiveEventTime, "effectiveEventTime");
    Objects.requireNonNull(kind, "kind");
  }
}
