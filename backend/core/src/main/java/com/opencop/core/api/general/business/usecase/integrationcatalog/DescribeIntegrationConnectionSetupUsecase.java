// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetup;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupOutcome;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupRejection;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupUnavailableException;
import com.opencop.core.api.general.business.policy.IntegrationConnectionSetupPolicy;
import com.opencop.core.api.general.business.usecase.integrationcatalog.setup.ConnectionSetupDescribers;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.integrationcatalog.DescribeIntegrationConnectionSetup;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class DescribeIntegrationConnectionSetupUsecase
    implements DescribeIntegrationConnectionSetup {

  private final IntegrationCatalogGateway catalogGateway;
  private final ConnectionSetupDescribers describers;
  private final Map<IntegrationConnectionSchemaKey, IntegrationConnectionSchema> registeredSchemas;

  public DescribeIntegrationConnectionSetupUsecase(
      IntegrationCatalogGateway catalogGateway,
      ConnectionSetupDescribers describers,
      List<IntegrationConnectionSchema> schemas) {
    this.catalogGateway = Objects.requireNonNull(catalogGateway, "catalogGateway");
    this.describers = Objects.requireNonNull(describers, "describers");
    this.registeredSchemas = index(schemas);
  }

  @Override
  /**
   * The gateway read runs in its own transaction, so this method deliberately declares none: an
   * outer transaction would bind a second connection for the duration without doing any work of its
   * own.
   */
  public IntegrationConnectionSetup describe(UUID integrationId, int schemaVersion) {
    var outcome =
        requestedSchema(integrationId, schemaVersion)
            .map(this::decide)
            .orElseGet(DescribeIntegrationConnectionSetupUsecase::unknown);
    return switch (outcome) {
      case IntegrationConnectionSetupOutcome.Described described -> described.setup();
      case IntegrationConnectionSetupOutcome.NotDescribable notDescribable ->
          throw new IntegrationConnectionSetupUnavailableException(notDescribable.reason());
    };
  }

  private IntegrationConnectionSetupOutcome decide(IntegrationConnectionSchemaKey key) {
    var registeredSchema = Optional.ofNullable(registeredSchemas.get(key));
    return IntegrationConnectionSetupPolicy.describe(
        key,
        catalogGateway.findSchemaOwner(key),
        registeredSchema,
        registeredSchema.map(describers::describe).orElse(List.of()));
  }

  /**
   * Builds the requested key, returning empty when no catalog row could carry it, so a malformed
   * reference is answered the same way an unknown one is instead of letting the key's own rejection
   * escape as a server error.
   */
  private static Optional<IntegrationConnectionSchemaKey> requestedSchema(
      UUID integrationId, int schemaVersion) {
    if (integrationId == null || schemaVersion <= 0) {
      return Optional.empty();
    }
    return Optional.of(new IntegrationConnectionSchemaKey(integrationId, schemaVersion));
  }

  private static IntegrationConnectionSetupOutcome unknown() {
    return new IntegrationConnectionSetupOutcome.NotDescribable(
        IntegrationConnectionSetupRejection.UNKNOWN);
  }

  /**
   * Indexes the connection schema versions this runtime registers, so availability is a lookup
   * against what is actually wired here rather than against anything persisted.
   */
  private static Map<IntegrationConnectionSchemaKey, IntegrationConnectionSchema> index(
      List<IntegrationConnectionSchema> schemas) {
    Objects.requireNonNull(schemas, "schemas");
    var indexed = new HashMap<IntegrationConnectionSchemaKey, IntegrationConnectionSchema>();
    for (var schema : schemas) {
      Objects.requireNonNull(schema, "schema");
      var key =
          new IntegrationConnectionSchemaKey(schema.integration().id(), schema.schemaVersion());
      if (indexed.putIfAbsent(key, schema) != null) {
        throw new IllegalArgumentException("Duplicate connection schema " + key);
      }
    }
    return Map.copyOf(indexed);
  }
}
