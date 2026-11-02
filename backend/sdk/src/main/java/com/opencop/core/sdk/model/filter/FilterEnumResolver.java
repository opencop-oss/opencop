// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.filter;

/**
 * Turns a wire token into the operand bound into an equality predicate for a closed-domain column.
 *
 * <p>Returns {@code Object} deliberately: the sole consumer is {@code CriteriaBuilder.equal(
 * Expression, Object)}, so a narrower type would buy nothing and force a cast at the call site. It
 * lets one resolver yield a Java enum constant (for an {@code @Enumerated(STRING)} column) and
 * another yield the {@code String} token itself (for a text column whose values happen to be a
 * closed set) without the criterion needing to know which.
 *
 * @see FilterValueTypes#enumerated
 * @see FilterValueTypes#tokens
 */
@FunctionalInterface
public interface FilterEnumResolver {

  /**
   * @throws IllegalArgumentException if the token is outside the field's value domain; {@link
   *     FilterCriterion} rewraps it with the offending field's wire name
   */
  Object resolve(String token);
}
