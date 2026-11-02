// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.registry.integration.geocoding;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocoderDescriptor;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingOperation;
import com.opencop.core.sdk.ports.integration.geocoding.ForwardGeocoding;
import com.opencop.core.sdk.ports.integration.geocoding.GeocoderCapability;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable registry of the geocoders integrations contribute, addressed by the pair (integration
 * id, geocoder id).
 *
 * <p>An empty registry is valid: a deployment may enable no geocoder.
 */
public final class GeocoderRegistry {

  private final List<GeocoderCapability> geocoders;
  private final Map<UUID, GeocoderCapability> geocodersById;

  public GeocoderRegistry(List<GeocoderCapability> geocoders, List<Integration> integrations) {
    Objects.requireNonNull(geocoders, "geocoders");
    Objects.requireNonNull(integrations, "integrations");

    var registeredIntegrations = new HashSet<UUID>();
    for (var integration : integrations) {
      registeredIntegrations.add(Objects.requireNonNull(integration, "integration").id());
    }

    var indexedById = new HashMap<UUID, GeocoderCapability>();
    for (var geocoder : geocoders) {
      Objects.requireNonNull(geocoder, "geocoder");
      var id = Objects.requireNonNull(geocoder.id(), "geocoder.id");
      var integration = Objects.requireNonNull(geocoder.integration(), "geocoder.integration");
      if (!registeredIntegrations.contains(integration.id())) {
        throw new IllegalArgumentException(
            "Unregistered integration " + integration.id() + " for geocoder '" + id + "'");
      }
      if (indexedById.putIfAbsent(id, geocoder) != null) {
        throw new IllegalArgumentException("Duplicate geocoder identifier '" + id + "'");
      }
      if (operations(geocoder).isEmpty()) {
        throw new IllegalArgumentException(
            "Geocoder '" + id + "' implements no geocoding operation");
      }
      requireOwnDescriptor(geocoder, id);
    }
    this.geocoders = List.copyOf(geocoders);
    this.geocodersById = Map.copyOf(indexedById);
  }

  /** Returns every registered geocoder in registration order. */
  public List<GeocoderCapability> all() {
    return geocoders;
  }

  /**
   * Resolves a geocoder by its pair. A geocoder id registered under another integration resolves to
   * nothing: the caller must name both, and the registry never infers one from the other.
   */
  public Optional<GeocoderCapability> find(UUID integrationId, UUID geocoderId) {
    Objects.requireNonNull(integrationId, "integrationId");
    Objects.requireNonNull(geocoderId, "geocoderId");
    return Optional.ofNullable(geocodersById.get(geocoderId))
        .filter(geocoder -> geocoder.integration().id().equals(integrationId));
  }

  /** Resolves a geocoder by its pair when it supports forward geocoding. */
  public Optional<ForwardGeocoding> findForwardGeocoding(UUID integrationId, UUID geocoderId) {
    return find(integrationId, geocoderId)
        .filter(ForwardGeocoding.class::isInstance)
        .map(ForwardGeocoding.class::cast);
  }

  /** Returns the operations a geocoder supports, derived from the interfaces it implements. */
  public Set<GeocodingOperation> operationsOf(GeocoderCapability geocoder) {
    return operations(Objects.requireNonNull(geocoder, "geocoder"));
  }

  private static Set<GeocodingOperation> operations(GeocoderCapability geocoder) {
    var operations = EnumSet.noneOf(GeocodingOperation.class);
    if (geocoder instanceof ForwardGeocoding) {
      operations.add(GeocodingOperation.SEARCH);
    }
    return Set.copyOf(operations);
  }

  /**
   * Resolves the descriptor at construction so a missing or mismatched declaration aborts startup
   * instead of surfacing in discovery.
   */
  private static void requireOwnDescriptor(GeocoderCapability geocoder, UUID id) {
    GeocoderDescriptor descriptor = geocoder.descriptor();
    if (descriptor == null) {
      throw new IllegalArgumentException("Missing descriptor for geocoder '" + id + "'");
    }
    if (!descriptor.id().equals(id)) {
      throw new IllegalArgumentException(
          "Geocoder '" + id + "' declares a descriptor for '" + descriptor.id() + "'");
    }
  }
}
