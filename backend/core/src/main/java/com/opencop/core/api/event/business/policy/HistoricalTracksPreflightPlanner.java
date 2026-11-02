// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import com.opencop.core.api.event.business.model.HistoricalPreflightPlan;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class HistoricalTracksPreflightPlanner {

  private static final long SIX_HOURS_SECONDS = 6 * 60 * 60;
  private static final long SEVEN_DAYS_SECONDS = 7 * 24 * 60 * 60;

  public HistoricalPreflightPlan plan(Instant requestedFrom, Instant requestedTo) {
    var resolution = selectResolution(requestedFrom, requestedTo);
    var effectiveFrom = alignFloor(requestedFrom, resolution);
    var effectiveTo = alignCeiling(requestedTo, resolution);
    var chunkCount =
        ChronoUnit.MILLIS.between(effectiveFrom, effectiveTo) / resolution.chunkSize().toMillis();

    return new HistoricalPreflightPlan(resolution, effectiveFrom, effectiveTo, chunkCount);
  }

  private HistoricalResolution selectResolution(Instant requestedFrom, Instant requestedTo) {
    long rangeSeconds = ChronoUnit.SECONDS.between(requestedFrom, requestedTo);
    if (rangeSeconds <= SIX_HOURS_SECONDS) {
      return HistoricalResolution.MINUTE_1;
    }
    if (rangeSeconds <= SEVEN_DAYS_SECONDS) {
      return HistoricalResolution.HOUR_1;
    }
    return HistoricalResolution.DAY_1;
  }

  private Instant alignFloor(Instant instant, HistoricalResolution resolution) {
    return switch (resolution) {
      case MINUTE_1 -> instant.truncatedTo(ChronoUnit.MINUTES);
      case HOUR_1 -> instant.truncatedTo(ChronoUnit.HOURS);
      case DAY_1 -> instant.truncatedTo(ChronoUnit.DAYS);
    };
  }

  private Instant alignCeiling(Instant instant, HistoricalResolution resolution) {
    var floored = alignFloor(instant, resolution);
    return floored.equals(instant) ? instant : floored.plus(resolution.chunkSize());
  }
}
