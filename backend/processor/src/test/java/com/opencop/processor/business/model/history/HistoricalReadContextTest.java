// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalReadContextTest {

  @Test
  void forSourceIds_whenSingleSource_enablesSingleSourceHelperContext() {
    var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

    var context = HistoricalReadContext.forSourceIds(List.of(sourceId));

    assertEquals(List.of(sourceId), context.sourceIds());
    assertEquals(sourceId, context.singleSourceId());
    assertTrue(context.isSingleSource());
    assertTrue(context.helperTableEnabled());
  }

  @Test
  void forSourceIds_whenMultipleSources_disablesSingleSourceHelperContext() {
    var sourceIds =
        List.of(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440010"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440011"));

    var context = HistoricalReadContext.forSourceIds(sourceIds);

    assertEquals(sourceIds, context.sourceIds());
    assertNull(context.singleSourceId());
    assertFalse(context.isSingleSource());
    assertFalse(context.helperTableEnabled());
  }

  @Test
  void forSourceIds_whenNoSources_returnsEmptyContext() {
    var context = HistoricalReadContext.forSourceIds(List.of());

    assertTrue(context.sourceIds().isEmpty());
    assertTrue(context.isEmpty());
    assertNull(context.singleSourceId());
    assertFalse(context.helperTableEnabled());
  }
}
