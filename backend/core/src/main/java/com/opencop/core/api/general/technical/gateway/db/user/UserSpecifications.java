// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.user;

import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.sdk.model.iam.user.UserReadFilter;
import com.opencop.core.sdk.technical.gateway.db.LikePatterns;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {
  private static final List<String> SEARCH_ATTRIBUTES =
      List.of("username", "firstName", "lastName", "email", "displayName");

  private UserSpecifications() {}

  public static Specification<InternalUserEntity> from(UserReadFilter filter) {
    UserReadFilter effectiveFilter = filter == null ? UserReadFilter.empty() : filter;
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      predicates.add(cb.isNull(root.get("deletedAt")));

      if (effectiveFilter.hasSearch()) {
        // Prefix matching, deliberately: this backs the drawer assign pickers, where the admin is
        // completing a known username. The admin Users grid moved to substring matching (see
        // UserDirectorySpecifications); this endpoint stays on a prefix so a btree remains usable.
        String prefix = LikePatterns.startsWith(effectiveFilter.search());
        List<Predicate> searchPredicates = new ArrayList<>();
        for (String attribute : SEARCH_ATTRIBUTES) {
          searchPredicates.add(cb.like(cb.lower(root.get(attribute)), prefix, LikePatterns.ESCAPE));
        }
        predicates.add(cb.or(searchPredicates.toArray(Predicate[]::new)));
      }

      if (effectiveFilter.active() != null) {
        predicates.add(cb.equal(root.get("active"), effectiveFilter.active()));
      }

      if (effectiveFilter.admin() != null) {
        predicates.add(cb.equal(root.get("administrator"), effectiveFilter.admin()));
      }

      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
