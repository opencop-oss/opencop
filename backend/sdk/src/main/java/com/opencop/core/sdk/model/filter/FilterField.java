// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.filter;

import java.util.Set;

/**
 * One filterable column: its wire name, the entity attribute it reads, how its values parse, and
 * which operators it accepts.
 *
 * <p>Implemented by a per-endpoint enum whose constants are the closed set of columns a client may
 * filter on. That whitelist — not the query layer — is what keeps a caller from filtering on an
 * internal column or asking for a comparison the read cannot apply.
 *
 * <p>Wire names are normally identical to the JPA attribute names, so there is no translation table
 * to keep in sync; {@link #jpaAttribute()} is still carried explicitly so the coupling is auditable
 * at a glance and can diverge later without a wire break.
 */
public interface FilterField {

  /** The token a client sends in the {@code filter} param's first position. */
  String wireName();

  /** The entity attribute the predicate reads. */
  String jpaAttribute();

  FilterValueType valueType();

  Set<FilterOperator> supportedOperators();

  default boolean supports(FilterOperator operator) {
    return supportedOperators().contains(operator);
  }
}
