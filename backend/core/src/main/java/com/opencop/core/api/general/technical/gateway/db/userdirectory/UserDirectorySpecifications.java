// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.userdirectory;

import com.opencop.core.sdk.model.filter.FilterCriterion;
import com.opencop.core.sdk.model.iam.userdirectory.UserDirectoryFilterField;
import com.opencop.core.sdk.model.iam.userdirectory.UserDirectoryReadFilter;
import com.opencop.core.sdk.technical.gateway.db.LikePatterns;
import com.opencop.core.sdk.technical.gateway.db.filter.FilterPredicates;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the admin Users grid's query. The per-column criteria are translated by the shared {@link
 * FilterPredicates}; what stays here is user-directory-specific — which attributes free-text search
 * spans, and the fact that group rows carry NULL for the user-only columns, so filtering on one of
 * those drops them via plain SQL three-valued logic with no explicit type exclusion anywhere below.
 */
public final class UserDirectorySpecifications {

  /** Attributes the free-text {@code search} term is matched against. */
  private static final List<String> SEARCH_ATTRIBUTES =
      List.of("name", "firstName", "lastName", "email", "displayName");

  private UserDirectorySpecifications() {}

  public static Specification<UserDirectoryEntryEntity> from(UserDirectoryReadFilter filter) {
    UserDirectoryReadFilter effectiveFilter =
        filter == null ? UserDirectoryReadFilter.empty() : filter;
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (effectiveFilter.hasSearch()) {
        // Substring rather than prefix: an admin expects "wyman" to find "aaron.wyman". The leading
        // wildcard means this scans; V38 records why a trigram index was measured and rejected.
        String pattern = LikePatterns.contains(effectiveFilter.search());
        List<Predicate> searchPredicates = new ArrayList<>();
        for (String attribute : SEARCH_ATTRIBUTES) {
          searchPredicates.add(
              cb.like(cb.lower(root.get(attribute)), pattern, LikePatterns.ESCAPE));
        }
        predicates.add(cb.or(searchPredicates.toArray(Predicate[]::new)));
      }

      for (FilterCriterion<UserDirectoryFilterField> criterion : effectiveFilter.criteria()) {
        predicates.add(FilterPredicates.toPredicate(criterion, root, cb, effectiveFilter.zone()));
      }

      return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
