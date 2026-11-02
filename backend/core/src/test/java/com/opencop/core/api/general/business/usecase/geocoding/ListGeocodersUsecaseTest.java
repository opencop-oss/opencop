// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.geocoding.GeocoderSummary;
import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocoderDescriptor;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingOperation;
import com.opencop.core.sdk.business.registry.integration.geocoding.GeocoderRegistry;
import com.opencop.core.sdk.ports.integration.geocoding.ForwardGeocoding;
import com.opencop.core.sdk.ports.integration.geocoding.GeocoderCapability;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListGeocodersUsecaseTest {

  private static final Integration ALPHA =
      integration("11111111-1111-1111-1111-111111111111", "Alpha");
  private static final Integration BRAVO =
      integration("22222222-2222-2222-2222-222222222222", "Bravo");
  private static final UUID FIRST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID SECOND_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final UUID THIRD_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

  private final IntegrationCatalogGateway catalog = mock(IntegrationCatalogGateway.class);

  @Test
  void listsAnActiveGeocoderWithTheIdsSearchAcceptsAndItsOperations() {
    var geocoder = forwardGeocoding(ALPHA, FIRST_ID, "Streets");
    setActive(ALPHA, true);

    var geocoders = usecase(geocoder).list();

    assertThat(geocoders)
        .containsExactly(
            new GeocoderSummary(
                ALPHA.id(),
                "Alpha",
                FIRST_ID,
                "Streets",
                "Finds Streets places.",
                List.of(GeocodingOperation.SEARCH)));
  }

  @Test
  void listsNothingWhenNoGeocoderIsRegistered() {
    assertThat(usecase().list()).isEmpty();
  }

  @Test
  void omitsAGeocoderWhoseIntegrationIsInactive() {
    var inactive = forwardGeocoding(ALPHA, FIRST_ID, "Streets");
    var active = forwardGeocoding(BRAVO, SECOND_ID, "Landmarks");
    setActive(ALPHA, false);
    setActive(BRAVO, true);

    var geocoders = usecase(inactive, active).list();

    assertThat(geocoders).extracting(GeocoderSummary::geocoderId).containsExactly(SECOND_ID);
  }

  @Test
  void omitsAGeocoderWhoseIntegrationIsMissingFromTheCatalog() {
    var geocoder = forwardGeocoding(ALPHA, FIRST_ID, "Streets");
    when(catalog.findIntegration(ALPHA.id())).thenReturn(Optional.empty());

    assertThat(usecase(geocoder).list()).isEmpty();
  }

  @Test
  void dropsAGeocoderOnceItsIntegrationIsDeactivatedWithoutRebuildingTheUsecase() {
    var usecase = usecase(forwardGeocoding(ALPHA, FIRST_ID, "Streets"));
    setActive(ALPHA, true);
    assertThat(usecase.list()).hasSize(1);

    setActive(ALPHA, false);

    assertThat(usecase.list()).isEmpty();
  }

  @Test
  void ordersByIntegrationLabelThenGeocoderLabel() {
    var bravoStreets = forwardGeocoding(BRAVO, FIRST_ID, "Streets");
    var alphaStreets = forwardGeocoding(ALPHA, SECOND_ID, "Streets");
    var alphaLandmarks = forwardGeocoding(ALPHA, THIRD_ID, "Landmarks");
    setActive(ALPHA, true);
    setActive(BRAVO, true);

    var geocoders = usecase(bravoStreets, alphaStreets, alphaLandmarks).list();

    assertThat(geocoders)
        .extracting(GeocoderSummary::geocoderId)
        .containsExactly(THIRD_ID, SECOND_ID, FIRST_ID);
  }

  private ListGeocodersUsecase usecase(GeocoderCapability... geocoders) {
    var registry = new GeocoderRegistry(List.of(geocoders), List.of(ALPHA, BRAVO));
    return new ListGeocodersUsecase(registry, catalog);
  }

  private void setActive(Integration integration, boolean active) {
    when(catalog.findIntegration(integration.id()))
        .thenReturn(
            Optional.of(
                new IntegrationIdentity(
                    integration.id(), integration.name(), integration.description(), active)));
  }

  private static ForwardGeocoding forwardGeocoding(Integration integration, UUID id, String label) {
    var geocoder = mock(ForwardGeocoding.class);
    when(geocoder.integration()).thenReturn(integration);
    when(geocoder.id()).thenReturn(id);
    when(geocoder.descriptor())
        .thenReturn(new GeocoderDescriptor(id, label, "Finds " + label + " places."));
    return geocoder;
  }

  private static Integration integration(String id, String name) {
    var uuid = UUID.fromString(id);
    return new Integration() {
      @Override
      public UUID id() {
        return uuid;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return "A test integration.";
      }
    };
  }
}
