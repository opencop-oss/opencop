// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.nominatim.business.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.integration.geocoding.PlaceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NominatimPlaceTypePolicyTest {

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "country, COUNTRY",
    "state, REGION",
    "province, REGION",
    "region, REGION",
    "county, COUNTY",
    "district, COUNTY",
    "city, LOCALITY",
    "town, LOCALITY",
    "village, LOCALITY",
    "hamlet, LOCALITY",
    "municipality, LOCALITY",
    "city_district, NEIGHBOURHOOD",
    "suburb, NEIGHBOURHOOD",
    "quarter, NEIGHBOURHOOD",
    "neighbourhood, NEIGHBOURHOOD",
    "postcode, POSTALCODE",
    "road, STREET",
    "house_number, ADDRESS",
    "building, VENUE",
    "amenity, VENUE",
    "shop, VENUE",
    "tourism, VENUE",
    "leisure, VENUE",
    "historic, VENUE",
    "railway, VENUE",
    "aeroway, VENUE",
    // Observed in recorded responses and deliberately left unmapped.
    "locality, OTHER",
    "park, OTHER",
    "highway, OTHER",
    "healthcare, OTHER",
    "office, OTHER",
    "residential, OTHER",
    "river, OTHER",
    "junction, OTHER"
  })
  void mapsTheAddressType(String addressType, PlaceType expected) {
    assertThat(NominatimPlaceTypePolicy.placeType(addressType, "boundary")).isEqualTo(expected);
  }

  @Test
  void ignoresCase() {
    assertThat(NominatimPlaceTypePolicy.placeType("City", null)).isEqualTo(PlaceType.LOCALITY);
  }

  @Test
  void fallsBackToTheCategoryWhenTheAddressTypeIsMissing() {
    assertThat(NominatimPlaceTypePolicy.placeType(null, "amenity")).isEqualTo(PlaceType.VENUE);
    assertThat(NominatimPlaceTypePolicy.placeType(" ", "amenity")).isEqualTo(PlaceType.VENUE);
  }

  @Test
  void keepsAPresentAddressTypeEvenWhenItIsUnmapped() {
    assertThat(NominatimPlaceTypePolicy.placeType("park", "leisure")).isEqualTo(PlaceType.OTHER);
  }

  @Test
  void returnsOtherWhenNeitherValueIsPresent() {
    assertThat(NominatimPlaceTypePolicy.placeType(null, null)).isEqualTo(PlaceType.OTHER);
  }
}
