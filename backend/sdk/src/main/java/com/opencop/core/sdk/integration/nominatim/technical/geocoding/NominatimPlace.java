// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.nominatim.technical.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** One result of Nominatim's {@code /search?format=jsonv2}, limited to the fields we map. */
@JsonIgnoreProperties(ignoreUnknown = true)
record NominatimPlace(
    @JsonProperty("osm_type") String osmType,
    @JsonProperty("osm_id") Long osmId,
    String lat,
    String lon,
    String category,
    String addresstype,
    String name,
    @JsonProperty("display_name") String displayName,
    List<String> boundingbox,
    Address address) {

  /** The address breakdown, requested only for the country and postcode of a computed postcode. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Address(@JsonProperty("country_code") String countryCode, String postcode) {}
}
