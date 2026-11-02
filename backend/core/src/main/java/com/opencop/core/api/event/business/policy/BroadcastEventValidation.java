// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import static com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes.MISSING_INFORMATION;
import static com.opencop.core.api.event.ports.business.usecase.EventExceptionCodes.STALE_EVENT;

import com.opencop.core.api.event.ports.business.usecase.OpencopEventException;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import java.time.Instant;
import java.util.UUID;
import org.geojson.GeoJsonObject;
import org.geojson.GeometryCollection;
import org.geojson.LineString;
import org.geojson.MultiLineString;
import org.geojson.MultiPoint;
import org.geojson.MultiPolygon;
import org.geojson.Point;
import org.geojson.Polygon;
import org.springframework.http.HttpStatus;

public final class BroadcastEventValidation {

  private BroadcastEventValidation() {}

  public static void assertGeometryValid(GeoJsonObject geometry) {
    if (geometry == null) {
      throw new GlobalOpenCopException(
          "Specified event geometry is missing", HttpStatus.BAD_REQUEST);
    }

    if (geometry instanceof Point
        || geometry instanceof LineString
        || geometry instanceof Polygon
        || geometry instanceof MultiPoint
        || geometry instanceof MultiLineString
        || geometry instanceof MultiPolygon) {
      return;
    }

    if (geometry instanceof GeometryCollection collection) {
      if (collection.getGeometries() == null || collection.getGeometries().isEmpty()) {
        throw new GlobalOpenCopException(
            "Specified event geometry is invalid", HttpStatus.BAD_REQUEST);
      }
      collection.getGeometries().forEach(BroadcastEventValidation::assertGeometryValid);
      return;
    }

    throw new GlobalOpenCopException("Specified event geometry is invalid", HttpStatus.BAD_REQUEST);
  }

  public static void assertSourceMatching(
      UUID expectedSource, String pathLabel, OpenCopProperties properties) {
    if (!expectedSource.equals(properties.getSource())) {
      throw new GlobalOpenCopException(
          "Specified " + pathLabel + " does not match the event source specified",
          HttpStatus.BAD_REQUEST);
    }
  }

  public static void assertTimestampzValid(
      OpenCopProperties properties, boolean allowMissingStart) {
    if (properties.getTimestampz() == null) {
      throw new OpencopEventException(MISSING_INFORMATION, HttpStatus.BAD_REQUEST);
    }

    if (properties.getTimestampz().getStale() <= 0) {
      throw new OpencopEventException(MISSING_INFORMATION, HttpStatus.BAD_REQUEST);
    }

    if (properties.getTimestampz().getStale() <= Instant.now().toEpochMilli()) {
      throw new OpencopEventException(STALE_EVENT, HttpStatus.BAD_REQUEST);
    }

    if (!allowMissingStart && properties.getTimestampz().getStart() <= 0) {
      throw new OpencopEventException(MISSING_INFORMATION, HttpStatus.BAD_REQUEST);
    }
  }
}
