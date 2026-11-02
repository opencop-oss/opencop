// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.directory;

import com.opencop.core.sdk.model.filter.FilterCriterion;
import com.opencop.core.sdk.model.iam.layer.LayerDirectoryFilterField;
import com.opencop.core.sdk.model.iam.layer.LayerDirectoryReadFilter;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.LikePatterns;
import com.opencop.core.sdk.technical.gateway.db.filter.FilterPredicates;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds JPA {@link Specification}s for {@link LayerDirectoryEntity} from a {@link
 * LayerDirectoryReadFilter}, mirroring {@code UserDirectorySpecifications}. Record visibility
 * filters on the projected {@code lifecycleState}, matching {@code LayerSpecifications} on the
 * single-table layer query:
 *
 * <ul>
 *   <li>{@code ACTIVE} → {@code lifecycleState = 'ACTIVE'}: active layers plus all groups (groups
 *       synthesize {@code 'ACTIVE'} in the subselect).
 *   <li>{@code ARCHIVED} → {@code lifecycleState = 'ARCHIVED'}: archived layers only; groups drop
 *       out.
 *   <li>{@code ALL} → no lifecycle predicate: every row.
 * </ul>
 *
 * <p>{@code ACTIVE} and {@code ARCHIVED} match exact states, so the transitional merge states
 * ({@code MERGE_SOURCE_LOCKED}, {@code PENDING_MERGE}, {@code MERGE_FAILED}) surface only under
 * {@code ALL} — a layer mid-merge is hidden from both the active and archived admin views until the
 * merge settles. This is intentional and consistent with {@code LayerSpecifications}.
 *
 * <p>Per-column criteria are translated by the shared {@code FilterPredicates}, so this grid and
 * the Users grid answer the same operator the same way. Which columns are filterable, and why this
 * allowlist is narrower than the user directory's, is {@code LayerDirectoryFilterField}.
 */
public final class LayerDirectorySpecifications {

  private LayerDirectorySpecifications() {}

  public static Specification<LayerDirectoryEntity> from(LayerDirectoryReadFilter filter) {
    LayerDirectoryReadFilter effectiveFilter =
        filter == null ? LayerDirectoryReadFilter.empty() : filter;
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Exact-state match (mirrors LayerSpecifications); ALL adds no predicate, so the transitional
      // merge states surface only under ALL. See the class doc for the full contract.
      RecordVisibility recordVisibility = effectiveFilter.recordVisibilityOrDefault();
      if (recordVisibility == RecordVisibility.ACTIVE) {
        predicates.add(cb.equal(root.get("lifecycleState"), "ACTIVE"));
      } else if (recordVisibility == RecordVisibility.ARCHIVED) {
        predicates.add(cb.equal(root.get("lifecycleState"), "ARCHIVED"));
      }

      if (effectiveFilter.hasSearch()) {
        // Substring rather than prefix, matching the Users grid: an admin expects "route" to find
        // "alpha-route-overlay". The leading wildcard means this scans; V40 records why a trigram
        // index was measured and rejected. Metacharacters are escaped so a typed % or _ is literal.
        predicates.add(
            cb.like(
                cb.lower(root.get("name")),
                LikePatterns.contains(effectiveFilter.search()),
                LikePatterns.ESCAPE));
      }

      for (FilterCriterion<LayerDirectoryFilterField> criterion : effectiveFilter.criteria()) {
        predicates.add(FilterPredicates.toPredicate(criterion, root, cb, effectiveFilter.zone()));
      }

      return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
