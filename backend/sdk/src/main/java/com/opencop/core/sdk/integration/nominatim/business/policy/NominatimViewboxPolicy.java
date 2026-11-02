// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.nominatim.business.policy;

import com.opencop.core.sdk.business.model.integration.geocoding.BoundingBox;
import com.opencop.core.sdk.business.model.integration.geocoding.GeoPoint;

/**
 * Turns a focus point into the Nominatim viewbox that biases ranking towards it.
 *
 * <p>Nominatim has no proximity parameter; an unbounded viewbox boosts places inside it without
 * excluding the rest.
 */
public final class NominatimViewboxPolicy {

  private static final double MAX_LATITUDE = 90;
  private static final double MAX_LONGITUDE = 180;

  private NominatimViewboxPolicy() {}

  /**
   * Returns the box extending {@code halfSizeDegrees} on each side of {@code focus}, clamped to
   * WGS84 ranges. Near a pole or the antimeridian the box is cut short rather than wrapped, since
   * Nominatim rejects out-of-range coordinates.
   */
  public static BoundingBox viewbox(GeoPoint focus, double halfSizeDegrees) {
    return new BoundingBox(
        clamp(focus.lat() - halfSizeDegrees, MAX_LATITUDE),
        clamp(focus.lon() - halfSizeDegrees, MAX_LONGITUDE),
        clamp(focus.lat() + halfSizeDegrees, MAX_LATITUDE),
        clamp(focus.lon() + halfSizeDegrees, MAX_LONGITUDE));
  }

  private static double clamp(double degrees, double maxMagnitude) {
    return Math.clamp(degrees, -maxMagnitude, maxMagnitude);
  }
}
