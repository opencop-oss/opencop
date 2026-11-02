// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class BoundedMetricTagGuardTest {

  @Test
  void requireAllowed_whenValueIsAllowlisted_returnsValue() {
    assertEquals(
        "success",
        BoundedMetricTagGuard.requireAllowed("outcome", "success", Set.of("success", "error")));
  }

  @Test
  void requireAllowed_rejectsBlankTagKey() {
    var thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> BoundedMetricTagGuard.requireAllowed(" ", "success", Set.of("success")));

    assertEquals("tag key must be non-empty", thrown.getMessage());
  }

  @Test
  void requireAllowed_rejectsBlankValue() {
    var thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> BoundedMetricTagGuard.requireAllowed("outcome", " ", Set.of("success")));

    assertEquals("outcome must be non-empty", thrown.getMessage());
  }

  @Test
  void requireAllowed_rejectsUuidLikeValue() {
    var thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BoundedMetricTagGuard.requireAllowed(
                    "request", "550e8400-e29b-41d4-a716-446655440120", Set.of("success")));

    assertTrue(thrown.getMessage().contains("UUID-like"));
  }

  @Test
  void requireAllowed_rejectsCoordinateLikeValue() {
    var thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> BoundedMetricTagGuard.requireAllowed("location", "39.9526", Set.of("success")));

    assertTrue(thrown.getMessage().contains("coordinate-like"));
  }

  @Test
  void requireAllowed_rejectsUnknownValues() {
    var thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> BoundedMetricTagGuard.requireAllowed("outcome", "unknown", Set.of("success")));

    assertTrue(thrown.getMessage().contains("must be one of"));
  }

  @Test
  void allowedValues_collectsEnumValues() {
    assertEquals(
        Set.of("hit", "miss"), BoundedMetricTagGuard.allowedValues(TestTag.HIT, TestTag.MISS));
  }

  private enum TestTag implements BoundedMetricTagValue {
    HIT("hit"),
    MISS("miss");

    private final String value;

    TestTag(String value) {
      this.value = value;
    }

    @Override
    public String value() {
      return value;
    }
  }
}
