// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.model.datasource.DataSourceRuntime;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectInstanceFailoverTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442101");
  private static final UUID ACTOR_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442102");

  @Test
  void startWithFailover_persistsDesiredStateThenPublishesStartNudge() {
    var publisher = mock(SocketReconciliationNudgePublisher.class);
    var targetResolver = mock(ConnectRoutingTargetResolver.class);
    var repository = mock(DataSourceRepository.class);
    var failover =
        new ConnectInstanceFailover(
            publisher, targetResolver, repository, mock(DataSourceRuntimeService.class));
    when(targetResolver.resolveOwnershipTarget(SOURCE_ID, "connect-old", "START"))
        .thenReturn(new ConnectRoutingTargetResolver.OwnershipTarget("connect-new", true));

    var startedOn = failover.startWithFailover(SOURCE_ID, "connect-old", true, true, ACTOR_ID);

    assertEquals("connect-new", startedOn);
    var order = inOrder(repository, publisher);
    order.verify(repository).setAssignedInstance(SOURCE_ID, "connect-new", ACTOR_ID);
    order.verify(repository).setEnabled(SOURCE_ID, true, ACTOR_ID);
    order.verify(publisher).requestReconciliation();
  }

  @Test
  void publishAndAwaitSettled_whenPublisherFailsIsNonFatal() {
    var publisher = mock(SocketReconciliationNudgePublisher.class);
    doThrow(new IllegalStateException("notify failed")).when(publisher).requestReconciliation();
    var failover =
        new ConnectInstanceFailover(
            publisher,
            mock(ConnectRoutingTargetResolver.class),
            mock(DataSourceRepository.class),
            mock(DataSourceRuntimeService.class));

    assertThatCode(() -> failover.publishAndAwaitSettled(SOURCE_ID)).doesNotThrowAnyException();

    verify(publisher).requestReconciliation();
  }

  /** What a creation does once committed: wake Connect, then wait for the connection to settle. */
  @Test
  void publishAndAwaitSettled_wakesConnectThenWaitsUntilNotConnecting() {
    var publisher = mock(SocketReconciliationNudgePublisher.class);
    var runtimeService = mock(DataSourceRuntimeService.class);
    when(runtimeService.getWithRuntime(SOURCE_ID))
        .thenReturn(
            runtime(new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTING, null, "connect-1")),
            runtime(new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, null, "connect-1")));
    var failover =
        new ConnectInstanceFailover(
            publisher,
            mock(ConnectRoutingTargetResolver.class),
            mock(DataSourceRepository.class),
            runtimeService);

    failover.publishAndAwaitSettled(SOURCE_ID);

    var order = inOrder(publisher, runtimeService);
    order.verify(publisher).requestReconciliation();
    order.verify(runtimeService, times(2)).getWithRuntime(SOURCE_ID);
  }

  @Test
  void waitUntilNotConnecting_retriesConnectingAndUnknownUntilConcreteState() {
    var runtimeService = mock(DataSourceRuntimeService.class);
    when(runtimeService.getWithRuntime(SOURCE_ID))
        .thenReturn(
            runtime(new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTING, null, "connect-1")),
            runtime(RuntimeStatus.unknown(SOURCE_ID)),
            runtime(new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, null, "connect-1")));
    var failover = failover(runtimeService);

    failover.waitUntilNotConnecting(SOURCE_ID, Duration.ofSeconds(1));

    verify(runtimeService, times(3)).getWithRuntime(SOURCE_ID);
  }

  @Test
  void waitUntilNotConnecting_whenRuntimeReadFailsIsNonFatal() {
    var runtimeService = mock(DataSourceRuntimeService.class);
    when(runtimeService.getWithRuntime(SOURCE_ID))
        .thenThrow(new IllegalStateException("postgres unavailable"));
    var failover = failover(runtimeService);

    assertThatCode(() -> failover.waitUntilNotConnecting(SOURCE_ID, Duration.ofSeconds(1)))
        .doesNotThrowAnyException();
  }

  private static ConnectInstanceFailover failover(DataSourceRuntimeService runtimeService) {
    return new ConnectInstanceFailover(
        mock(SocketReconciliationNudgePublisher.class),
        mock(ConnectRoutingTargetResolver.class),
        mock(DataSourceRepository.class),
        runtimeService);
  }

  private static DataSourceRuntime runtime(RuntimeStatus status) {
    return new DataSourceRuntime(null, status);
  }
}
