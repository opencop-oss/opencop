// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.connector;

import static com.opencop.api.connect.business.support.ConnectorRegistryTestSupport.rawOnly;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.api.connect.business.support.RecordingSocketConnectorFactory;
import com.opencop.api.connect.business.support.fake.NoopAccessModeTransitionRecorder;
import com.opencop.api.connect.business.support.fake.NoopIntegrationAvailabilityRecorder;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationWriter;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class ConnectionManagerRuntimeObservationTest {

  private static final String INSTANCE_ID = "connect-observer";
  private static final Duration RUNTIME_TTL = Duration.ofSeconds(42);
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443201");
  private static final ConnectionSpec SPEC =
      new ConnectionSpec(
          new TcpConfig("127.0.0.1", 8087, 3000, 1000, true, null, new NoSecurity()));

  private SourceRuntimeObservationWriter observationWriter;
  private RecordingSocketConnectorFactory connectorFactory;
  private ConnectionManager manager;

  @BeforeEach
  void setUp() {
    observationWriter = mock(SourceRuntimeObservationWriter.class);
    connectorFactory = new RecordingSocketConnectorFactory();
    manager =
        new ConnectionManager(
            rawOnly(connectorFactory),
            INSTANCE_ID,
            RUNTIME_TTL,
            observationWriter,
            NoopAccessModeTransitionRecorder.INSTANCE,
            NoopIntegrationAvailabilityRecorder.INSTANCE);
  }

  @Test
  void stateChangesWritePostgresObservations() throws Exception {
    startReadOnly();

    var statusCaptor = ArgumentCaptor.forClass(RuntimeStatus.class);
    verify(observationWriter).writeRuntime(statusCaptor.capture(), eq(RUNTIME_TTL));
    assertThat(statusCaptor.getValue().id()).isEqualTo(SOURCE_ID);
    assertThat(statusCaptor.getValue().state()).isEqualTo(ConnectorState.CONNECTED);
    assertThat(statusCaptor.getValue().instanceId()).isEqualTo(INSTANCE_ID);
    assertThat(statusCaptor.getValue().connectorStartedAt()).isNotNull();
  }

  @Test
  void messageUpdatesLastMessageAtObservedByHeartbeatTick() throws Exception {
    startReadOnly();
    reset(observationWriter);

    connectorFactory.latest(SOURCE_ID).emitMessage(new byte[] {1, 2, 3});
    manager.refreshAllRuntimeStatus();

    var statusCaptor = ArgumentCaptor.forClass(RuntimeStatus.class);
    verify(observationWriter).writeRuntime(statusCaptor.capture(), eq(RUNTIME_TTL));
    assertThat(statusCaptor.getValue().id()).isEqualTo(SOURCE_ID);
    assertThat(statusCaptor.getValue().state()).isEqualTo(ConnectorState.CONNECTED);
    assertThat(statusCaptor.getValue().lastMessageAt()).isNotNull();
    assertThat(statusCaptor.getValue().instanceId()).isEqualTo(INSTANCE_ID);
    assertThat(statusCaptor.getValue().connectorStartedAt()).isNotNull();
  }

  @Test
  void aSourceWithNoTrafficReportsNoMessageToken() throws Exception {
    startReadOnly();

    var statusCaptor = ArgumentCaptor.forClass(RuntimeStatus.class);
    verify(observationWriter).writeRuntime(statusCaptor.capture(), eq(RUNTIME_TTL));
    assertThat(statusCaptor.getValue().lastMessageToken()).isNull();
  }

  @Test
  void eachAcceptedMessageGetsADistinctToken() throws Exception {
    startReadOnly();

    connectorFactory.latest(SOURCE_ID).emitMessage(new byte[] {1});
    manager.refreshAllRuntimeStatus();
    var afterFirst = capturedToken();

    reset(observationWriter);
    connectorFactory.latest(SOURCE_ID).emitMessage(new byte[] {2});
    manager.refreshAllRuntimeStatus();
    var afterSecond = capturedToken();

    assertThat(afterFirst).isNotNull();
    assertThat(afterSecond).isNotNull().isNotEqualTo(afterFirst);
  }

  @Test
  void anUnchangedTokenIsReportedAgainWhenNoNewMessageArrives() throws Exception {
    startReadOnly();
    connectorFactory.latest(SOURCE_ID).emitMessage(new byte[] {1});
    manager.refreshAllRuntimeStatus();
    var afterMessage = capturedToken();

    reset(observationWriter);
    manager.refreshAllRuntimeStatus();

    assertThat(capturedToken())
        .as("an idle heartbeat must carry the same token, so it promotes no new activity")
        .isEqualTo(afterMessage);
  }

  @Test
  void aFreshRunMintsTokensThatCannotCollideWithAnEarlierRun() throws Exception {
    startReadOnly();
    connectorFactory.latest(SOURCE_ID).emitMessage(new byte[] {1});
    manager.refreshAllRuntimeStatus();
    var firstRunToken = capturedToken();

    // A restart of the same deployment: the instance id is deliberately stable for a redeployed
    // container or an explicit OPENCOP_INSTANCE_ID, and the per-source counter starts over. Without
    // a per-run id the first message after a restart would re-mint a token already recorded as
    // activity, and be discarded as a replay.
    var restartedWriter = mock(SourceRuntimeObservationWriter.class);
    var restartedFactory = new RecordingSocketConnectorFactory();
    var restarted =
        new ConnectionManager(
            rawOnly(restartedFactory),
            INSTANCE_ID,
            RUNTIME_TTL,
            restartedWriter,
            NoopAccessModeTransitionRecorder.INSTANCE,
            NoopIntegrationAvailabilityRecorder.INSTANCE);
    restarted.ensureStarted(context(SPEC), TransportKind.TCP, AccessMode.READ);
    restartedFactory.latest(SOURCE_ID).emitMessage(new byte[] {1});
    restarted.refreshAllRuntimeStatus();

    var statusCaptor = ArgumentCaptor.forClass(RuntimeStatus.class);
    verify(restartedWriter, atLeastOnce()).writeRuntime(statusCaptor.capture(), eq(RUNTIME_TTL));
    assertThat(statusCaptor.getAllValues().getLast().lastMessageToken())
        .as("the first message of a new run must not reuse the first run's token")
        .isNotEqualTo(firstRunToken);
  }

  @Test
  void aRestartedSourceMintsTokensThatCannotCollideWithItsPreviousSession() throws Exception {
    startReadOnly();
    connectorFactory.latest(SOURCE_ID).emitMessage(new byte[] {1});
    manager.refreshAllRuntimeStatus();
    var beforeRestart = capturedToken();

    // A source restarted in place, which is what ConnectionLifecycleService does when it applies a
    // restart epoch: ensureStopped then ensureStarted on this same manager. instanceId and runId
    // are both unchanged, and the replacement entry's counter starts over at zero, so without a
    // per-session value this first message would re-mint the token above and be discarded as a
    // replay - freezing the source's activity until its counter passed the pre-restart value.
    manager.ensureStopped(SOURCE_ID);
    manager.ensureStarted(context(SPEC), TransportKind.TCP, AccessMode.READ);
    reset(observationWriter);
    connectorFactory.latest(SOURCE_ID).emitMessage(new byte[] {1});
    manager.refreshAllRuntimeStatus();

    assertThat(capturedToken())
        .as(
            "the first message after an in-place restart must not reuse the previous session's token")
        .isNotNull()
        .isNotEqualTo(beforeRestart);
  }

  @Test
  void aWriteOnlySourceMintsNoTokenBecauseItNeverReadsAMessage() throws Exception {
    manager.ensureStarted(context(SPEC), TransportKind.TCP, AccessMode.WRITE);
    reset(observationWriter);

    connectorFactory.latest(SOURCE_ID).emitMessage(new byte[] {1});
    manager.refreshAllRuntimeStatus();

    // onMessage returns early for a write-only TCP source before anything is counted, so an
    // outbound-only source reports no activity. Pre-existing behavior, asserted here because the
    // counter increment sits below that early return and moving it above would silently start
    // crediting sources that never received anything.
    assertThat(capturedToken()).isNull();
  }

  @Test
  void theReportedTimeAndTokenDescribeTheSameMessage() throws Exception {
    startReadOnly();
    reset(observationWriter);

    var connector = connectorFactory.latest(SOURCE_ID);
    connector.emitMessage(new byte[] {1});
    Thread.sleep(5); // Put the two arrivals in distinct milliseconds so a mismatch is visible.
    long beforeSecond = System.currentTimeMillis();
    connector.emitMessage(new byte[] {2});
    long afterSecond = System.currentTimeMillis();

    manager.refreshAllRuntimeStatus();

    // Pairing the second message's token with the first message's arrival is the shape of the torn
    // read: the ledger accepts the new token carrying a time it has already recorded, and because
    // the next heartbeat repeats that token the promote trigger never revisits it.
    var status = capturedStatus();
    assertThat(status.lastMessageToken()).endsWith(":2");
    assertThat(status.lastMessageAt().toEpochMilli()).isBetween(beforeSecond, afterSecond);
  }

  @Test
  void aMessageLandingDuringAHeartbeatNeverReportsATokenWithoutItsTime() throws Exception {
    startReadOnly();
    reset(observationWriter);

    var connector = connectorFactory.latest(SOURCE_ID);
    var start = new CountDownLatch(1);
    var emitter = Executors.newSingleThreadExecutor();
    try {
      var emitting =
          emitter.submit(
              () -> {
                start.await();
                for (int i = 0; i < 20_000; i++) {
                  connector.emitMessage(new byte[] {1});
                }
                return null;
              });

      start.countDown();
      for (int i = 0; i < 20_000; i++) {
        manager.refreshAllRuntimeStatus();
      }
      emitting.get(30, TimeUnit.SECONDS);
    } finally {
      emitter.shutdownNow();
    }

    // A token names a message, so a status carrying one must carry that message's arrival time.
    // Read as separate fields, a heartbeat crossing the very first message reports a token with a
    // null time; the promote trigger's WHEN clause drops it, and the repeated token means the
    // receipt is never recorded at all.
    var statusCaptor = ArgumentCaptor.forClass(RuntimeStatus.class);
    verify(observationWriter, atLeastOnce()).writeRuntime(statusCaptor.capture(), eq(RUNTIME_TTL));
    assertThat(statusCaptor.getAllValues())
        .allSatisfy(
            status -> {
              if (status.lastMessageToken() != null) {
                assertThat(status.lastMessageAt()).isNotNull();
              }
            });
  }

  @Test
  void aMessageAcceptedBeforeAnIncompatibleTransitionKeepsItsToken() throws Exception {
    startReadOnly();
    connectorFactory.latest(SOURCE_ID).emitMessage(new byte[] {1});
    // No heartbeat has run, so this receipt exists only in memory.
    reset(observationWriter);

    assertThat(manager.ensureIncompatible(SOURCE_ID, TransportKind.UDP, AccessMode.WRITE)).isTrue();

    // The entry is dropped here, so this observation is the last chance to report that receipt -
    // no later heartbeat can reconstruct the token once the session is gone.
    var status = capturedStatus();
    assertThat(status.state()).isEqualTo(ConnectorState.INCOMPATIBLE);
    assertThat(status.lastMessageAt()).isNotNull();
    assertThat(status.lastMessageToken()).isNotNull().endsWith(":1");
  }

  @Test
  void stoppingAnIncompatibleSourceKeepsTheTokenItWasHolding() throws Exception {
    startReadOnly();
    connectorFactory.latest(SOURCE_ID).emitMessage(new byte[] {1});
    manager.ensureIncompatible(SOURCE_ID, TransportKind.UDP, AccessMode.WRITE);
    reset(observationWriter);

    assertThat(manager.ensureStopped(SOURCE_ID)).isTrue();

    var status = capturedStatus();
    assertThat(status.state()).isEqualTo(ConnectorState.STOPPED);
    assertThat(status.lastMessageToken()).isNotNull().endsWith(":1");
  }

  /** The most recent observation written for {@link #SOURCE_ID}. */
  private RuntimeStatus capturedStatus() {
    var statusCaptor = ArgumentCaptor.forClass(RuntimeStatus.class);
    verify(observationWriter, atLeastOnce()).writeRuntime(statusCaptor.capture(), eq(RUNTIME_TTL));
    return statusCaptor.getAllValues().getLast();
  }

  /** The token from the most recent observation written for {@link #SOURCE_ID}. */
  private String capturedToken() {
    var statusCaptor = ArgumentCaptor.forClass(RuntimeStatus.class);
    verify(observationWriter, atLeastOnce()).writeRuntime(statusCaptor.capture(), eq(RUNTIME_TTL));
    return statusCaptor.getAllValues().getLast().lastMessageToken();
  }

  @Test
  void failedStateWritesRuntimeObservationForReconciliationRecovery() throws Exception {
    startReadOnly();
    reset(observationWriter);

    connectorFactory.latest(SOURCE_ID).emitState(ConnectorState.FAILED);

    var statusCaptor = ArgumentCaptor.forClass(RuntimeStatus.class);
    verify(observationWriter).writeRuntime(statusCaptor.capture(), eq(RUNTIME_TTL));
    assertThat(statusCaptor.getValue().id()).isEqualTo(SOURCE_ID);
    assertThat(statusCaptor.getValue().state()).isEqualTo(ConnectorState.FAILED);
    assertThat(statusCaptor.getValue().connectorStartedAt()).isNotNull();
  }

  @Test
  void incompatibleStateIsNotRecoveredByEnsureStarted() throws Exception {
    startReadOnly();
    connectorFactory.latest(SOURCE_ID).setState(ConnectorState.INCOMPATIBLE);

    var outcome = manager.ensureStarted(context(SPEC), TransportKind.TCP, AccessMode.READ);

    assertThat(outcome).isEqualTo(ConnectionManager.StartOutcome.ALREADY_ACTIVE);
    assertThat(connectorFactory.createdCount(SOURCE_ID)).isEqualTo(1);
    assertThat(ConnectorState.INCOMPATIBLE.isActive()).isFalse();
  }

  @Test
  void terminalRuntimeReplacementPassesTheLatestRawCreationInputs() {
    var firstContext = context(SPEC);
    manager.ensureStarted(firstContext, TransportKind.TCP, AccessMode.READ);
    connectorFactory.latest(SOURCE_ID).setState(ConnectorState.DISCONNECTED);
    var replacementContext =
        new ConnectorCreationContext(
            SOURCE_ID, new ConnectionSpec(SPEC.transport()), null, Optional.empty());

    var outcome = manager.ensureStarted(replacementContext, TransportKind.TCP, AccessMode.READ);

    assertThat(outcome).isEqualTo(ConnectionManager.StartOutcome.RESTARTED);
    assertThat(connectorFactory.latestCreation(SOURCE_ID).specification())
        .isSameAs(replacementContext.spec());
  }

  @ParameterizedTest
  @CsvSource({"UDP, WRITE", "UDP, READ_WRITE", "RTSP, WRITE", "RTSP, READ_WRITE"})
  void activeLegacySocketIsStoppedMarkedIncompatibleAndCanBeRepaired(
      TransportKind transport, AccessMode incompatibleAccess) throws Exception {
    var forwardedMessages = new AtomicInteger();
    var listener =
        new ConnectorListener() {
          @Override
          public void onMessage(UUID sourceId, byte[] data) {
            forwardedMessages.incrementAndGet();
          }
        };
    var spec = legacySpec(transport);
    manager.ensureStarted(context(spec), transport, AccessMode.READ, listener);
    var incompatibleConnector = connectorFactory.latest(SOURCE_ID);
    reset(observationWriter);

    assertThat(manager.ensureIncompatible(SOURCE_ID, transport, incompatibleAccess)).isTrue();

    assertThat(incompatibleConnector.stopCount()).isEqualTo(1);
    assertThat(manager.isLoaded(SOURCE_ID)).isFalse();
    assertThat(manager.getConnectionCount()).isZero();
    assertThat(manager.getRuntime(SOURCE_ID).orElseThrow().state())
        .isEqualTo(ConnectorState.INCOMPATIBLE);
    incompatibleConnector.emitMessage(new byte[] {1});
    assertThat(forwardedMessages).hasValue(0);
    var statusCaptor = ArgumentCaptor.forClass(RuntimeStatus.class);
    verify(observationWriter).writeRuntime(statusCaptor.capture(), eq(RUNTIME_TTL));
    assertThat(statusCaptor.getValue().state()).isEqualTo(ConnectorState.INCOMPATIBLE);

    assertThat(manager.ensureStarted(context(spec), transport, AccessMode.READ))
        .isEqualTo(ConnectionManager.StartOutcome.STARTED);
    assertThat(connectorFactory.createdCount(SOURCE_ID)).isEqualTo(2);
    assertThat(manager.getRuntime(SOURCE_ID).orElseThrow().state())
        .isEqualTo(ConnectorState.CONNECTED);
  }

  @Test
  void refreshWhenPostgresWriteFailsIsNonFatal() throws Exception {
    startReadOnly();
    reset(observationWriter);
    doThrow(new IllegalStateException("postgres unavailable"))
        .when(observationWriter)
        .writeRuntime(any(RuntimeStatus.class), eq(RUNTIME_TTL));

    assertThatCode(manager::refreshAllRuntimeStatus).doesNotThrowAnyException();

    verify(observationWriter).writeRuntime(any(RuntimeStatus.class), eq(RUNTIME_TTL));
  }

  @Test
  void stoppedStateWhenPostgresWriteFailsIsNonFatal() throws Exception {
    startReadOnly();
    reset(observationWriter);
    doThrow(new IllegalStateException("postgres unavailable"))
        .when(observationWriter)
        .writeRuntime(any(RuntimeStatus.class), eq(RUNTIME_TTL));

    assertThatCode(() -> manager.ensureStopped(SOURCE_ID)).doesNotThrowAnyException();

    verify(observationWriter).writeRuntime(any(RuntimeStatus.class), eq(RUNTIME_TTL));
  }

  @Test
  void messageAdmittedBeforeGateClosureFinishesWhileLaterMessagesAreDropped() throws Exception {
    var admitted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var publishedCount = new AtomicInteger();
    var listener =
        new ConnectorListener() {
          @Override
          public void onMessage(UUID sourceId, byte[] data) {
            admitted.countDown();
            try {
              if (!release.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release admitted message");
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException("Interrupted while publishing admitted message", e);
            }
            publishedCount.incrementAndGet();
          }
        };
    startReadOnly(listener);
    var executor = Executors.newSingleThreadExecutor();

    try {
      var admittedMessage =
          executor.submit(() -> connectorFactory.latest(SOURCE_ID).emitMessage(new byte[] {1}));
      assertThat(admitted.await(1, TimeUnit.SECONDS)).isTrue();

      assertThat(manager.applyDesiredAccess(SOURCE_ID, AccessMode.WRITE)).isTrue();
      connectorFactory.latest(SOURCE_ID).emitMessage(new byte[] {2});
      assertThat(publishedCount).hasValue(0);

      release.countDown();
      admittedMessage.get(1, TimeUnit.SECONDS);
      assertThat(publishedCount).hasValue(1);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void repeatedObservationFailuresWarnOnceAndRecoveryIsReported() throws Exception {
    var logger = (Logger) LoggerFactory.getLogger(ConnectionManager.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);

    try {
      startReadOnly();
      reset(observationWriter);
      doThrow(new IllegalStateException("postgres unavailable"))
          .when(observationWriter)
          .writeRuntime(any(RuntimeStatus.class), eq(RUNTIME_TTL));

      // Several heartbeats' worth of failures. Warning on each would mean one line per source per
      // 15s for the length of the outage, which is why the previous behaviour was to say nothing.
      manager.refreshAllRuntimeStatus();
      manager.refreshAllRuntimeStatus();
      manager.refreshAllRuntimeStatus();

      var warnings =
          appender.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
      assertThat(warnings).hasSize(1);
      assertThat(warnings.getFirst().getFormattedMessage()).contains(SOURCE_ID.toString());
      // The throwable is the point: the DEBUG line it replaces carried only getMessage().
      assertThat(warnings.getFirst().getThrowableProxy()).isNotNull();

      reset(observationWriter);
      manager.refreshAllRuntimeStatus();

      assertThat(appender.list)
          .filteredOn(event -> event.getLevel() == Level.INFO)
          .extracting(ILoggingEvent::getFormattedMessage)
          .anyMatch(message -> message.startsWith("Runtime observation writes recovered"));
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void accessTransitionsEmitStructuredSourceTransportModeAndCauseContext() {
    var logger = (Logger) LoggerFactory.getLogger(ConnectionManager.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);

    try {
      startReadOnly();
      manager.applyDesiredAccess(SOURCE_ID, AccessMode.WRITE);

      var transition =
          appender.list.stream()
              .filter(event -> event.getFormattedMessage().equals("Runtime access mode transition"))
              .toList()
              .getLast();
      assertThat(transition.getKeyValuePairs())
          .extracting(pair -> pair.key + "=" + pair.value)
          .containsExactlyInAnyOrder(
              "source=" + SOURCE_ID,
              "transport=TCP",
              "oldMode=R",
              "newMode=W",
              "cause=desired_state_reconciliation");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void incompatibilityEmitsStructuredSourceTransportModeAndCauseContext() {
    var logger = (Logger) LoggerFactory.getLogger(ConnectionManager.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);

    try {
      manager.ensureIncompatible(SOURCE_ID, TransportKind.RTSP, AccessMode.READ_WRITE);

      var incompatibility =
          appender.list.stream()
              .filter(
                  event ->
                      event
                          .getFormattedMessage()
                          .equals("Runtime access mode is incompatible with socket transport"))
              .findFirst()
              .orElseThrow();
      assertThat(incompatibility.getKeyValuePairs())
          .extracting(pair -> pair.key + "=" + pair.value)
          .containsExactlyInAnyOrder(
              "source=" + SOURCE_ID,
              "transport=RTSP",
              "oldMode=NONE",
              "newMode=RW",
              "cause=incompatible_transport_access");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  private void startReadOnly(ConnectorListener... listeners) {
    manager.ensureStarted(context(SPEC), TransportKind.TCP, AccessMode.READ, listeners);
  }

  private static ConnectorCreationContext context(ConnectionSpec spec) {
    return new ConnectorCreationContext(SOURCE_ID, spec, null, Optional.empty());
  }

  private static ConnectionSpec legacySpec(TransportKind transport) {
    var transportConfig =
        transport == TransportKind.UDP
            ? new UdpConfig("127.0.0.1", 8087, 8088)
            : new RtspConfig("rtsp://localhost:8554/component", null);
    return new ConnectionSpec(transportConfig);
  }
}
