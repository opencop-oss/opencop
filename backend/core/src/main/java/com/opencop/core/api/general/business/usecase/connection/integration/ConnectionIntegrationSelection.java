// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.integration;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.policy.connection.ConnectionIntegrationReferencePolicy;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks the integration schema version a connection binds to, whichever transport or API version
 * creates or patches it: unknown, then inactive, then unavailable in this runtime.
 *
 * <p>Availability is decided against the schema versions this runtime registers, which are fixed at
 * startup, so it needs no recheck under the integration lock the way activation does.
 */
public class ConnectionIntegrationSelection {

  private final IntegrationCatalogGateway catalog;
  private final Set<IntegrationConnectionSchemaReference> registered;

  public ConnectionIntegrationSelection(
      IntegrationCatalogGateway catalog, List<IntegrationConnectionSchema> schemas) {
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.registered =
        schemas.stream()
            .map(IntegrationConnectionSchema::reference)
            .collect(Collectors.toUnmodifiableSet());
  }

  /** Requires the schema version to be catalogued, its integration active, and registered here. */
  public void requireSelectable(IntegrationConnectionSchemaReference reference) {
    ConnectionIntegrationReferencePolicy.assertSelectable(
        reference, catalog.findSchemaOwner(key(reference)));
    ConnectionIntegrationReferencePolicy.assertAvailable(reference, registered.contains(reference));
  }

  /**
   * Like {@link #requireSelectable}, and locks the owning integration against concurrent activation
   * changes until the transaction ends.
   */
  public void requireSelectableForUpdate(IntegrationConnectionSchemaReference reference) {
    ConnectionIntegrationReferencePolicy.assertSelectable(
        reference, catalog.findSchemaOwnerForUpdate(key(reference)));
    ConnectionIntegrationReferencePolicy.assertAvailable(reference, registered.contains(reference));
  }

  private static IntegrationConnectionSchemaKey key(
      IntegrationConnectionSchemaReference reference) {
    return new IntegrationConnectionSchemaKey(reference.integrationId(), reference.schemaVersion());
  }
}
