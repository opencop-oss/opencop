// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.layer;

import com.opencop.core.sdk.model.filter.FilterCriterion;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Server-side filters for the combined layer/layer-group directory grid.
 *
 * <p>{@code search} is a free-text term matched (case-insensitively, as a substring) against the
 * entry's name. {@code criteria} carries the per-column constraints built by the admin grid's
 * filter bar; every criterion ANDs with the others and with {@code search}, including two criteria
 * naming the same field. {@code recordVisibility} selects active vs archived (layers only — groups
 * are always treated as active).
 *
 * <p>Narrowing by entry kind is a criterion ({@code type,equals,LAYER_GROUP}) rather than a
 * dedicated field, so there is one way to say it — matching {@code DirectoryReadFilter}.
 *
 * <p>{@code zone} resolves the day boundaries of date-only criteria, for the same reason and with
 * the same default as {@code DirectoryReadFilter} — the two admin grids must answer "created before
 * X" identically.
 */
public record LayerDirectoryReadFilter(
    String search,
    RecordVisibility recordVisibility,
    List<FilterCriterion<LayerDirectoryFilterField>> criteria,
    ZoneId zone) {

  public LayerDirectoryReadFilter {
    criteria = criteria == null ? List.of() : List.copyOf(criteria);
    zone = zone == null ? ZoneOffset.UTC : zone;
  }

  /** Zone-less form for callers with no date criteria; day boundaries resolve in UTC. */
  public LayerDirectoryReadFilter(
      String search,
      RecordVisibility recordVisibility,
      List<FilterCriterion<LayerDirectoryFilterField>> criteria) {
    this(search, recordVisibility, criteria, ZoneOffset.UTC);
  }

  public static LayerDirectoryReadFilter empty() {
    return new LayerDirectoryReadFilter(null, null, List.of(), ZoneOffset.UTC);
  }

  public boolean hasSearch() {
    return search != null && !search.isBlank();
  }

  public boolean hasCriteria() {
    return !criteria.isEmpty();
  }

  public RecordVisibility recordVisibilityOrDefault() {
    return recordVisibility == null ? RecordVisibility.ACTIVE : recordVisibility;
  }
}
