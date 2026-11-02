// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HistoricalTracksPreflightPlannerTest {

  private final HistoricalTracksPreflightPlanner planner = new HistoricalTracksPreflightPlanner();

  @Test
  void plan_usesMinuteResolutionAndAlignsRangeForShortRequests() {
    var from = Instant.parse("2026-04-06T10:02:17Z");
    var to = Instant.parse("2026-04-06T12:13:02Z");

    var plan = planner.plan(from, to);

    assertEquals(HistoricalResolution.MINUTE_1, plan.resolution());
    assertEquals(Instant.parse("2026-04-06T10:02:00Z"), plan.effectiveFrom());
    assertEquals(Instant.parse("2026-04-06T12:14:00Z"), plan.effectiveTo());
    assertEquals(132, plan.chunkCount());
  }

  @Test
  void plan_usesHourResolutionForMidSizedRequests() {
    var from = Instant.parse("2026-04-01T10:02:17Z");
    var to = Instant.parse("2026-04-03T12:13:02Z");

    var plan = planner.plan(from, to);

    assertEquals(HistoricalResolution.HOUR_1, plan.resolution());
    assertEquals(Instant.parse("2026-04-01T10:00:00Z"), plan.effectiveFrom());
    assertEquals(Instant.parse("2026-04-03T13:00:00Z"), plan.effectiveTo());
    assertEquals(51, plan.chunkCount());
  }

  @Test
  void plan_usesDayResolutionForLongRequests() {
    var from = Instant.parse("2026-04-01T10:02:17Z");
    var to = Instant.parse("2026-04-12T12:13:02Z");

    var plan = planner.plan(from, to);

    assertEquals(HistoricalResolution.DAY_1, plan.resolution());
    assertEquals(Instant.parse("2026-04-01T00:00:00Z"), plan.effectiveFrom());
    assertEquals(Instant.parse("2026-04-13T00:00:00Z"), plan.effectiveTo());
    assertEquals(12, plan.chunkCount());
  }
}
