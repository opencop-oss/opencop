// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.filter;

import com.opencop.core.sdk.model.filter.FilterCriterion;
import com.opencop.core.sdk.model.filter.FilterField;
import com.opencop.core.sdk.model.filter.FilterFieldCatalog;
import jakarta.servlet.http.HttpServletRequest;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/** Reads the filter-related query params off a request for a filtered endpoint. */
public final class FilterParams {

  private FilterParams() {}

  /**
   * Resolves the caller's IANA zone, which fixes the day boundaries of date-only criteria. Rejects
   * an unknown zone rather than silently falling back to UTC -- a silent fallback would return a
   * result set that disagrees with the dates on screen. Absent or blank means UTC, which keeps the
   * parameter optional on the wire for callers with no date criteria.
   */
  public static ZoneId zoneOf(String tz) {
    if (tz == null || tz.isBlank()) {
      return ZoneOffset.UTC;
    }
    try {
      return ZoneId.of(tz.strip());
    } catch (DateTimeException e) {
      throw new InvalidFilterException("Unknown timezone");
    }
  }

  /**
   * Reads the repeated {@code filter} params straight off the request. Binding them as
   * {@code @RequestParam List<String>} corrupts the single-filter case: Spring hands back a bare
   * String when only one value is present, and the collection converter then splits it on commas,
   * shredding {@code email,contains,jo} into three tokens. Spring Data's own {@code
   * SortHandlerMethodArgumentResolver} reads {@code getParameterValues} for the same reason.
   *
   * <p>The filter model throws {@link IllegalArgumentException} to stay free of Spring Web, so
   * translating it to a 400 is this layer's job. The catch wraps only the parse call.
   *
   * @param catalog the calling endpoint's column allowlist, e.g. {@code
   *     DirectoryFilterField::fromWire}
   */
  public static <F extends Enum<F> & FilterField> List<FilterCriterion<F>> from(
      HttpServletRequest request, FilterFieldCatalog<F> catalog) {
    try {
      return FilterCriterion.parseAll(request.getParameterValues("filter"), catalog);
    } catch (IllegalArgumentException e) {
      throw new InvalidFilterException(e.getMessage());
    }
  }
}
