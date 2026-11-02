// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.connector;

import static com.opencop.api.connect.business.support.ConnectorRegistryTestSupport.rawOnly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.api.connect.business.support.RecordingSocketConnectorFactory;
import com.opencop.api.connect.business.support.fake.FakeDesiredSocketSourceRepository;
import com.opencop.api.connect.business.support.fake.NoopAccessModeTransitionRecorder;
import com.opencop.api.connect.business.support.fake.NoopIntegrationAvailabilityRecorder;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationWriter;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConnectionLifecycleServiceTest {

  private static final String INSTANCE_ID = "connect-unit-a";
  private static final String OTHER_INSTANCE_ID = "connect-unit-b";
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441001");
  private static final UUID PROFILE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441002");
  private static final IntegrationConnectionSchemaReference INTEGRATION =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("550e8400-e29b-41d4-a716-446655441003"), 4);
  private static final IntegrationConnectionSchemaReference SWITCHED_INTEGRATION =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("550e8400-e29b-41d4-a716-446655441004"), 1);
  private static final Duration RUNTIME_TTL = Duration.ofSeconds(42);
  private static final ConnectionSpec SPEC =
      new ConnectionSpec(
          new TcpConfig("127.0.0.1", 8087, 3000, 1000, true, null, new NoSecurity()));
  private static final ConnectionSpec UPDATED_SPEC =
      new ConnectionSpec(
          new TcpConfig("127.0.0.1", 8088, 3000, 1000, true, null, new NoSecurity()));
  private static final ConnectorListener NOOP_LISTENER = new ConnectorListener() {};

  @Test
  void startsConnectorWithRawCreationInputs() {
    var repository = new FakeDesiredSocketSourceRepository();
    var connectorFactory = new RecordingSocketConnectorFactory();
    var manager = connectionManager(connectorFactory);
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);

    lifecycleService.ensureStarted(desired(socketSource(true, INSTANCE_ID)));

    assertThat(connectorFactory.latestCreation(SOURCE_ID).specification()).isSameAs(SPEC);
  }

  @Test
  void startsConnectorWithThePersistedIntegrationReferenceInItsCreationContext() {
    var repository = new FakeDesiredSocketSourceRepository();
    var manager = mock(ConnectionManager.class);
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);

    lifecycleService.ensureStarted(desired(socketSource(true, INSTANCE_ID, SPEC, INTEGRATION)));

    var context = ArgumentCaptor.forClass(ConnectorCreationContext.class);
    verify(manager)
        .ensureStarted(
            context.capture(),
            eq(TransportKind.TCP),
            eq(AccessMode.READ),
            any(ConnectorListener[].class));
    assertThat(context.getValue().integration()).isEqualTo(INTEGRATION);
  }

  @Test
  void startsConnectorWithTheRowsStoredSettingsWithoutReadingTheRepository() {
    var repository = new FakeDesiredSocketSourceRepository();
    repository.setFailReads(true);
    var manager = mock(ConnectionManager.class);
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);
    var settings = Map.<String, Map<String, Object>>of("presence", Map.of("callsign", "Alpha"));

    lifecycleService.ensureStarted(
        new DesiredSocketSource(
            socketSource(true, INSTANCE_ID, SPEC, INTEGRATION),
            Optional.of(new SettingsOverrides(settings))));

    var context = ArgumentCaptor.forClass(ConnectorCreationContext.class);
    verify(manager)
        .ensureStarted(
            context.capture(),
            eq(TransportKind.TCP),
            eq(AccessMode.READ),
            any(ConnectorListener[].class));
    assertThat(context.getValue().overrides()).contains(new SettingsOverrides(settings));
  }

  @Test
  void localRestartRequestRestartsImmediatelyWhenStillDesiredWithoutRestartEpochWriter() {
    var repository = new FakeDesiredSocketSourceRepository();
    var connectorFactory = new RecordingSocketConnectorFactory();
    var manager = connectionManager(connectorFactory);
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);
    var source = socketSource(true, INSTANCE_ID);
    repository.put(source);

    lifecycleService.ensureStarted(desired(source));

    var firstConnector = connectorFactory.latest(SOURCE_ID);
    firstConnector.emitRestartRequested(new SocketTimeoutException("idle"));

    assertThat(firstConnector.stopCount()).isEqualTo(1);
    assertThat(connectorFactory.createdCount(SOURCE_ID)).isEqualTo(2);
    assertThat(manager.getRuntime(SOURCE_ID).orElseThrow().state())
        .isEqualTo(ConnectorState.CONNECTED);
  }

  @Test
  void localRestartRequestDoesNotResetPeriodicRestartSchedule() throws Exception {
    var repository = new FakeDesiredSocketSourceRepository();
    var connectorFactory = new RecordingSocketConnectorFactory();
    var observationWriter = mock(SourceRuntimeObservationWriter.class);
    var manager = connectionManager(connectorFactory, observationWriter);
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);
    var source = socketSource(true, INSTANCE_ID);
    repository.put(source);
    lifecycleService.ensureStarted(desired(source));
    var firstStartedAt = manager.getRuntime(SOURCE_ID).orElseThrow().connectorStartedAt();
    var firstConnector = connectorFactory.latest(SOURCE_ID);

    Thread.sleep(20);
    firstConnector.emitRestartRequested(new SocketTimeoutException("idle"));
    var replacementStartedAt = manager.getRuntime(SOURCE_ID).orElseThrow().connectorStartedAt();
    var replacementConnector = connectorFactory.latest(SOURCE_ID);

    assertThat(replacementStartedAt).isAfter(firstStartedAt);
    assertThat(manager.getRuntime(SOURCE_ID).orElseThrow().state())
        .isEqualTo(ConnectorState.CONNECTED);

    reset(observationWriter);
    firstConnector.emitState(ConnectorState.DISCONNECTED);
    verify(observationWriter, times(0)).writeRuntime(any(RuntimeStatus.class), eq(RUNTIME_TTL));

    replacementConnector.emitState(ConnectorState.CONNECTED);
    var statusCaptor = ArgumentCaptor.forClass(RuntimeStatus.class);
    verify(observationWriter).writeRuntime(statusCaptor.capture(), eq(RUNTIME_TTL));
    assertThat(statusCaptor.getValue().state()).isEqualTo(ConnectorState.CONNECTED);
    assertThat(statusCaptor.getValue().connectorStartedAt()).isEqualTo(replacementStartedAt);

    var result = lifecycleService.restartFromDesired(desired(source));

    assertThat(result).contains(ConnectionManager.StartOutcome.STARTED);
    assertThat(connectorFactory.createdCount(SOURCE_ID)).isEqualTo(3);
  }

  @Test
  void localRestartRequestStopsOnlyWhenDbNoLongerDesiresSourceHere() {
    var repository = new FakeDesiredSocketSourceRepository();
    var connectorFactory = new RecordingSocketConnectorFactory();
    var manager = connectionManager(connectorFactory);
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);
    var source = socketSource(true, INSTANCE_ID);
    repository.put(source);
    lifecycleService.ensureStarted(desired(source));

    var firstConnector = connectorFactory.latest(SOURCE_ID);
    repository.put(socketSource(false, INSTANCE_ID));
    firstConnector.emitRestartRequested(new SocketTimeoutException("idle"));

    assertThat(firstConnector.stopCount()).isEqualTo(1);
    assertThat(connectorFactory.createdCount(SOURCE_ID)).isEqualTo(1);
    assertThat(manager.isLoaded(SOURCE_ID)).isFalse();
  }

  @Test
  void localRestartRequestReadFailureSkipsImmediateRestartFromStaleState() {
    var repository = new FakeDesiredSocketSourceRepository();
    var connectorFactory = new RecordingSocketConnectorFactory();
    var manager = connectionManager(connectorFactory);
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);
    var source = socketSource(true, INSTANCE_ID);
    repository.put(source);
    lifecycleService.ensureStarted(desired(source));

    var firstConnector = connectorFactory.latest(SOURCE_ID);
    firstConnector.setState(ConnectorState.DISCONNECTED);
    repository.setFailReads(true);
    firstConnector.emitRestartRequested(new SocketTimeoutException("idle"));

    assertThat(firstConnector.stopCount()).isZero();
    assertThat(connectorFactory.createdCount(SOURCE_ID)).isEqualTo(1);
    assertThat(manager.isLoaded(SOURCE_ID)).isTrue();
    assertThat(manager.getRuntime(SOURCE_ID).orElseThrow().state())
        .isEqualTo(ConnectorState.DISCONNECTED);
  }

  @Test
  void restartFromDesiredUsesProvidedDbRowWithoutRepositoryRead() {
    var repository = new FakeDesiredSocketSourceRepository();
    var connectorFactory = new RecordingSocketConnectorFactory();
    var manager = connectionManager(connectorFactory);
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);
    var source = socketSource(true, INSTANCE_ID);
    var updatedSource = socketSource(true, INSTANCE_ID, UPDATED_SPEC);
    repository.put(source);
    lifecycleService.ensureStarted(desired(source));
    repository.setFailReads(true);

    var result = lifecycleService.restartFromDesired(desired(updatedSource));

    assertThat(result).contains(ConnectionManager.StartOutcome.STARTED);
    assertThat(connectorFactory.createdCount(SOURCE_ID)).isEqualTo(2);
    assertThat(connectorFactory.latest(SOURCE_ID).spec()).isEqualTo(UPDATED_SPEC);
  }

  /** A replaced setup reaches Connect as a new spec with a restart epoch to apply it. */
  @Test
  void appliedRestartEpochBuildsTheConnectorFromTheUpdatedSpec() {
    var repository = new FakeDesiredSocketSourceRepository();
    var connectorFactory = new RecordingSocketConnectorFactory();
    var manager = connectionManager(connectorFactory);
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);
    lifecycleService.ensureStarted(desired(socketSource(true, INSTANCE_ID)));

    var result =
        lifecycleService.restartIfDesired(
            desired(socketSource(true, INSTANCE_ID, UPDATED_SPEC)), 1L);

    assertThat(result).contains(ConnectionManager.StartOutcome.STARTED);
    assertThat(connectorFactory.createdCount(SOURCE_ID)).isEqualTo(2);
    assertThat(connectorFactory.latest(SOURCE_ID).spec()).isEqualTo(UPDATED_SPEC);
    assertThat(lifecycleService.hasUnappliedRestartEpoch(SOURCE_ID, 1L)).isFalse();
  }

  /** A setup that switches integration reaches Connect as a row naming the new one. */
  @Test
  void appliedRestartEpochStartsTheConnectorUnderTheSwitchedIntegration() {
    var repository = new FakeDesiredSocketSourceRepository();
    var manager = mock(ConnectionManager.class);
    when(manager.ensureStarted(any(), any(), any(), any(ConnectorListener[].class)))
        .thenReturn(ConnectionManager.StartOutcome.STARTED);
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);

    lifecycleService.restartIfDesired(
        desired(socketSource(true, INSTANCE_ID, UPDATED_SPEC, SWITCHED_INTEGRATION)), 1L);

    var context = ArgumentCaptor.forClass(ConnectorCreationContext.class);
    verify(manager)
        .ensureStarted(
            context.capture(),
            eq(TransportKind.TCP),
            eq(AccessMode.READ),
            any(ConnectorListener[].class));
    assertThat(context.getValue().integration()).isEqualTo(SWITCHED_INTEGRATION);
    assertThat(context.getValue().spec()).isSameAs(UPDATED_SPEC);
  }

  @Test
  void restartFromDesiredDoesNotMarkRestartEpochApplied() {
    var repository = new FakeDesiredSocketSourceRepository();
    var connectorFactory = new RecordingSocketConnectorFactory();
    var manager = connectionManager(connectorFactory);
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);
    var source = socketSource(true, INSTANCE_ID);
    lifecycleService.ensureStarted(desired(source));

    lifecycleService.restartFromDesired(desired(source));

    assertThat(lifecycleService.hasUnappliedRestartEpoch(SOURCE_ID, 7L)).isTrue();
  }

  @Test
  void restartFromDesiredStopsOnlyWhenProvidedRowNotDesiredHere() {
    var repository = new FakeDesiredSocketSourceRepository();
    var connectorFactory = new RecordingSocketConnectorFactory();
    var manager = connectionManager(connectorFactory);
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);
    var source = socketSource(true, INSTANCE_ID);
    lifecycleService.ensureStarted(desired(source));

    var disabledResult =
        lifecycleService.restartFromDesired(desired(socketSource(false, INSTANCE_ID)));

    assertThat(disabledResult).isEmpty();
    assertThat(manager.isLoaded(SOURCE_ID)).isFalse();
    assertThat(connectorFactory.createdCount(SOURCE_ID)).isEqualTo(1);

    lifecycleService.ensureStarted(desired(source));
    var reassignedResult =
        lifecycleService.restartFromDesired(desired(socketSource(true, OTHER_INSTANCE_ID)));

    assertThat(reassignedResult).isEmpty();
    assertThat(manager.isLoaded(SOURCE_ID)).isFalse();
    assertThat(connectorFactory.createdCount(SOURCE_ID)).isEqualTo(2);
  }

  @Test
  void restartFromDesiredDetachesOldManagerListener() {
    var repository = new FakeDesiredSocketSourceRepository();
    var connectorFactory = new RecordingSocketConnectorFactory();
    var observationWriter = mock(SourceRuntimeObservationWriter.class);
    var manager = connectionManager(connectorFactory, observationWriter);
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);
    var source = socketSource(true, INSTANCE_ID);
    lifecycleService.ensureStarted(desired(source));
    var firstConnector = connectorFactory.latest(SOURCE_ID);

    lifecycleService.restartFromDesired(desired(source));
    var replacementStartedAt = manager.getRuntime(SOURCE_ID).orElseThrow().connectorStartedAt();
    var replacementConnector = connectorFactory.latest(SOURCE_ID);
    reset(observationWriter);

    firstConnector.emitState(ConnectorState.DISCONNECTED);
    verify(observationWriter, times(0)).writeRuntime(any(RuntimeStatus.class), eq(RUNTIME_TTL));

    replacementConnector.emitState(ConnectorState.CONNECTED);
    var statusCaptor = ArgumentCaptor.forClass(RuntimeStatus.class);
    verify(observationWriter).writeRuntime(statusCaptor.capture(), eq(RUNTIME_TTL));
    assertThat(statusCaptor.getValue().state()).isEqualTo(ConnectorState.CONNECTED);
    assertThat(statusCaptor.getValue().connectorStartedAt()).isEqualTo(replacementStartedAt);
  }

  private static ConnectionManager connectionManager(RecordingSocketConnectorFactory factory) {
    return connectionManager(factory, null);
  }

  private static ConnectionManager connectionManager(
      RecordingSocketConnectorFactory factory, SourceRuntimeObservationWriter observationWriter) {
    return new ConnectionManager(
        rawOnly(factory),
        INSTANCE_ID,
        RUNTIME_TTL,
        observationWriter,
        NoopAccessModeTransitionRecorder.INSTANCE,
        NoopIntegrationAvailabilityRecorder.INSTANCE);
  }

  private static SocketDataSource socketSource(boolean enabled, String assignedInstance) {
    return socketSource(enabled, assignedInstance, SPEC);
  }

  private static DesiredSocketSource desired(SocketDataSource source) {
    return new DesiredSocketSource(source, Optional.empty());
  }

  private static SocketDataSource socketSource(
      boolean enabled, String assignedInstance, ConnectionSpec spec) {
    return socketSource(enabled, assignedInstance, spec, null);
  }

  private static SocketDataSource socketSource(
      boolean enabled,
      String assignedInstance,
      ConnectionSpec spec,
      IntegrationConnectionSchemaReference integration) {
    return new SocketDataSource(
        SOURCE_ID,
        "Alpha",
        "default",
        spec,
        null,
        null,
        integration,
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
}
