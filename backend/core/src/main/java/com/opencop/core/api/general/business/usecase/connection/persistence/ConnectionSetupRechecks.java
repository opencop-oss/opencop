// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.persistence;

import com.opencop.core.api.general.business.model.connection.access.ConnectionAccess;
import com.opencop.core.api.general.business.policy.connection.ListenPortExclusivityPolicy;
import com.opencop.core.api.general.business.service.SelectedMapperLock;
import com.opencop.core.api.general.business.usecase.connection.integration.ConnectionIntegrationSelection;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Rechecks, within the transaction that stores a connection's setup, what a concurrent change could
 * have broken since the setup was resolved, and holds it until that transaction ends: the
 * integration is still selectable, the mappers are still selectable, and the port the setup listens
 * on is free.
 *
 * <p>Resolving the setup checked all three already, before anything was provisioned. Only a
 * reservation that races this one past the unlocked port lookup is left to the unique index.
 */
@Component
public class ConnectionSetupRechecks {

  private final ConnectionIntegrationSelection integrationSelection;
  private final SelectedMapperLock selectedMapperLock;
  private final DataSourceRepository dataSources;

  public ConnectionSetupRechecks(
      ConnectionIntegrationSelection integrationSelection,
      SelectedMapperLock selectedMapperLock,
      DataSourceRepository dataSources) {
    this.integrationSelection = integrationSelection;
    this.selectedMapperLock = selectedMapperLock;
    this.dataSources = dataSources;
  }

  /**
   * @param sourceId the connection the setup is stored for, whose own port reservation it keeps
   */
  public void recheck(
      UUID sourceId,
      IntegrationConnectionSchemaReference integration,
      ConnectionAccess access,
      ConnectionSpec spec) {
    integrationSelection.requireSelectableForUpdate(integration);
    selectedMapperLock.hold(access);
    ListenPortExclusivityPolicy.reservedListenPort(spec.transport())
        .ifPresent(port -> requireListenPortFree(sourceId, spec.transport().kind(), port));
  }

  /** A connection keeps the port it already reserves; only another source's reservation refuses. */
  private void requireListenPortFree(UUID sourceId, TransportKind transport, int port) {
    ListenPortExclusivityPolicy.assertAvailable(
        port,
        dataSources
            .findActiveByListenPort(transport, port)
            .filter(holder -> !holder.equals(sourceId)));
  }
}
