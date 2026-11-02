// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.helper;

import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Optional;

public final class LocaleSupport {

  private LocaleSupport() {}

  public static boolean isSupported(String rawTag) {
    if (rawTag == null || rawTag.isBlank()) {
      return false;
    }

    Locale locale = Locale.forLanguageTag(rawTag.trim());

    return !locale.getLanguage().isBlank();
  }

  /**
   * Parses a well-formed BCP 47 tag that names a language. Unlike {@link Locale#forLanguageTag},
   * malformed trailing syntax is refused rather than silently discarded.
   */
  public static Optional<Locale> parseLanguageTag(String rawTag) {
    if (rawTag == null || rawTag.isBlank()) {
      return Optional.empty();
    }
    try {
      Locale locale = new Locale.Builder().setLanguageTag(rawTag.strip()).build();
      return locale.getLanguage().isEmpty() ? Optional.empty() : Optional.of(locale);
    } catch (IllformedLocaleException exception) {
      return Optional.empty();
    }
  }
}
