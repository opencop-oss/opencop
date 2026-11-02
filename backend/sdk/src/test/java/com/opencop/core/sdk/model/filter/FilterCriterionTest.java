// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.iam.layer.LayerDirectoryFilterField;
import com.opencop.core.sdk.model.iam.layer.LayerDirectoryType;
import com.opencop.core.sdk.model.iam.userdirectory.UserDirectoryEntryType;
import com.opencop.core.sdk.model.iam.userdirectory.UserDirectoryFilterField;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Grammar and validation rules for the {@code filter=field,operator,value} wire format.
 *
 * <p>Rejections surface as {@link IllegalArgumentException} so this model stays free of Spring Web;
 * the endpoints translate them into a 400 (see {@code InvalidFilterException}).
 *
 * <p>Exercised against two field catalogs. The parser is shared, so running the same grammar
 * against a second endpoint's columns is what distinguishes "genuinely parameterized" from "written
 * for the directory and reused by coincidence".
 */
class FilterCriterionTest {

  @Nested
  class UserDirectoryFields {

    private FilterCriterion<UserDirectoryFilterField> parse(String raw) {
      return FilterCriterion.parse(raw, UserDirectoryFilterField::fromWire);
    }

    private List<FilterCriterion<UserDirectoryFilterField>> parseAll(String[] raw) {
      return FilterCriterion.parseAll(raw, UserDirectoryFilterField::fromWire);
    }

    @Test
    void parse_readsFieldOperatorAndValue() {
      FilterCriterion<UserDirectoryFilterField> criterion = parse("email,contains,jo");

      assertEquals(UserDirectoryFilterField.EMAIL, criterion.field());
      assertEquals(FilterOperator.CONTAINS, criterion.operator());
      assertEquals("jo", criterion.value());
      assertNull(criterion.valueTo());
    }

    @Test
    void parse_keepsCommasInsideTheValue() {
      // The split is limited to 3 tokens precisely so a value may contain commas of its own.
      FilterCriterion<UserDirectoryFilterField> criterion = parse("lastName,equals,Doe, John");

      assertEquals("Doe, John", criterion.value());
    }

    @Test
    void parse_acceptsNoValueOperatorWithoutAValueToken() {
      FilterCriterion<UserDirectoryFilterField> criterion = parse("admin,isTrue");

      assertEquals(FilterOperator.IS_TRUE, criterion.operator());
      assertNull(criterion.value());
    }

    @Test
    void parse_acceptsNoValueOperatorWithAnEmptyValueToken() {
      // The web serializer emits the uniform three-token form, e.g. "email,isEmpty,".
      FilterCriterion<UserDirectoryFilterField> criterion = parse("email,isEmpty,");

      assertEquals(FilterOperator.IS_EMPTY, criterion.operator());
      assertNull(criterion.value());
    }

    @Test
    void parse_splitsBetweenRangeOnTheDoubleDot() {
      FilterCriterion<UserDirectoryFilterField> criterion =
          parse("createdAt,between,2026-01-01..2026-03-01");

      assertEquals(LocalDate.of(2026, 1, 1), criterion.dateValue());
      assertEquals(LocalDate.of(2026, 3, 1), criterion.dateValueTo());
    }

    @Test
    void parse_stripsSurroundingWhitespaceFromTheValue() {
      assertEquals("jo", parse("email,contains,  jo  ").value());
    }

    @Test
    void parse_readsStartsWith() {
      FilterCriterion<UserDirectoryFilterField> criterion = parse("name,startsWith,ali");

      assertEquals(FilterOperator.STARTS_WITH, criterion.operator());
      assertEquals("ali", criterion.value());
    }

    @Test
    void parse_normalizesEnumValueCase() {
      assertEquals(UserDirectoryEntryType.GROUP, parse("type,equals,group").enumValue());
    }

    @Test
    void booleanValue_derivesFromTheOperatorForIsTrueAndIsFalse() {
      assertTrue(parse("admin,isTrue").booleanValue());
      assertEquals(false, parse("admin,isFalse").booleanValue());
    }

    @Test
    void booleanValue_parsesTheValueForEquals() {
      assertTrue(parse("admin,equals,true").booleanValue());
      assertEquals(false, parse("active,equals,false").booleanValue());
    }

    @Test
    void parse_rejectsUnknownField() {
      assertThrows(IllegalArgumentException.class, () -> parse("emial,equals,x"));
    }

