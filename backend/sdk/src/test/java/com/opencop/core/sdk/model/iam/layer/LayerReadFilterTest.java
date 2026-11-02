// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LayerReadFilterTest {

  @Test
  void recordVisibilityOrDefault_whenNull_returnsActive() {
    var filter = new LayerReadFilter(null, LayerAccessScope.GLOBAL, "Ops", null, Set.of());

    assertEquals(RecordVisibility.ACTIVE, filter.recordVisibilityOrDefault());
  }

  @Test
  void hasIds_whenIdsIsEmpty_returnsFalse() {
    var filter = new LayerReadFilter(RecordVisibility.ALL, null, null, null, Set.of());

    assertFalse(filter.hasIds());
  }

  @Test
  void hasIds_whenIdsContainsValues_returnsTrue() {
    var filter =
        new LayerReadFilter(
            RecordVisibility.ALL, null, null, null, Set.of(UUID.randomUUID(), UUID.randomUUID()));

    assertTrue(filter.hasIds());
  }
}
