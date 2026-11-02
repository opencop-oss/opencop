// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.geocoding;

import java.util.List;

/**
 * Lists the geocoders this deployment offers and the operations each supports. Open to any
 * authenticated user.
 */
public interface ListGeocoders {

  /**
   * Returns the available geocoders, ordered by integration label, then geocoder label, with ties
   * broken by geocoder id so the order never depends on registration.
   */
  List<GeocoderSummary> list();
}
