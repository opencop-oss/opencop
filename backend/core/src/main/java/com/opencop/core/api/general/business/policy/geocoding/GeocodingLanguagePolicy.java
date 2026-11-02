// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.geocoding;

import com.opencop.core.sdk.helper.LocaleSupport;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Decides which language geocoding results are labelled in. */
public final class GeocodingLanguagePolicy {

  private GeocodingLanguagePolicy() {}

  /**
   * Resolves the result language: the requested {@code lang}, else the first usable language the
   * caller's client prefers, else {@code defaultLanguage}.
   *
   * <p>Returns empty only when {@code lang} is given but is not a well-formed language tag: an
   * explicit choice the caller got wrong is refused, while unusable client preferences are skipped.
   */
  public static Optional<Locale> resolve(
      String lang, List<Locale> preferredLanguages, Locale defaultLanguage) {
    if (lang != null) {
      return LocaleSupport.parseLanguageTag(lang);
    }
    return Optional.of(
        preferredLanguages.stream()
            .filter(GeocodingLanguagePolicy::isUsable)
            .findFirst()
            .orElse(defaultLanguage));
  }

  private static boolean isUsable(Locale language) {
    return LocaleSupport.isSupported(language.toLanguageTag());
  }
}
