// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.geocoding;

import java.util.Locale;
import java.util.Objects;

/**
 * Deployment-wide bounds and defaults for geocoding requests.
 *
 * @param defaultLanguage the language results are labelled in when a request names none
 * @param defaultLimit the number of places returned when a request names no limit
 * @param maxLimit the largest limit a request may ask for
 * @param maxTextLength the longest search text accepted, in characters
 */
public record GeocodingSettings(
    Locale defaultLanguage, int defaultLimit, int maxLimit, int maxTextLength) {

  public GeocodingSettings {
    Objects.requireNonNull(defaultLanguage, "defaultLanguage");
    if (maxLimit < 1) {
      throw new IllegalArgumentException("maxLimit must be positive");
    }
    if (defaultLimit < 1 || defaultLimit > maxLimit) {
      throw new IllegalArgumentException("defaultLimit must be between 1 and maxLimit");
    }
    if (maxTextLength < 1) {
      throw new IllegalArgumentException("maxTextLength must be positive");
    }
  }
}
