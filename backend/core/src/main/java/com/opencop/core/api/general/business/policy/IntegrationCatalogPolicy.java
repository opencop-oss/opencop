// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogConflictException;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogEntry;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogRegistration;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogSnapshot;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.business.model.integrationcatalog.UnknownTransportProtocolException;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class IntegrationCatalogPolicy {

  /**
   * Canonical text, not {@link UUID#compareTo}, whose signed-long order a client cannot reproduce.
   */
  private static final Comparator<UUID> BY_CANONICAL_TEXT = Comparator.comparing(UUID::toString);

  /**
   * Total order over a discovery response. Name is what a client renders but is unique to neither
   * an integration nor a version, so the identifier and version break ties to keep repeated
   * requests identical.
   *
   * <p>Names compare case-sensitively, so uppercase sorts ahead of lowercase. A client that wants a
   * case-insensitive listing re-sorts; the contract only promises the order is the same every time.
   */
  private static final Comparator<IntegrationCatalogEntry> CATALOG_ORDER =
      Comparator.comparing(IntegrationCatalogEntry::name)
          .thenComparing(IntegrationCatalogEntry::integrationId, BY_CANONICAL_TEXT)
          .thenComparingInt(IntegrationCatalogEntry::schemaVersion);

  private IntegrationCatalogPolicy() {}

  /**
   * Resolves the optional protocol filter carried on a discovery request.
   *
   * @param requested the raw query value; {@code null} when the caller sent no filter
   * @return the parsed protocol, or empty when the caller asked for the unfiltered catalog
   * @throws UnknownTransportProtocolException when a value was supplied but names no known
   *     protocol. A blank value counts as unknown: the caller sent the parameter, so answering as
   *     though they had not would hide the mistake.
   */
  public static Optional<TransportProtocol> parseProtocol(String requested) {
    if (requested == null) {
      return Optional.empty();
    }
    return Optional.of(
        Arrays.stream(TransportProtocol.values())
            .filter(protocol -> protocol.name().equalsIgnoreCase(requested.strip()))
            .findFirst()
            .orElseThrow(
                () -> new UnknownTransportProtocolException(requested, acceptedProtocols())));
  }

  /**
   * Reconciles what the catalog records against the connection schemas this runtime actually
   * loaded.
   *
   * <p>Deactivation and unavailability answer different questions and are not interchangeable. A
   * deactivated integration is an operator's decision recorded against the integration, so every
   * one of its versions is withheld. An unavailable version is merely absent from this runtime, so
   * it is still reported: a client may need to name a version an existing connection already binds
   * to.
   *
   * <p>Because protocols are declared by the loaded schema and never persisted, an unavailable
   * version declares nothing and therefore matches no protocol filter. That is why a filtered
   * response can only ever contain available versions.
   *
   * @param catalog every integration and connection schema version the catalog records
   * @param declaredProtocols the protocols each version loaded by this runtime declares
   * @param requestedProtocol the filter to apply, or empty for the whole catalog
   */
  public static List<IntegrationCatalogEntry> discover(
      IntegrationCatalogSnapshot catalog,
      Map<IntegrationConnectionSchemaKey, Set<TransportProtocol>> declaredProtocols,
      Optional<TransportProtocol> requestedProtocol) {
    Objects.requireNonNull(catalog, "catalog");
    Objects.requireNonNull(declaredProtocols, "declaredProtocols");
    Objects.requireNonNull(requestedProtocol, "requestedProtocol");

    var byId =
        catalog.integrations().stream()
            .collect(Collectors.toMap(IntegrationIdentity::id, Function.identity()));

    return catalog.schemas().stream()
        .map(schema -> entryFor(schema, byId.get(schema.integrationId()), declaredProtocols))
        .flatMap(Optional::stream)
        .filter(entry -> matchesProtocol(entry, declaredProtocols, requestedProtocol))
        .sorted(CATALOG_ORDER)
        .toList();
  }

  /**
   * Empty when the version cannot be offered at all: its integration is deactivated, or the
   * snapshot carries no integration for it. The snapshot guarantees the latter cannot happen, so
   * the check is defensive only, reachable if a row is ever deleted; it answers by withholding the
   * version rather than by inventing display metadata for it.
   */
  private static Optional<IntegrationCatalogEntry> entryFor(
      IntegrationConnectionSchemaKey schema,
      IntegrationIdentity integration,
      Map<IntegrationConnectionSchemaKey, Set<TransportProtocol>> declaredProtocols) {
    if (integration == null || !integration.active()) {
      return Optional.empty();
    }
    return Optional.of(
        IntegrationCatalogEntry.of(
            integration, schema.schemaVersion(), declaredProtocols.containsKey(schema)));
  }

  private static boolean matchesProtocol(
      IntegrationCatalogEntry entry,
      Map<IntegrationConnectionSchemaKey, Set<TransportProtocol>> declaredProtocols,
      Optional<TransportProtocol> requestedProtocol) {
    if (requestedProtocol.isEmpty()) {
      return true;
    }
    return declaredProtocols
        .getOrDefault(entry.key(), Set.of())
        .contains(requestedProtocol.orElseThrow());
  }

  private static String acceptedProtocols() {
    return Arrays.stream(TransportProtocol.values())
        .map(Enum::name)
        .collect(Collectors.joining(", "));
  }

  /**
   * Requires every integration and every schema version to be published once, and every schema
   * version to belong to a published integration: the catalog cannot record a version of a contract
   * whose integration it does not know.
   */
  public static void requireConsistentRegistration(IntegrationCatalogRegistration registration) {
    Objects.requireNonNull(registration, "registration");
    var integrationIds = new HashSet<UUID>();
    for (var integration : registration.integrations()) {
      if (!integrationIds.add(integration.id())) {
        throw new IntegrationCatalogConflictException(
            "Duplicate registered integration " + integration.id());
      }
    }
    var schemas = new HashSet<IntegrationConnectionSchemaKey>();
    for (var schema : registration.schemas()) {
      if (!schemas.add(schema)) {
        throw new IntegrationCatalogConflictException(
            "Duplicate registered connection schema " + schema);
      }
      if (!integrationIds.contains(schema.integrationId())) {
        throw new IntegrationCatalogConflictException(
            "Connection schema " + schema + " belongs to an integration no plugin publishes");
      }
    }
  }

  /**
   * Returns the catalog integration with the published display metadata when that metadata changed.
   * Activation is the catalog's, never the plugin's: an operator sets it.
   */
  public static Optional<IntegrationIdentity> displayMetadataRefresh(
      IntegrationIdentity registered, IntegrationIdentity catalog) {
    Objects.requireNonNull(registered, "registered");
    Objects.requireNonNull(catalog, "catalog");
    if (!registered.id().equals(catalog.id())) {
      throw new IllegalArgumentException("A refresh must be the same integration");
    }
    if (sameDisplayMetadata(registered, catalog)) {
      return Optional.empty();
    }
    return Optional.of(catalog.withDisplayMetadata(registered.name(), registered.description()));
  }

  private static boolean sameDisplayMetadata(
      IntegrationIdentity registered, IntegrationIdentity catalog) {
    return registered.name().equals(catalog.name())
        && registered.description().equals(catalog.description());
  }
}
