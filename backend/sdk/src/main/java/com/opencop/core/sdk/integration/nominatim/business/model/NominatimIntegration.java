// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.nominatim.business.model;

import com.opencop.core.sdk.business.model.integration.Integration;
import java.util.UUID;

/** The Nominatim integration: OpenStreetMap geocoding against a self-hosted Nominatim. */
public record NominatimIntegration() implements Integration {

  public static final UUID NOMINATIM_ID = UUID.fromString("00a1f521-8dd4-4270-9f9d-404004e84df3");

  @Override
  public UUID id() {
    return NOMINATIM_ID;
  }

  @Override
  public String name() {
    return "Nominatim";
  }

  @Override
  public String description() {
    return "OpenStreetMap geocoding through a Nominatim instance the deploying organization hosts.";
  }
}
