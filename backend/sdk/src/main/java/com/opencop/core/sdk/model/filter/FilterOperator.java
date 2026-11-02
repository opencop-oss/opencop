// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.filter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Comparison operators a filtered read can apply. The wire names are byte-identical to the web
 * client's {@code FilterOperator} strings (see {@code filter-builder.types.ts}), so the browser
 * serializes its filter chips straight through with no rename table on either side.
 *
 * <p>Matching is case-sensitive: {@code startswith} is not {@code startsWith} and is rejected.
 * Operators the backend cannot apply efficiently are deliberately absent rather than silently
 * ignored — an unknown token is a 400, never an unfiltered result set.
 *
 * <p>Shared across every filtered endpoint. Which subset a given column actually accepts is
 * declared per-endpoint by its {@link FilterField} enum, not here.
 */
public enum FilterOperator {
  EQUALS("equals", true),
  NOT_EQUALS("notEquals", true),
  CONTAINS("contains", true),
  NOT_CONTAINS("notContains", true),
  STARTS_WITH("startsWith", true),
  IS_EMPTY("isEmpty", false),
  IS_NOT_EMPTY("isNotEmpty", false),
  IS_TRUE("isTrue", false),
  IS_FALSE("isFalse", false),
  BEFORE("before", true),
  AFTER("after", true),
  BETWEEN("between", true);

  private static final Map<String, FilterOperator> BY_WIRE_NAME =
      Arrays.stream(values())
          .collect(Collectors.toMap(FilterOperator::wireName, Function.identity()));

  private final String wireName;
  private final boolean requiresValue;

  FilterOperator(String wireName, boolean requiresValue) {
    this.wireName = wireName;
    this.requiresValue = requiresValue;
  }

  public String wireName() {
    return wireName;
  }

  /**
   * Whether the operator needs a value token; {@code isEmpty}/{@code isTrue} and friends do not.
   */
  public boolean requiresValue() {
    return requiresValue;
  }

  /**
   * @throws IllegalArgumentException if no operator has that wire name
   */
  public static FilterOperator fromWire(String token) {
    FilterOperator operator = BY_WIRE_NAME.get(token);
    if (operator == null) {
      throw new IllegalArgumentException("Unknown filter operator '" + token + "'");
    }
    return operator;
  }
}
