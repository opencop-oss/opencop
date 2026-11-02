// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PageablesTest {

  private static final Sort DEFAULT = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

  @Test
  void appliesDefaultSortWhenCallerSuppliedNone() {
    var result = Pageables.withStableOrder(PageRequest.of(0, 25), DEFAULT, "id");

    assertEquals(
        List.of(Sort.Order.asc("name"), Sort.Order.asc("id")), result.getSort().stream().toList());
  }

  @Test
  void appendsTieBreakerToCallerSortAndPreservesPaging() {
    var caller = PageRequest.of(1, 10, Sort.by(Sort.Order.desc("name")));

    var result = Pageables.withStableOrder(caller, DEFAULT, "id");

    assertEquals(
        List.of(Sort.Order.desc("name"), Sort.Order.asc("id")), result.getSort().stream().toList());
    assertEquals(1, result.getPageNumber());
    assertEquals(10, result.getPageSize());
  }

  @Test
  void doesNotDuplicateTieBreakerAlreadyPresent() {
    var caller = PageRequest.of(0, 5, Sort.by(Sort.Order.desc("id")));

    var result = Pageables.withStableOrder(caller, DEFAULT, "id");

    assertEquals(List.of(Sort.Order.desc("id")), result.getSort().stream().toList());
  }

  @Test
  void appendsMultipleTieBreakersSkippingOnesAlreadyPresent() {
    var caller = PageRequest.of(0, 5, Sort.by(Sort.Order.asc("type")));

    var result = Pageables.withStableOrder(caller, DEFAULT, "type", "id");

    assertEquals(
        List.of(Sort.Order.asc("type"), Sort.Order.asc("id")), result.getSort().stream().toList());
  }
}
