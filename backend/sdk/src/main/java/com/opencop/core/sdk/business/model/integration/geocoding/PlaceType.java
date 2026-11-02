// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.geocoding;

/**
 * The administrative or feature level of a geocoded place, following the Who's On First / Pelias
 * layer vocabulary. Clients treat a value they do not know as {@link #OTHER}, so finer levels can
 * be added without breaking them.
 */
public enum PlaceType {
  COUNTRY,
  REGION,
  COUNTY,
  LOCALITY,
  NEIGHBOURHOOD,
  POSTALCODE,
  STREET,
  ADDRESS,
  VENUE,
  OTHER
}
