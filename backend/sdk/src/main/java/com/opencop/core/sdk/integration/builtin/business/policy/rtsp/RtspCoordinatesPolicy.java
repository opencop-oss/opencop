// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.policy.rtsp;

import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;
import java.util.Optional;
import org.geojson.GeoJsonObject;
import org.geojson.LngLatAlt;
import org.geojson.Point;

/**
 * Where an RTSP camera stands: a point given by both a latitude and a longitude, or nowhere when
 * neither is given. One without the other places the camera nowhere sensible, so it is rejected.
 */
public final class RtspCoordinatesPolicy {

  /** The declared fields an RTSP stream reads its camera position from. */
  public static final String LATITUDE_FIELD = "latitude";

  public static final String LONGITUDE_FIELD = "longitude";

  public static final String INCOMPLETE_COORDINATES = "INCOMPLETE_RTSP_COORDINATES";
  public static final String INVALID_COORDINATE = "INVALID_RTSP_COORDINATE";

  private static final double MAX_LATITUDE = 90;
  private static final double MAX_LONGITUDE = 180;

  private RtspCoordinatesPolicy() {}

  /** The camera's position, or empty when the request placed it nowhere. */
  public static Optional<GeoJsonObject> cameraPosition(String latitude, String longitude)
      throws ConnectionInputException {
    var hasLatitude = isSupplied(latitude);
    var hasLongitude = isSupplied(longitude);
    if (!hasLatitude && !hasLongitude) {
      return Optional.empty();
    }
    if (!hasLatitude || !hasLongitude) {
      throw rejection(
          hasLatitude ? LONGITUDE_FIELD : LATITUDE_FIELD,
          INCOMPLETE_COORDINATES,
          "Supply both a latitude and a longitude, or neither");
    }
    var lat = coordinate(latitude, LATITUDE_FIELD, MAX_LATITUDE);
    var lng = coordinate(longitude, LONGITUDE_FIELD, MAX_LONGITUDE);
    return Optional.of(new Point(new LngLatAlt(lng, lat)));
  }

  private static boolean isSupplied(String value) {
    return value != null && !value.isBlank();
  }

  private static double coordinate(String supplied, String field, double bound)
      throws ConnectionInputException {
    try {
      var value = Double.parseDouble(supplied.strip());
      if (Double.isFinite(value) && Math.abs(value) <= bound) {
        return value;
      }
    } catch (NumberFormatException _) {
      // Reported below with the range the value had to fall in.
    }
    throw rejection(
        field,
        INVALID_COORDINATE,
        capitalized(field) + " must be a number between " + -bound + " and " + bound);
  }

  private static String capitalized(String field) {
    return Character.toUpperCase(field.charAt(0)) + field.substring(1);
  }

  private static ConnectionInputException rejection(
      String field, String detailCode, String message) {
    return ConnectionInputException.rejecting(
        field, ConnectionInputFailure.INVALID_INPUT, detailCode, message);
  }
}
