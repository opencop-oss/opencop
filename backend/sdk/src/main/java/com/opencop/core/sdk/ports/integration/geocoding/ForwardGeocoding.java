// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.geocoding;

import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingAnswer;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingSearchQuery;

/** A geocoder that finds places by name. */
public interface ForwardGeocoding extends GeocoderCapability {

  /**
   * Finds places matching the query, in the provider's ranking order.
   *
   * @throws GeocodingException when the provider cannot answer
   */
  GeocodingAnswer search(GeocodingSearchQuery query) throws GeocodingException;
}