    @Test
    void parse_rejectsUnknownOperator() {
      assertThrows(IllegalArgumentException.class, () -> parse("email,matches,x"));
    }

    @Test
    void parse_operatorTokensAreCaseSensitive() {
      assertThrows(IllegalArgumentException.class, () -> parse("email,startswith,x"));
    }

    @Test
    void parse_rejectsOperatorNotSupportedForTheField() {
      assertThrows(IllegalArgumentException.class, () -> parse("name,before,2026-01-01"));
    }

    @Test
    void parse_rejectsDeferredContainsOnANonStringField() {
      assertThrows(IllegalArgumentException.class, () -> parse("type,contains,GRO"));
    }

    @Test
    void parse_rejectsAMissingOperator() {
      assertThrows(IllegalArgumentException.class, () -> parse("email"));
    }

    @Test
    void parse_rejectsAMissingValueForAValueOperator() {
      assertThrows(IllegalArgumentException.class, () -> parse("email,equals,"));
    }

    @Test
    void parse_rejectsAnInvalidEnumValue() {
      assertThrows(IllegalArgumentException.class, () -> parse("type,equals,PERSON"));
    }

    @Test
    void parse_rejectsAnInvalidDateValue() {
      assertThrows(IllegalArgumentException.class, () -> parse("createdAt,before,notadate"));
    }

    @Test
    void parse_rejectsAnIncompleteBetweenRange() {
      assertThrows(IllegalArgumentException.class, () -> parse("createdAt,between,2026-01-01"));
    }

    @Test
    void parse_rejectsAReversedBetweenRange() {
      // Contradictory bounds would otherwise build a predicate that matches nothing, so the caller
      // sees an ordinary empty page instead of being told the range is backwards.
      assertThrows(
          IllegalArgumentException.class, () -> parse("createdAt,between,2026-03-01..2026-01-01"));
    }

    @Test
    void parse_acceptsABetweenRangeWithEqualEndpoints() {
      // A single-day range is the common "on this day" filter, not a reversed one.
      assertEquals(
          LocalDate.of(2026, 1, 1), parse("createdAt,between,2026-01-01..2026-01-01").dateValue());
    }

    @Test
    void parse_rejectsADateWhoseNextDayBoundaryWouldOverflow() {
      // The predicate builder advances the exclusive upper bound by a day; at LocalDate.MAX that
      // throws after the endpoint's parse-time catch, which would surface as a 500.
      assertThrows(IllegalArgumentException.class, () -> parse("createdAt,after,+999999999-12-31"));
      assertThrows(
          IllegalArgumentException.class,
          () -> parse("createdAt,between,2026-01-01..+999999999-12-31"));
    }

    @Test
    void parse_rejectsANonBooleanValueForABooleanField() {
      // Boolean.parseBoolean would quietly read "yes" as false; the filter must reject it instead.
      assertThrows(IllegalArgumentException.class, () -> parse("admin,equals,yes"));
    }

    @Test
    void parse_rejectsAnOverlongValue() {
      String tooLong = "x".repeat(FilterCriterion.MAX_VALUE_LENGTH + 1);

      assertThrows(IllegalArgumentException.class, () -> parse("email,contains," + tooLong));
    }

    @Test
    void parse_rejectsAnOverlongBetweenEndpoint() {
      // The second endpoint obeys the same cap as the first.
      String tooLong = "x".repeat(FilterCriterion.MAX_VALUE_LENGTH + 1);

      assertThrows(
          IllegalArgumentException.class, () -> parse("createdAt,between,2026-01-01.." + tooLong));
    }

    @Test
    void parse_doesNotEchoAnUnboundedFieldToken() {
      // The token reaches the 400 body and a WARN log, so it must not carry an arbitrary payload.
      String huge = "z".repeat(5000);

      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> parse(huge + ",equals,x"));

      assertTrue(thrown.getMessage().length() < 200, "message should not embed the whole token");
    }

    @Test
    void parse_stripsControlCharactersFromAnUnknownToken() {
      // A newline in a token would otherwise split or forge a log line.
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> parse("bad\nfield,equals,x"));

