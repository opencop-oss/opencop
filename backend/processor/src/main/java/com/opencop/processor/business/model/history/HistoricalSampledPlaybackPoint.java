// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HistoricalSampledPlaybackPoint(
    UUID eventId,
    UUID sourceId,
    String objectId,
    Instant bucketStart,
    Instant playbackAt,
    String payloadJson) {

  public HistoricalSampledPlaybackPoint {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(objectId, "objectId");
    Objects.requireNonNull(bucketStart, "bucketStart");
    Objects.requireNonNull(playbackAt, "playbackAt");
    Objects.requireNonNull(payloadJson, "payloadJson");
  }

  public static HistoricalSampledPlaybackPoint fromEvent(
      HistoricalSourceEvent event, Instant bucketStart) {
    return new HistoricalSampledPlaybackPoint(
        event.eventId(),
        event.sourceId(),
        event.objectId(),
        bucketStart,
        event.playbackAt(),
        event.payloadJson());
  }
}
