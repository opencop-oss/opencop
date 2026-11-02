// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.api.connect.business.support.fake.NoopAccessModeTransitionRecorder;
import com.opencop.api.connect.business.support.fake.NoopIntegrationAvailabilityRecorder;
import com.opencop.api.connect.ports.gateway.socket.connector.ConnectorProvider;
import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationWriter;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The manager hands a loaded connector its desired settings, and nothing else. */
class ConnectionManagerDesiredSettingsTest {

  private static final UUID SOURCE_ID = UUID.fromString("4f0c2a1e-8d3b-4c6a-9e7f-1b2c3d4e5f60");
  private static final ConnectionSpec SPEC =
      new ConnectionSpec(
          new TcpConfig("127.0.0.1", 8087, 3000, 1000, true, null, new NoSecurity()));
  private static final ConnectorCreationContext DESIRED =
      new ConnectorCreationContext(
          SOURCE_ID,
          SPEC,
          new com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference(java.util.UUID.fromString("714e8096-8832-4486-ad47-5422f86f918a"), 1),
          Optional.of(new SettingsOverrides(Map.of("presence", Map.of("callsign", "Bravo")))));

  private final ConnectorProvider provider = mock(ConnectorProvider.class);
  private final SocketConnector connector = mock(SocketConnector.class);
  private final ConnectionManager manager =
      new ConnectionManager(
          provider,
          "connect-unit-a",
          Duration.ofSeconds(90),
          mock(SourceRuntimeObservationWriter.class),
          NoopAccessModeTransitionRecorder.INSTANCE,
          NoopIntegrationAvailabilityRecorder.INSTANCE);

  @Test
  void handsTheLoadedConnectorItsDesiredSettings() {
    startConnector();
    when(provider.applySettings(connector, DESIRED)).thenReturn(true);

    var swapped = manager.applyDesiredSettings(DESIRED);

    assertThat(swapped).isTrue();
    verify(provider).applySettings(connector, DESIRED);
  }

  @Test
  void leavesASourceThatIsNotLoadedAlone() {
    var swapped = manager.applyDesiredSettings(DESIRED);

    assertThat(swapped).isFalse();
    verify(provider, never()).applySettings(any(), any());
  }

  @Test
  void leavesAStoppedSourceAlone() {
    startConnector();
    manager.ensureStopped(SOURCE_ID);

    var swapped = manager.applyDesiredSettings(DESIRED);

    assertThat(swapped).isFalse();
    verify(provider, never()).applySettings(any(), any());
  }

  private void startConnector() {
    when(provider.canRun(any())).thenReturn(true);
    when(provider.create(any())).thenReturn(connector);
    when(connector.id()).thenReturn(SOURCE_ID);
    when(connector.state()).thenReturn(ConnectorState.CONNECTED);
    manager.ensureStarted(DESIRED, TransportKind.TCP, AccessMode.READ);
  }
}
