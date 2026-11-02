// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.geojson.Point;

final class CoordinateTextParser {

  private static final Pattern ALLOWED_SEPARATOR = Pattern.compile("[\\s,;]+");

  private static final Pattern DMS_COORDINATE =
      Pattern.compile(
          "(\\d{1,3})\\s*(?:°|d)?\\s*(\\d{1,2})\\s*(?:'|m)?\\s*(\\d{1,2}(?:\\.\\d+)?)\\s*(?:\"|s)?\\s*([NSEW])",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern DM_COORDINATE =
      Pattern.compile(
          "(\\d{1,3})\\s*(?:°|d)?\\s*(\\d{1,2}(?:\\.\\d+)?)\\s*(?:'|m)?\\s*([NSEW])",
          Pattern.CASE_INSENSITIVE);

  private static final String GEOHASH_ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz";

  private CoordinateTextParser() {}

  static Point parseDmsPoint(String raw) throws MapperEngineException {
    return parseTwoCoordinates(raw, DMS_COORDINATE, true, "dms");
  }

  static Point parseDmPoint(String raw) throws MapperEngineException {
    return parseTwoCoordinates(raw, DM_COORDINATE, false, "dm");
  }

  static Point parseGeohashPoint(String raw) throws MapperEngineException {
    String geohash = raw.trim().toLowerCase(Locale.ROOT);
    if (geohash.isBlank()) {
      return null;
    }

    double minLat = -90d;
    double maxLat = 90d;
    double minLon = -180d;
    double maxLon = 180d;
    boolean evenBit = true;

    for (int i = 0; i < geohash.length(); i++) {
      int value = GEOHASH_ALPHABET.indexOf(geohash.charAt(i));
      if (value < 0) {
        throw new MapperEngineException("TRANSFORM_ERROR", "Invalid geohash text " + raw, null);
      }

      for (int mask = 16; mask != 0; mask >>= 1) {
        if (evenBit) {
          double mid = (minLon + maxLon) / 2d;
          if ((value & mask) != 0) {
            minLon = mid;
          } else {
            maxLon = mid;
          }
        } else {
          double mid = (minLat + maxLat) / 2d;
          if ((value & mask) != 0) {
            minLat = mid;
          } else {
            maxLat = mid;
          }
        }
        evenBit = !evenBit;
      }
    }

    return new Point((minLon + maxLon) / 2d, (minLat + maxLat) / 2d);
  }

  private static Point parseTwoCoordinates(
      String raw, Pattern pattern, boolean expectSeconds, String label)
      throws MapperEngineException {
    String text = raw.trim().toUpperCase(Locale.ROOT);
    if (text.isBlank()) {
      return null;
    }

    Matcher matcher = pattern.matcher(text);
    double latitude = Double.NaN;
    double longitude = Double.NaN;
    int matches = 0;
    boolean latitudeSeen = false;
    boolean longitudeSeen = false;
    int previousEnd = 0;

    while (matcher.find()) {
      if (!isAllowedSeparator(text.substring(previousEnd, matcher.start()))) {
        throw new MapperEngineException(
            "TRANSFORM_ERROR", "Invalid %s text %s".formatted(label, raw), null);
      }

      matches++;
      double degrees = Double.parseDouble(matcher.group(1));
      double minutes = Double.parseDouble(matcher.group(2));
      double seconds = expectSeconds ? Double.parseDouble(matcher.group(3)) : 0d;
      char hemisphere = matcher.group(expectSeconds ? 4 : 3).charAt(0);

      validateCoordinateComponent(degrees, minutes, seconds, hemisphere, label, raw);

      double decimal = degrees + (minutes / 60d) + (seconds / 3600d);
      if (hemisphere == 'S' || hemisphere == 'W') {
        decimal = -decimal;
      }

      if (hemisphere == 'N' || hemisphere == 'S') {
        if (latitudeSeen) {
          throw new MapperEngineException(
              "TRANSFORM_ERROR", "Invalid %s text %s".formatted(label, raw), null);
        }
        latitude = decimal;
        latitudeSeen = true;
      } else {
        if (longitudeSeen) {
          throw new MapperEngineException(
              "TRANSFORM_ERROR", "Invalid %s text %s".formatted(label, raw), null);
        }
        longitude = decimal;
        longitudeSeen = true;
      }

      previousEnd = matcher.end();
    }

    if (matches < 2 || Double.isNaN(latitude) || Double.isNaN(longitude)) {
      throw new MapperEngineException(
          "TRANSFORM_ERROR", "Invalid %s text %s".formatted(label, raw), null);
    }
    if (!isAllowedSeparator(text.substring(previousEnd))) {
      throw new MapperEngineException(
          "TRANSFORM_ERROR", "Invalid %s text %s".formatted(label, raw), null);
    }

    return new Point(longitude, latitude);
  }

  private static boolean isAllowedSeparator(String text) {
    return text.isEmpty() || ALLOWED_SEPARATOR.matcher(text).matches();
  }

  private static void validateCoordinateComponent(
      double degrees, double minutes, double seconds, char hemisphere, String label, String raw)
      throws MapperEngineException {
    if (minutes < 0d || minutes >= 60d || seconds < 0d || seconds >= 60d) {
      throw new MapperEngineException(
          "TRANSFORM_ERROR", "Invalid %s text %s".formatted(label, raw), null);
    }

    double decimal = degrees + (minutes / 60d) + (seconds / 3600d);
    double max = (hemisphere == 'N' || hemisphere == 'S') ? 90d : 180d;
    if (decimal > max) {
      throw new MapperEngineException(
          "TRANSFORM_ERROR", "Invalid %s text %s".formatted(label, raw), null);
    }
  }
}
