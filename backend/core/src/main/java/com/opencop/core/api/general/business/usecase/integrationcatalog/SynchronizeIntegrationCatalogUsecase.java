// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog;

import com.opencop.core.api.general.business.factory.IntegrationCatalogRegistrationFactory;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogConflictException;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.business.policy.IntegrationCatalogPolicy;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogInsertConflictException;
import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records every published integration and connection schema version in the catalog. A missing
 * integration is inserted active, a known one gets its published display metadata, and a missing
 * schema version is inserted. Activation is never changed here: it belongs to the integration, and
 * only an operator sets it.
 */
public class SynchronizeIntegrationCatalogUsecase {

  private final List<Integration> integrations;
  private final List<IntegrationConnectionSchema> schemas;
  private final IntegrationCatalogGateway gateway;
  private final IntegrationCatalogRegistrationFactory factory;

  public SynchronizeIntegrationCatalogUsecase(
      List<Integration> integrations,
      List<IntegrationConnectionSchema> schemas,
      IntegrationCatalogGateway gateway,
      IntegrationCatalogRegistrationFactory factory) {
    this.integrations = List.copyOf(integrations);
    this.schemas = List.copyOf(schemas);
    this.gateway = gateway;
    this.factory = factory;
  }

  @Transactional
  public void synchronize() {
    var registration = factory.create(integrations, schemas);
    IntegrationCatalogPolicy.requireConsistentRegistration(registration);
    // Integrations first: a schema version references its integration.
    registration.integrations().forEach(this::synchronizeIntegration);
    registration.schemas().forEach(this::synchronizeSchema);
  }

  private void synchronizeIntegration(IntegrationIdentity published) {
    var catalog = gateway.findIntegration(published.id());
    if (catalog.isPresent()) {
      refreshDisplayMetadata(published, catalog.orElseThrow());
      return;
    }
    try {
      gateway.insertIntegration(published);
    } catch (IntegrationCatalogInsertConflictException conflict) {
      var insertedConcurrently =
          gateway
              .findIntegration(published.id())
              .orElseThrow(
                  () ->
                      new IntegrationCatalogConflictException(
                          "Integration insert conflicted but no catalog row exists for "
                              + published.id(),
                          conflict));
      refreshDisplayMetadata(published, insertedConcurrently);
    }
  }

  private void synchronizeSchema(IntegrationConnectionSchemaKey published) {
    if (gateway.hasSchema(published)) {
      return;
    }
    try {
      gateway.insertSchema(published);
    } catch (IntegrationCatalogInsertConflictException conflict) {
      // Losing the race to another synchronizer is expected; a conflict leaving no row is not.
      if (!gateway.hasSchema(published)) {
        throw new IntegrationCatalogConflictException(
            "Connection schema insert conflicted but no catalog row exists for " + published,
            conflict);
      }
    }
  }

  private void refreshDisplayMetadata(IntegrationIdentity published, IntegrationIdentity catalog) {
    IntegrationCatalogPolicy.displayMetadataRefresh(published, catalog)
        .ifPresent(gateway::updateDisplayMetadata);
  }
}
