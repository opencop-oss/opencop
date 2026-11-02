// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.layer;

import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

public final class LayerSpecifications {

  private static final String ID = "id";
  private static final String IDS = "ids";
  private static final String NAME = "name";
  private static final String ACCESS_SCOPE = "accessScope";
  private static final String LIFECYCLE_STATE = "lifecycleState";
  private static final Set<LayerLifecycleState> NAME_RESERVING_STATES =
      Set.of(
          LayerLifecycleState.ACTIVE,
          LayerLifecycleState.MERGE_SOURCE_LOCKED,
          LayerLifecycleState.PENDING_MERGE);

  private LayerSpecifications() {}

  public static Specification<LayerEntity> from(LayerReadFilter filter) {
    LayerReadFilter effectiveFilter =
        filter == null ? new LayerReadFilter(null, null, null) : filter;
    return (root, query, criteriaBuilder) -> {
      List<Predicate> predicates = new ArrayList<>();
      appendRecordVisibilityPredicate(
          effectiveFilter.recordVisibilityOrDefault(), root, criteriaBuilder, predicates);
      if (effectiveFilter.id() != null) {
        predicates.add(criteriaBuilder.equal(root.get(ID), effectiveFilter.id()));
      }
      if (effectiveFilter.hasIds()) {
        predicates.add(root.get(ID).in(effectiveFilter.ids()));
      }
      if (effectiveFilter.accessScope() != null) {
        predicates.add(
            criteriaBuilder.equal(root.get(ACCESS_SCOPE), effectiveFilter.accessScope()));
      }
      if (effectiveFilter.name() != null) {
        predicates.add(criteriaBuilder.equal(root.get(NAME), effectiveFilter.name()));
      }
      return predicates.isEmpty()
          ? criteriaBuilder.conjunction()
          : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
    };
  }

  public static Specification<LayerEntity> reservedName(String name) {
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.and(
            criteriaBuilder.equal(root.get(NAME), name),
            root.get(LIFECYCLE_STATE).in(NAME_RESERVING_STATES));
  }

  private static void appendRecordVisibilityPredicate(
      RecordVisibility recordVisibility,
      Root<LayerEntity> root,
      CriteriaBuilder criteriaBuilder,
      List<Predicate> predicates) {
    if (recordVisibility == RecordVisibility.ACTIVE) {
      predicates.add(criteriaBuilder.equal(root.get(LIFECYCLE_STATE), LayerLifecycleState.ACTIVE));
      return;
    }
    if (recordVisibility == RecordVisibility.ARCHIVED) {
      predicates.add(
          criteriaBuilder.equal(root.get(LIFECYCLE_STATE), LayerLifecycleState.ARCHIVED));
    }
  }
}
