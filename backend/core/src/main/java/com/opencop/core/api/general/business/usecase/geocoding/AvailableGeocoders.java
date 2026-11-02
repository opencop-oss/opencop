// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.geocoding;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.sdk.business.registry.integration.geocoding.GeocoderRegistry;
import com.opencop.core.sdk.ports.integration.geocoding.GeocoderCapability;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The geocoders clients may use: registered, which the deployment configuration decides, and owned
 * by an active integration, which administrators decide at runtime. Discovery and every operation
 * resolve geocoders here so they agree on what is available.
 */
final class AvailableGeocoders {

  private final GeocoderRegistry registry;
  private final IntegrationCatalogGateway catalogGateway;

  AvailableGeocoders(GeocoderRegistry registry, IntegrationCatalogGateway catalogGateway) {
    this.registry = registry;
    this.catalogGateway = catalogGateway;
  }

  /**
   * Whether a registered geocoder's integration is active. Callers resolve the geocoder from the
   * registry first, so an inactive integration is indistinguishable from an unknown one.
   */
  boolean isAvailable(GeocoderCapability geocoder) {
    return isActive(catalogGateway.findIntegration(geocoder.integration().id()));
  }

  /** Returns every available geocoder in registration order, reading each integration once. */
  List<GeocoderCapability> all() {
    var activeByIntegration = new HashMap<UUID, Boolean>();
    return registry.all().stream()
        .filter(
            geocoder ->
                activeByIntegration.computeIfAbsent(
                    geocoder.integration().id(),
                    integrationId -> isActive(catalogGateway.findIntegration(integrationId))))
        .toList();
  }

  private static boolean isActive(Optional<IntegrationIdentity> integration) {
    return integration.map(IntegrationIdentity::active).orElse(false);
  }
}
