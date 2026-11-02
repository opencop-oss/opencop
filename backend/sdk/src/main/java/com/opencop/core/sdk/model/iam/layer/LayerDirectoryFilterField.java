// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.layer;

import static com.opencop.core.sdk.model.filter.FilterOperator.AFTER;
import static com.opencop.core.sdk.model.filter.FilterOperator.BEFORE;
import static com.opencop.core.sdk.model.filter.FilterOperator.BETWEEN;
import static com.opencop.core.sdk.model.filter.FilterOperator.CONTAINS;
import static com.opencop.core.sdk.model.filter.FilterOperator.EQUALS;
import static com.opencop.core.sdk.model.filter.FilterOperator.NOT_CONTAINS;
import static com.opencop.core.sdk.model.filter.FilterOperator.NOT_EQUALS;
import static com.opencop.core.sdk.model.filter.FilterOperator.STARTS_WITH;

import com.opencop.core.sdk.model.filter.FilterField;
import com.opencop.core.sdk.model.filter.FilterOperator;
import com.opencop.core.sdk.model.filter.FilterValueType;
import com.opencop.core.sdk.model.filter.FilterValueTypes;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The closed set of layer-directory columns a client may filter on, and which operators each one
 * accepts. Sibling of {@code DirectoryFilterField}; the grammar, limits and predicate translation
 * are shared, only this allowlist differs.
 *
 * <p>Four deliberate divergences from the user directory, each of which will otherwise read as an
 * oversight:
 *
 * <ul>
 *   <li><b>{@code NAME} has no {@code isEmpty}/{@code isNotEmpty}.</b> No branch of the projection
 *       leaves a shared column null — {@code iam.layer.name} and {@code iam.layer_group.name} are
 *       both {@code NOT NULL UNIQUE} — so the operator could only ever match the empty string. The
 *       user directory needs it because group rows carry NULL for the user-only columns.
 *   <li><b>{@code ACCESS_SCOPE} uses {@link FilterValueTypes#tokens} rather than {@link
 *       FilterValueTypes#enumerated}.</b> The two union branches carry different Postgres enum
 *       types ({@code iam.layer_access_scope}, {@code iam.layer_group_access_scope}) and are cast
 *       to {@code text} so they union, so the entity attribute is a {@code String}. Binding a Java
 *       enum against it would be a type mismatch at query time rather than a compile error.
 *   <li><b>{@code archived} is absent.</b> It is a two-group boolean derived in the query service
 *       from a five-valued {@code lifecycleState}, not a column; the grid narrows on it through the
 *       {@code recordVisibility} view mode instead. {@code LayerDirectorySort} excludes it from
 *       sorting for the same reason.
 *   <li><b>{@code description} is absent</b> because it is no longer projected — no grid column
 *       ever rendered it.
 * </ul>
 */
public enum LayerDirectoryFilterField implements FilterField {
  NAME(
      "name",
      "name",
      FilterValueTypes.TEXT,
      EnumSet.of(CONTAINS, NOT_CONTAINS, STARTS_WITH, EQUALS, NOT_EQUALS)),
  TYPE(
      "type",
      "type",
      FilterValueTypes.enumerated(LayerDirectoryType.values()),
      EnumSet.of(EQUALS, NOT_EQUALS)),
  ACCESS_SCOPE(
      "accessScope",
      "accessScope",
      FilterValueTypes.tokens("PRIVATE", "GLOBAL"),
      EnumSet.of(EQUALS, NOT_EQUALS)),
  CREATED_AT("createdAt", "createdAt", FilterValueTypes.DATE, EnumSet.of(BEFORE, AFTER, BETWEEN));

  private static final Map<String, LayerDirectoryFilterField> BY_WIRE_NAME =
      Arrays.stream(values())
          .collect(Collectors.toMap(LayerDirectoryFilterField::wireName, Function.identity()));

  private final String wireName;
  private final String jpaAttribute;
  private final FilterValueType valueType;
  private final Set<FilterOperator> supportedOperators;

  LayerDirectoryFilterField(
      String wireName,
      String jpaAttribute,
      FilterValueType valueType,
      Set<FilterOperator> supportedOperators) {
    this.wireName = wireName;
    this.jpaAttribute = jpaAttribute;
    this.valueType = valueType;
    this.supportedOperators = Set.copyOf(supportedOperators);
  }

  @Override
  public String wireName() {
    return wireName;
  }

  @Override
  public String jpaAttribute() {
    return jpaAttribute;
  }

  @Override
  public FilterValueType valueType() {
    return valueType;
  }

  @Override
  public Set<FilterOperator> supportedOperators() {
    return supportedOperators;
  }

  /**
   * @throws IllegalArgumentException if no field has that wire name
   */
  public static LayerDirectoryFilterField fromWire(String token) {
    LayerDirectoryFilterField field = BY_WIRE_NAME.get(token);
    if (field == null) {
      throw new IllegalArgumentException("Unknown filter field '" + token + "'");
    }
    return field;
  }
}
