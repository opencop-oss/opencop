// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.connection.CurrentBrokerProvider;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationReader;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorStateReason;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataSourceRuntimeServiceTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442001");
  private static final UUID SOURCE_TWO_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442002");
  private static final String BROKER_PROVIDER = "LOCAL";
  private static final Instant LAST_MESSAGE_AT = Instant.parse("2026-05-02T15:45:00Z");

  @Test
  void getWithRuntime_whenDisabledSourceHasFreshActiveObservation_returnsStopped() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = freshResolver("connect-a");
    var runtimeService =
        runtimeService(List.of(socketSource(SOURCE_ID, false, "connect-a")), reader, resolver);

    var runtime = runtimeService.getWithRuntime(SOURCE_ID).runtime();

    assertEquals(ConnectorState.STOPPED, runtime.state());
    assertNull(runtime.instanceId());
    assertNull(runtime.stateReason());
    verify(reader, never()).findFreshForInstance(SOURCE_ID, "connect-a");
  }

  @Test
  void getWithRuntime_whenEnabledSourceHasNoAssignment_returnsUnknown() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = freshResolver("connect-a");
    var runtimeService =
        runtimeService(List.of(socketSource(SOURCE_ID, true, null)), reader, resolver);

    var runtime = runtimeService.getWithRuntime(SOURCE_ID).runtime();

    assertEquals(ConnectorState.UNKNOWN, runtime.state());
    assertNull(runtime.instanceId());
    verifyNoInteractions(reader);
  }

  @Test
  void getWithRuntime_whenAssignedWorkerIsMissing_returnsUnknown() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = freshResolver("connect-wrong-owner");
    var runtimeService =
        runtimeService(List.of(socketSource(SOURCE_ID, true, "connect-a")), reader, resolver);

    var runtime = runtimeService.getWithRuntime(SOURCE_ID).runtime();

    assertEquals(ConnectorState.UNKNOWN, runtime.state());
    assertNull(runtime.instanceId());
    verifyNoInteractions(reader);
  }

  @Test
  void getWithRuntime_whenAssignedWorkerIsFreshButObservationMissing_returnsUnknown() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = freshResolver("connect-a");
    var runtimeService =
        runtimeService(List.of(socketSource(SOURCE_ID, true, "connect-a")), reader, resolver);
    when(reader.findFreshForInstance(SOURCE_ID, "connect-a")).thenReturn(Optional.empty());

    var runtime = runtimeService.getWithRuntime(SOURCE_ID).runtime();

    assertEquals(ConnectorState.UNKNOWN, runtime.state());
    assertNull(runtime.instanceId());
  }

  @Test
  void getWithRuntime_whenAssignedWorkerAndObservationAreFresh_returnsAssignedObservation() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = freshResolver("connect-a");
    var runtimeService =
        runtimeService(List.of(socketSource(SOURCE_ID, true, "connect-a")), reader, resolver);
    when(reader.findFreshForInstance(SOURCE_ID, "connect-a"))
        .thenReturn(
            Optional.of(
                new RuntimeStatus(
                    SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "connect-a")));

    var runtime = runtimeService.getWithRuntime(SOURCE_ID).runtime();

    assertEquals(ConnectorState.CONNECTED, runtime.state());
    assertEquals(LAST_MESSAGE_AT, runtime.lastMessageAt());
    assertEquals("connect-a", runtime.instanceId());
  }

  @Test
  void getWithRuntime_whenAssignedWorkerReportsTheIntegrationUnavailable_carriesTheReason() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = freshResolver("connect-a");
    var runtimeService =
        runtimeService(List.of(socketSource(SOURCE_ID, true, "connect-a")), reader, resolver);
    when(reader.findFreshForInstance(SOURCE_ID, "connect-a"))
        .thenReturn(
            Optional.of(
                RuntimeStatus.failed(
                    SOURCE_ID, "connect-a", ConnectorStateReason.INTEGRATION_UNAVAILABLE)));

    var runtime = runtimeService.getWithRuntime(SOURCE_ID).runtime();

    assertEquals(ConnectorState.FAILED, runtime.state());
    assertEquals(ConnectorStateReason.INTEGRATION_UNAVAILABLE, runtime.stateReason());
  }

  /**
   * Replicas disagreeing about availability resolve to the view of the one that owns the source.
   */
  @Test
  void getWithRuntime_whenAnotherWorkerReportsTheIntegrationUnavailable_ignoresIt() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = freshResolver("connect-a", "connect-b");
    var runtimeService =
        runtimeService(List.of(socketSource(SOURCE_ID, true, "connect-a")), reader, resolver);
    when(reader.findFresh(SOURCE_ID))
        .thenReturn(
            Optional.of(
                RuntimeStatus.failed(
                    SOURCE_ID, "connect-b", ConnectorStateReason.INTEGRATION_UNAVAILABLE)));
    when(reader.findFreshForInstance(SOURCE_ID, "connect-a"))
        .thenReturn(
            Optional.of(
                new RuntimeStatus(
                    SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "connect-a")));

    var runtime = runtimeService.getWithRuntime(SOURCE_ID).runtime();

    assertEquals(ConnectorState.CONNECTED, runtime.state());
    assertNull(runtime.stateReason());
  }

  @Test
  void getWithRuntime_whenWrongOwnerIsLatest_usesAssignedOwnerObservationOnly() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = freshResolver("connect-a", "connect-wrong-owner");
    var runtimeService =
        runtimeService(List.of(socketSource(SOURCE_ID, true, "connect-a")), reader, resolver);
    when(reader.findFresh(SOURCE_ID))
        .thenReturn(
            Optional.of(
                new RuntimeStatus(
                    SOURCE_ID, ConnectorState.CONNECTING, LAST_MESSAGE_AT, "connect-wrong-owner")));
    when(reader.findFreshForInstance(SOURCE_ID, "connect-a"))
        .thenReturn(
            Optional.of(
                new RuntimeStatus(
                    SOURCE_ID,
                    ConnectorState.CONNECTED,
                    LAST_MESSAGE_AT.minusSeconds(10),
                    "connect-a")));

    var runtime = runtimeService.getWithRuntime(SOURCE_ID).runtime();

    assertEquals(ConnectorState.CONNECTED, runtime.state());
    assertEquals(LAST_MESSAGE_AT.minusSeconds(10), runtime.lastMessageAt());
    assertEquals("connect-a", runtime.instanceId());
    verify(reader, never()).findFresh(SOURCE_ID);
  }

  @Test
  void getWithRuntime_whenWorkerHealthReadFails_returnsUnknown() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = mock(ConnectInstanceResolver.class);
    var runtimeService =
        runtimeService(List.of(socketSource(SOURCE_ID, true, "connect-a")), reader, resolver);
    when(resolver.getAllInstanceIds()).thenThrow(new IllegalStateException("postgres unavailable"));

    var runtime = runtimeService.getWithRuntime(SOURCE_ID).runtime();

    assertEquals(ConnectorState.UNKNOWN, runtime.state());
    assertNull(runtime.instanceId());
    verifyNoInteractions(reader);
  }

  @Test
  void getWithRuntime_whenObservationReadFails_returnsUnknown() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = freshResolver("connect-a");
    var runtimeService =
        runtimeService(List.of(socketSource(SOURCE_ID, true, "connect-a")), reader, resolver);
    when(reader.findFreshForInstance(SOURCE_ID, "connect-a"))
        .thenThrow(new IllegalStateException("postgres unavailable"));

    var runtime = runtimeService.getWithRuntime(SOURCE_ID).runtime();

    assertEquals(ConnectorState.UNKNOWN, runtime.state());
    assertNull(runtime.instanceId());
  }

  @Test
  void getWithRuntime_gathersFreshWorkersOnceForBulkSocketRead() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = freshResolver("connect-a", "connect-b");
    var runtimeService =
        runtimeService(
            List.of(
                socketSource(SOURCE_ID, true, "connect-a"),
                socketSource(SOURCE_TWO_ID, true, "connect-b")),
            reader,
            resolver);
    when(reader.findFreshForInstance(SOURCE_ID, "connect-a"))
        .thenReturn(
            Optional.of(new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, null, "connect-a")));
    when(reader.findFreshForInstance(SOURCE_TWO_ID, "connect-b"))
        .thenReturn(
            Optional.of(
                new RuntimeStatus(SOURCE_TWO_ID, ConnectorState.STOPPED, null, "connect-b")));

    var result = runtimeService.getWithRuntime(Set.of(SOURCE_ID, SOURCE_TWO_ID));

    assertEquals(ConnectorState.CONNECTED, result.get(SOURCE_ID).runtime().state());
    assertEquals(ConnectorState.STOPPED, result.get(SOURCE_TWO_ID).runtime().state());
    verify(resolver).getAllInstanceIds();
  }

  @Test
  void getWithRuntime_withArchivedFilterBuildsRepositoryFilterWithRecordVisibilityAndProvider() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = freshResolver("connect-a", "connect-b");
    var repository = mock(DataSourceRepository.class);
    when(repository.findAll(
            new DataSourceReadFilter(
                null,
                Set.of(SOURCE_ID, SOURCE_TWO_ID),
                RecordVisibility.ARCHIVED,
                BROKER_PROVIDER,
                null,
                null)))
        .thenReturn(
            List.of(
                socketSource(SOURCE_ID, true, "connect-a"),
                socketSource(SOURCE_TWO_ID, true, "connect-b")));
    when(reader.findFreshForInstance(SOURCE_ID, "connect-a"))
        .thenReturn(
            Optional.of(new RuntimeStatus(SOURCE_ID, ConnectorState.CONNECTED, null, "connect-a")));
    when(reader.findFreshForInstance(SOURCE_TWO_ID, "connect-b"))
        .thenReturn(
            Optional.of(
                new RuntimeStatus(SOURCE_TWO_ID, ConnectorState.STOPPED, null, "connect-b")));

    var runtimeService =
        new DataSourceRuntimeService(
            repository,
            new CurrentBrokerProvider(BROKER_PROVIDER),
            mock(KafkaProvisioningService.class),
            reader,
            resolver);

    runtimeService.getWithRuntime(Set.of(SOURCE_ID, SOURCE_TWO_ID), RecordVisibility.ARCHIVED);

    verify(repository)
        .findAll(
            new DataSourceReadFilter(
                null,
                Set.of(SOURCE_ID, SOURCE_TWO_ID),
                RecordVisibility.ARCHIVED,
                BROKER_PROVIDER,
                null,
                null));
  }

  @Test
  void getWithRuntime_whenKafkaSource_readsByodLastMessageButNotConnectWorkers() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = mock(ConnectInstanceResolver.class);
    var runtimeService = runtimeService(List.of(kafkaSource()), reader, resolver);
    when(reader.findFresh(SOURCE_ID))
        .thenReturn(
            Optional.of(
                new RuntimeStatus(
                    SOURCE_ID, ConnectorState.CONNECTED, LAST_MESSAGE_AT, "processor-a")));

    var runtime = runtimeService.getWithRuntime(SOURCE_ID).runtime();

    assertEquals(ConnectorState.UNKNOWN, runtime.state());
    assertEquals(LAST_MESSAGE_AT, runtime.lastMessageAt());
    assertNull(runtime.instanceId());
    verify(reader).findFresh(SOURCE_ID);
    verifyNoInteractions(resolver);
  }

  @Test
  void getWithRuntime_whenKafkaObservationReadFails_returnsNullLastMessage() {
    var reader = mock(SourceRuntimeObservationReader.class);
    var resolver = mock(ConnectInstanceResolver.class);
    var runtimeService = runtimeService(List.of(kafkaSource()), reader, resolver);
    when(reader.findFresh(SOURCE_ID)).thenThrow(new IllegalStateException("postgres unavailable"));

    var runtime = runtimeService.getWithRuntime(SOURCE_ID).runtime();

    assertEquals(ConnectorState.UNKNOWN, runtime.state());
    assertNull(runtime.lastMessageAt());
    verifyNoInteractions(resolver);
  }

  private static DataSourceRuntimeService runtimeService(
      List<DataSource> dataSources,
      SourceRuntimeObservationReader reader,
      ConnectInstanceResolver resolver) {
    var repository = mock(DataSourceRepository.class);
    when(repository.findAll(
            new DataSourceReadFilter(
                null, Set.of(SOURCE_ID), RecordVisibility.ACTIVE, BROKER_PROVIDER, null, null)))
        .thenReturn(dataSources.stream().filter(ds -> ds.id().equals(SOURCE_ID)).toList());
    when(repository.findAll(
            new DataSourceReadFilter(
                null,
                Set.of(SOURCE_ID, SOURCE_TWO_ID),
                RecordVisibility.ACTIVE,
                BROKER_PROVIDER,
                null,
                null)))
        .thenReturn(dataSources);
    return new DataSourceRuntimeService(
        repository,
        new CurrentBrokerProvider(BROKER_PROVIDER),
        mock(KafkaProvisioningService.class),
        reader,
        resolver);
  }

  private static ConnectInstanceResolver freshResolver(String... instanceIds) {
    var resolver = mock(ConnectInstanceResolver.class);
    when(resolver.getAllInstanceIds()).thenReturn(List.of(instanceIds));
    return resolver;
  }

  private static SocketDataSource socketSource(UUID id, boolean enabled, String assignedInstance) {
    return new SocketDataSource(
        id,
        "Alpha",
        "tenant",
        new ConnectionSpec(
            new TcpConfig("localhost", 4242, null, null, null, null, new NoSecurity())),
        null,
        null,
        null,
        AccessMode.READ_WRITE,
        enabled,
        assignedInstance,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        true);
  }

  private static KafkaDataSource kafkaSource() {
    return new KafkaDataSource(
        SOURCE_ID,
        "Kafka",
        "tenant",
        null,
        null,
        null,
        null,
        AccessMode.READ_WRITE,
        true,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        BROKER_PROVIDER,
        true);
  }
}
