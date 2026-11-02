// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.geocoding;

/**
 * Finds places by name through the geocoder the caller selected. Open to any authenticated user.
 */
public interface SearchGeocoder {

  /**
   * @throws GeocodingRequestException when the request is invalid, the geocoder is unknown or its
   *     integration inactive, or its provider cannot answer
   */
  GeocodingSearchResult search(SearchGeocoderCommand command);
}
