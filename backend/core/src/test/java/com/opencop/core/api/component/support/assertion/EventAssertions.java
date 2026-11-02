// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.assertion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.api.event.business.model.EventHistoryResponse;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import java.util.UUID;

public final class EventAssertions {

  private EventAssertions() {}

  public static void assertEventDetails(
      OpenCopProperties response, String expectedobjectId, UUID expectedsource) {
    assertEquals(expectedobjectId, response.getObjectId());
    assertEquals(expectedsource, response.getSource());
  }

  public static void assertHistorySummary(
      EventHistoryResponse response,
      String expectedobjectId,
      String expectedStrategy,
      int maxPointsRequested,
      int totalPointsFound,
      int usedPointsReturned,
      int featureCount) {
    assertEquals(expectedobjectId, response.objectId());
    assertEquals(expectedStrategy, response.sampling().strategy());
    assertEquals(maxPointsRequested, response.sampling().maxPointsRequested());
    assertEquals(totalPointsFound, response.sampling().totalPointsFound());
    assertEquals(usedPointsReturned, response.sampling().usedPointsReturned());
    assertEquals(featureCount, response.data().getFeatures().size());
  }

  public static void assertHistorySummary(
      EventHistoryResponse response,
      String expectedobjectId,
      int maxPointsRequested,
      int totalPointsFound,
      int usedPointsReturned,
      int featureCount) {
    assertEquals(expectedobjectId, response.objectId());
    assertEquals(maxPointsRequested, response.sampling().maxPointsRequested());
    assertEquals(totalPointsFound, response.sampling().totalPointsFound());
    assertEquals(usedPointsReturned, response.sampling().usedPointsReturned());
    assertEquals(featureCount, response.data().getFeatures().size());
  }
}
