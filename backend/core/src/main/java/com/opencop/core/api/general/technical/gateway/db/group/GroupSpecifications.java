// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.group;

import com.opencop.core.sdk.model.iam.group.GroupReadFilter;
import com.opencop.core.sdk.technical.gateway.db.LikePatterns;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class GroupSpecifications {

  private GroupSpecifications() {}

  public static Specification<GroupEntity> from(GroupReadFilter filter) {
    GroupReadFilter effectiveFilter = filter == null ? GroupReadFilter.empty() : filter;
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (effectiveFilter.hasSearch()) {
        // Prefix rather than substring, because the pickers this backs complete a known group
        // name; the admin directory grids are the ones needing substring. This scans today
        // idx_user_group_directory_order is on name, which lower(name) cannot use but a prefix
        // leaves a functional index on lower(name) available if the group directory ever grows
        // enough to want one. A substring match would not.
        predicates.add(
            cb.like(
                cb.lower(root.get("name")),
                LikePatterns.startsWith(effectiveFilter.search()),
                LikePatterns.ESCAPE));
      }

      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
