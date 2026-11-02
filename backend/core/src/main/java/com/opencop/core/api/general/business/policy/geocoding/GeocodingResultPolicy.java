// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.geocoding;

import com.opencop.core.api.general.business.model.geocoding.DefaultedField;
import com.opencop.core.api.general.business.model.geocoding.PlaceDefect;
import com.opencop.core.api.general.business.model.geocoding.ScreenedAnswer;
import com.opencop.core.sdk.business.model.integration.geocoding.BoundingBox;
import com.opencop.core.sdk.business.model.integration.geocoding.GeoPoint;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodedPlace;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingAnswer;
import com.opencop.core.sdk.business.model.integration.geocoding.PlaceType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Decides which places a geocoder returned are fit to send to clients. */
public final class GeocodingResultPolicy {

  private GeocodingResultPolicy() {}

  /**
   * Reduces an answer to the places clients may receive, or returns empty when the answer itself is
   * unusable: missing, or without a places list.
   *
   * <p>Plugins leave a field empty rather than fail a whole answer when one provider result is
   * unreadable, so a single malformed place is dropped and reported instead. The usable places are
   * then de-duplicated by id, keeping the first since provider order is meaningful, and truncated
   * to {@code limit}. Dropping comes first so a malformed place never displaces a valid one with
   * the same id.
   *
   * <p>A null place type, category or bounding box is a plugin bug, but each has a meaningful
   * default, so the place is kept with {@link PlaceType#OTHER} or an empty value instead. Only the
   * returned places report their defaults.
   *
   * <p>Attribution is a licence obligation with no safe default, so a place without an attribution
   * list, or with a blank credit in it, is dropped. An empty list is valid, since each plugin
   * decides what its provider requires. The answer's attribution is built from the returned places
   * only, so it never credits a dropped or truncated place.
   */
  public static Optional<ScreenedAnswer> screen(GeocodingAnswer answer, int limit) {
    if (answer == null || answer.places() == null) {
      return Optional.empty();
    }
    var places = new ArrayList<GeocodedPlace>();
    var defects = new ArrayList<PlaceDefect>();
    var defaults = new ArrayList<DefaultedField>();
    var ids = new HashSet<String>();
    for (var place : answer.places()) {
      var defect = defect(place);
      if (defect.isPresent()) {
        defects.add(defect.get());
      } else if (ids.add(place.id()) && places.size() < limit) {
        places.add(withDefaults(place, defaults));
      }
    }
    return Optional.of(
        new ScreenedAnswer(
            attributionOf(places),
            List.copyOf(places),
            List.copyOf(defects),
            List.copyOf(defaults)));
  }

  private static Optional<PlaceDefect> defect(GeocodedPlace place) {
    if (place == null) {
      return Optional.of(PlaceDefect.MISSING_PLACE);
    }
    if (isBlank(place.id())) {
      return Optional.of(PlaceDefect.BLANK_ID);
    }
    if (isBlank(place.name())) {
      return Optional.of(PlaceDefect.BLANK_NAME);
    }
    if (isBlank(place.label())) {
      return Optional.of(PlaceDefect.BLANK_LABEL);
    }
    if (!isValidPosition(place.position())) {
      return Optional.of(PlaceDefect.INVALID_POSITION);
    }
    if (!hasValidBbox(place)) {
      return Optional.of(PlaceDefect.INVALID_BBOX);
    }
    if (!hasValidAttribution(place)) {
      return Optional.of(PlaceDefect.INVALID_ATTRIBUTION);
    }
    return Optional.empty();
  }

  private static List<String> attributionOf(List<GeocodedPlace> places) {
    var credits = new LinkedHashSet<String>();
    places.forEach(place -> credits.addAll(place.attribution()));
    return List.copyOf(credits);
  }

  private static boolean hasValidAttribution(GeocodedPlace place) {
    return place.attribution() != null
        && place.attribution().stream().noneMatch(GeocodingResultPolicy::isBlank);
  }

  /**
   * Fills null optional fields with their defaults, and snapshots the attribution so a plugin
   * changing its list after screening cannot alter what clients receive.
   */
  private static GeocodedPlace withDefaults(GeocodedPlace place, List<DefaultedField> defaults) {
    if (place.placeType() == null) {
      defaults.add(DefaultedField.PLACE_TYPE);
    }
    if (place.category() == null) {
      defaults.add(DefaultedField.CATEGORY);
    }
    if (place.bbox() == null) {
      defaults.add(DefaultedField.BBOX);
    }
    return new GeocodedPlace(
        place.id(),
        place.name(),
        place.label(),
        Objects.requireNonNullElse(place.placeType(), PlaceType.OTHER),
        Objects.requireNonNullElse(place.category(), Optional.empty()),
        place.position(),
        Objects.requireNonNullElse(place.bbox(), Optional.empty()),
        List.copyOf(place.attribution()));
  }

  private static boolean isValidPosition(GeoPoint position) {
    return position != null
        && Wgs84Policy.isLatitude(position.lat())
        && Wgs84Policy.isLongitude(position.lon());
  }

  /** A place may have no bounding box, but one it has must be well formed. */
  private static boolean hasValidBbox(GeocodedPlace place) {
    return place.bbox() == null
        || place.bbox().map(GeocodingResultPolicy::isValidBbox).orElse(true);
  }

  /** Longitudes are not ordered: {@code minLon > maxLon} is a box that crosses the antimeridian. */
  private static boolean isValidBbox(BoundingBox bbox) {
    return Wgs84Policy.isLatitude(bbox.minLat())
        && Wgs84Policy.isLatitude(bbox.maxLat())
        && Wgs84Policy.isLongitude(bbox.minLon())
        && Wgs84Policy.isLongitude(bbox.maxLon())
        && bbox.minLat() <= bbox.maxLat();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
