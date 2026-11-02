// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.connection.access.ReadAccess;
import com.opencop.core.api.general.business.service.SelectedMapperLock;
import com.opencop.core.api.general.business.usecase.connection.integration.ConnectionIntegrationSelection;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionSetupRechecksTest {

  private static final UUID SOURCE_ID = UUID.fromString("7d8e9f0a-1b2c-4d3e-9f4a-5b6c7d8e9f0a");
  private static final UUID OTHER_SOURCE_ID =
      UUID.fromString("8e9f0a1b-2c3d-4e4f-8a5b-6c7d8e9f0a1b");
  private static final UUID MAPPER_ID = UUID.fromString("9f0a1b2c-3d4e-4f5a-9b6c-7d8e9f0a1b2c");
  private static final int LISTEN_PORT = 18090;
  private static final ReadAccess ACCESS = new ReadAccess(MAPPER_ID);
  private static final ConnectionSpec UDP_SPEC =
      new ConnectionSpec(new UdpConfig("127.0.0.1", LISTEN_PORT, null));

  private final ConnectionIntegrationSelection integrationSelection =
      mock(ConnectionIntegrationSelection.class);
  private final SelectedMapperLock selectedMapperLock = mock(SelectedMapperLock.class);
  private final DataSourceRepository dataSources = mock(DataSourceRepository.class);
  private final ConnectionSetupRechecks rechecks =
      new ConnectionSetupRechecks(integrationSelection, selectedMapperLock, dataSources);

  @Test
  void locksTheIntegrationThenTheMappersThenChecksTheListenPort() {
    when(dataSources.findActiveByListenPort(TransportKind.UDP, LISTEN_PORT))
        .thenReturn(Optional.empty());

    rechecks.recheck(SOURCE_ID, NativeConnectionSchema.V1_REFERENCE, ACCESS, UDP_SPEC);

    var order = inOrder(integrationSelection, selectedMapperLock, dataSources);
    order
        .verify(integrationSelection)
        .requireSelectableForUpdate(NativeConnectionSchema.V1_REFERENCE);
    order.verify(selectedMapperLock).hold(ACCESS);
    order.verify(dataSources).findActiveByListenPort(TransportKind.UDP, LISTEN_PORT);
  }

  @Test
  void keepsTheListenPortTheConnectionItselfReserves() {
    when(dataSources.findActiveByListenPort(TransportKind.UDP, LISTEN_PORT))
        .thenReturn(Optional.of(SOURCE_ID));

    rechecks.recheck(SOURCE_ID, NativeConnectionSchema.V1_REFERENCE, ACCESS, UDP_SPEC);
  }

  @Test
  void refusesAListenPortAnotherSourceReserves() {
    when(dataSources.findActiveByListenPort(TransportKind.UDP, LISTEN_PORT))
        .thenReturn(Optional.of(OTHER_SOURCE_ID));

    assertThatThrownBy(
            () ->
                rechecks.recheck(SOURCE_ID, NativeConnectionSchema.V1_REFERENCE, ACCESS, UDP_SPEC))
        .isInstanceOf(ConnectionManagementException.class);
  }

  /** An outbound transport binds no local port, so it reserves none to check. */
  @Test
  void looksUpNoListenPortForAnOutboundTransport() {
    rechecks.recheck(
        SOURCE_ID,
        NativeConnectionSchema.V1_REFERENCE,
        ACCESS,
        new ConnectionSpec(
            new TcpConfig("relay.example.test", 9090, null, null, null, null, new NoSecurity())));

    verify(dataSources, never()).findActiveByListenPort(any(), anyInt());
  }
}
