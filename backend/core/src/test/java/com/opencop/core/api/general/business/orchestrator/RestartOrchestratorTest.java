// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.orchestrator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.service.ConnectRoutingTargetResolver;
import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.SourceRestartEpochWriter;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationReader;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RestartOrchestratorTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440601");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440602");
  private static final String STALE_INSTANCE_ID = "connect-old";
  private static final String REPLACEMENT_INSTANCE_ID = "connect-2";

  private ConnectInstanceResolver instanceResolver;
  private DataSourceRepository dataSourceRepository;
  private SourceRestartEpochWriter restartEpochWriter;
  private SocketReconciliationNudgePublisher nudgePublisher;
  private SourceRuntimeObservationReader sourceRuntimeObservationReader;
  private RestartOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    instanceResolver = mock(ConnectInstanceResolver.class);
    dataSourceRepository = mock(DataSourceRepository.class);
    restartEpochWriter = mock(SourceRestartEpochWriter.class);
    nudgePublisher = mock(SocketReconciliationNudgePublisher.class);
    sourceRuntimeObservationReader = mock(SourceRuntimeObservationReader.class);
    orchestrator = orchestrator(Duration.ZERO, Duration.ofMillis(1));
  }

  @Test
  void restart_whenAssignedInstanceIsStale_reassignsAndRequestsReconciliation() {
    when(dataSourceRepository.findById(SOURCE_ID))
        .thenReturn(Optional.of(socketSource(STALE_INSTANCE_ID)));
    when(instanceResolver.getRoutableInstances(any()))
        .thenReturn(List.of(connectInfo(REPLACEMENT_INSTANCE_ID, 0)));
    when(restartEpochWriter.incrementRestartEpoch(SOURCE_ID)).thenReturn(7L);

    orchestrator.restart(SOURCE_ID, USER_ID);

    verify(dataSourceRepository).setAssignedInstance(SOURCE_ID, REPLACEMENT_INSTANCE_ID, null);
    verify(restartEpochWriter).incrementRestartEpoch(SOURCE_ID);
    verify(nudgePublisher).requestReconciliation();
    verify(dataSourceRepository, never()).setEnabled(eq(SOURCE_ID), eq(false), any());
  }

  @Test
  void restart_whenAssignedInstanceIsRouteFresh_preservesAssignmentAndRequestsReconciliation() {
    when(dataSourceRepository.findById(SOURCE_ID))
        .thenReturn(Optional.of(socketSource(REPLACEMENT_INSTANCE_ID)));
    when(instanceResolver.getRoutableInstances(any()))
        .thenReturn(List.of(connectInfo(REPLACEMENT_INSTANCE_ID, 0)));
    when(restartEpochWriter.incrementRestartEpoch(SOURCE_ID)).thenReturn(8L);

    orchestrator.restart(SOURCE_ID, USER_ID);

    verify(dataSourceRepository, never()).setAssignedInstance(any(), any(), any());
    verify(restartEpochWriter).incrementRestartEpoch(SOURCE_ID);
    verify(nudgePublisher).requestReconciliation();
    verify(dataSourceRepository, never()).setEnabled(eq(SOURCE_ID), eq(false), any());
    verify(dataSourceRepository, never()).setEnabled(eq(SOURCE_ID), eq(true), any());
  }

  @Test
  void restart_whenSourceIsDisabled_throwsConflictWithoutAdvancingEpoch() {
    when(dataSourceRepository.findById(SOURCE_ID))
        .thenReturn(Optional.of(socketSource(REPLACEMENT_INSTANCE_ID, false)));

    var exception =
        assertThrows(GlobalOpenCopException.class, () -> orchestrator.restart(SOURCE_ID, USER_ID));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verify(restartEpochWriter, never()).incrementRestartEpoch(SOURCE_ID);
    verify(nudgePublisher, never()).requestReconciliation();
  }

  @Test
  void restart_whenSourceIsArchived_throwsConflictWithoutSideEffects() {
    when(dataSourceRepository.findById(SOURCE_ID))
        .thenReturn(Optional.of(archivedSocketSource(REPLACEMENT_INSTANCE_ID)));

    var exception =
        assertThrows(GlobalOpenCopException.class, () -> orchestrator.restart(SOURCE_ID, USER_ID));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verify(instanceResolver, never()).getRoutableInstances(any());
    verify(dataSourceRepository, never()).setAssignedInstance(any(), any(), any());
    verify(restartEpochWriter, never()).incrementRestartEpoch(SOURCE_ID);
    verify(nudgePublisher, never()).requestReconciliation();
  }

  @Test
  void restart_whenSourceIsKafka_throwsBadRequestWithoutAdvancingEpoch() {
    when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(kafkaSource()));

    var exception =
        assertThrows(GlobalOpenCopException.class, () -> orchestrator.restart(SOURCE_ID, USER_ID));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    verify(restartEpochWriter, never()).incrementRestartEpoch(SOURCE_ID);
    verify(nudgePublisher, never()).requestReconciliation();
  }

  @Test
  void restart_whenSourceDoesNotExist_throwsNotFoundWithoutAdvancingEpoch() {
    when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.empty());

    var exception =
        assertThrows(GlobalOpenCopException.class, () -> orchestrator.restart(SOURCE_ID, USER_ID));

    assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
    verify(restartEpochWriter, never()).incrementRestartEpoch(SOURCE_ID);
    verify(nudgePublisher, never()).requestReconciliation();
  }

  @Test
  void restart_whenNoRouteFreshTarget_throwsServiceUnavailableWithoutAdvancingEpoch() {
    when(dataSourceRepository.findById(SOURCE_ID))
        .thenReturn(Optional.of(socketSource(STALE_INSTANCE_ID)));
    when(instanceResolver.getRoutableInstances(any())).thenReturn(List.of());

    var exception =
        assertThrows(GlobalOpenCopException.class, () -> orchestrator.restart(SOURCE_ID, USER_ID));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getHttpStatus());
    verify(dataSourceRepository, never()).setAssignedInstance(any(), any(), any());
    verify(restartEpochWriter, never()).incrementRestartEpoch(SOURCE_ID);
    verify(nudgePublisher, never()).requestReconciliation();
  }

  @Test
  void restart_whenNudgePublishFails_stillAcceptsDurableRestartEpoch() {
    when(dataSourceRepository.findById(SOURCE_ID))
        .thenReturn(Optional.of(socketSource(REPLACEMENT_INSTANCE_ID)));
    when(instanceResolver.getRoutableInstances(any()))
        .thenReturn(List.of(connectInfo(REPLACEMENT_INSTANCE_ID, 0)));
    when(restartEpochWriter.incrementRestartEpoch(SOURCE_ID)).thenReturn(9L);
    doThrow(new IllegalStateException("notify failed"))
        .when(nudgePublisher)
        .requestReconciliation();

    assertDoesNotThrow(() -> orchestrator.restart(SOURCE_ID, USER_ID));

    verify(restartEpochWriter).incrementRestartEpoch(SOURCE_ID);
    verify(nudgePublisher).requestReconciliation();
  }

  @Test
  void restart_waitsBestEffortForFreshConnectorGeneration() {
    var baseline = Instant.parse("2026-05-04T12:00:00Z");
    var fresh = baseline.plusSeconds(1);
    orchestrator = orchestrator(Duration.ofMillis(50), Duration.ofMillis(1));
    when(dataSourceRepository.findById(SOURCE_ID))
        .thenReturn(Optional.of(socketSource(REPLACEMENT_INSTANCE_ID)));
    when(instanceResolver.getRoutableInstances(any()))
        .thenReturn(List.of(connectInfo(REPLACEMENT_INSTANCE_ID, 0)));
    when(restartEpochWriter.incrementRestartEpoch(SOURCE_ID)).thenReturn(10L);
    when(sourceRuntimeObservationReader.findFreshForInstance(SOURCE_ID, REPLACEMENT_INSTANCE_ID))
        .thenReturn(
            Optional.of(runtime(baseline)),
            Optional.of(runtime(baseline)),
            Optional.of(runtime(fresh)));

    orchestrator.restart(SOURCE_ID, USER_ID);

    verify(sourceRuntimeObservationReader, org.mockito.Mockito.atLeast(3))
        .findFreshForInstance(SOURCE_ID, REPLACEMENT_INSTANCE_ID);
  }

  @Test
  void restart_whenObservationWaitTimesOut_stillAcceptsDurableRestartEpoch() {
    var baseline = Instant.parse("2026-05-04T12:00:00Z");
    orchestrator = orchestrator(Duration.ofMillis(5), Duration.ofMillis(1));
    when(dataSourceRepository.findById(SOURCE_ID))
        .thenReturn(Optional.of(socketSource(REPLACEMENT_INSTANCE_ID)));
    when(instanceResolver.getRoutableInstances(any()))
        .thenReturn(List.of(connectInfo(REPLACEMENT_INSTANCE_ID, 0)));
    when(restartEpochWriter.incrementRestartEpoch(SOURCE_ID)).thenReturn(11L);
    when(sourceRuntimeObservationReader.findFreshForInstance(SOURCE_ID, REPLACEMENT_INSTANCE_ID))
        .thenReturn(Optional.of(runtime(baseline)));

    assertDoesNotThrow(() -> orchestrator.restart(SOURCE_ID, USER_ID));

    verify(restartEpochWriter).incrementRestartEpoch(SOURCE_ID);
    verify(nudgePublisher).requestReconciliation();
  }

  private static ConnectInstanceInfo connectInfo(String instanceId, int connectionCount) {
    return new ConnectInstanceInfo(
        instanceId, Instant.now(), connectionCount, instanceId + "-reg", 1);
  }

  private static SocketDataSource socketSource(String assignedInstance) {
    return socketSource(assignedInstance, true);
  }

  private static SocketDataSource socketSource(String assignedInstance, boolean enabled) {
    var spec =
        new ConnectionSpec(
            new TcpConfig("127.0.0.1", 8087, 3000, 15000, true, null, new NoSecurity()));
    return new SocketDataSource(
        SOURCE_ID,
        "Alpha",
        "default",
        spec,
        null,
        null,
        null,
        AccessMode.READ,
        enabled,
        assignedInstance,
        null,
        null,
        Instant.now(),
        null,
        Instant.now(),
        null,
        null,
        true);
  }

  private static SocketDataSource archivedSocketSource(String assignedInstance) {
    var spec =
        new ConnectionSpec(
            new TcpConfig("127.0.0.1", 8087, 3000, 15000, true, null, new NoSecurity()));
    return new SocketDataSource(
        SOURCE_ID,
        "Alpha",
        "default",
        spec,
        null,
        null,
        null,
        AccessMode.READ,
        true,
        assignedInstance,
        Instant.parse("2026-05-04T12:00:00Z"),
        USER_ID,
        Instant.now(),
        null,
        Instant.now(),
        null,
        null,
        true);
  }

  private static KafkaDataSource kafkaSource() {
    return new KafkaDataSource(
        SOURCE_ID,
        "Kafka",
        "default",
        "topic-alpha",
        "user-alpha",
        null,
        null,
        AccessMode.READ,
        true,
        null,
        null,
        Instant.now(),
        null,
        Instant.now(),
        null,
        null,
        "localhost:9092",
        true);
  }

  private static RuntimeStatus runtime(Instant connectorStartedAt) {
    return new RuntimeStatus(
        SOURCE_ID,
        ConnectorState.CONNECTED,
        Instant.parse("2026-05-04T12:00:05Z"),
        REPLACEMENT_INSTANCE_ID,
        connectorStartedAt);
  }

  private RestartOrchestrator orchestrator(Duration wait, Duration poll) {
    return new RestartOrchestrator(
        dataSourceRepository,
        new ConnectRoutingTargetResolver(instanceResolver),
        new ConnectorRestart(
            restartEpochWriter, nudgePublisher, sourceRuntimeObservationReader, wait, poll));
  }
}
