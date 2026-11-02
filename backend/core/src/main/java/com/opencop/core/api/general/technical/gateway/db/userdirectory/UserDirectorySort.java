// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.userdirectory;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/**
 * Allowlist of the user directory's public sort fields. A name outside it is rejected with a 400
 * instead of reaching query construction, where an unknown property surfaces as a 500 — the same
 * guard {@link com.opencop.core.api.iam.technical.gateway.db.layers.directory.LayerDirectorySort}
 * applies to the layer directory.
 *
 * <p>The wire names equal the {@link UserDirectoryEntryEntity} attributes, so the map is an
 * allowlist rather than a translation; keeping it explicit means a new sortable column stays a
 * decision, not a side effect of adding an entity field. {@code displayName} is absent because the
 * grid has no such column.
 */
final class UserDirectorySort {

  /** Public sort field → {@link UserDirectoryEntryEntity} property. */
  private static final Map<String, String> ALLOWED =
      Map.of(
          "name", "name",
          "type", "type",
          "admin", "admin",
          "active", "active",
          "firstName", "firstName",
          "lastName", "lastName",
          "email", "email",
          "createdAt", "createdAt");

  private UserDirectorySort() {}

  /**
   * Returns a {@link Pageable} whose sort properties are validated, preserving page number, size
   * and direction. Unsorted requests pass through unchanged — the query service applies a
   * deterministic default order downstream.
   */
  static Pageable validate(Pageable pageable) {
    if (pageable.getSort().isUnsorted()) {
      return pageable;
    }
    Sort validated =
        Sort.by(pageable.getSort().stream().map(UserDirectorySort::validateOrder).toList());
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), validated);
  }

  private static Sort.Order validateOrder(Sort.Order order) {
    String property = ALLOWED.get(order.getProperty());
    if (property == null) {
      throw new GlobalOpenCopException(
          "INVALID_SORT",
          "Unsupported sort field '" + order.getProperty() + "'",
          HttpStatus.BAD_REQUEST);
    }
    // withProperty preserves direction, ignore-case and null-handling from the caller's order.
    return order.withProperty(property);
  }
}
