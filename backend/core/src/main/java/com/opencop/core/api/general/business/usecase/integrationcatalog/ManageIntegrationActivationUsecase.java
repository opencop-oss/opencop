// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationNotFoundException;
import com.opencop.core.api.general.business.policy.IntegrationActivationPolicy;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.integrationcatalog.ManageIntegrationActivation;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class ManageIntegrationActivationUsecase implements ManageIntegrationActivation {

  private final IntegrationCatalogGateway catalogGateway;
  private final ConnectionMetadataGateway connectionMetadataGateway;

  public ManageIntegrationActivationUsecase(
      IntegrationCatalogGateway catalogGateway,
      ConnectionMetadataGateway connectionMetadataGateway) {
    this.catalogGateway = catalogGateway;
    this.connectionMetadataGateway = connectionMetadataGateway;
  }

  @Override
  @Transactional
  public void setActive(UUID integrationId, boolean active) {
    if (!active) {
      IntegrationActivationPolicy.requireDeactivatable(integrationId);
    }
    var integration =
        catalogGateway
            .findIntegrationForUpdate(integrationId)
            .orElseThrow(() -> new IntegrationNotFoundException(integrationId));
    if (IntegrationActivationPolicy.isNoOp(integration, active)) {
      return;
    }
    if (!active) {
      IntegrationActivationPolicy.requireUnused(
          connectionMetadataGateway.findNonArchivedByIntegrationId(integrationId));
    }
    catalogGateway.updateActivation(integrationId, active);
  }
}
