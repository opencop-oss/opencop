// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.nominatim.business.policy;

import static java.util.Map.entry;

import com.opencop.core.sdk.business.model.integration.geocoding.PlaceType;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a Nominatim result's {@code addresstype}, or its {@code category} when the address type is
 * missing, to a {@link PlaceType}.
 *
 * <p>Checked against recorded responses: administrative districts can come back as {@code city},
 * townships as {@code town}, subdistricts as {@code suburb} and communities as {@code quarter}.
 * Named buildings in results are landmarks rather than street addresses, so {@code building} is a
 * venue.
 */
public final class NominatimPlaceTypePolicy {

  private static final Map<String, PlaceType> PLACE_TYPES =
      Map.ofEntries(
          entry("country", PlaceType.COUNTRY),
          entry("state", PlaceType.REGION),
          entry("province", PlaceType.REGION),
          entry("region", PlaceType.REGION),
          entry("county", PlaceType.COUNTY),
          entry("district", PlaceType.COUNTY),
          entry("city", PlaceType.LOCALITY),
          entry("town", PlaceType.LOCALITY),
          entry("village", PlaceType.LOCALITY),
          entry("hamlet", PlaceType.LOCALITY),
          entry("municipality", PlaceType.LOCALITY),
          entry("city_district", PlaceType.NEIGHBOURHOOD),
          entry("suburb", PlaceType.NEIGHBOURHOOD),
          entry("quarter", PlaceType.NEIGHBOURHOOD),
          entry("neighbourhood", PlaceType.NEIGHBOURHOOD),
          entry("postcode", PlaceType.POSTALCODE),
          entry("road", PlaceType.STREET),
          entry("house_number", PlaceType.ADDRESS),
          entry("building", PlaceType.VENUE),
          entry("amenity", PlaceType.VENUE),
          entry("shop", PlaceType.VENUE),
          entry("tourism", PlaceType.VENUE),
          entry("leisure", PlaceType.VENUE),
          entry("historic", PlaceType.VENUE),
          entry("railway", PlaceType.VENUE),
          entry("aeroway", PlaceType.VENUE));

  private NominatimPlaceTypePolicy() {}

  public static PlaceType placeType(String addressType, String category) {
    var value = isPresent(addressType) ? addressType : category;
    if (!isPresent(value)) {
      return PlaceType.OTHER;
    }
    return PLACE_TYPES.getOrDefault(value.strip().toLowerCase(Locale.ROOT), PlaceType.OTHER);
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }
}
