// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db;

import java.util.Locale;

/**
 * Helpers for building safe SQL {@code LIKE} patterns from user-supplied search terms. The value is
 * always bound as a JPA parameter (so this is not about SQL injection); the concern is the {@code
 * LIKE} metacharacters {@code % _ \}, which would otherwise act as wildcards when a user types
 * them.
 */
public final class LikePatterns {

  /**
   * Escape character embedded in the generated patterns. Pass it to the escape-aware {@code
   * CriteriaBuilder.like(expression, pattern, escapeChar)} overload so the escaped metacharacters
   * match literally.
   */
  public static final char ESCAPE = '\\';

  private LikePatterns() {}

  /**
   * Escapes the {@code LIKE} metacharacters ({@code \ % _}) in a user-supplied term so they match
   * literally instead of acting as wildcards. The backslash is escaped first so the {@code
   * %}/{@code _} escapes it inserts are not double-escaped.
   */
  public static String escape(String term) {
    return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  /**
   * Builds an escaped, lower-cased, trimmed prefix pattern ({@code term%}) for a case-insensitive
   * starts-with match against a {@code LOWER(column)} expression. Lower-casing uses {@link
   * Locale#ROOT} so normalization does not depend on the JVM default locale (e.g. the Turkish
   * dotless {@code I}); it must stay consistent with the database {@code LOWER(column)} collation.
   * Use with {@link #ESCAPE}.
   */
  public static String startsWith(String term) {
    return escape(term.strip().toLowerCase(Locale.ROOT)) + "%";
  }

  /**
   * Builds an escaped, lower-cased, trimmed substring pattern ({@code %term%}) for a
   * case-insensitive contains match against a {@code LOWER(column)} expression. Same normalization
   * rules as {@link #startsWith(String)}.
   *
   * <p>A leading wildcard cannot use a btree index, so this scans. Measured acceptable on both
   * admin directories at production scale; see {@code V38__user_directory_filter_indexes.sql} and
   * {@code V40__layer_directory_indexes.sql} for why a trigram index was measured and rejected in
   * each. Use with {@link #ESCAPE}.
   */
  public static String contains(String term) {
    return "%" + escape(term.strip().toLowerCase(Locale.ROOT)) + "%";
  }
}
