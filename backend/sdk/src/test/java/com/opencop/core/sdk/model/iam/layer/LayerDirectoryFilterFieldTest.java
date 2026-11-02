// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.layer;

import static com.opencop.core.sdk.model.filter.FilterOperator.AFTER;
import static com.opencop.core.sdk.model.filter.FilterOperator.BEFORE;
import static com.opencop.core.sdk.model.filter.FilterOperator.BETWEEN;
import static com.opencop.core.sdk.model.filter.FilterOperator.CONTAINS;
import static com.opencop.core.sdk.model.filter.FilterOperator.EQUALS;
import static com.opencop.core.sdk.model.filter.FilterOperator.IS_EMPTY;
import static com.opencop.core.sdk.model.filter.FilterOperator.IS_NOT_EMPTY;
import static com.opencop.core.sdk.model.filter.FilterOperator.NOT_CONTAINS;
import static com.opencop.core.sdk.model.filter.FilterOperator.NOT_EQUALS;
import static com.opencop.core.sdk.model.filter.FilterOperator.STARTS_WITH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the layer directory's filter surface. The parser and predicate translator are shared, so
 * this allowlist is the only thing deciding what an admin can actually filter on — widening it by
 * accident should fail here rather than reach the wire.
 */
class LayerDirectoryFilterFieldTest {

  @Test
  void wireNamesAreTheDocumentedContract() {
    assertEquals("name", LayerDirectoryFilterField.NAME.wireName());
    assertEquals("type", LayerDirectoryFilterField.TYPE.wireName());
    assertEquals("accessScope", LayerDirectoryFilterField.ACCESS_SCOPE.wireName());
    assertEquals("createdAt", LayerDirectoryFilterField.CREATED_AT.wireName());
  }

  @Test
  void everyFieldMapsToAProjectedEntityAttribute() {
    for (LayerDirectoryFilterField field : LayerDirectoryFilterField.values()) {
      assertEquals(field.wireName(), field.jpaAttribute(), field.name());
    }
  }

  @Test
  void nameOffersTextOperatorsButNotTheEmptinessPair() {
    assertEquals(
        Set.of(CONTAINS, NOT_CONTAINS, STARTS_WITH, EQUALS, NOT_EQUALS),
        LayerDirectoryFilterField.NAME.supportedOperators());
    // Both name columns are NOT NULL, so these could only ever match the empty string.
    assertFalse(LayerDirectoryFilterField.NAME.supports(IS_EMPTY));
    assertFalse(LayerDirectoryFilterField.NAME.supports(IS_NOT_EMPTY));
  }

  @Test
  void closedDomainFieldsOfferEqualityOnly() {
    assertEquals(Set.of(EQUALS, NOT_EQUALS), LayerDirectoryFilterField.TYPE.supportedOperators());
    assertEquals(
        Set.of(EQUALS, NOT_EQUALS), LayerDirectoryFilterField.ACCESS_SCOPE.supportedOperators());
  }

  @Test
  void createdAtOffersTheDateRangeOperators() {
    assertEquals(
        Set.of(BEFORE, AFTER, BETWEEN), LayerDirectoryFilterField.CREATED_AT.supportedOperators());
  }

  @Test
  void fromWireResolvesEveryDeclaredField() {
    for (LayerDirectoryFilterField field : LayerDirectoryFilterField.values()) {
      assertSame(field, LayerDirectoryFilterField.fromWire(field.wireName()));
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "archived", // derived from lifecycleState in the query service, not a column
        "lifecycleState", // an implementation detail behind the recordVisibility view mode
        "description", // no longer projected
        "id",
        "Name", // wire names are case-sensitive
        "bogus"
      })
  void fromWireRejectsAnythingOutsideTheAllowlist(String token) {
    assertThrows(IllegalArgumentException.class, () -> LayerDirectoryFilterField.fromWire(token));
  }
}
