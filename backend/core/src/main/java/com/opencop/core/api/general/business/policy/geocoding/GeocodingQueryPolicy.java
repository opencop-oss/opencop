// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.geocoding;

import com.opencop.core.sdk.business.model.integration.geocoding.GeoPoint;
import java.util.Optional;
import java.util.OptionalInt;

/** Decides whether a caller's geocoding request values are acceptable. */
public final class GeocodingQueryPolicy {

  private static final int MIN_LIMIT = 1;

  private GeocodingQueryPolicy() {}

  /**
   * Returns the trimmed search text when it is non-blank and no longer than {@code maxTextLength}
   * characters, or empty when it is missing, blank or too long.
   *
   * <p>Counts characters, not UTF-16 units, so text outside the Basic Multilingual Plane is not
   * held to a shorter limit.
   */
  public static Optional<String> searchText(String text, int maxTextLength) {
    if (text == null) {
      return Optional.empty();
    }
    var trimmed = text.strip();
    if (trimmed.isEmpty() || characterCount(trimmed) > maxTextLength) {
      return Optional.empty();
    }
    return Optional.of(trimmed);
  }

  /**
   * Returns the requested number of places when {@code limit} is a whole number from 1 to {@code
   * maxLimit}, or empty otherwise. Defaulting an omitted limit is the caller's concern.
   */
  public static OptionalInt limit(String limit, int maxLimit) {
    try {
      var value = Integer.parseInt(limit.strip());
      return value >= MIN_LIMIT && value <= maxLimit ? OptionalInt.of(value) : OptionalInt.empty();
    } catch (NumberFormatException _) {
      return OptionalInt.empty();
    }
  }

  /**
   * Returns the focus point when both coordinates are given, finite and within WGS84 ranges, or
   * empty otherwise. A focus point is all or nothing: one coordinate alone is refused. Treating a
   * request with neither as unfocused is the caller's concern.
   */
  public static Optional<GeoPoint> focus(String lat, String lon) {
    var latitude = coordinate(lat).filter(Wgs84Policy::isLatitude);
    var longitude = coordinate(lon).filter(Wgs84Policy::isLongitude);
    if (latitude.isEmpty() || longitude.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new GeoPoint(latitude.get(), longitude.get()));
  }

  private static Optional<Double> coordinate(String value) {
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(Double.parseDouble(value.strip()));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }

  private static int characterCount(String text) {
    return text.codePointCount(0, text.length());
  }
}
