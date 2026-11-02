// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.api.connect.business.connector.ConnectionLifecycleService;
import com.opencop.api.connect.business.connector.ConnectionManager;
import com.opencop.api.connect.business.connector.DesiredSocketSource;
import com.opencop.api.connect.ports.gateway.repository.DesiredSocketSourceRepository;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.gateway.api.SourceRestartEpochReader;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The reconciler applies a changed access mode to running connectors, never restarting them. */
class DesiredAccessReconciliationTest {
  private static final String INSTANCE_ID = "connect-unit-a";
  private static final UUID SOURCE_ID = UUID.fromString("7d3e5a21-6c84-4b19-9f02-3a5b7c8d9e10");
  private static final SocketDataSource ROW =
      new SocketDataSource(
          SOURCE_ID,
          "Alpha",
          "default",
          new ConnectionSpec(
              new TcpConfig("127.0.0.1", 8087, 3000, 1000, true, null, new NoSecurity())),
          null,
          null,
          null,
          AccessMode.READ_WRITE,
          true,
          INSTANCE_ID,
          null,
          null,
          Instant.now(),
          null,
          Instant.now(),
          null,
          null,
          true);
  private static final DesiredSocketSource DESIRED = new DesiredSocketSource(ROW, Optional.empty());

  private final ConnectionManager manager = mock(ConnectionManager.class);
  private final DesiredSocketSourceRepository repository =
      mock(DesiredSocketSourceRepository.class);
  private final ConnectionLifecycleService lifecycle = mock(ConnectionLifecycleService.class);
  private final SourceRestartEpochReader restartEpochs = mock(SourceRestartEpochReader.class);
  private final DesiredOwnershipReconciler reconciler =
      new DesiredOwnershipReconciler(
          manager,
          repository,
          lifecycle,
          INSTANCE_ID,
          Duration.ZERO,
          restartEpochs,
          DesiredStateOutagePolicy.continueExisting(),
          Clock.systemUTC());

  /** Access swaps in place on the running connector, so its generation is kept. */
  @Test
  void appliesTheDesiredAccessToARunningConnectorWithoutReplacingIt() {
    givenRunning(ConnectorState.CONNECTED);

    var result = reconciler.reconcileOnce();

    verify(lifecycle).applyDesiredAccess(ROW);
    assertThat(result.startedCount()).isZero();
    verify(lifecycle, never()).ensureStarted(any());
    verify(lifecycle, never()).restartFromDesired(any());
    verify(lifecycle, never()).restartIfDesired(any(), anyLong());
  }

  private void givenRunning(ConnectorState state) {
    when(manager.getAllRuntime())
        .thenReturn(List.of(new RuntimeStatus(SOURCE_ID, state, null, INSTANCE_ID)));
    when(manager.isLoaded(SOURCE_ID)).thenReturn(true);
    when(repository.findEnabledSocketSourcesAssignedTo(INSTANCE_ID)).thenReturn(List.of(DESIRED));
    when(repository.findSocketSourcesByIds(anySet())).thenReturn(Map.of(SOURCE_ID, ROW));
    when(restartEpochs.findRestartEpochs(anySet())).thenReturn(Map.of());
  }
}
