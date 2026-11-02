// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.geocoding;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A validated forward-geocoding request, built by the host.
 *
 * @param text the trimmed, non-blank search text
 * @param limit the maximum number of places to return
 * @param focus a position to rank nearby places higher, without excluding distant ones
 * @param language the language results should be labelled in
 */
public record GeocodingSearchQuery(
    String text, int limit, Optional<GeoPoint> focus, Locale language) {

  public GeocodingSearchQuery {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(focus, "focus");
    Objects.requireNonNull(language, "language");
  }
}
