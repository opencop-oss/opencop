// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.geocoding;

import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.geocoding.GeocoderSummary;
import com.opencop.core.api.general.ports.usecase.geocoding.ListGeocoders;
import com.opencop.core.sdk.business.registry.integration.geocoding.GeocoderRegistry;
import com.opencop.core.sdk.ports.integration.geocoding.GeocoderCapability;
import java.util.Comparator;
import java.util.List;

public class ListGeocodersUsecase implements ListGeocoders {

  private static final Comparator<GeocoderSummary> DISCOVERY_ORDER =
      Comparator.comparing(GeocoderSummary::integrationLabel)
          .thenComparing(GeocoderSummary::label)
          .thenComparing(GeocoderSummary::geocoderId);

  private final GeocoderRegistry registry;
  private final AvailableGeocoders availableGeocoders;

  public ListGeocodersUsecase(GeocoderRegistry registry, IntegrationCatalogGateway catalogGateway) {
    this.registry = registry;
    this.availableGeocoders = new AvailableGeocoders(registry, catalogGateway);
  }

  @Override
  public List<GeocoderSummary> list() {
    return availableGeocoders.all().stream().map(this::summary).sorted(DISCOVERY_ORDER).toList();
  }

  private GeocoderSummary summary(GeocoderCapability geocoder) {
    var descriptor = geocoder.descriptor();
    return new GeocoderSummary(
        geocoder.integration().id(),
        geocoder.integration().name(),
        geocoder.id(),
        descriptor.label(),
        descriptor.description(),
        registry.operationsOf(geocoder).stream().sorted().toList());
  }
}
