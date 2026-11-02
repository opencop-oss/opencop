// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.connector;

import static com.opencop.api.connect.business.support.ConnectorRegistryTestSupport.rawOnly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.opencop.api.connect.business.support.fake.NoopAccessModeTransitionRecorder;
import com.opencop.api.connect.business.support.fake.NoopIntegrationAvailabilityRecorder;
import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.api.connect.technical.gateway.socket.factory.ConnectorFactory;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationWriter;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionManagerRuntimeTtlTest {

  private static final String INSTANCE_ID = "connect-ttl";
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443101");
  private static final ConnectionSpec SPEC =
      new ConnectionSpec(
          new TcpConfig("127.0.0.1", 8087, 3000, 1000, true, null, new NoSecurity()));

  @Test
  void refreshAllRuntimeStatus_usesConfiguredRuntimeTtl() throws Exception {
    var observationWriter = mock(SourceRuntimeObservationWriter.class);
    var manager =
        new ConnectionManager(
            rawOnly(new FakeConnectorFactory()),
            INSTANCE_ID,
            Duration.ofSeconds(42),
            observationWriter,
            NoopAccessModeTransitionRecorder.INSTANCE,
            NoopIntegrationAvailabilityRecorder.INSTANCE);

    manager.ensureStarted(
        new ConnectorCreationContext(SOURCE_ID, SPEC, null, Optional.empty()),
        TransportKind.TCP,
        AccessMode.READ);
    manager.refreshAllRuntimeStatus();

    verify(observationWriter).writeRuntime(any(RuntimeStatus.class), eq(Duration.ofSeconds(42)));
  }

  private static final class FakeConnectorFactory implements ConnectorFactory {
    @Override
    public SocketConnector create(UUID sourceId, ConnectionSpec specification) {
      return new FakeConnector(sourceId);
    }
  }

  private static final class FakeConnector implements SocketConnector {
    private final UUID sourceId;

    private FakeConnector(UUID sourceId) {
      this.sourceId = sourceId;
    }

    @Override
    public UUID id() {
      return sourceId;
    }

    @Override
    public ConnectorState state() {
      return ConnectorState.CONNECTED;
    }

    @Override
    public void start() {}

    @Override
    public boolean stop() {
      return true;
    }

    @Override
    public void addListener(ConnectorListener listener) {}

    @Override
    public void removeListener(ConnectorListener listener) {}
  }
}
