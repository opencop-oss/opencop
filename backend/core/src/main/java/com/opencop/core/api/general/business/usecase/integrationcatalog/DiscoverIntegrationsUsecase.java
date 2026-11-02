// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogEntry;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.policy.IntegrationCatalogPolicy;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.integrationcatalog.DiscoverIntegrations;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DiscoverIntegrationsUsecase implements DiscoverIntegrations {

  private final IntegrationCatalogGateway catalogGateway;
  private final List<IntegrationConnectionSchema> connectionSchemas;

  public DiscoverIntegrationsUsecase(
      IntegrationCatalogGateway catalogGateway,
      List<IntegrationConnectionSchema> connectionSchemas) {
    this.catalogGateway = catalogGateway;
    this.connectionSchemas = List.copyOf(connectionSchemas);
  }

  /**
   * The gateway's catalog read runs in its own transaction, so this method deliberately declares
   * none: an outer transaction would bind a second connection for the duration without doing any
   * work of its own.
   */
  @Override
  public List<IntegrationCatalogEntry> discover(String protocol) {
    // Parsed before the catalog is touched: a malformed filter is the caller's error, so it must
    // not cost a connection, nor surface as a 500 when the database is what is unavailable.
    var requested = IntegrationCatalogPolicy.parseProtocol(protocol);
    return IntegrationCatalogPolicy.discover(
        catalogGateway.findAll(), declaredProtocolsByKey(), requested);
  }

  /**
   * Indexes the connection schemas this runtime loaded by catalog key. Two loaded schemas claiming
   * one key leaves no defensible answer about what that version offers, so the merge fails rather
   * than silently dropping one of them.
   */
  private Map<IntegrationConnectionSchemaKey, Set<TransportProtocol>> declaredProtocolsByKey() {
    return connectionSchemas.stream()
        .collect(
            Collectors.toUnmodifiableMap(
                schema ->
                    new IntegrationConnectionSchemaKey(
                        schema.integration().id(), schema.schemaVersion()),
                DiscoverIntegrationsUsecase::protocolsOf,
                DiscoverIntegrationsUsecase::rejectDuplicate));
  }

  private static Set<TransportProtocol> rejectDuplicate(
      Set<TransportProtocol> first, Set<TransportProtocol> second) {
    throw new IllegalStateException(
        "Two loaded connection schemas claim one catalog version, declaring "
            + first
            + " and "
            + second);
  }

  private static Set<TransportProtocol> protocolsOf(IntegrationConnectionSchema schema) {
    var protocols = schema.protocols();
    return protocols == null ? Set.of() : Set.copyOf(protocols);
  }
}
