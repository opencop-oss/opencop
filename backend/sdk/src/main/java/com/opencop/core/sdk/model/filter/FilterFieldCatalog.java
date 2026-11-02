// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.filter;

/**
 * Resolves a wire token to one endpoint's filterable column. Each {@link FilterField} enum already
 * exposes a static {@code fromWire}, so a catalog is written as a method reference — {@code
 * FilterCriterion.parseAll(raw, DirectoryFilterField::fromWire)} — which is also what lets the
 * parser infer {@code F} with no wildcards or casts at the call site.
 */
@FunctionalInterface
public interface FilterFieldCatalog<F extends Enum<F> & FilterField> {

  /**
   * @throws IllegalArgumentException if no field has that wire name
   */
  F fromWire(String token);
}
