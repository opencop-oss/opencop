// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.group;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/**
 * Allowlist of the sort fields {@code GET /v1/user-groups/search} accepts. An unknown name is
 * rejected with a 400; without this it reaches query construction and surfaces as a {@code
 * PropertyReferenceException} and a 500.
 *
 * <p>The allowlist is narrower than the entity, and narrower than the projection. Sorting by a
 * hidden column would reveal its relative values: {@code sort=idpIssuer} would order groups by
 * their issuer without returning a single issuer string.
 *
 * <p>{@code source} is deliberately absent even though the projection carries it. That is a
 * pagination decision rather than a disclosure one: management source is coarse, non-sensitive, and
 * already on every row, but ordering by a two-valued column is not a total order and the picker has
 * no use for it. Whether a group is IdP-provisioned is no longer withheld; the issuer string and
 * the external group id still are.
 */
final class GroupSearchSort {

  /** Sort fields the picker projection exposes, so ordering reveals nothing it does not show. */
  private static final Set<String> ALLOWED = Set.of("id", "name");

  private GroupSearchSort() {}

  /**
   * Returns a {@link Pageable} whose sort properties are validated, preserving page number, size
   * and direction. Unsorted requests pass through unchanged so the caller's stable-order default
   * still applies; validation runs before that default is layered on, so its internal {@code id}
   * tie-breaker is never subject to this allowlist.
   */
  static Pageable validate(Pageable pageable) {
    if (pageable.getSort().isUnsorted()) {
      return pageable;
    }
    Sort validated =
        Sort.by(pageable.getSort().stream().map(GroupSearchSort::validateOrder).toList());
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), validated);
  }

  private static Sort.Order validateOrder(Sort.Order order) {
    if (!ALLOWED.contains(order.getProperty())) {
      throw new GlobalOpenCopException(
          "INVALID_SORT",
          "Unsupported sort field '" + order.getProperty() + "'",
          HttpStatus.BAD_REQUEST);
    }
    return order;
  }
}
