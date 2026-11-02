// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Helpers for making offset pagination deterministic. Offset paging ({@code LIMIT}/{@code OFFSET})
 * only returns stable, non-overlapping pages when the query defines a total order, so every paged
 * query needs a unique tie-breaker. {@link #withStableOrder} applies {@code defaultSort} when the
 * caller supplied no sort, then appends the given tie-breaker properties (ascending) unless they
 * are already part of the sort — so a non-unique primary sort such as {@code username} still yields
 * deterministic page boundaries.
 */
public final class Pageables {

  private Pageables() {}

  public static Pageable withStableOrder(
      Pageable pageable, Sort defaultSort, String... tieBreakerProperties) {
    Sort base = pageable.getSort().isSorted() ? pageable.getSort() : defaultSort;
    Set<String> properties = new LinkedHashSet<>();
    base.forEach(order -> properties.add(order.getProperty()));
    Sort effective = base;
    for (String property : tieBreakerProperties) {
      if (properties.add(property)) {
        effective = effective.and(Sort.by(Sort.Order.asc(property)));
      }
    }
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), effective);
  }
}
