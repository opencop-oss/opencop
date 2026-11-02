// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.service;

import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_FILTER_LIMIT_EXCEEDED;
import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_INVALID_FILTER;
import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_INVALID_POLYGON;
import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_LAYER_LIMIT_EXCEEDED;
import static com.opencop.core.sdk.model.watchbox.WatchboxErrorCode.WATCHBOX_LAYER_REQUIRED;

import com.opencop.core.api.watchbox.business.model.WatchboxSettings;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxLayerAccess;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxFilterTesting;
import com.opencop.core.sdk.model.watchbox.WatchboxConfigurationLimits;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterEvaluator;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterMatch;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterOperation;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterPredicate;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterTestRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;
import org.springframework.http.HttpStatus;

public class WatchboxValidationService implements WatchboxFilterTesting {

  private static final Pattern ATTRIBUTE_PATH =
      Pattern.compile("properties\\.opencop\\.attributes\\.[A-Za-z0-9_-]{1,64}");
  private static final Set<String> EXACT_ALLOWED_PATHS =
      Set.of(
          "properties.opencop.kind",
          "properties.opencop.displayName",
          "properties.opencop.element.symbol");
  private static final double MIN_LONGITUDE = -180.0;
  private static final double MAX_LONGITUDE = 180.0;
  private static final double MIN_LATITUDE = -90.0;
  private static final double MAX_LATITUDE = 90.0;

  private final GeometryFactory geometryFactory = new GeometryFactory();
  private final WatchboxSettings settings;
  private final WatchboxLayerAccess layerAccess;

  public WatchboxValidationService(WatchboxSettings settings, WatchboxLayerAccess layerAccess) {
    this.settings = settings;
    this.layerAccess = layerAccess;
  }

  public Polygon validateAndNormalizePolygon(Polygon polygon) {
    if (polygon == null) {
      throw invalidPolygon("polygon is required");
    }
    if (polygon.getInteriorRings() != null && !polygon.getInteriorRings().isEmpty()) {
      throw invalidPolygon("polygon holes are not supported");
    }

    List<LngLatAlt> ring = polygon.getExteriorRing();
    if (ring == null || ring.size() < 4) {
      throw invalidPolygon("polygon exterior ring must be closed with at least three vertices");
    }
    if (ring.size() > settings.maxPolygonVertices()) {
      throw invalidPolygon("polygon has too many vertices");
    }

    var normalized = new ArrayList<LngLatAlt>();
    var unique = new HashSet<String>();
    LngLatAlt previous = null;
    for (LngLatAlt point : ring) {
      if (point == null
          || !Double.isFinite(point.getLongitude())
          || !Double.isFinite(point.getLatitude())) {
        throw invalidPolygon("polygon coordinates must be finite");
      }
      double lon = point.getLongitude();
      double lat = point.getLatitude();
      if (lon < MIN_LONGITUDE || lon > MAX_LONGITUDE || lat < MIN_LATITUDE || lat > MAX_LATITUDE) {
        throw invalidPolygon("polygon coordinates are out of range");
      }
      // MVP guardrail: reject antimeridian-crossing rings. A single ring whose adjacent
      // vertices jump more than 180 degrees in longitude is ambiguous to planar JTS -- it
      // reads as the giant complementary polygon spanning the rest of the globe -- so we
      // reject rather than risk misleading ENTER/LEAVE alerts. This is intended to be
      // relaxed once the runtime point-in-polygon evaluator can handle crossings via
      // unwrapped (or split-and-OR'd) geometry under a single transition state.
      if (previous != null && Math.abs(previous.getLongitude() - lon) > 180.0) {
        throw invalidPolygon("polygon crosses the antimeridian");
      }
      normalized.add(new LngLatAlt(lon, lat));
      unique.add(lon + "," + lat);
      previous = point;
    }

    if (!samePosition(normalized.getFirst(), normalized.getLast())) {
      throw invalidPolygon("polygon exterior ring must be closed");
    }
    if (unique.size() < 3) {
      throw invalidPolygon("polygon must contain at least three unique vertices");
    }

    Coordinate[] coordinates =
        normalized.stream()
            .map(p -> new Coordinate(p.getLongitude(), p.getLatitude()))
            .toArray(Coordinate[]::new);
    var linearRing = geometryFactory.createLinearRing(coordinates);
    var jtsPolygon = geometryFactory.createPolygon(linearRing);
    var validity = new IsValidOp(jtsPolygon);
    if (!validity.isValid()) {
      throw invalidPolygon(validity.getValidationError());
    }
    if (jtsPolygon.isEmpty() || jtsPolygon.getArea() <= 0.0) {
      throw invalidPolygon("polygon is invalid");
    }

    return new Polygon(normalized);
  }

