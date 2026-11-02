// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.registry.integration.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocoderDescriptor;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingOperation;
import com.opencop.core.sdk.ports.integration.geocoding.ForwardGeocoding;
import com.opencop.core.sdk.ports.integration.geocoding.GeocoderCapability;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeocoderRegistryTest {

  private static final Integration FIRST = integration("11111111-1111-1111-1111-111111111111");
  private static final Integration SECOND = integration("22222222-2222-2222-2222-222222222222");
  private static final UUID GEOCODER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID GEOCODER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

  @Test
  void acceptsNoGeocoders() {
    var registry = new GeocoderRegistry(List.of(), List.of());

    assertThat(registry.all()).isEmpty();
    assertThat(registry.find(FIRST.id(), GEOCODER_A)).isEmpty();
    assertThat(registry.findForwardGeocoding(FIRST.id(), GEOCODER_A)).isEmpty();
  }

  @Test
  void findsAGeocoderByItsIntegrationAndIdentifier() {
    var first = forward(FIRST, GEOCODER_A);
    var second = forward(SECOND, GEOCODER_B);
    var registry = new GeocoderRegistry(List.of(first, second), List.of(FIRST, SECOND));

    assertThat(registry.find(SECOND.id(), GEOCODER_B)).contains(second);
    assertThat(registry.findForwardGeocoding(FIRST.id(), GEOCODER_A)).contains(first);
  }

  @Test
  void findsNothingForAGeocoderPairedWithAnotherIntegration() {
    var registry =
        new GeocoderRegistry(List.of(forward(FIRST, GEOCODER_A)), List.of(FIRST, SECOND));

    assertThat(registry.find(SECOND.id(), GEOCODER_A)).isEmpty();
    assertThat(registry.findForwardGeocoding(SECOND.id(), GEOCODER_A)).isEmpty();
  }

  @Test
  void listsEveryGeocoderInRegistrationOrder() {
    var first = forward(FIRST, GEOCODER_A);
    var second = forward(SECOND, GEOCODER_B);
    var registry = new GeocoderRegistry(List.of(second, first), List.of(FIRST, SECOND));

    assertThat(registry.all()).containsExactly(second, first);
  }

  @Test
  void reportsTheOperationsAGeocoderImplements() {
    var geocoder = forward(FIRST, GEOCODER_A);
    var registry = new GeocoderRegistry(List.of(geocoder), List.of(FIRST));

    assertThat(registry.operationsOf(geocoder)).containsExactly(GeocodingOperation.SEARCH);
  }

  @Test
  void rejectsADuplicateGeocoderIdentifierAcrossIntegrations() {
    var geocoders =
        List.<GeocoderCapability>of(forward(FIRST, GEOCODER_A), forward(SECOND, GEOCODER_A));

    assertThatThrownBy(() -> new GeocoderRegistry(geocoders, List.of(FIRST, SECOND)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate geocoder identifier")
        .hasMessageContaining(GEOCODER_A.toString());
  }

  @Test
  void rejectsAGeocoderWhoseIntegrationIsNotRegistered() {
    var geocoders = List.<GeocoderCapability>of(forward(SECOND, GEOCODER_A));

    assertThatThrownBy(() -> new GeocoderRegistry(geocoders, List.of(FIRST)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unregistered integration")
        .hasMessageContaining(SECOND.id().toString());
  }

  @Test
  void rejectsAGeocoderImplementingNoOperation() {
    var identityOnly = mock(GeocoderCapability.class);
    when(identityOnly.integration()).thenReturn(FIRST);
    when(identityOnly.id()).thenReturn(GEOCODER_A);
    when(identityOnly.descriptor()).thenReturn(descriptor(GEOCODER_A));

    assertThatThrownBy(() -> new GeocoderRegistry(List.of(identityOnly), List.of(FIRST)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("implements no geocoding operation")
        .hasMessageContaining(GEOCODER_A.toString());
  }

  @Test
  void rejectsADescriptorDescribingAnotherGeocoder() {
    var geocoder = forward(FIRST, GEOCODER_A);
    when(geocoder.descriptor()).thenReturn(descriptor(GEOCODER_B));

    assertThatThrownBy(() -> new GeocoderRegistry(List.of(geocoder), List.of(FIRST)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("descriptor")
        .hasMessageContaining(GEOCODER_A.toString());
  }

  private static ForwardGeocoding forward(Integration integration, UUID id) {
    var geocoder = mock(ForwardGeocoding.class);
    when(geocoder.integration()).thenReturn(integration);
    when(geocoder.id()).thenReturn(id);
    when(geocoder.descriptor()).thenReturn(descriptor(id));
    return geocoder;
  }

  private static GeocoderDescriptor descriptor(UUID id) {
    return new GeocoderDescriptor(id, "Geocoder", "A test geocoder.");
  }

  private static Integration integration(String id) {
    var uuid = UUID.fromString(id);
    return new Integration() {
      @Override
      public UUID id() {
        return uuid;
      }

      @Override
      public String name() {
        return "Integration " + id;
      }

      @Override
      public String description() {
        return "A test integration.";
      }
    };
  }
}
