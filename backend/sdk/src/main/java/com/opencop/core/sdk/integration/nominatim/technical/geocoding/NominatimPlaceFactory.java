// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.nominatim.technical.geocoding;

import com.opencop.core.sdk.business.model.integration.geocoding.BoundingBox;
import com.opencop.core.sdk.business.model.integration.geocoding.GeoPoint;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodedPlace;
import com.opencop.core.sdk.integration.nominatim.business.policy.NominatimPlaceTypePolicy;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps a Nominatim result to a provider-neutral place.
 *
 * <p>Leaves a field it cannot read empty rather than failing the whole answer: the host validates
 * every place and drops the ones it cannot use.
 */
final class NominatimPlaceFactory {

  /** Required by the ODbL and the OpenStreetMap Foundation's geocoding guideline. */
  private static final List<String> ATTRIBUTION = List.of("© OpenStreetMap contributors");

  private static final int BOUNDING_BOX_VALUES = 4;
  private static final String POSTCODE_ADDRESS_TYPE = "postcode";
  private static final String POSTCODE_ID_PREFIX = "P:";
  private static final String POSTCODE_ID_SEPARATOR = ":";

  private NominatimPlaceFactory() {}

  static GeocodedPlace create(NominatimPlace place) {
    return new GeocodedPlace(
        id(place),
        name(place),
        place.displayName(),
        NominatimPlaceTypePolicy.placeType(place.addresstype(), place.category()),
        Optional.empty(),
        position(place),
        bbox(place.boundingbox()),
        ATTRIBUTION);
  }

  /**
   * The OpenStreetMap reference, such as {@code R2988933}. Never Nominatim's {@code place_id},
   * which changes when the instance re-imports.
   *
   * <p>A postcode Nominatim computes from addresses has no OpenStreetMap reference, so it is
   * identified by its country and postcode instead, such as {@code P:cn:100010}; the prefix keeps
   * it apart from OpenStreetMap references. Any other result without a reference gets a blank id.
   */
  private static String id(NominatimPlace place) {
    if (place.osmType() == null || place.osmType().isEmpty() || place.osmId() == null) {
      return postcodeId(place);
    }
    return place.osmType().substring(0, 1).toUpperCase(Locale.ROOT) + place.osmId();
  }

  private static String postcodeId(NominatimPlace place) {
    var address = place.address();
    if (!POSTCODE_ADDRESS_TYPE.equals(place.addresstype())
        || address == null
        || isBlank(address.countryCode())
        || isBlank(address.postcode())) {
      return "";
    }
    return POSTCODE_ID_PREFIX
        + address.countryCode().strip().toLowerCase(Locale.ROOT)
        + POSTCODE_ID_SEPARATOR
        + address.postcode().strip();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String name(NominatimPlace place) {
    if (place.name() != null && !place.name().isBlank()) {
      return place.name();
    }
    if (place.displayName() == null) {
      return null;
    }
    return place.displayName().split(",", 2)[0].strip();
  }

  private static GeoPoint position(NominatimPlace place) {
    var lat = parse(place.lat());
    var lon = parse(place.lon());
    if (lat.isEmpty() || lon.isEmpty()) {
      return null;
    }
    return new GeoPoint(lat.get(), lon.get());
  }

  /** Reorders Nominatim's {@code [minLat, maxLat, minLon, maxLon]}. */
  private static Optional<BoundingBox> bbox(List<String> values) {
    if (values == null || values.size() != BOUNDING_BOX_VALUES) {
      return Optional.empty();
    }
    var minLat = parse(values.get(0));
    var maxLat = parse(values.get(1));
    var minLon = parse(values.get(2));
    var maxLon = parse(values.get(3));
    if (minLat.isEmpty() || maxLat.isEmpty() || minLon.isEmpty() || maxLon.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new BoundingBox(minLat.get(), minLon.get(), maxLat.get(), maxLon.get()));
  }

  private static Optional<Double> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(Double.parseDouble(value));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }
}
