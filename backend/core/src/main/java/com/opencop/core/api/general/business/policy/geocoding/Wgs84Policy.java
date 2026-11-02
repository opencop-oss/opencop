// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.geocoding;

/** Decides whether decimal degrees are a valid WGS84 coordinate. */
public final class Wgs84Policy {

  private static final double MAX_LATITUDE = 90;
  private static final double MAX_LONGITUDE = 180;

  private Wgs84Policy() {}

  /** Whether {@code degrees} is a finite latitude from -90 to 90. */
  public static boolean isLatitude(double degrees) {
    return Double.isFinite(degrees) && Math.abs(degrees) <= MAX_LATITUDE;
  }

  /** Whether {@code degrees} is a finite longitude from -180 to 180. */
  public static boolean isLongitude(double degrees) {
    return Double.isFinite(degrees) && Math.abs(degrees) <= MAX_LONGITUDE;
  }
}
