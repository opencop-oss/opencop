// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.userdirectory;

import com.opencop.core.sdk.model.filter.FilterCriterion;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Server-side filters for listing user directory entries (users + groups).
 *
 * <p>{@code search} is a free-text term matched (case-insensitively, as a substring) against the
 * entry's name/email fields. {@code criteria} carries the per-column constraints built by the admin
 * grid's filter bar. Every criterion ANDs with the others and with {@code search}, including two
 * criteria naming the same field. Both are optional; an empty filter constrains nothing.
 *
 * <p>Narrowing by principal kind or active status is a criterion ({@code type,equals,GROUP}, {@code
 * active,equals,false}) rather than a dedicated field, so there is one way to say it.
 *
 * <p>{@code zone} resolves the day boundaries of date-only criteria. The grid renders timestamps in
 * the viewer's configured timezone, so resolving in UTC would exclude rows whose displayed day
 * matches the filter -- at UTC+8 every row created after 16:00 UTC shows as the next day. Defaults
 * to UTC when a caller sends no zone, which keeps the parameter optional on the wire.
 */
public record UserDirectoryReadFilter(
    String search, List<FilterCriterion<UserDirectoryFilterField>> criteria, ZoneId zone) {

  public UserDirectoryReadFilter {
    criteria = criteria == null ? List.of() : List.copyOf(criteria);
    zone = zone == null ? ZoneOffset.UTC : zone;
  }

  /** Zone-less form for callers with no date criteria; day boundaries resolve in UTC. */
  public UserDirectoryReadFilter(
      String search, List<FilterCriterion<UserDirectoryFilterField>> criteria) {
    this(search, criteria, ZoneOffset.UTC);
  }

  public static UserDirectoryReadFilter empty() {
    return new UserDirectoryReadFilter(null, List.of(), ZoneOffset.UTC);
  }

  public boolean hasSearch() {
    return search != null && !search.isBlank();
  }

  public boolean hasCriteria() {
    return !criteria.isEmpty();
  }
}
