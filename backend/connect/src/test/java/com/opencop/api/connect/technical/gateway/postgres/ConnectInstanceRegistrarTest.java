// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.api.connect.business.connector.ConnectionManager;
import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.ports.gateway.api.ConnectWorkerHealthWriter;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConnectInstanceRegistrarTest {

  private static final String INSTANCE_ID = "connect-test";
  private static final Duration INSTANCE_TTL = Duration.ofSeconds(60);

  private ConnectionManager connectionManager;
  private ConnectWorkerHealthWriter workerHealthWriter;

  @BeforeEach
  void setUp() {
    connectionManager = mock(ConnectionManager.class);
    workerHealthWriter = mock(ConnectWorkerHealthWriter.class);
  }

  @Test
  void registerWritesPostgresWorkerHealth() throws Exception {
    when(connectionManager.getConnectionCount()).thenReturn(3);
    var registrar =
        new ConnectInstanceRegistrar(connectionManager, workerHealthWriter, INSTANCE_ID);

    registrar.register();
    registrar.deregister();

    var workerRunIdCaptor = ArgumentCaptor.forClass(UUID.class);
    var infoCaptor = ArgumentCaptor.forClass(ConnectInstanceInfo.class);

    verify(workerHealthWriter)
        .writeHeartbeat(workerRunIdCaptor.capture(), infoCaptor.capture(), eq(INSTANCE_TTL));

    assertThat(workerRunIdCaptor.getValue()).isNotNull();
    assertThat(infoCaptor.getValue().instanceId()).isEqualTo(INSTANCE_ID);
    assertThat(infoCaptor.getValue().connectionCount()).isEqualTo(3);
    assertThat(infoCaptor.getValue().registrationId())
        .isEqualTo(workerRunIdCaptor.getValue().toString());
    assertThat(infoCaptor.getValue().heartbeatSequence()).isEqualTo(1);
  }

  @Test
  void heartbeatTickUpdatesHealthAndRefreshesRuntimeStatus() throws Exception {
    when(connectionManager.getConnectionCount()).thenReturn(2);
    var registrar =
        new ConnectInstanceRegistrar(connectionManager, workerHealthWriter, INSTANCE_ID);

    registrar.heartbeatTick();

    verify(workerHealthWriter)
        .writeHeartbeat(any(UUID.class), any(ConnectInstanceInfo.class), eq(INSTANCE_TTL));
    verify(connectionManager).refreshAllRuntimeStatus();
  }

  @Test
  void heartbeatTickWhenPostgresWriteFailsStillRefreshesRuntimeStatus() throws Exception {
    doThrow(new IllegalStateException("postgres unavailable"))
        .when(workerHealthWriter)
        .writeHeartbeat(any(UUID.class), any(ConnectInstanceInfo.class), eq(INSTANCE_TTL));
    var registrar =
        new ConnectInstanceRegistrar(connectionManager, workerHealthWriter, INSTANCE_ID);

    assertThatCode(registrar::heartbeatTick).doesNotThrowAnyException();

    verify(connectionManager).refreshAllRuntimeStatus();
  }

  @Test
  void deregisterStopsPostgresRun() throws Exception {
    var registrar =
        new ConnectInstanceRegistrar(connectionManager, workerHealthWriter, INSTANCE_ID);

    registrar.deregister();

    verify(workerHealthWriter).markStopped(any(UUID.class));
  }
}
