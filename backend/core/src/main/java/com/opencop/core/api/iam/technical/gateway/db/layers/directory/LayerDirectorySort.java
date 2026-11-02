// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.directory;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/**
 * Translates the layer directory's public sort field names to {@link LayerDirectoryEntity}
 * properties so callers sort by the response model rather than by JPA internals. Any name outside
 * the allowlist is rejected with a 400 instead of reaching query construction and surfacing as a
 * 500. {@code archived} is intentionally not sortable: it is a two-group boolean derived from the
 * five-valued {@code lifecycleState}, so ordering by the underlying label would not reproduce the
 * boolean grouping — and no grid column sorts by it (archived is shown as a badge, not a sort key).
 *
 * <p>Every translated order sorts nulls last, in both directions — see {@link #translateOrder}.
 */
final class LayerDirectorySort {

  /** Public sort field → {@link LayerDirectoryEntity} property. */
  private static final Map<String, String> ALLOWED =
      Map.of(
          "name", "name",
          "type", "type",
          "createdAt", "createdAt",
          "updatedAt", "updatedAt",
          "lastEventAt", "lastEventAt",
          "accessScope", "accessScope");

  private LayerDirectorySort() {}

  /**
   * Returns a {@link Pageable} whose sort properties are translated to entity properties,
   * preserving page number, size, and direction. Unsorted requests pass through unchanged (the
   * query service applies a deterministic default order downstream).
   */
  static Pageable translate(Pageable pageable) {
    if (pageable.getSort().isUnsorted()) {
      return pageable;
    }
    Sort translated =
        Sort.by(pageable.getSort().stream().map(LayerDirectorySort::translateOrder).toList());
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), translated);
  }

  private static Sort.Order translateOrder(Sort.Order order) {
    String property = ALLOWED.get(order.getProperty());
    if (property == null) {
      throw new GlobalOpenCopException(
          "INVALID_SORT",
          "Unsupported sort field '" + order.getProperty() + "'",
          HttpStatus.BAD_REQUEST);
    }
    // withProperty preserves direction and ignore-case from the caller's order. Null handling is
    // forced rather than inherited: a layer that has never been updated has a null `updatedAt`
    // permanently — backfilling it would invent an edit that never happened — and Postgres' native
    // DESC ordering puts NULLs first, so "most recently updated" would open on a block of
    // never-updated rows. Nulls sort last in both directions instead. `lastEventAt` is null under
    // the same rule and for the same reason. No-op for the other allowlisted properties, which are
    // all NOT NULL.
    return order.withProperty(property).nullsLast();
  }
}
