// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.geocoding;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.stream.Stream;

/** Reads the languages an {@code Accept-Language} header prefers, most preferred first. */
final class AcceptLanguageHeader {

  private static final String RANGE_SEPARATOR = ",";
  private static final String WILDCARD_RANGE = "*";

  private AcceptLanguageHeader() {}

  /**
   * Returns the header's languages ordered by quality, or an empty list when the header is missing.
   *
   * <p>Only the languages as written are kept: the JDK also offers registered equivalents (such as
   * {@code de-DD} for {@code de-DE}), which no geocoder needs.
   *
   * <p>Each comma-separated range is parsed on its own, so a malformed range drops only itself
   * rather than the whole header: browsers and proxies set this header without the user's
   * involvement. Wildcards and zero-quality ranges name no language the user wants, so they are
   * dropped too.
   */
  static List<Locale> preferredLanguages(String header) {
    if (header == null) {
      return List.of();
    }
    return Arrays.stream(header.split(RANGE_SEPARATOR))
        .flatMap(AcceptLanguageHeader::parseRange)
        .filter(AcceptLanguageHeader::namesALanguage)
        .sorted(Comparator.comparingDouble(LanguageRange::getWeight).reversed())
        .map(range -> Locale.forLanguageTag(range.getRange()))
        .toList();
  }

  private static Stream<LanguageRange> parseRange(String range) {
    if (range.isBlank()) {
      return Stream.empty();
    }
    try {
      return LanguageRange.parse(range).stream().limit(1);
    } catch (IllegalArgumentException _) {
      return Stream.empty();
    }
  }

  private static boolean namesALanguage(LanguageRange range) {
    return range.getWeight() > LanguageRange.MIN_WEIGHT
        && !range.getRange().contains(WILDCARD_RANGE);
  }
}
