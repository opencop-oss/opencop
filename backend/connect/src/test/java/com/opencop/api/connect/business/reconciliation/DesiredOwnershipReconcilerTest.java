// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.reconciliation;

import static com.opencop.api.connect.business.support.ConnectorRegistryTestSupport.rawOnly;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.opencop.api.connect.business.connector.ConnectionLifecycleService;
import com.opencop.api.connect.business.connector.ConnectionManager;
import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.api.connect.business.connector.DesiredSocketSource;
import com.opencop.api.connect.business.support.RecordingSocketConnectorFactory;
import com.opencop.api.connect.business.support.fake.FakeDesiredSocketSourceRepository;
import com.opencop.api.connect.business.support.fake.NoopAccessModeTransitionRecorder;
import com.opencop.api.connect.business.support.fake.NoopIntegrationAvailabilityRecorder;
import com.opencop.api.connect.ports.gateway.repository.DesiredSocketSourceRepository;
import com.opencop.api.connect.technical.gateway.socket.ConnectorRegistry;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.gateway.api.SourceRestartEpochReader;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorStateReason;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DesiredOwnershipReconcilerTest {

  private static final String INSTANCE_ID = "connect-unit-a";
  private static final String OTHER_INSTANCE_ID = "connect-unit-b";
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441001");
  private static final UUID PROFILE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441002");
  private static final UUID OTHER_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655441003");
  private static final ConnectionSpec SPEC =
      new ConnectionSpec(
          new TcpConfig("127.0.0.1", 8087, 3000, 1000, true, null, new NoSecurity()));
  private static final ConnectionSpec UPDATED_SPEC =
      new ConnectionSpec(
          new TcpConfig("127.0.0.1", 8088, 3000, 1000, true, null, new NoSecurity()));
  private static final ConnectorListener NOOP_LISTENER = new ConnectorListener() {};
  private static final Duration RECOVERY_INTERVAL = Duration.ofSeconds(60);

  @Test
  void lostStart_missingDesiredSource_isStartedFromDbState() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));

    var result = harness.reconciler.reconcileOnce();

    assertEquals(1, result.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(
        ConnectorState.CONNECTED, harness.manager.getRuntime(SOURCE_ID).orElseThrow().state());
  }

  @Test
  void lostStop_disabledLocalSource_isStoppedFromDbState() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.repository.put(socketSource(false, INSTANCE_ID));

    var result = harness.reconciler.reconcileOnce();

    assertEquals(1, result.stoppedCount());
    assertFalse(harness.manager.isLoaded(SOURCE_ID));
  }

  @Test
  void lostStop_deletedLocalSource_isStoppedFromMissingDbRow() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.repository.remove(SOURCE_ID);

    var result = harness.reconciler.reconcileOnce();

    assertEquals(1, result.stoppedCount());
    assertFalse(harness.manager.isLoaded(SOURCE_ID));
  }

  @Test
  void reassignedLocalSource_isStoppedWhenDbAssignsAnotherInstance() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.repository.put(socketSource(true, OTHER_INSTANCE_ID));

    var result = harness.reconciler.reconcileOnce();

    assertEquals(1, result.stoppedCount());
    assertFalse(harness.manager.isLoaded(SOURCE_ID));
  }

  @Test
  void failedDesiredSource_isRecovered() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.factory.latest(SOURCE_ID).setState(ConnectorState.FAILED);

    var result = harness.reconciler.reconcileOnce();

    assertEquals(1, result.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(2, harness.factory.createdCount(SOURCE_ID));
  }

  @Test
  void disconnectedDesiredSource_isRecovered() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.factory.latest(SOURCE_ID).setState(ConnectorState.DISCONNECTED);

    var result = harness.reconciler.reconcileOnce();

    assertEquals(1, result.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(2, harness.factory.createdCount(SOURCE_ID));
  }

  @Test
  void disconnectedDesiredSource_isRetainedDuringReadOutageAndRecoveredAfterward() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.factory.latest(SOURCE_ID).setState(ConnectorState.DISCONNECTED);
    harness.repository.setFailReads(true);

    var outage = harness.reconciler.reconcileOnce();

    assertTrue(outage.failed());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(
        ConnectorState.DISCONNECTED, harness.manager.getRuntime(SOURCE_ID).orElseThrow().state());
    assertEquals(1, harness.factory.createdCount(SOURCE_ID));

    harness.repository.setFailReads(false);

    var recovery = harness.reconciler.reconcileOnce();

    assertEquals(1, recovery.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(
        ConnectorState.CONNECTED, harness.manager.getRuntime(SOURCE_ID).orElseThrow().state());
    assertEquals(2, harness.factory.createdCount(SOURCE_ID));
  }

  @Test
  void stoppedDesiredSource_isRecovered() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.factory.latest(SOURCE_ID).setState(ConnectorState.STOPPED);

    var result = harness.reconciler.reconcileOnce();

    assertEquals(1, result.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(2, harness.factory.createdCount(SOURCE_ID));
  }

  @Test
  void incompatibleDesiredSource_isNotRecovered() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.factory.latest(SOURCE_ID).setState(ConnectorState.INCOMPATIBLE);

    var result = harness.reconciler.reconcileOnce();

    assertEquals(0, result.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(1, harness.factory.createdCount(SOURCE_ID));
  }

  @Test
  void terminalRecoveryBackoff_skipsRepeatedRecoveryUntilIntervalElapses() {
    var clock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
    var harness = Harness.create(Duration.ofSeconds(60), clock);
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.factory.latest(SOURCE_ID).setState(ConnectorState.FAILED);

    var first = harness.reconciler.reconcileOnce();
    harness.factory.latest(SOURCE_ID).setState(ConnectorState.FAILED);
    var second = harness.reconciler.reconcileOnce();
    clock.advance(Duration.ofSeconds(60));
    var third = harness.reconciler.reconcileOnce();

    assertEquals(1, first.startedCount());
    assertEquals(0, second.startedCount());
    assertEquals(1, third.startedCount());
    assertEquals(3, harness.factory.createdCount(SOURCE_ID));
  }

  @Test
  void terminalRecoveryFailureAfterAppliedRestartEpochDoesNotBypassBackoff() {
    var clock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
    var harness = Harness.create(Duration.ofSeconds(60), clock);
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.restartEpochs.put(SOURCE_ID, 1L);
    harness.reconciler.reconcileOnce();
    assertFalse(harness.lifecycleService.hasUnappliedRestartEpoch(SOURCE_ID, 1L));
    harness.factory.latest(SOURCE_ID).setState(ConnectorState.FAILED);
    harness.factory.failCreateOnceFor(SOURCE_ID);

    var failedRecovery = harness.reconciler.reconcileOnce();
    var beforeInterval = harness.reconciler.reconcileOnce();
    var loadedBeforeInterval = harness.manager.isLoaded(SOURCE_ID);
    clock.advance(Duration.ofSeconds(60));
    var afterInterval = harness.reconciler.reconcileOnce();

    assertEquals(1, failedRecovery.lifecycleFailureCount());
    assertFalse(loadedBeforeInterval);
    assertEquals(0, beforeInterval.lifecycleFailureCount());
    assertEquals(0, beforeInterval.startedCount());
    assertEquals(1, afterInterval.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
  }

  @Test
  void terminalRecoveryBackoffIsBypassedByNewerManualEpoch() {
    var clock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
    var harness = Harness.create(Duration.ofSeconds(60), clock);
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.restartEpochs.put(SOURCE_ID, 1L);
    harness.reconciler.reconcileOnce();
    assertFalse(harness.lifecycleService.hasUnappliedRestartEpoch(SOURCE_ID, 1L));
    harness.factory.latest(SOURCE_ID).setState(ConnectorState.FAILED);
    harness.factory.failCreateOnceFor(SOURCE_ID);
    var failedRecovery = harness.reconciler.reconcileOnce();
    var sameEpochBeforeInterval = harness.reconciler.reconcileOnce();

    harness.restartEpochs.put(SOURCE_ID, 2L);
    var newerEpochBeforeInterval = harness.reconciler.reconcileOnce();

    assertEquals(1, failedRecovery.lifecycleFailureCount());
    assertEquals(0, sameEpochBeforeInterval.startedCount());
    assertEquals(1, newerEpochBeforeInterval.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertFalse(harness.lifecycleService.hasUnappliedRestartEpoch(SOURCE_ID, 2L));
  }

  /**
   * The integration cannot become available inside this process, so an unavailable connection is
   * re-evaluated under the recovery backoff rather than on every tick.
   */
  @Test
  void unavailableIntegration_isRetriedOnlyOnceTheRecoveryIntervalElapses() {
    var clock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
    var harness = Harness.create(RECOVERY_INTERVAL, clock);
    harness.repository.put(takSource(true, INSTANCE_ID));

    var first = harness.reconciler.reconcileOnce();
    var unavailable = harness.manager.getRuntime(SOURCE_ID).orElseThrow();
    harness.reconciler.reconcileOnce();
    harness.reconciler.reconcileOnce();
    harness.assertAvailabilityChecks(1);
    clock.advance(RECOVERY_INTERVAL);
    harness.reconciler.reconcileOnce();
    harness.assertAvailabilityChecks(2);
    // The recovery attempt keeps its backoff too.
    harness.reconciler.reconcileOnce();
    harness.assertAvailabilityChecks(2);

    assertEquals(0, first.startedCount());
    assertEquals(0, first.lifecycleFailureCount());
    assertEquals(ConnectorState.FAILED, unavailable.state());
    assertEquals(ConnectorStateReason.INTEGRATION_UNAVAILABLE, unavailable.stateReason());
    assertEquals(0, harness.factory.createAttemptCount(SOURCE_ID));
  }

  @Test
  void unavailableIntegration_isReevaluatedImmediatelyForANewerRestartEpoch() {
    var clock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
    var harness = Harness.create(RECOVERY_INTERVAL, clock);
    harness.repository.put(takSource(true, INSTANCE_ID));
    harness.reconciler.reconcileOnce();
    harness.reconciler.reconcileOnce();

    harness.restartEpochs.put(SOURCE_ID, 1L);
    harness.reconciler.reconcileOnce();
    harness.reconciler.reconcileOnce();

    harness.assertAvailabilityChecks(2);
    assertFalse(harness.lifecycleService.hasUnappliedRestartEpoch(SOURCE_ID, 1L));
  }

  @Test
  void unavailableIntegration_isStoppedWhenTheConnectionMovesToAnotherInstance() {
    var harness = Harness.create();
    harness.repository.put(takSource(true, INSTANCE_ID));
    harness.reconciler.reconcileOnce();
    harness.repository.put(takSource(true, OTHER_INSTANCE_ID));

    var result = harness.reconciler.reconcileOnce();

    assertEquals(1, result.stoppedCount());
    assertTrue(harness.manager.getAllRuntime().isEmpty());
  }

  @Test
  void unavailableIntegration_isStoppedWhenTheConnectionIsDisabled() {
    var harness = Harness.create();
    harness.repository.put(takSource(true, INSTANCE_ID));
    harness.reconciler.reconcileOnce();
    harness.repository.put(takSource(false, INSTANCE_ID));

    var result = harness.reconciler.reconcileOnce();

    assertEquals(1, result.stoppedCount());
    assertTrue(harness.manager.getAllRuntime().isEmpty());
  }

  @Test
  void missingDesiredStartFailureUsesRecoveryBackoff() {
    var clock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
    var harness = Harness.create(Duration.ofSeconds(60), clock);
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.factory.failCreateOnceFor(SOURCE_ID);

    var first = harness.reconciler.reconcileOnce();
    var second = harness.reconciler.reconcileOnce();
    clock.advance(Duration.ofSeconds(60));
    var third = harness.reconciler.reconcileOnce();

    assertEquals(1, first.lifecycleFailureCount());
    assertEquals(0, first.startedCount());
    assertEquals(0, second.lifecycleFailureCount());
    assertEquals(0, second.startedCount());
    assertEquals(1, third.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(2, harness.factory.createAttemptCount(SOURCE_ID));
  }

  @Test
  void firstTimeMissingDesiredStartIsNotGatedByBackoff() {
    var clock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
    var harness = Harness.create(Duration.ofSeconds(60), clock);
    harness.repository.put(socketSource(true, INSTANCE_ID));

    var result = harness.reconciler.reconcileOnce();

    assertEquals(1, result.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(1, harness.factory.createAttemptCount(SOURCE_ID));
  }

  @Test
  void activeDesiredSource_isNoop() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();

    var result = harness.reconciler.reconcileOnce();

    assertEquals(0, result.startedCount());
    assertEquals(0, result.stoppedCount());
    assertEquals(0, result.connectedRestartedCount());
    assertEquals(1, harness.factory.createdCount(SOURCE_ID));
  }

  @Test
  void fullModeRestartsConnectedSourceFromDbState() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    var firstConnector = harness.factory.latest(SOURCE_ID);
    harness.repository.put(socketSource(SOURCE_ID, true, INSTANCE_ID, UPDATED_SPEC));

    var result =
        harness.reconciler.reconcileOnce(DesiredOwnershipReconciler.ReconciliationMode.FULL);

    assertEquals(1, result.startedCount());
    assertEquals(1, result.stoppedCount());
    assertEquals(1, result.connectedRestartedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(1, firstConnector.stopCount());
    assertEquals(2, harness.factory.createdCount(SOURCE_ID));
    assertEquals(UPDATED_SPEC, harness.factory.latest(SOURCE_ID).spec());
  }

  @Test
  void fullModeConnectedRestartFailureBacksOffMissingRuntimeRetry() {
    var clock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
    var harness = Harness.create(Duration.ofSeconds(60), clock);
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.factory.failCreateOnceFor(SOURCE_ID);

    var full = harness.reconciler.reconcileOnce(DesiredOwnershipReconciler.ReconciliationMode.FULL);
    var beforeInterval = harness.reconciler.reconcileOnce();
    var loadedBeforeInterval = harness.manager.isLoaded(SOURCE_ID);
    clock.advance(Duration.ofSeconds(60));
    var afterInterval = harness.reconciler.reconcileOnce();

    assertEquals(1, full.lifecycleFailureCount());
    assertFalse(loadedBeforeInterval);
    assertEquals(0, beforeInterval.lifecycleFailureCount());
    assertEquals(0, beforeInterval.startedCount());
    assertEquals(1, afterInterval.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(3, harness.factory.createAttemptCount(SOURCE_ID));
  }

  @Test
  void restartEpochBypassesRecoveryBackoffAfterFullRestartFailure() {
    var clock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
    var harness = Harness.create(Duration.ofSeconds(60), clock);
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.factory.failCreateOnceFor(SOURCE_ID);
    harness.reconciler.reconcileOnce(DesiredOwnershipReconciler.ReconciliationMode.FULL);
    harness.restartEpochs.put(SOURCE_ID, 1L);

    var result = harness.reconciler.reconcileOnce();

    assertEquals(1, result.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertFalse(harness.lifecycleService.hasUnappliedRestartEpoch(SOURCE_ID, 1L));
    assertEquals(3, harness.factory.createAttemptCount(SOURCE_ID));
  }

  @Test
  void fullModeDoesNotRestartDisabledSource() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.repository.put(socketSource(false, INSTANCE_ID));

    var result =
        harness.reconciler.reconcileOnce(DesiredOwnershipReconciler.ReconciliationMode.FULL);

    assertEquals(0, result.startedCount());
    assertEquals(1, result.stoppedCount());
    assertEquals(0, result.connectedRestartedCount());
    assertFalse(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(1, harness.factory.createdCount(SOURCE_ID));
  }

  @Test
  void fullModeDoesNotRestartSourceMadeStaleBetweenDesiredStateReads() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.repository.setFindByIdsHook(
        () -> harness.repository.put(socketSource(false, INSTANCE_ID)));

    var result =
        harness.reconciler.reconcileOnce(DesiredOwnershipReconciler.ReconciliationMode.FULL);

    assertEquals(0, result.startedCount());
    assertEquals(1, result.stoppedCount());
    assertEquals(0, result.connectedRestartedCount());
    assertFalse(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(1, harness.factory.createdCount(SOURCE_ID));
  }

  @Test
  void fullModeSkipsNewAndConnectingSources() {
    var harness = Harness.create();
    harness.repository.put(socketSource(SOURCE_ID, true, INSTANCE_ID));
    harness.repository.put(socketSource(OTHER_SOURCE_ID, true, INSTANCE_ID));
    harness.lifecycleService.ensureStarted(desired(socketSource(SOURCE_ID, true, INSTANCE_ID)));
    harness.lifecycleService.ensureStarted(
        desired(socketSource(OTHER_SOURCE_ID, true, INSTANCE_ID)));
    harness.factory.latest(SOURCE_ID).setState(ConnectorState.NEW);
    harness.factory.latest(OTHER_SOURCE_ID).setState(ConnectorState.CONNECTING);

    var result =
        harness.reconciler.reconcileOnce(DesiredOwnershipReconciler.ReconciliationMode.FULL);

    assertEquals(0, result.startedCount());
    assertEquals(0, result.stoppedCount());
    assertEquals(0, result.connectedRestartedCount());
    assertEquals(1, harness.factory.createdCount(SOURCE_ID));
    assertEquals(1, harness.factory.createdCount(OTHER_SOURCE_ID));
  }

  @Test
  void restartEpochWinsOverFullModeConnectedRestart() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.restartEpochs.put(SOURCE_ID, 1L);

    var result =
        harness.reconciler.reconcileOnce(DesiredOwnershipReconciler.ReconciliationMode.FULL);

    assertEquals(1, result.startedCount());
    assertEquals(1, result.stoppedCount());
    assertEquals(0, result.connectedRestartedCount());
    assertEquals(2, harness.factory.createdCount(SOURCE_ID));
    assertFalse(harness.lifecycleService.hasUnappliedRestartEpoch(SOURCE_ID, 1L));
  }

  @Test
  void sameRestartEpochFailureDoesNotRetryEveryNormalTick() {
    var clock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
    var harness = Harness.create(Duration.ofSeconds(60), clock);
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.restartEpochs.put(SOURCE_ID, 1L);
    harness.factory.failCreateOnceFor(SOURCE_ID);

    var first = harness.reconciler.reconcileOnce();
    var second = harness.reconciler.reconcileOnce();
    var loadedAfterSameEpochRetry = harness.manager.isLoaded(SOURCE_ID);
    var sameEpochStillUnapplied = harness.lifecycleService.hasUnappliedRestartEpoch(SOURCE_ID, 1L);
    harness.restartEpochs.put(SOURCE_ID, 2L);
    var third = harness.reconciler.reconcileOnce();

    assertEquals(1, first.lifecycleFailureCount());
    assertFalse(loadedAfterSameEpochRetry);
    assertTrue(sameEpochStillUnapplied);
    assertEquals(0, second.lifecycleFailureCount());
    assertEquals(0, second.startedCount());
    assertEquals(1, third.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertFalse(harness.lifecycleService.hasUnappliedRestartEpoch(SOURCE_ID, 2L));
    assertEquals(3, harness.factory.createAttemptCount(SOURCE_ID));
  }

  @Test
  void prongTwoLocalRestartFailureDoesNotLoopEveryNormalTick() {
    var clock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
    var harness = Harness.create(Duration.ofSeconds(60), clock);
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.factory.failCreateOnceFor(SOURCE_ID);

    assertThrows(
        IllegalStateException.class, () -> harness.lifecycleService.restartIfDesired(SOURCE_ID));
    harness.factory.failCreateOnceFor(SOURCE_ID);
    var pickup = harness.reconciler.reconcileOnce();
    var beforeInterval = harness.reconciler.reconcileOnce();
    var loadedBeforeInterval = harness.manager.isLoaded(SOURCE_ID);
    clock.advance(Duration.ofSeconds(60));
    var afterInterval = harness.reconciler.reconcileOnce();

    assertEquals(1, pickup.lifecycleFailureCount());
    assertFalse(loadedBeforeInterval);
    assertEquals(0, beforeInterval.lifecycleFailureCount());
    assertEquals(0, beforeInterval.startedCount());
    assertEquals(1, afterInterval.startedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(4, harness.factory.createAttemptCount(SOURCE_ID));
  }

  @Test
  void fullModeTerminalStatesRecoverAsBefore() {
    for (var state :
        List.of(ConnectorState.FAILED, ConnectorState.DISCONNECTED, ConnectorState.STOPPED)) {
      var harness = Harness.create();
      harness.repository.put(socketSource(true, INSTANCE_ID));
      harness.startDesired();
      harness.factory.latest(SOURCE_ID).setState(state);

      var result =
          harness.reconciler.reconcileOnce(DesiredOwnershipReconciler.ReconciliationMode.FULL);

      assertEquals(1, result.startedCount());
      assertEquals(0, result.connectedRestartedCount());
      assertTrue(harness.manager.isLoaded(SOURCE_ID));
      assertEquals(2, harness.factory.createdCount(SOURCE_ID));
    }
  }

  @Test
  void missedRestartNudge_activeDesiredSource_bouncesOnceFromRestartEpoch() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.restartEpochs.put(SOURCE_ID, 1L);

    var first = harness.reconciler.reconcileOnce();
    var second = harness.reconciler.reconcileOnce();

    assertEquals(1, first.startedCount());
    assertEquals(1, first.stoppedCount());
    assertEquals(0, second.startedCount());
    assertEquals(0, second.stoppedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(2, harness.factory.createdCount(SOURCE_ID));
  }

  @Test
  void restartEpochCommittedBetweenDesiredStateReads_restartsFromTheCommittedSpec() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    commitUpdatedSpecDuringDesiredStateRead(harness, 1L);

    var result = harness.reconciler.reconcileOnce();

    assertEquals(1, result.startedCount());
    assertEquals(2, harness.factory.createdCount(SOURCE_ID));
    assertEquals(UPDATED_SPEC, harness.factory.latestCreation(SOURCE_ID).specification());
    assertFalse(harness.lifecycleService.hasUnappliedRestartEpoch(SOURCE_ID, 1L));
  }

  @Test
  void restartEpochCommittedBetweenDesiredStateReads_startsAMissingRuntimeFromTheCommittedSpec() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    commitUpdatedSpecDuringDesiredStateRead(harness, 1L);

    var result = harness.reconciler.reconcileOnce();

    assertEquals(1, result.startedCount());
    assertEquals(UPDATED_SPEC, harness.factory.latestCreation(SOURCE_ID).specification());
    assertFalse(harness.lifecycleService.hasUnappliedRestartEpoch(SOURCE_ID, 1L));
  }

  @Test
  void failedDesiredSourceRestart_doesNotPreventAnotherDesiredSourceFromStarting() {
    var badSource = socketSource(SOURCE_ID, true, INSTANCE_ID);
    var goodSource = socketSource(OTHER_SOURCE_ID, true, INSTANCE_ID);
    var repository = new OrderedDesiredSocketSourceRepository(List.of(badSource, goodSource));
    var restartEpochs = new FakeRestartEpochReader();
    var factory = new RecordingSocketConnectorFactory();
    var manager = Harness.connectionManager(rawOnly(factory));
    var lifecycleService =
        new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);
    var reconciler =
        new DesiredOwnershipReconciler(
            manager,
            repository,
            lifecycleService,
            INSTANCE_ID,
            Duration.ZERO,
            restartEpochs,
            DesiredStateOutagePolicy.continueExisting(),
            Clock.systemUTC());
    lifecycleService.ensureStarted(desired(badSource));
    factory.failCreateFor(SOURCE_ID);
    restartEpochs.put(SOURCE_ID, 1L);

    var result = reconciler.reconcileOnce();

    assertFalse(result.failed());
    assertEquals(1, result.startedCount());
    assertFalse(manager.isLoaded(SOURCE_ID));
    assertTrue(manager.isLoaded(OTHER_SOURCE_ID));
    assertTrue(lifecycleService.hasUnappliedRestartEpoch(SOURCE_ID, 1L));
    assertEquals(1, factory.createdCount(OTHER_SOURCE_ID));
  }

  @Test
  void missingDesiredSourceWithRestartEpoch_startsAndMarksEpochApplied() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.restartEpochs.put(SOURCE_ID, 7L);

    var first = harness.reconciler.reconcileOnce();
    var second = harness.reconciler.reconcileOnce();

    assertEquals(1, first.startedCount());
    assertEquals(0, first.stoppedCount());
    assertEquals(0, second.startedCount());
    assertEquals(0, second.stoppedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
    assertEquals(1, harness.factory.createdCount(SOURCE_ID));
  }

  @Test
  void repositoryFailure_abortsWithoutLifecycleActions() {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.repository.setFailReads(true);

    var result = harness.reconciler.reconcileOnce();

    assertTrue(result.failed());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
  }

  @Test
  void desiredStateOutageStopPolicy_waitsForGraceBeforeStoppingLocalSources() {
    var clock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
    var harness =
        Harness.create(
            Duration.ZERO, clock, new DesiredStateOutagePolicy(true, Duration.ofSeconds(60)));
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.repository.setFailReads(true);

    var first = harness.reconciler.reconcileOnce();
    clock.advance(Duration.ofSeconds(59));
    var second = harness.reconciler.reconcileOnce();
    clock.advance(Duration.ofSeconds(1));
    var third = harness.reconciler.reconcileOnce();

    assertTrue(first.failed());
    assertEquals(0, first.stoppedCount());
    assertEquals(0, second.stoppedCount());
    assertEquals(1, third.stoppedCount());
    assertFalse(harness.manager.isLoaded(SOURCE_ID));
  }

  @Test
  void desiredStateOutageStopPolicy_successfulReadResetsOutageTimer() {
    var clock = new MutableClock(Instant.parse("2026-05-01T00:00:00Z"));
    var harness =
        Harness.create(
            Duration.ZERO, clock, new DesiredStateOutagePolicy(true, Duration.ofSeconds(60)));
    harness.repository.put(socketSource(true, INSTANCE_ID));
    harness.startDesired();
    harness.repository.setFailReads(true);
    harness.reconciler.reconcileOnce();
    clock.advance(Duration.ofSeconds(30));
    harness.repository.setFailReads(false);
    harness.reconciler.reconcileOnce();

    harness.repository.setFailReads(true);
    clock.advance(Duration.ofSeconds(31));
    var afterReset = harness.reconciler.reconcileOnce();

    assertEquals(0, afterReset.stoppedCount());
    assertTrue(harness.manager.isLoaded(SOURCE_ID));
  }

  @Test
  void overlappingReconcile_isSkipped() throws Exception {
    var harness = Harness.create();
    harness.repository.put(socketSource(true, INSTANCE_ID));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    harness.repository.setFindAssignedHook(new BlockingGate(entered, release)::block);
    var executor = Executors.newSingleThreadExecutor();

    try {
      var first = executor.submit(() -> harness.reconciler.reconcileOnce());
      assertTrue(entered.await(1, TimeUnit.SECONDS));

      var second = harness.reconciler.reconcileOnce();
      release.countDown();

      assertTrue(second.skipped());
      assertFalse(first.get(1, TimeUnit.SECONDS).skipped());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  private static void commitUpdatedSpecDuringDesiredStateRead(Harness harness, long epoch) {
    harness.repository.setFindByIdsHook(
        () -> {
          harness.repository.setFindByIdsHook(null);
          harness.repository.put(socketSource(SOURCE_ID, true, INSTANCE_ID, UPDATED_SPEC));
          harness.restartEpochs.put(SOURCE_ID, epoch);
        });
  }

  private static SocketDataSource socketSource(boolean enabled, String assignedInstance) {
    return socketSource(SOURCE_ID, enabled, assignedInstance);
  }

  private static SocketDataSource socketSource(
      UUID sourceId, boolean enabled, String assignedInstance) {
    return socketSource(sourceId, enabled, assignedInstance, SPEC);
  }

  /** A source selecting TAK, which the harness's runtime registers no implementation for. */
  private static SocketDataSource takSource(boolean enabled, String assignedInstance) {
    return socketSource(
        SOURCE_ID, enabled, assignedInstance, SPEC, new com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference(java.util.UUID.fromString("714e8096-8832-4486-ad47-5422f86f918a"), 1));
  }

  private static SocketDataSource socketSource(
      UUID sourceId, boolean enabled, String assignedInstance, ConnectionSpec spec) {
    return socketSource(sourceId, enabled, assignedInstance, spec, null);
  }

  private static SocketDataSource socketSource(
      UUID sourceId,
      boolean enabled,
      String assignedInstance,
      ConnectionSpec spec,
      IntegrationConnectionSchemaReference integration) {
    return new SocketDataSource(
        sourceId,
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

  private static DesiredSocketSource desired(SocketDataSource source) {
    return new DesiredSocketSource(source, Optional.empty());
  }

  private record OrderedDesiredSocketSourceRepository(List<SocketDataSource> sources)
      implements DesiredSocketSourceRepository {

    @Override
    public String diagnosticLabel() {
      return "ordered desired-state repository";
    }

    @Override
    public Optional<DesiredSocketSource> findSocketSourceById(UUID sourceId) {
      return sources.stream()
          .filter(source -> source.id().equals(sourceId))
          .findFirst()
          .map(DesiredOwnershipReconcilerTest::desired);
    }

    @Override
    public List<DesiredSocketSource> findEnabledSocketSourcesAssignedTo(String instanceId) {
      return sources.stream()
          .filter(source -> source.enabled() && instanceId.equals(source.assignedInstance()))
          .map(DesiredOwnershipReconcilerTest::desired)
          .toList();
    }

    @Override
    public Map<UUID, SocketDataSource> findSocketSourcesByIds(Set<UUID> sourceIds) {
      var found = new ConcurrentHashMap<UUID, SocketDataSource>();
      for (var source : sources) {
        if (sourceIds.contains(source.id())) {
          found.put(source.id(), source);
        }
      }
      return found;
    }
  }

  private record BlockingGate(CountDownLatch entered, CountDownLatch release) {
    void block() {
      entered.countDown();
      try {
        assertTrue(release.await(1, TimeUnit.SECONDS));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
  }

  private static final class Harness {
    final FakeDesiredSocketSourceRepository repository = new FakeDesiredSocketSourceRepository();
    final FakeRestartEpochReader restartEpochs = new FakeRestartEpochReader();
    final RecordingSocketConnectorFactory factory = new RecordingSocketConnectorFactory();
    final ConnectorRegistry registry = spy(rawOnly(factory));
    final ConnectionManager manager;
    final ConnectionLifecycleService lifecycleService;
    final DesiredOwnershipReconciler reconciler;

    private Harness(Duration recoveryMinInterval, Clock clock) {
      this(recoveryMinInterval, clock, DesiredStateOutagePolicy.continueExisting());
    }

    private Harness(
        Duration recoveryMinInterval, Clock clock, DesiredStateOutagePolicy outagePolicy) {
      manager = connectionManager(registry);
      lifecycleService =
          new ConnectionLifecycleService(manager, repository, NOOP_LISTENER, INSTANCE_ID);
      reconciler =
          new DesiredOwnershipReconciler(
              manager,
              repository,
              lifecycleService,
              INSTANCE_ID,
              recoveryMinInterval,
              restartEpochs,
              outagePolicy,
              clock);
    }

    static Harness create() {
      return create(Duration.ZERO, Clock.systemUTC());
    }

    static Harness create(Duration recoveryMinInterval, Clock clock) {
      return new Harness(recoveryMinInterval, clock);
    }

    static Harness create(
        Duration recoveryMinInterval, Clock clock, DesiredStateOutagePolicy outagePolicy) {
      return new Harness(recoveryMinInterval, clock, outagePolicy);
    }

    void startDesired() {
      lifecycleService.ensureStarted(desired(socketSource(true, INSTANCE_ID)));
    }

    /** Verifies how often the runtime evaluated whether it could run the source's integration. */
    void assertAvailabilityChecks(int expected) {
      verify(registry, times(expected)).canRun(any());
    }

    private static ConnectionManager connectionManager(ConnectorRegistry registry) {
      return new ConnectionManager(
          registry,
          INSTANCE_ID,
          null,
          null,
          NoopAccessModeTransitionRecorder.INSTANCE,
          NoopIntegrationAvailabilityRecorder.INSTANCE);
    }
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }
  }

  private static final class FakeRestartEpochReader extends ConcurrentHashMap<UUID, Long>
      implements SourceRestartEpochReader {

    @Override
    public Map<UUID, Long> findRestartEpochs(Set<UUID> sourceIds) {
      var found = new ConcurrentHashMap<UUID, Long>();
      for (var sourceId : sourceIds) {
        var epoch = get(sourceId);
        if (epoch != null) {
          found.put(sourceId, epoch);
        }
      }
      return found;
    }
  }
}