  public WatchboxFilter validateFilter(WatchboxFilter filter) {
    WatchboxFilter effective = filter == null ? WatchboxFilter.empty() : filter;
    List<WatchboxFilterPredicate> predicates = effective.predicates();
    if (predicates.size() > WatchboxConfigurationLimits.MAX_FILTER_PREDICATES) {
      throw new WatchboxException(
          WATCHBOX_FILTER_LIMIT_EXCEEDED,
          "Watchbox filter must not exceed "
              + WatchboxConfigurationLimits.MAX_FILTER_PREDICATES
              + " predicates",
          HttpStatus.BAD_REQUEST,
          List.of("filter.predicates"));
    }
    if (!predicates.isEmpty() && effective.combine() == null) {
      throw invalidFilter("filter combine is required", "filter.combine");
    }
    var normalized = new ArrayList<WatchboxFilterPredicate>();
    for (int i = 0; i < predicates.size(); i++) {
      WatchboxFilterPredicate predicate = predicates.get(i);
      String fieldPrefix = "filter.predicates[" + i + "]";
      if (predicate == null) {
        throw invalidFilter("filter predicate is required", fieldPrefix);
      }
      validateFilterPath(predicate.path(), fieldPrefix + ".path");
      WatchboxFilterOperation operation = predicate.operation();
      if (operation == null) {
        throw invalidFilter("filter operation is required", fieldPrefix + ".operation");
      }
      List<String> values =
          predicate.values() == null ? List.of() : predicate.values().stream().toList();
      validateFilterValues(operation, values, fieldPrefix + ".values");
      validateFilterPosition(operation, predicate, fieldPrefix);
      normalized.add(
          new WatchboxFilterPredicate(
              predicate.path(),
              operation,
              List.copyOf(values),
              predicate.negate(),
              predicate.offset(),
              predicate.length(),
              predicate.caseInsensitive()));
    }
    return new WatchboxFilter(effective.combine(), normalized);
  }

  @Override
  public WatchboxFilterMatch test(WatchboxFilterTestRequest request) {
    return WatchboxFilterEvaluator.evaluate(validateFilter(request.filter()), request.feature());
  }

  public Set<UUID> validateLayerIds(Set<UUID> layerIds) {
    if (layerIds == null
        || layerIds.isEmpty()
        || layerIds.stream().anyMatch(java.util.Objects::isNull)) {
      throw new WatchboxException(
          WATCHBOX_LAYER_REQUIRED,
          "At least one layer is required",
          HttpStatus.BAD_REQUEST,
          List.of("layerIds"));
    }
    if (layerIds.size() > WatchboxConfigurationLimits.MAX_LAYER_IDS) {
      throw new WatchboxException(
          WATCHBOX_LAYER_LIMIT_EXCEEDED,
          "Watchbox must not reference more than "
              + WatchboxConfigurationLimits.MAX_LAYER_IDS
              + " layers",
          HttpStatus.BAD_REQUEST,
          List.of("layerIds"));
    }
    return Set.copyOf(layerIds);
  }

