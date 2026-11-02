// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.filter;

import com.opencop.core.sdk.model.filter.FilterCriterion;
import com.opencop.core.sdk.model.filter.FilterValueType;
import com.opencop.core.sdk.technical.gateway.db.LikePatterns;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Translates {@link FilterCriterion}s into JPA predicates. Shared by every filtered read, so the
 * two admin grids answer "created after X" and "name contains Y" the same way.
 *
 * <p>Endpoint-specific concerns — free-text search attributes, visibility narrowing, how NULLs
 * arise in a particular projection — stay in that endpoint's {@code Specification}; only the
 * per-column criterion translation lives here.
 */
public final class FilterPredicates {

  private FilterPredicates() {}

  /**
   * Translates one per-column criterion into a predicate. The criterion's own constructor has
   * already checked that the operator is legal for the field and that the value parses, so these
   * branches are total — a fallthrough means the field/operator matrix and this switch disagree,
   * which is a programming error rather than bad input.
   *
   * @param zone resolves the day boundaries of date-only criteria; see {@link #datePredicate}
   */
  public static Predicate toPredicate(
      FilterCriterion<?> criterion, Root<?> root, CriteriaBuilder cb, ZoneId zone) {
    Path<Object> path = root.get(criterion.field().jpaAttribute());
    return switch (criterion.field().valueType()) {
      case FilterValueType.Text() -> textPredicate(criterion, path, cb);
      case FilterValueType.Enumerated _ -> enumPredicate(criterion, path, cb);
      case FilterValueType.Bool() -> booleanPredicate(criterion, path, cb);
      case FilterValueType.Date() -> datePredicate(criterion, path, cb, zone);
    };
  }

  private static Predicate textPredicate(
      FilterCriterion<?> criterion, Path<Object> path, CriteriaBuilder cb) {
    Expression<String> text = path.as(String.class);
    Expression<String> lowered = cb.lower(text);
    return switch (criterion.operator()) {
      case CONTAINS ->
          cb.like(lowered, LikePatterns.contains(criterion.value()), LikePatterns.ESCAPE);
      case NOT_CONTAINS ->
          cb.notLike(lowered, LikePatterns.contains(criterion.value()), LikePatterns.ESCAPE);
      case STARTS_WITH ->
          cb.like(lowered, LikePatterns.startsWith(criterion.value()), LikePatterns.ESCAPE);
      case EQUALS -> cb.equal(lowered, normalize(criterion.value()));
      case NOT_EQUALS -> cb.notEqual(lowered, normalize(criterion.value()));
      // The one operator where NULL must pass: where a projection leaves a column null for some
      // rows, "is empty" should match them. Every other text operator lets three-valued logic drop
      // those rows, which is what a caller filtering on a populated column expects.
      case IS_EMPTY -> cb.or(cb.isNull(path), cb.equal(cb.trim(text), ""));
      case IS_NOT_EMPTY -> cb.and(cb.isNotNull(path), cb.notEqual(cb.trim(text), ""));
      default -> throw unsupported(criterion);
    };
  }

  private static Predicate enumPredicate(
      FilterCriterion<?> criterion, Path<Object> path, CriteriaBuilder cb) {
    return switch (criterion.operator()) {
      case EQUALS -> cb.equal(path, criterion.enumValue());
      case NOT_EQUALS -> cb.notEqual(path, criterion.enumValue());
      default -> throw unsupported(criterion);
    };
  }

  /**
   * {@code booleanValue()} folds {@code isTrue}/{@code isFalse} and {@code equals} into one
   * operand, so all three share a branch. The explicit switch keeps a later addition such as {@code
   * notEquals} failing loudly rather than silently becoming an equality predicate.
   */
  private static Predicate booleanPredicate(
      FilterCriterion<?> criterion, Path<Object> path, CriteriaBuilder cb) {
    return switch (criterion.operator()) {
      case EQUALS, IS_TRUE, IS_FALSE -> cb.equal(path, criterion.booleanValue());
      default -> throw unsupported(criterion);
    };
  }

  /**
   * Day-granularity comparisons against an {@code Instant} column, with boundaries resolved in the
   * caller's zone so a filtered day matches the day the grid renders. Resolving in UTC instead
   * excludes rows whose displayed date matches the filter: at UTC+8 a row created 16:00-24:00 UTC
   * shows as the next day, so a third of rows can disagree with the filter.
   *
   * <p>{@code before} excludes the named day entirely, {@code after} starts at the following day,
   * and {@code between} includes both endpoint days.
   */
  private static Predicate datePredicate(
      FilterCriterion<?> criterion, Path<Object> path, CriteriaBuilder cb, ZoneId zone) {
    Expression<Instant> instant = path.as(Instant.class);
    return switch (criterion.operator()) {
      case BEFORE -> cb.lessThan(instant, startOfDay(criterion.dateValue(), zone));
      case AFTER ->
          cb.greaterThanOrEqualTo(instant, startOfDay(criterion.dateValue().plusDays(1), zone));
      case BETWEEN ->
          cb.and(
              cb.greaterThanOrEqualTo(instant, startOfDay(criterion.dateValue(), zone)),
              cb.lessThan(instant, startOfDay(criterion.dateValueTo().plusDays(1), zone)));
      default -> throw unsupported(criterion);
    };
  }

  /** Uses the zone's rules at that date, so DST transitions shift the boundary correctly. */
  private static Instant startOfDay(LocalDate day, ZoneId zone) {
    return day.atStartOfDay(zone).toInstant();
  }

  /** Matches the lower-casing {@link LikePatterns} applies, so equality and LIKE agree. */
  private static String normalize(String value) {
    return value.strip().toLowerCase(Locale.ROOT);
  }

  /**
   * Unreachable by construction: {@link FilterCriterion} rejects an operator its field does not
   * declare. Arriving here means the whitelist and these switches have drifted — a programming
   * error, hence a 500 rather than the 400 raised for a malformed filter.
   */
  private static IllegalStateException unsupported(FilterCriterion<?> criterion) {
    return new IllegalStateException(
        "Filter operator '"
            + criterion.operator().wireName()
            + "' is whitelisted for field '"
            + criterion.field().wireName()
            + "' but has no predicate branch");
  }
}
