// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.ports.integration.connection.behavior.message.ConnectionMessageFactory;
import com.opencop.core.sdk.testing.integration.connection.behavior.ConnectionBehaviorRegistryFixture;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubOnConnectBehavior;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubRecurringBehavior;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

/** Swapping a running connector's behavior plan without touching its raw socket. */
class ConnectionBehaviorPlanSwapTest {

  private static final UUID SOURCE_ID = UUID.fromString("5b6c1d2e-3f40-4a51-8b62-7c83d94ea5f6");
  private static final TestConnectionSchema SCHEMA =
      new TestConnectionSchema(
          UUID.fromString("c9569448-d9fa-4fde-82af-81338d997dd2"), 1, "Test", "Test integration");
  private static final Duration OLD_DELAY = Duration.ofSeconds(20);
  private static final Duration NEW_DELAY = Duration.ofSeconds(45);
  private static final String NEW_SETTINGS = "new settings";
  private static final Duration AWAIT = Duration.ofSeconds(2);

  private final ControllableScheduler scheduler = new ControllableScheduler();
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final RecordingRawSocketConnector raw = new RecordingRawSocketConnector(SOURCE_ID);
  private final CountDownLatch releaseSends = new CountDownLatch(1);

  @AfterEach
  void shutDown() {
    releaseSends.countDown();
    executor.close();
    scheduler.shutdownNow();
  }

  @Test
  void swapsTheRunningPlanAndAnnouncesItWithoutTouchingTheSocket() throws Exception {
    var connector = decorate(plan(1, OLD_DELAY));
    connector.start();
    raw.awaitSentCount(1, Duration.ofSeconds(1));

    var swapped = connector.applyPlan(plan(2, NEW_DELAY), NEW_SETTINGS);

    assertThat(swapped).isTrue();
    assertThat(raw.awaitSentCount(2, AWAIT)).isTrue();
    assertThat(raw.state()).isEqualTo(ConnectorState.CONNECTED);
    assertThat(raw.sentPayloads())
        .extracting(payload -> payload[0])
        .containsExactly((byte) 1, (byte) 2);
    assertThat(scheduler.pendingDelays()).containsExactly(NEW_DELAY);
    assertThat(connector.effectiveSettings()).isEqualTo(NEW_SETTINGS);
  }

  @Test
  void anEmptyPlanCanGainBehaviorsBySwapping() throws Exception {
    var connector = decorate(ConnectionBehaviorPlan.empty());
    connector.start();

    connector.applyPlan(plan(2, NEW_DELAY), NEW_SETTINGS);

    assertThat(raw.awaitSentCount(1, AWAIT)).isTrue();
    assertThat(raw.sentPayloads()).extracting(payload -> payload[0]).containsExactly((byte) 2);
    assertThat(scheduler.pendingDelays()).containsExactly(NEW_DELAY);
  }

