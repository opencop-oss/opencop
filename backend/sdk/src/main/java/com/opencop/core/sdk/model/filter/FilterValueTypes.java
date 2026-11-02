// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.filter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factories for the {@link FilterValueType} variants. A {@link FilterField} enum declares its
 * columns through these rather than instantiating the records directly.
 *
 * <p>Both closed-domain factories resolve without reflection — {@link #enumerated} is handed the
 * constants array, never a {@code Class}, so there is no {@code Enum.valueOf(Class, String)} and no
 * unchecked cast anywhere in the path from wire token to predicate operand.
 */
public final class FilterValueTypes {

  public static final FilterValueType TEXT = new FilterValueType.Text();
  public static final FilterValueType BOOL = new FilterValueType.Bool();
  public static final FilterValueType DATE = new FilterValueType.Date();

  private FilterValueTypes() {}

  /**
   * A column mapped as {@code @Enumerated(STRING)}. The operand bound into the predicate is the
   * Java enum constant, which is what JPA expects for such a column.
   *
   * <p>Tokens are matched case-insensitively, so {@code type,equals,group} and {@code
   * type,equals,GROUP} both resolve.
   */
  public static <E extends Enum<E>> FilterValueType enumerated(E[] constants) {
    Map<String, E> byName =
        Arrays.stream(constants).collect(Collectors.toMap(Enum::name, Function.identity()));
    return new FilterValueType.Enumerated(
        token -> {
          E constant = byName.get(normalize(token));
          if (constant == null) {
            throw new IllegalArgumentException("Unknown value '" + normalize(token) + "'");
          }
          return constant;
        });
  }

  /**
   * A column stored as text whose values form a closed set. The operand is the normalized token
   * itself, not an enum constant.
   *
   * <p>Needed wherever a projection casts an enum to {@code text} — a {@code UNION ALL} over two
   * tables carrying different Postgres enum types has to, and binding a Java enum against the
   * resulting text column is a type mismatch at query time rather than a compile error.
   */
  public static FilterValueType tokens(String... allowed) {
    Set<String> permitted =
        Arrays.stream(allowed)
            .map(FilterValueTypes::normalize)
            .collect(Collectors.toUnmodifiableSet());
    return new FilterValueType.Enumerated(
        token -> {
          String normalized = normalize(token);
          if (!permitted.contains(normalized)) {
            throw new IllegalArgumentException("Unknown value '" + normalized + "'");
          }
          return normalized;
        });
  }

  private static String normalize(String token) {
    return token.strip().toUpperCase(Locale.ROOT);
  }
}
