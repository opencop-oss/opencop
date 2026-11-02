// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.model;

import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import org.springframework.http.HttpStatus;

public record BoundingBox(double minLon, double minLat, double maxLon, double maxLat) {

  private static final double MIN_LAT = -90.0d;
  private static final double MAX_LAT = 90.0d;
  private static final double MIN_LON = -180.0d;
  private static final double MAX_LON = 180.0d;

  public static BoundingBox parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    String[] parts = raw.split(",");
    if (parts.length != 4) {
      throw new GlobalOpenCopException(
          "'bbox' must be formatted as minLon,minLat,maxLon,maxLat", HttpStatus.BAD_REQUEST);
    }

    try {
      double minLon = normalizeLongitude(Double.parseDouble(parts[0].trim()));
      double minLat = Double.parseDouble(parts[1].trim());
      double maxLon = normalizeLongitude(Double.parseDouble(parts[2].trim()));
      double maxLat = Double.parseDouble(parts[3].trim());
      validate(minLon, minLat, maxLon, maxLat);
      return new BoundingBox(minLon, minLat, maxLon, maxLat);
    } catch (NumberFormatException e) {
      throw new GlobalOpenCopException(
          "'bbox' must contain valid numeric coordinates", HttpStatus.BAD_REQUEST);
    }
  }

  private static void validate(double minLon, double minLat, double maxLon, double maxLat) {
    if (!Double.isFinite(minLon)
        || !Double.isFinite(minLat)
        || !Double.isFinite(maxLon)
        || !Double.isFinite(maxLat)) {
      throw new GlobalOpenCopException(
          "'bbox' must contain finite numeric coordinates", HttpStatus.BAD_REQUEST);
    }
    if (minLat < MIN_LAT || minLat > MAX_LAT || maxLat < MIN_LAT || maxLat > MAX_LAT) {
      throw new GlobalOpenCopException(
          "'bbox' latitude values must be between -90 and 90", HttpStatus.BAD_REQUEST);
    }
    if (minLat > maxLat) {
      throw new GlobalOpenCopException(
          "'bbox' min values must be less than or equal to max values", HttpStatus.BAD_REQUEST);
    }
  }

  public boolean crossesAntimeridian() {
    return minLon > maxLon;
  }

  private static double normalizeLongitude(double lon) {
    if (!Double.isFinite(lon)) {
      return lon;
    }
    double normalized = ((lon + 180.0d) % 360.0d + 360.0d) % 360.0d - 180.0d;
    if (normalized == MIN_LON && lon > 0.0d) {
      return MAX_LON;
    }
    return normalized;
  }
}