      assertFalse(thrown.getMessage().contains("\n"));
    }

    @Test
    void parse_preservesTheCauseOnAnInvalidDate() {
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> parse("createdAt,before,notadate"));

      assertNotNull(thrown.getCause());
    }

    @Test
    void parseAll_returnsEmptyForNullOrEmptyInput() {
      assertEquals(List.of(), parseAll(null));
      assertEquals(List.of(), parseAll(new String[0]));
    }

    @Test
    void parseAll_preservesOrderAcrossRepeatedParams() {
      List<FilterCriterion<UserDirectoryFilterField>> criteria =
          parseAll(new String[] {"type,equals,USER", "email,contains,jo"});

      assertEquals(2, criteria.size());
      assertEquals(UserDirectoryFilterField.TYPE, criteria.get(0).field());
      assertEquals(UserDirectoryFilterField.EMAIL, criteria.get(1).field());
    }

    @Test
    void parseAll_rejectsMoreThanTheCriteriaCap() {
      String[] tooMany =
          IntStream.rangeClosed(0, FilterCriterion.MAX_CRITERIA)
              .mapToObj(i -> "email,contains,v" + i)
              .toArray(String[]::new);

      assertThrows(IllegalArgumentException.class, () -> parseAll(tooMany));
    }
  }

  /**
   * The same grammar against a second endpoint's catalog. These are what distinguish a genuinely
   * parameterized parser from one written for the directory and reused by coincidence — in
   * particular that the closed-domain resolver yields an enum constant for one field and a plain
   * String for another, and that each catalog's allowlist is enforced independently.
   */
  @Nested
  class LayerDirectoryFields {

    private FilterCriterion<LayerDirectoryFilterField> parse(String raw) {
      return FilterCriterion.parse(raw, LayerDirectoryFilterField::fromWire);
    }

    @Test
    void parse_resolvesTypeToTheLayerDirectoryEnumConstant() {
      assertEquals(LayerDirectoryType.LAYER_GROUP, parse("type,equals,LAYER_GROUP").enumValue());
    }

    @Test
    void parse_normalizesTypeCase() {
      assertEquals(LayerDirectoryType.LAYER, parse("type,equals,layer").enumValue());
    }

    @Test
    void parse_resolvesAccessScopeToAStringNotAnEnum() {
      // The union casts two different Postgres enum types to text, so the operand must be the
      // token itself; binding a Java enum here would be a type mismatch at query time.
      Object operand = parse("accessScope,equals,global").enumValue();

      assertEquals("GLOBAL", operand);
      assertInstanceOf(String.class, operand);
    }

    @Test
    void parse_rejectsAnAccessScopeOutsideTheClosedDomain() {
      assertThrows(IllegalArgumentException.class, () -> parse("accessScope,equals,INTERNAL"));
    }

    @Test
    void parse_rejectsIsEmptyOnName() {
      // Both name columns are NOT NULL, so the operator is deliberately not offered here even
      // though the user directory's name column does support it.
      assertThrows(IllegalArgumentException.class, () -> parse("name,isEmpty,"));
    }

    @Test
    void parse_rejectsTheDerivedArchivedField() {
      // archived is computed from lifecycleState in the query service; it is not a column.
      assertThrows(IllegalArgumentException.class, () -> parse("archived,equals,true"));
    }

    @Test
    void parse_rejectsTheRemovedDescriptionField() {
      assertThrows(IllegalArgumentException.class, () -> parse("description,contains,x"));
    }

    @Test
    void parse_rejectsAFieldThatOnlyTheUserDirectoryHas() {
      // The two catalogs are independent: `email` is valid there and must not leak in here.
      assertThrows(IllegalArgumentException.class, () -> parse("email,contains,jo"));
    }

    @Test
    void parse_splitsBetweenRangeOnTheDoubleDot() {
      FilterCriterion<LayerDirectoryFilterField> criterion =
          parse("createdAt,between,2026-01-01..2026-03-01");

      assertEquals(LocalDate.of(2026, 1, 1), criterion.dateValue());
      assertEquals(LocalDate.of(2026, 3, 1), criterion.dateValueTo());
    }

    @Test
    void parse_keepsCommasInsideTheValue() {
      assertEquals("alpha, bravo", parse("name,equals,alpha, bravo").value());
    }

    @Test
    void parseAll_rejectsMoreThanTheCriteriaCap() {
      String[] tooMany =
          IntStream.rangeClosed(0, FilterCriterion.MAX_CRITERIA)
              .mapToObj(i -> "name,contains,v" + i)
              .toArray(String[]::new);

      assertThrows(
          IllegalArgumentException.class,
          () -> FilterCriterion.parseAll(tooMany, LayerDirectoryFilterField::fromWire));
    }
  }
}
