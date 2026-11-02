// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import com.opencop.core.api.general.business.policy.connection.ConnectionIntegrationReferencePolicy;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.datasource.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resolves the connection a request about its extensions concerns, the same way for listing them
 * and invoking one: an active connection the caller can see, whose integration runs here.
 *
 * <p>Visibility is checked before anything about the connection's extensions, so a caller who
 * cannot see the connection learns nothing about them. Availability is checked before any
 * extension, because this runtime knows the extensions only of the integrations it registers.
 */
public class ExtensionConnections {

  private final DataSourceRepository dataSources;
  private final ConnectionAuthorization authorization;
  private final Set<IntegrationConnectionSchemaReference> registered;

  public ExtensionConnections(
      DataSourceRepository dataSources,
      ConnectionAuthorization authorization,
      List<IntegrationConnectionSchema> schemas) {
    this.dataSources = dataSources;
    this.authorization = authorization;
    this.registered =
        schemas.stream()
            .map(IntegrationConnectionSchema::reference)
            .collect(Collectors.toUnmodifiableSet());
  }

  /** An active connection the caller can see; anything else is reported as missing. */
  DataSource readable(UUID sourceId, UUID userId, boolean isGlobalAdmin) {
    var connection =
        active(sourceId)
            .orElseThrow(() -> ConnectionManagementException.connectionNotFound(sourceId));
    authorization.assertCanRead(sourceId, userId, isGlobalAdmin);
    return connection;
  }

  /** The connection, unless it is missing or deleted. */
  Optional<DataSource> active(UUID sourceId) {
    return dataSources.findById(sourceId).filter(found -> found.archivedAt() == null);
  }

  /** Whether this runtime registers the schema version, and so knows its extensions. */
  boolean runsHere(IntegrationConnectionSchemaReference integration) {
    return registered.contains(integration);
  }

  /**
   * The integration the connection binds to, once this runtime registers it, or empty for a
   * connection bound to none, which carries no extension.
   */
  Optional<IntegrationConnectionSchemaReference> availableIntegration(DataSource connection) {
    var integration = Optional.ofNullable(connection.integration());
    integration.ifPresent(
        reference ->
            ConnectionIntegrationReferencePolicy.assertAvailable(reference, runsHere(reference)));
    return integration;
  }
}