  public void validateActiveLayers(Set<UUID> layerIds) {
    for (UUID layerId : validateLayerIds(layerIds)) {
      switch (layerAccess.activeLayerState(layerId)) {
        case ACTIVE -> {}
        case MISSING_OR_ARCHIVED ->
            throw new WatchboxException(
                WATCHBOX_LAYER_REQUIRED,
                "Selected layer does not exist",
                HttpStatus.BAD_REQUEST,
                List.of("layerIds"));
      }
    }
  }

  private static void validateFilterPath(String path, String field) {
    if (path == null
        || path.isBlank()
        || (!EXACT_ALLOWED_PATHS.contains(path) && !ATTRIBUTE_PATH.matcher(path).matches())) {
      throw invalidFilter("filter path is not supported", field);
    }
  }

  private static void validateFilterValues(
      WatchboxFilterOperation operation, List<String> values, String field) {
    switch (operation) {
      case EXISTS -> {
        if (!values.isEmpty()) {
          throw invalidFilter("EXISTS filter does not accept values", field);
        }
      }
      case EQUALS -> {
        if (values.size() != 1) {
          throw invalidFilter("EQUALS filter requires exactly one value", field);
        }
      }
      case IN, SUBSTRING_IN -> {
        if (values.isEmpty() || values.size() > 50) {
          throw invalidFilter("filter operation requires 1 to 50 values", field);
        }
      }
    }
    for (String value : values) {
      if (value == null || value.isBlank() || value.length() > 128) {
        throw invalidFilter("filter value is invalid", field);
      }
    }
  }

  private static void validateFilterPosition(
      WatchboxFilterOperation operation, WatchboxFilterPredicate predicate, String fieldPrefix) {
    if (operation != WatchboxFilterOperation.SUBSTRING_IN) {
      if (predicate.offset() != null) {
        throw invalidFilter(
            "filter offset is only valid for SUBSTRING_IN", fieldPrefix + ".offset");
      }
      if (predicate.length() != null) {
        throw invalidFilter(
            "filter length is only valid for SUBSTRING_IN", fieldPrefix + ".length");
      }
      return;
    }

    if (predicate.offset() == null || predicate.offset() < 0) {
      throw invalidFilter("SUBSTRING_IN requires a non-negative offset", fieldPrefix + ".offset");
    }
    if (predicate.length() == null || predicate.length() < 1) {
      throw invalidFilter("SUBSTRING_IN requires a positive length", fieldPrefix + ".length");
    }
    long end = (long) predicate.offset() + predicate.length();
    if (end > 128) {
      throw invalidFilter("SUBSTRING_IN window is too large", fieldPrefix + ".length");
    }
  }

  private static WatchboxException invalidPolygon(String message) {
    return new WatchboxException(
        WATCHBOX_INVALID_POLYGON, message, HttpStatus.BAD_REQUEST, List.of("polygon"));
  }

  /** Self-intersection gets a specific message; every other validity failure stays generic. */
  private static WatchboxException invalidPolygon(TopologyValidationError error) {
    boolean selfIntersects =
        error.getErrorType() == TopologyValidationError.SELF_INTERSECTION
            || error.getErrorType() == TopologyValidationError.RING_SELF_INTERSECTION;
    if (!selfIntersects) {
      return invalidPolygon("polygon is invalid");
    }
    var at = error.getCoordinate();
    return invalidPolygon(
        String.format(
            Locale.ROOT, "polygon crosses itself near longitude %.5f, latitude %.5f", at.x, at.y));
  }

  private static WatchboxException invalidFilter(String message, String field) {
    return new WatchboxException(
        WATCHBOX_INVALID_FILTER, message, HttpStatus.BAD_REQUEST, List.of(field));
  }

  private static boolean samePosition(LngLatAlt left, LngLatAlt right) {
    return Double.compare(left.getLongitude(), right.getLongitude()) == 0
        && Double.compare(left.getLatitude(), right.getLatitude()) == 0;
  }
}
