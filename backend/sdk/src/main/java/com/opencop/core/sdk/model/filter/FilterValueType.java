// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.filter;

/**
 * Shapes how a criterion's value token is parsed and which predicate branch applies.
 *
 * <p>A sealed interface rather than an enum so {@link Enumerated} can carry its resolver with it:
 * omitting one is caught where the field is declared rather than surfacing as an NPE during query
 * construction. Java requires the argument to be passed, and the compact constructor rejects a
 * null, so the two together make a resolver-less closed domain unconstructable. It also makes the
 * translator's {@code switch} exhaustive with no {@code default} arm.
 *
 * <p>Construct these through {@link FilterValueTypes} rather than directly.
 */
public sealed interface FilterValueType {

  /** Free text. Any non-blank value is legal; LIKE metacharacters are escaped at query time. */
  record Text() implements FilterValueType {}

  /** {@code true}/{@code false} only — see {@link FilterCriterion#booleanValue()}. */
  record Bool() implements FilterValueType {}

  /** An ISO {@code yyyy-MM-dd} day, compared against an {@code Instant} column. */
  record Date() implements FilterValueType {}

  /** A closed value domain; {@code resolver} turns a wire token into the predicate operand. */
  record Enumerated(FilterEnumResolver resolver) implements FilterValueType {
    public Enumerated {
      if (resolver == null) {
        throw new IllegalArgumentException("A closed value domain requires a resolver");
      }
    }
  }
}
