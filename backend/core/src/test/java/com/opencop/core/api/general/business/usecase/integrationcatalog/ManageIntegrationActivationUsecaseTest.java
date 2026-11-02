// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationInUseException;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationNotDeactivatableException;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationNotFoundException;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManageIntegrationActivationUsecaseTest {

  private static final UUID INTEGRATION_ID =
      UUID.fromString("4b1f0c7e-1a2b-4c3d-8e9f-0a1b2c3d4e5f");

  private final IntegrationCatalogGateway catalog = mock(IntegrationCatalogGateway.class);
  private final ConnectionMetadataGateway connections = mock(ConnectionMetadataGateway.class);
  private final ManageIntegrationActivationUsecase usecase =
      new ManageIntegrationActivationUsecase(catalog, connections);

  @Test
  void anUnknownIntegrationIsNotFound() {
    when(catalog.findIntegrationForUpdate(INTEGRATION_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> usecase.setActive(INTEGRATION_ID, false))
        .isInstanceOf(IntegrationNotFoundException.class);
    verify(catalog, never()).updateActivation(any(), anyBoolean());
  }

  @Test
  void anIntegrationAlreadyInTheRequestedStateIsLeftAlone() {
    when(catalog.findIntegrationForUpdate(INTEGRATION_ID))
        .thenReturn(Optional.of(integration(true)));

    usecase.setActive(INTEGRATION_ID, true);

    verify(catalog, never()).updateActivation(any(), anyBoolean());
    verifyNoInteractions(connections);
  }

  @Test
  void anIntegrationUsedByAConnectionCannotBeDeactivated() {
    when(catalog.findIntegrationForUpdate(INTEGRATION_ID))
        .thenReturn(Optional.of(integration(true)));
    when(connections.findNonArchivedByIntegrationId(INTEGRATION_ID))
        .thenReturn(List.of(mock(ConnectionMetadata.class)));

    assertThatThrownBy(() -> usecase.setActive(INTEGRATION_ID, false))
        .isInstanceOf(IntegrationInUseException.class);
    verify(catalog, never()).updateActivation(any(), anyBoolean());
  }

  /** Native sets up every connection no plugin owns, so it stays available. */
  @Test
  void theNativeIntegrationIsNeverDeactivated() {
    assertThatThrownBy(() -> usecase.setActive(NativeIntegration.NATIVE_ID, false))
        .isInstanceOf(IntegrationNotDeactivatableException.class);
    verifyNoInteractions(catalog, connections);
  }

  @Test
  void anUnusedIntegrationIsDeactivated() {
    when(catalog.findIntegrationForUpdate(INTEGRATION_ID))
        .thenReturn(Optional.of(integration(true)));
    when(connections.findNonArchivedByIntegrationId(INTEGRATION_ID)).thenReturn(List.of());

    usecase.setActive(INTEGRATION_ID, false);

    verify(catalog).updateActivation(INTEGRATION_ID, false);
  }

  @Test
  void activatingNeverChecksForConnections() {
    when(catalog.findIntegrationForUpdate(INTEGRATION_ID))
        .thenReturn(Optional.of(integration(false)));

    usecase.setActive(INTEGRATION_ID, true);

    verify(catalog).updateActivation(INTEGRATION_ID, true);
    verifyNoInteractions(connections);
  }

  private static IntegrationIdentity integration(boolean active) {
    return new IntegrationIdentity(INTEGRATION_ID, "Example", "Example integration", active);
  }
}