  @Test
  @Timeout(value = 10, threadMode = ThreadMode.SEPARATE_THREAD)
  void aSwapReturnsWithoutWaitingForItsAnnouncementToBeSent() throws Exception {
    var connector = decorate(ConnectionBehaviorPlan.empty());
    connector.start();
    var entered = new CountDownLatch(1);
    raw.blockSends(entered, new CountDownLatch(1), releaseSends);

    var swapped = connector.applyPlan(plan(2, NEW_DELAY), NEW_SETTINGS);

    assertThat(swapped).isTrue();
    assertThat(entered.await(AWAIT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
    releaseSends.countDown();
    assertThat(raw.awaitSentCount(1, AWAIT)).isTrue();
  }

  @Test
  @Timeout(10)
  void aFatalAnnouncementFailsTheConnectorWithoutReachingTheSwapCaller() throws Exception {
    var connector = decorate(ConnectionBehaviorPlan.empty());
    var failed = new CountDownLatch(1);
    connector.addListener(
        new ConnectorListener() {
          @Override
          public void onStateChange(UUID sourceId, ConnectorState state) {
            if (state == ConnectorState.FAILED) {
              failed.countDown();
            }
          }
        });
    connector.start();

    var swapped =
        connector.applyPlan(
            onConnectPlan(
                (_, _) -> {
                  throw new AssertionError("broken integration");
                }),
            NEW_SETTINGS);

    assertThat(swapped).isTrue();
    assertThat(failed.await(AWAIT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
    assertThat(connector.state()).isEqualTo(ConnectorState.FAILED);
  }

  @Test
  @Timeout(value = 10, threadMode = ThreadMode.SEPARATE_THREAD)
  void aSwapLetsTheOldPlansInFlightSendFinishInsteadOfInterruptingIt() throws Exception {
    var connector = decorate(plan(1, OLD_DELAY));
    connector.start();
    assertThat(raw.awaitSentCount(1, AWAIT)).isTrue();
    var entered = new CountDownLatch(1);
    var interrupted = new CountDownLatch(1);
    raw.blockSends(entered, interrupted, releaseSends);
    scheduler.runNext(OLD_DELAY);
    assertThat(entered.await(AWAIT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();

    connector.applyPlan(plan(2, NEW_DELAY), NEW_SETTINGS);

    assertThat(interrupted.await(200, TimeUnit.MILLISECONDS)).isFalse();
    releaseSends.countDown();
    assertThat(raw.awaitSentCount(3, AWAIT)).isTrue();
    assertThat(raw.sentPayloads())
        .extracting(payload -> payload[0])
        .containsExactlyInAnyOrder((byte) 1, (byte) 11, (byte) 2);
    assertThat(raw.state()).isEqualTo(ConnectorState.CONNECTED);
  }

  @Test
  void aPlanSwappedBeforeConnectingRunsOnceConnected() throws Exception {
    var connector = decorate(plan(1, OLD_DELAY));

    connector.applyPlan(plan(2, NEW_DELAY), NEW_SETTINGS);

    assertThat(raw.sentPayloads()).isEmpty();
    assertThat(scheduler.pendingDelays()).isEmpty();

    connector.start();

    assertThat(raw.sentPayloads()).extracting(payload -> payload[0]).containsExactly((byte) 2);
    assertThat(scheduler.pendingDelays()).containsExactly(NEW_DELAY);
  }

  @Test
  void aSwapAfterTheConnectorClosedChangesNothing() {
    var connector = decorate(plan(1, OLD_DELAY));
    connector.start();
    connector.stop();

    var swapped = connector.applyPlan(plan(2, NEW_DELAY), NEW_SETTINGS);

    assertThat(swapped).isFalse();
    assertThat(raw.sentPayloads()).extracting(payload -> payload[0]).containsExactly((byte) 1);
    assertThat(scheduler.pendingDelays()).isEmpty();
    assertThat(connector.effectiveSettings()).isEqualTo(NoConnectionSettings.INSTANCE);
  }

  @Test
  void aTerminalStateAfterASwapCancelsTheSwappedPlan() {
    var connector = decorate(plan(1, OLD_DELAY));
    connector.start();
    connector.applyPlan(plan(2, NEW_DELAY), NEW_SETTINGS);

    raw.emitState(ConnectorState.DISCONNECTED);

    assertThat(scheduler.pendingDelays()).isEmpty();
  }

  private ConnectionBehaviorSocketConnector decorate(ConnectionBehaviorPlan plan) {
    return (ConnectionBehaviorSocketConnector)
        new ConnectionBehaviorRuntime(scheduler, executor, Clock.systemUTC())
            .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);
  }

  /** A plan whose only behavior builds its on-connect message with {@code factory}. */
  private static ConnectionBehaviorPlan onConnectPlan(ConnectionMessageFactory factory) {
    return ConnectionBehaviorRegistryFixture.registryFor(
            SCHEMA, new StubOnConnectBehavior(SCHEMA, "announce", factory))
        .planFor(SCHEMA, NoConnectionSettings.INSTANCE);
  }

  /** A plan announcing {@code marker} on connect and repeating it after {@code delay}. */
  private static ConnectionBehaviorPlan plan(int marker, Duration delay) {
    return ConnectionBehaviorRegistryFixture.registryFor(
            SCHEMA,
            new StubOnConnectBehavior(SCHEMA, "announce", (_, _) -> new byte[] {(byte) marker}),
            new StubRecurringBehavior(
                SCHEMA, "refresh", (_, _) -> new byte[] {(byte) (marker + 10)}, delay))
        .planFor(SCHEMA, NoConnectionSettings.INSTANCE);
  }
}
