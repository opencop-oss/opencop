// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.simulation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Structured response from {@link HistoricalSimulationService#generate}. Designed so a verifier
 * (Playwright spec, manual smoke, etc.) has ground truth to compare against the rendered histogram
 * + chunk overlay + map elements without inspecting the actual data feed.
 */
public record HistoricalSimulationResponse(
    UUID sourceId,
    Instant windowStart,
    Instant windowEnd,
    long totalMessagesPublished,
    int chunkCount,
    int tickCount,
    long bucketWidthMs,
    int entityCount,
    List<EntitySummary> entities) {

  /** One row per generated entity. Lets the caller assert per-bucket / per-chunk presence. */
  public record EntitySummary(
      String objectId,
      String displayName,
      String sidc,
      Instant firstEventAt,
      Instant lastEventAt,
      Instant staleAt,
      long tickCount) {}
}
