// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.visibility.RecordVisibility;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataSourceReadFilterTest {

  @Test
  void recordVisibilityOrDefault_whenNull_returnsActive() {
    var filter = new DataSourceReadFilter(null, Set.of(), null, null, null, null);

    assertEquals(RecordVisibility.ACTIVE, filter.recordVisibilityOrDefault());
  }

  @Test
  void hasIds_whenIdsIsEmpty_returnsFalse() {
    var filter = new DataSourceReadFilter(null, Set.of(), RecordVisibility.ALL, null, null, null);

    assertFalse(filter.hasIds());
  }

  @Test
  void hasIds_whenIdsContainsValues_returnsTrue() {
    var filter =
        new DataSourceReadFilter(
            null,
            Set.of(UUID.randomUUID(), UUID.randomUUID()),
            RecordVisibility.ALL,
            null,
            null,
            null);

    assertTrue(filter.hasIds());
  }

  @Test
  void canonicalConstructor_whenIdsIsNull_usesEmptySetAndKeepsOptionalFilters() {
    var filter =
        new DataSourceReadFilter(null, null, RecordVisibility.ARCHIVED, "LOCAL", true, null);

    assertTrue(filter.ids().isEmpty());
    assertEquals(Boolean.TRUE, filter.enabled());
    assertNull(filter.assignedInstance());
  }
}
