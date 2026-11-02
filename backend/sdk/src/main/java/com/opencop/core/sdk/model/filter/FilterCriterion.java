// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.filter;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * One per-column constraint on a filtered read, e.g. "email contains jo".
 *
 * <p>Wire format is a single {@code filter} query param holding {@code field,operator,value} — the
 * same comma-delimited shape Spring Data uses for {@code sort=name,asc}. Stacking is expressed by
 * repeating the param; every criterion ANDs, including two criteria naming the same field.
 *
 * <pre>
 *   ?filter=type,equals,GROUP&amp;filter=email,contains,jo
 *   ?filter=admin,isTrue                                  (no-value operators may omit the value)
 *   ?filter=createdAt,between,2026-01-01..2026-03-01      (range endpoints joined by "..")
 * </pre>
 *
 * <p>The value is split off with a limit of 3, so it keeps any commas of its own ({@code
 * name,equals,Doe, John} filters for the literal {@code "Doe, John"}). Range endpoints use {@code
 * ..} rather than a fourth comma so that limit-3 split stays unambiguous.
 *
 * <p>Values are validated and parsed eagerly at construction, so a malformed date or enum is a 400
 * at the request boundary rather than a failure deep in query translation. The typed accessors
 * ({@link #enumValue()}, {@link #dateValue()}, {@link #booleanValue()}) are therefore total for any
 * criterion that exists.
 *
 * <p>The grammar and its limits are shared by every filtered endpoint; what differs is {@code F},
 * the enum naming that endpoint's filterable columns. Keeping one parser means the two halves of
 * the wire contract — this class and the web client's serializer — cannot drift per-endpoint.
 */
public record FilterCriterion<F extends Enum<F> & FilterField>(
    F field, FilterOperator operator, String value, String valueTo) {

  /** Mirrored by hand in the web client as {@code MAX_FILTER_VALUE_LENGTH}. */
  public static final int MAX_VALUE_LENGTH = 100;

  /** Bounds worst-case query cost; far above any realistic filter bar. */
  public static final int MAX_CRITERIA = 10;

  /**
   * Cap on the field/operator tokens, which are echoed back in the rejection message, so an
   * unrecognised one cannot carry an arbitrary payload into the 400 body and the WARN log.
   */
  private static final int MAX_TOKEN_LENGTH = 64;

  private static final String RANGE_SEPARATOR = "..";

  public FilterCriterion {
    if (field == null || operator == null) {
      throw new IllegalArgumentException("Filter field and operator are required");
    }
    if (!field.supports(operator)) {
      throw new IllegalArgumentException(
          "Filter operator '"
              + operator.wireName()
              + "' is not supported for field '"
              + field.wireName()
              + "'");
    }

    value = normalizeValue(field, operator, value);
    valueTo = normalizeValueTo(field, operator, valueTo);

    validateValue(field, value);
    validateDateBounds(field, operator, value, valueTo);
  }

  /**
   * Rejects date bounds the predicate builder could not translate, so they surface as a 400 here
   * rather than deeper in query construction.
   *
   * <p>Two cases. A reversed {@code between} range would otherwise build contradictory predicates
   * and return an ordinary empty page, which reads to the caller as "no matches" rather than "your
   * filter is backwards". And the builder advances the exclusive upper bound with {@code
   * plusDays(1)} — at {@link LocalDate#MAX} that throws, past the endpoint's parse-time catch, so
   * an in-range-looking date would surface as a 500.
   */
  private static void validateDateBounds(
      FilterField field, FilterOperator operator, String value, String valueTo) {
    if (!(field.valueType() instanceof FilterValueType.Date)) {
      return;
    }
    switch (operator) {
      case AFTER -> requireNextDayRepresentable(field, parseDate(field, value));
      case BETWEEN -> {
        LocalDate from = parseDate(field, value);
        LocalDate to = parseDate(field, valueTo);
        if (from.isAfter(to)) {
          throw new IllegalArgumentException(
              "Filter range for field '" + field.wireName() + "' starts after it ends");
        }
        requireNextDayRepresentable(field, to);
      }
      default -> {
        // before, and the non-range operators, compare against the bound directly.
      }
    }
  }

  private static void requireNextDayRepresentable(FilterField field, LocalDate day) {
    try {
      day.plusDays(1);
    } catch (DateTimeException e) {
      throw new IllegalArgumentException(
          "Filter value for field '" + field.wireName() + "' is out of range", e);
    }
  }

  /**
   * Trims a field/operator token for echoing back. Control characters are dropped because the
   * message reaches a WARN log line, where an embedded newline would let a caller forge or split
   * entries. Truncates rather than rejects: the token is unknown either way.
   */
  private static String clampToken(String token) {
    String stripped = token.strip().replaceAll("\\p{Cntrl}", "");
    return stripped.length() > MAX_TOKEN_LENGTH
        ? stripped.substring(0, MAX_TOKEN_LENGTH)
        : stripped;
  }

  private static String normalizeValue(FilterField field, FilterOperator operator, String value) {
    if (!operator.requiresValue()) {
      return null;
    }
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(
          "Filter operator '"
              + operator.wireName()
              + "' on field '"
              + field.wireName()
              + "' requires a value");
    }
    return requireWithinLength(field, value.strip());
  }

  private static String normalizeValueTo(
      FilterField field, FilterOperator operator, String valueTo) {
    if (operator != FilterOperator.BETWEEN) {
      return null;
    }
    if (valueTo == null || valueTo.isBlank()) {
      throw new IllegalArgumentException(
          "Filter operator 'between' on field '"
              + field.wireName()
              + "' requires both range endpoints");
    }
    return requireWithinLength(field, valueTo.strip());
  }

  /** Shared so both range endpoints obey the cap, not just the first. */
  private static String requireWithinLength(FilterField field, String stripped) {
    if (stripped.length() > MAX_VALUE_LENGTH) {
      throw new IllegalArgumentException(
          "Filter value for field '"
              + field.wireName()
              + "' exceeds "
              + MAX_VALUE_LENGTH
              + " characters");
    }
    return stripped;
  }

  /** Rejects values the query layer could not translate — bad enum constants, unparseable dates. */
  private static void validateValue(FilterField field, String value) {
    if (value == null) {
      return;
    }
    switch (field.valueType()) {
      case FilterValueType.Enumerated enumerated -> resolveEnum(field, enumerated, value);
      case FilterValueType.Date() -> parseDate(field, value);
      case FilterValueType.Bool() -> parseBoolean(field, value);
      case FilterValueType.Text() -> {
        // Any non-blank string is a legal operand; LIKE metacharacters are escaped at query time.
      }
    }
  }

  private static Object resolveEnum(
      FilterField field, FilterValueType.Enumerated enumerated, String value) {
    try {
      return enumerated.resolver().resolve(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid value for filter field '" + field.wireName() + "'", e);
    }
  }

  private static LocalDate parseDate(FilterField field, String value) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
          "Filter field '" + field.wireName() + "' expects an ISO date (yyyy-MM-dd)", e);
    }
  }

  private static boolean parseBoolean(FilterField field, String value) {
    // Boolean.parseBoolean maps everything that isn't "true" to false, which would silently accept
    // garbage as a filter for "not an administrator". Match the two legal tokens explicitly.
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw new IllegalArgumentException(
        "Filter field '" + field.wireName() + "' expects true or false");
  }

  /**
   * The operand for a closed-domain field: an enum constant or the normalized token, depending on
   * how the field declared itself. {@code Object} because that is exactly what {@code
   * CriteriaBuilder.equal} takes.
   *
   * @throws IllegalStateException if the field is not closed-domain — unreachable, since the
   *     predicate builder only calls this from the matching branch
   */
  public Object enumValue() {
    if (!(field.valueType() instanceof FilterValueType.Enumerated enumerated)) {
      throw new IllegalStateException(
          "Filter field '" + field.wireName() + "' has no closed value domain");
    }
    return resolveEnum(field, enumerated, value);
  }

  public LocalDate dateValue() {
    return parseDate(field, value);
  }

  public LocalDate dateValueTo() {
    return parseDate(field, valueTo);
  }

  /**
   * The boolean operand: the parsed value for {@code equals}, or the operator's own polarity for
   * {@code isTrue}/{@code isFalse}. Lets the web client model Role/Status as enum columns (so the
   * chip reads "Role: Equals Admin") without the backend needing a second code path.
   */
  public boolean booleanValue() {
    return switch (operator) {
      case IS_TRUE -> true;
      case IS_FALSE -> false;
      default -> parseBoolean(field, value);
    };
  }

  /**
   * Parses one {@code field,operator,value} token against the given endpoint's column catalog.
   * Splits with a limit of 3 so the value keeps its own commas, and splits a {@code between} value
   * on its first {@code ..} into the two endpoints.
   *
   * @throws IllegalArgumentException if the token is malformed or names an unknown field/operator
   */
  public static <F extends Enum<F> & FilterField> FilterCriterion<F> parse(
      String raw, FilterFieldCatalog<F> catalog) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Filter must be 'field,operator,value'");
    }
    String[] tokens = raw.split(",", 3);
    if (tokens.length < 2) {
      throw new IllegalArgumentException("Filter must be 'field,operator,value'");
    }

    F field = catalog.fromWire(clampToken(tokens[0]));
    FilterOperator operator = FilterOperator.fromWire(clampToken(tokens[1]));
    // A no-value operator may be sent as two tokens or as three with the value slot left empty;
    // the web serializer emits the uniform three-token form.
    String rawValue = tokens.length == 3 ? tokens[2] : null;

    if (operator == FilterOperator.BETWEEN) {
      if (rawValue == null || !rawValue.contains(RANGE_SEPARATOR)) {
        throw new IllegalArgumentException(
            "Filter operator 'between' on field '" + field.wireName() + "' expects 'from..to'");
      }
      int separator = rawValue.indexOf(RANGE_SEPARATOR);
      return new FilterCriterion<>(
          field,
          operator,
          rawValue.substring(0, separator),
          rawValue.substring(separator + RANGE_SEPARATOR.length()));
    }

    return new FilterCriterion<>(field, operator, rawValue, null);
  }

  /**
   * Parses every repeated {@code filter} param. Null or empty input yields an empty list, so an
   * unfiltered read needs no special-casing at the call site.
   *
   * @throws IllegalArgumentException if any token is invalid or more than {@link #MAX_CRITERIA} are
   *     supplied
   */
  public static <F extends Enum<F> & FilterField> List<FilterCriterion<F>> parseAll(
      String[] raw, FilterFieldCatalog<F> catalog) {
    if (raw == null || raw.length == 0) {
      return List.of();
    }
    if (raw.length > MAX_CRITERIA) {
      throw new IllegalArgumentException("At most " + MAX_CRITERIA + " filters may be applied");
    }
    return Arrays.stream(raw).map(token -> parse(token, catalog)).toList();
  }
}
