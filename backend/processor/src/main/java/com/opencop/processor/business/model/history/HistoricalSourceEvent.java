// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HistoricalSourceEvent(
    UUID eventId,
    UUID sourceId,
    String objectId,
    Instant playbackAt,
    Instant createdAt,
    Instant startAt,
    Instant staleAt,
    String payloadJson) {

  public HistoricalSourceEvent {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(objectId, "objectId");
    Objects.requireNonNull(playbackAt, "playbackAt");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(payloadJson, "payloadJson");
  }

  public HistoricalReadCursor cursor() {
    return new HistoricalReadCursor(playbackAt, objectId, eventId);
  }
}
