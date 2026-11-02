// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.group;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@code hasSearch()} decides whether a search predicate is applied at all. The endpoint's
 * integration tests cannot observe the blank case: a whitespace term is trimmed into the pattern
 * {@code %}, which matches every row exactly as an omitted predicate would, so both paths return
 * the same rows over HTTP. The guard is pinned here instead.
 */
class GroupReadFilterTest {

  @Test
  void hasSearch_isFalseWhenTermIsAbsent() {
    assertFalse(new GroupReadFilter(null).hasSearch());
    assertFalse(GroupReadFilter.empty().hasSearch());
  }

  @Test
  void hasSearch_isFalseWhenTermIsOnlyWhitespace() {
    assertFalse(new GroupReadFilter("").hasSearch());
    assertFalse(new GroupReadFilter("   ").hasSearch());
    assertFalse(new GroupReadFilter("\t\n").hasSearch());
  }

  @Test
  void hasSearch_isTrueWhenTermHasContent() {
    assertTrue(new GroupReadFilter("ops").hasSearch());
    // Surrounding whitespace does not make a real term blank.
    assertTrue(new GroupReadFilter("  ops  ").hasSearch());
  }
}
