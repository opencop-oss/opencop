// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.user;

import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/**
 * Allowlist of the sort fields {@code GET /v1/users/search} accepts. A name outside it is rejected
 * with a 400 instead of reaching query construction, where an unknown property surfaces as a {@code
 * PropertyReferenceException} and a 500 — the same guard {@link
 * com.opencop.core.api.general.technical.gateway.db.userdirectory.UserDirectorySort} applies to the
 * user directory.
 *
 * <p>Deliberately narrower than {@link InternalUserEntity}: it admits only what {@link
 * com.opencop.core.api.general.technical.endpoint.user.UserSearchResult} puts on the wire. Ordering
 * by a hidden column leaks its relative values — {@code sort=email} discloses the pool's email
 * ordering without returning a single address. Sortable is capped at visible.
 */
final class UserSearchSort {

  /** Sort fields the picker projection exposes, so ordering reveals nothing it does not show. */
  private static final Set<String> ALLOWED = Set.of("id", "username", "displayName");

  private UserSearchSort() {}

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
        Sort.by(pageable.getSort().stream().map(UserSearchSort::validateOrder).toList());
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
