// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.userdirectory;

import static com.opencop.core.sdk.model.filter.FilterOperator.AFTER;
import static com.opencop.core.sdk.model.filter.FilterOperator.BEFORE;
import static com.opencop.core.sdk.model.filter.FilterOperator.BETWEEN;
import static com.opencop.core.sdk.model.filter.FilterOperator.CONTAINS;
import static com.opencop.core.sdk.model.filter.FilterOperator.EQUALS;
import static com.opencop.core.sdk.model.filter.FilterOperator.IS_EMPTY;
import static com.opencop.core.sdk.model.filter.FilterOperator.IS_FALSE;
import static com.opencop.core.sdk.model.filter.FilterOperator.IS_NOT_EMPTY;
import static com.opencop.core.sdk.model.filter.FilterOperator.IS_TRUE;
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
 * The closed set of user directory columns a client may filter on, and which operators each one
 * accepts. This whitelist — not the query layer — is what keeps an admin from filtering on an
 * internal column or asking for a comparison the read cannot apply.
 *
 * <p>Wire names are identical to the JPA attribute names on the user directory entity, so there is
 * no translation table to keep in sync. {@code jpaAttribute} is still carried explicitly so the
 * coupling is auditable at a glance and can diverge later without a wire break.
 *
 * <p>Group rows carry NULL for the user-only columns ({@code firstName}, {@code lastName}, {@code
 * email}, {@code admin}), so filtering on one of those drops groups through plain SQL three-valued
 * logic. {@link FilterOperator#IS_EMPTY} is the deliberate exception — see the predicate builder.
 *
 * <p>{@code displayName} is deliberately absent: the admin grid has no display-name column, and the
 * filter bar can only offer columns the grid renders, so a filter on it would be unreachable. It
 * remains matched by the free-text {@code search} term. Add it here when a column exists to drive
 * it.
 *
 * @see com.opencop.core.sdk.model.iam.layer.LayerDirectoryFilterField the sibling allowlist for the
 *     admin Layers grid
 */
public enum UserDirectoryFilterField implements FilterField {
  TYPE(
      "type",
      "type",
      FilterValueTypes.enumerated(UserDirectoryEntryType.values()),
      EnumSet.of(EQUALS, NOT_EQUALS)),
  ADMIN("admin", "admin", FilterValueTypes.BOOL, EnumSet.of(EQUALS, IS_TRUE, IS_FALSE)),
  ACTIVE("active", "active", FilterValueTypes.BOOL, EnumSet.of(EQUALS, IS_TRUE, IS_FALSE)),
  NAME("name", "name", FilterValueTypes.TEXT, stringOperators()),
  FIRST_NAME("firstName", "firstName", FilterValueTypes.TEXT, stringOperators()),
  LAST_NAME("lastName", "lastName", FilterValueTypes.TEXT, stringOperators()),
  EMAIL("email", "email", FilterValueTypes.TEXT, stringOperators()),
  CREATED_AT("createdAt", "createdAt", FilterValueTypes.DATE, EnumSet.of(BEFORE, AFTER, BETWEEN));

  private static final Map<String, UserDirectoryFilterField> BY_WIRE_NAME =
      Arrays.stream(values())
          .collect(Collectors.toMap(UserDirectoryFilterField::wireName, Function.identity()));

  private final String wireName;
  private final String jpaAttribute;
  private final FilterValueType valueType;
  private final Set<FilterOperator> supportedOperators;

  UserDirectoryFilterField(
      String wireName,
      String jpaAttribute,
      FilterValueType valueType,
      Set<FilterOperator> supportedOperators) {
    this.wireName = wireName;
    this.jpaAttribute = jpaAttribute;
    this.valueType = valueType;
    this.supportedOperators = Set.copyOf(supportedOperators);
  }

  private static Set<FilterOperator> stringOperators() {
    return EnumSet.of(
        CONTAINS, NOT_CONTAINS, STARTS_WITH, EQUALS, NOT_EQUALS, IS_EMPTY, IS_NOT_EMPTY);
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
  public static UserDirectoryFilterField fromWire(String token) {
    UserDirectoryFilterField field = BY_WIRE_NAME.get(token);
    if (field == null) {
      throw new IllegalArgumentException("Unknown filter field '" + token + "'");
    }
    return field;
  }
}
