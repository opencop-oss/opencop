// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.testing.integration.connection.behavior.ConnectionBehaviorRegistryFixture;
import com.opencop.core.sdk.testing.integration.connection.behavior.LifecycleEventRecorder;
import com.opencop.core.sdk.testing.integration.connection.behavior.RecordingConnectionMessageFactory;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubReceiveIdleMessageBehavior;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubReceiveIdleReconnectBehavior;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubRecurringBehavior;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConnectionBehaviorReceiveIdleTest {

  private static final UUID SOURCE_ID = UUID.fromString("18d420a8-bc40-4ecb-87ae-38e1a7b8fd48");

  @Test
  void inboundActivityResetsIdleWorkWithoutPostponingRecurrenceAndUsesRemainingDuration()
      throws Exception {
    var schema = testIntegration();
    var recurringMessages =
        new RecordingConnectionMessageFactory(
            "recurring", new byte[] {14}, new LifecycleEventRecorder());
    var idleMessages =
        new RecordingConnectionMessageFactory(
            "idle", new byte[] {15}, new LifecycleEventRecorder());
    var plan =
        ConnectionBehaviorRegistryFixture.registryFor(
                schema,
                new StubRecurringBehavior(
                    schema, "recurring", recurringMessages, Duration.ofSeconds(8)),
                new StubReceiveIdleMessageBehavior(
                    schema, "idle", idleMessages, Duration.ofSeconds(10)),
                new StubReceiveIdleReconnectBehavior(schema, "reconnect", Duration.ofSeconds(30)))
            .planFor(schema, NoConnectionSettings.INSTANCE);
    var scheduler = new ControllableScheduler();
    var clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
    var raw = new RecordingRawSocketConnector(SOURCE_ID);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      new ConnectionBehaviorRuntime(scheduler, executor, clock)
          .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE)
          .start();

      clock.set(Instant.parse("2026-08-25T12:00:06Z"));
      raw.emitMessage(new byte[] {42});

      assertThat(scheduler.delays())
          .containsExactly(
              Duration.ofSeconds(8),
              Duration.ofSeconds(10),
              Duration.ofSeconds(30),
              Duration.ofSeconds(10),
              Duration.ofSeconds(30));
      assertThat(scheduler.pendingDelays())
          .containsExactly(Duration.ofSeconds(8), Duration.ofSeconds(10), Duration.ofSeconds(30));

      clock.set(Instant.parse("2026-08-25T12:00:08Z"));
      scheduler.runNext(Duration.ofSeconds(8));
      assertThat(raw.awaitSentCount(1, Duration.ofSeconds(1))).isTrue();
      assertThat(raw.sentPayloads().getFirst()).containsExactly(14);

      clock.set(Instant.parse("2026-08-25T12:00:10Z"));
      scheduler.runNext(Duration.ofSeconds(10));
      assertThat(scheduler.awaitScheduleCount(7, Duration.ofSeconds(1))).isTrue();
      assertThat(scheduler.pendingDelays()).contains(Duration.ofSeconds(6));

      clock.set(Instant.parse("2026-08-25T12:00:16Z"));
      scheduler.runNext(Duration.ofSeconds(6));
      assertThat(raw.awaitSentCount(2, Duration.ofSeconds(1))).isTrue();
      assertThat(raw.sentPayloads().get(1)).containsExactly(15);
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void inboundActivityCannotLeaveDuplicateIdleTimersWhenPriorDispatchAlreadyEntered()
      throws Exception {
    var schema = testIntegration();
    var messages =
        new RecordingConnectionMessageFactory(
            "idle", new byte[] {19}, new LifecycleEventRecorder());
    var scheduler = new ControllableScheduler();
    var clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
    var raw = new RecordingRawSocketConnector(SOURCE_ID);

    try (var behaviorExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
      new ConnectionBehaviorRuntime(scheduler, behaviorExecutor, clock)
          .decorate(
              raw,
              SOURCE_ID,
              idlePlan(schema, messages, Duration.ofSeconds(10), null),
              NoConnectionSettings.INSTANCE)
          .start();

      clock.set(Instant.parse("2026-08-25T12:00:05Z"));
      var activityClockEntered = new CountDownLatch(1);
      var releaseActivityClock = new CountDownLatch(1);
      clock.blockNextInvocation(activityClockEntered, releaseActivityClock);
      var inbound = Thread.ofVirtual().start(() -> raw.emitMessage(new byte[] {42}));
      assertThat(activityClockEntered.await(1, TimeUnit.SECONDS)).isTrue();

      var dispatchEntered = new CountDownLatch(1);
      var dispatch =
          Thread.ofVirtual()
              .start(() -> scheduler.runNext(Duration.ofSeconds(10), dispatchEntered));
      assertThat(dispatchEntered.await(1, TimeUnit.SECONDS)).isTrue();

      releaseActivityClock.countDown();
      inbound.join();
      dispatch.join();

      assertThat(scheduler.pendingDelays()).containsExactly(Duration.ofSeconds(10));
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void repeatedIdlePingsAreThrottledWhileReconnectRemainsIndependent() throws Exception {
    var schema = testIntegration();
    var messages =
        new RecordingConnectionMessageFactory(
            "idle", new byte[] {16}, new LifecycleEventRecorder());
    var plan = idlePlan(schema, messages, Duration.ofSeconds(10), Duration.ofSeconds(25));
    var scheduler = new ControllableScheduler();
    var clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    var restartCount = new AtomicInteger();
    var restartReason = new AtomicReference<Throwable>();
    var restartRequested = new CountDownLatch(1);
    var reconnectCallbackEntered = new CountDownLatch(1);
    var releaseReconnectCallback = new CountDownLatch(1);
    var inboundForwarded = new CountDownLatch(1);
    var reconnectWorkerInterrupted = new AtomicBoolean();
    var lifecycle = new CopyOnWriteArrayList<String>();
    var rawStateAtDisconnected = new AtomicReference<ConnectorState>();
    var rawStateAtRestart = new AtomicReference<ConnectorState>();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, clock)
              .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);
      decorated.addListener(
          new ConnectorListener() {
            @Override
            public void onStateChange(UUID sourceId, ConnectorState state) {
              lifecycle.add("state:" + state);
              if (state == ConnectorState.DISCONNECTED) {
                rawStateAtDisconnected.set(raw.state());
                reconnectWorkerInterrupted.set(Thread.currentThread().isInterrupted());
                reconnectCallbackEntered.countDown();
                try {
                  releaseReconnectCallback.await();
                } catch (InterruptedException _) {
                  Thread.currentThread().interrupt();
                }
              }
            }

            @Override
            public void onMessage(UUID sourceId, byte[] data) {
              inboundForwarded.countDown();
            }

            @Override
            public void onRestartRequested(UUID sourceId, Throwable reason) {
              lifecycle.add("restart");
              rawStateAtRestart.set(raw.state());
              restartCount.incrementAndGet();
              restartReason.set(reason);
              restartRequested.countDown();
            }
          });
      decorated.start();

      clock.set(Instant.parse("2026-08-25T12:00:10Z"));
      scheduler.runNext(Duration.ofSeconds(10));
      assertThat(raw.awaitSentCount(1, Duration.ofSeconds(1))).isTrue();
      assertThat(scheduler.awaitScheduleCount(3, Duration.ofSeconds(1))).isTrue();

      clock.set(Instant.parse("2026-08-25T12:00:20Z"));
      scheduler.runNext(Duration.ofSeconds(10));
      assertThat(raw.awaitSentCount(2, Duration.ofSeconds(1))).isTrue();
      assertThat(messages.invocations())
          .extracting(
              RecordingConnectionMessageFactory.Invocation::sourceId,
              RecordingConnectionMessageFactory.Invocation::executionTime)
          .containsExactly(
              org.assertj.core.groups.Tuple.tuple(SOURCE_ID, Instant.parse("2026-08-25T12:00:10Z")),
              org.assertj.core.groups.Tuple.tuple(
                  SOURCE_ID, Instant.parse("2026-08-25T12:00:20Z")));

      clock.set(Instant.parse("2026-08-25T12:00:25Z"));
      scheduler.runNext(Duration.ofSeconds(25));

      assertThat(reconnectCallbackEntered.await(1, TimeUnit.SECONDS)).isTrue();
      var inbound = Thread.ofVirtual().start(() -> raw.emitMessage(new byte[] {42}));
      var forwardedDuringCallback = inboundForwarded.await(1, TimeUnit.SECONDS);
      releaseReconnectCallback.countDown();
      assertThat(forwardedDuringCallback).isTrue();
      inbound.join(Duration.ofSeconds(1));
      assertThat(inbound.isAlive()).isFalse();
      assertThat(reconnectWorkerInterrupted).isFalse();
      assertThat(restartRequested.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(restartCount).hasValue(1);
      assertThat(restartReason.get())
          .isInstanceOf(SocketTimeoutException.class)
          .hasMessage("No data received for 25000ms (threshold: 25000ms)");
      assertThat(lifecycle).containsExactly("state:CONNECTED", "state:DISCONNECTED", "restart");
      assertThat(rawStateAtDisconnected).hasValue(ConnectorState.STOPPED);
      assertThat(rawStateAtRestart).hasValue(ConnectorState.STOPPED);
      assertThat(raw.state()).isEqualTo(ConnectorState.STOPPED);
      assertThat(decorated.state()).isEqualTo(ConnectorState.DISCONNECTED);
    } finally {
      releaseReconnectCallback.countDown();
      scheduler.shutdownNow();
    }
  }

  @Test
  void rawStopFailureStillPublishesDisconnectedAndRestartBeforeReportingFailure() throws Exception {
    var closeFailure = new IllegalStateException("raw close failed");
    var schema = testIntegration();
    var plan =
        ConnectionBehaviorRegistryFixture.registryFor(
                schema,
                new StubReceiveIdleReconnectBehavior(schema, "reconnect", Duration.ofSeconds(10)))
            .planFor(schema, NoConnectionSettings.INSTANCE);
    var scheduler = new ControllableScheduler();
    var clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    raw.failStopsWith(closeFailure);
    var lifecycle = new CopyOnWriteArrayList<String>();
    var reportedFailure = new AtomicReference<Throwable>();
    var restartReason = new AtomicReference<Throwable>();
    var uncaughtFailure = new AtomicReference<Throwable>();
    var errorReported = new CountDownLatch(1);
    var restartRequested = new CountDownLatch(1);
    var failureEscaped = new CountDownLatch(1);

    try (var executor =
        Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual()
                .uncaughtExceptionHandler(
                    (_, failure) -> {
                      uncaughtFailure.set(failure);
                      failureEscaped.countDown();
                    })
                .factory())) {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, clock)
              .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);
      decorated.addListener(
          new ConnectorListener() {
            @Override
            public void onStateChange(UUID sourceId, ConnectorState state) {
              lifecycle.add("state:" + state);
            }

            @Override
            public void onError(UUID sourceId, Throwable error) {
              lifecycle.add("error");
              reportedFailure.set(error);
              errorReported.countDown();
            }

            @Override
            public void onRestartRequested(UUID sourceId, Throwable reason) {
              lifecycle.add("restart");
              restartReason.set(reason);
              restartRequested.countDown();
            }
          });
      decorated.start();

      clock.set(Instant.parse("2026-08-25T12:00:10Z"));
      scheduler.runNext(Duration.ofSeconds(10));

      assertThat(restartRequested.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(errorReported.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(failureEscaped.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(lifecycle)
          .containsExactly("state:CONNECTED", "state:DISCONNECTED", "restart", "error");
      assertThat(restartReason.get())
          .isInstanceOf(SocketTimeoutException.class)
          .hasMessage("No data received for 10000ms (threshold: 10000ms)");
      assertThat(reportedFailure).hasValue(closeFailure);
      assertThat(uncaughtFailure).hasValue(closeFailure);
      assertThat(raw.state()).isEqualTo(ConnectorState.STOPPED);
      assertThat(decorated.state()).isEqualTo(ConnectorState.DISCONNECTED);
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void reconnectWinsWhenPingAndReconnectBecomeDueTogether() throws Exception {
    var schema = testIntegration();
    var messages =
        new RecordingConnectionMessageFactory(
            "idle", new byte[] {21}, new LifecycleEventRecorder());
    var plan = idlePlan(schema, messages, Duration.ofSeconds(10), Duration.ofSeconds(10));
    var scheduler = new ControllableScheduler();
    var clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
    var raw = new RecordingRawSocketConnector(SOURCE_ID);

    try (var executor = Executors.newSingleThreadExecutor()) {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, clock)
              .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);
      decorated.start();

      clock.set(Instant.parse("2026-08-25T12:00:10Z"));
      scheduler.runNext(Duration.ofSeconds(10));
      executor.submit(() -> {}).get(1, TimeUnit.SECONDS);

      assertThat(messages.invocations()).isEmpty();
      assertThat(raw.sentPayloads()).isEmpty();

      scheduler.runNext(Duration.ofSeconds(10));
      executor.submit(() -> {}).get(1, TimeUnit.SECONDS);

      assertThat(raw.state()).isEqualTo(ConnectorState.STOPPED);
      assertThat(decorated.state()).isEqualTo(ConnectorState.DISCONNECTED);
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void reconnectWinsWhenEqualDeadlineWorkersStartTogether() throws Exception {
    for (int iteration = 0; iteration < 100; iteration++) {
      var schema = testIntegration();
      var messages =
          new RecordingConnectionMessageFactory(
              "idle", new byte[] {23}, new LifecycleEventRecorder());
      var plan = idlePlan(schema, messages, Duration.ofSeconds(10), Duration.ofSeconds(10));
      var scheduler = new ControllableScheduler();
      var clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
      var raw = new RecordingRawSocketConnector(SOURCE_ID);
      var restartRequested = new CountDownLatch(1);

      try (var executor = new CoordinatedExecutor()) {
        var decorated =
            new ConnectionBehaviorRuntime(scheduler, executor, clock)
                .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);
        decorated.addListener(
            new ConnectorListener() {
              @Override
              public void onRestartRequested(UUID sourceId, Throwable reason) {
                restartRequested.countDown();
              }
            });
        decorated.start();
        executor.coordinateNext(2);

        clock.set(Instant.parse("2026-08-25T12:00:10Z"));
        scheduler.runNext(Duration.ofSeconds(10));
        scheduler.runNext(Duration.ofSeconds(10));
        assertThat(executor.awaitCoordinated(Duration.ofSeconds(1))).isTrue();
        executor.releaseCoordinated();

        assertThat(restartRequested.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(messages.invocations()).isEmpty();
        assertThat(raw.sentPayloads()).isEmpty();
      } finally {
        scheduler.shutdownNow();
      }
    }
  }

  @Test
  void reconnectClosesImmediatelyWithoutWaitingForAnInFlightPing() throws Exception {
    var schema = testIntegration();
    var messages =
        new RecordingConnectionMessageFactory(
            "idle", new byte[] {22}, new LifecycleEventRecorder());
    var plan = idlePlan(schema, messages, Duration.ofSeconds(5), Duration.ofSeconds(10));
    var scheduler = new ControllableScheduler();
    var clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    var sendEntered = new CountDownLatch(1);
    var sendInterrupted = new CountDownLatch(1);
    var releaseSend = new CountDownLatch(1);
    var restartRequested = new CountDownLatch(1);
    var pingCompleted = new CountDownLatch(1);
    var executor = new CoordinatedExecutor();
    raw.blockSends(sendEntered, sendInterrupted, releaseSend);

    try {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, clock)
              .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);
      decorated.addListener(
          new ConnectorListener() {
            @Override
            public void onRestartRequested(UUID sourceId, Throwable reason) {
              restartRequested.countDown();
            }
          });
      decorated.start();

      executor.observeNextCompletion(pingCompleted);
      clock.set(Instant.parse("2026-08-25T12:00:05Z"));
      scheduler.runNext(Duration.ofSeconds(5));
      assertThat(sendEntered.await(1, TimeUnit.SECONDS)).isTrue();

      clock.set(Instant.parse("2026-08-25T12:00:10Z"));
      scheduler.runNext(Duration.ofSeconds(10));

      assertThat(restartRequested.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(sendInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(releaseSend.getCount()).isOne();
      releaseSend.countDown();
      assertThat(pingCompleted.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(executor.observedCompletionWasInterrupted()).isTrue();
      assertThat(raw.state()).isEqualTo(ConnectorState.STOPPED);
      assertThat(decorated.state()).isEqualTo(ConnectorState.DISCONNECTED);
      assertThat(scheduler.pendingDelays()).isEmpty();
    } finally {
      releaseSend.countDown();
      executor.close();
      scheduler.shutdownNow();
    }
  }

  @Test
  void slowSuccessfulIdlePingWaitsFullIntervalAfterCompletion() throws Exception {
    var schema = testIntegration();
    var messages =
        new RecordingConnectionMessageFactory(
            "idle", new byte[] {24}, new LifecycleEventRecorder());
    var plan = idlePlan(schema, messages, Duration.ofSeconds(5), null);
    var scheduler = new ControllableScheduler();
    var clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    var sendEntered = new CountDownLatch(1);
    var releaseSend = new CountDownLatch(1);
    raw.blockSends(sendEntered, new CountDownLatch(1), releaseSend);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      new ConnectionBehaviorRuntime(scheduler, executor, clock)
          .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE)
          .start();

      clock.set(Instant.parse("2026-08-25T12:00:05Z"));
      scheduler.runNext(Duration.ofSeconds(5));
      assertThat(sendEntered.await(1, TimeUnit.SECONDS)).isTrue();

      clock.set(Instant.parse("2026-08-25T12:00:12Z"));
      releaseSend.countDown();

      assertThat(raw.awaitSentCount(1, Duration.ofSeconds(1))).isTrue();
      assertThat(scheduler.awaitScheduleCount(2, Duration.ofSeconds(1))).isTrue();
      assertThat(scheduler.pendingDelays()).containsExactly(Duration.ofSeconds(5));
    } finally {
      releaseSend.countDown();
      scheduler.shutdownNow();
    }
  }

  @Test
  void idleSendFailureIsNonFatalAndLaterEligiblePingStillRuns() throws Exception {
    var schema = testIntegration();
    var messages =
        new RecordingConnectionMessageFactory(
            "idle", new byte[] {17}, new LifecycleEventRecorder());
    var plan = idlePlan(schema, messages, Duration.ofSeconds(5), null);
    var scheduler = new ControllableScheduler();
    var clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    var sendEntered = new CountDownLatch(1);
    var releaseSend = new CountDownLatch(1);
    raw.blockSends(sendEntered, new CountDownLatch(1), releaseSend);
    raw.failSendsWith(new IOException("planned idle failure"));

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      new ConnectionBehaviorRuntime(scheduler, executor, clock)
          .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE)
          .start();

      clock.set(Instant.parse("2026-08-25T12:00:05Z"));
      scheduler.runNext(Duration.ofSeconds(5));
      assertThat(sendEntered.await(1, TimeUnit.SECONDS)).isTrue();

      clock.set(Instant.parse("2026-08-25T12:00:12Z"));
      releaseSend.countDown();

      assertThat(scheduler.awaitScheduleCount(2, Duration.ofSeconds(1))).isTrue();
      assertThat(raw.state()).isEqualTo(ConnectorState.CONNECTED);
      assertThat(scheduler.pendingDelays()).containsExactly(Duration.ofSeconds(5));

      raw.failSendsWith(null);
      clock.set(Instant.parse("2026-08-25T12:00:17Z"));
      scheduler.runNext(Duration.ofSeconds(5));

      assertThat(raw.awaitSentCount(1, Duration.ofSeconds(1))).isTrue();
      assertThat(raw.sentPayloads().getFirst()).containsExactly(17);
    } finally {
      releaseSend.countDown();
      scheduler.shutdownNow();
    }
  }

  @Test
  void stopCancelsPendingIdlePingAndReconnect() throws Exception {
    var schema = testIntegration();
    var messages =
        new RecordingConnectionMessageFactory(
            "idle", new byte[] {18}, new LifecycleEventRecorder());
    var scheduler = new ControllableScheduler();
    var clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    var sendEntered = new CountDownLatch(1);
    var sendInterrupted = new CountDownLatch(1);
    var releaseSend = new CountDownLatch(1);
    var workerCompleted = new CountDownLatch(1);
    var executor = new CoordinatedExecutor();
    raw.blockSends(sendEntered, sendInterrupted, releaseSend);

    try {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, clock)
              .decorate(
                  raw,
                  SOURCE_ID,
                  idlePlan(schema, messages, Duration.ofSeconds(5), Duration.ofSeconds(20)),
                  NoConnectionSettings.INSTANCE);
      decorated.start();

      clock.set(Instant.parse("2026-08-25T12:00:01Z"));
      raw.emitState(ConnectorState.CONNECTED);
      assertThat(scheduler.pendingDelays())
          .containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(20));

      clock.set(Instant.parse("2026-08-25T12:00:05Z"));
      executor.observeNextCompletion(workerCompleted);
      scheduler.runNext(Duration.ofSeconds(5));
      assertThat(sendEntered.await(1, TimeUnit.SECONDS)).isTrue();

      decorated.stop();
      decorated.stop();

      assertThat(sendInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
      releaseSend.countDown();
      assertThat(workerCompleted.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(executor.observedCompletionWasInterrupted()).isTrue();
      assertThat(scheduler.pendingDelays()).isEmpty();
      assertThat(raw.sentPayloads()).isEmpty();
    } finally {
      releaseSend.countDown();
      executor.close();
      scheduler.shutdownNow();
    }
  }

  private static TestConnectionSchema testIntegration() {
    return new TestConnectionSchema(
        UUID.fromString("c9569448-d9fa-4fde-82af-81338d997dd2"), 1, "Test", "Test integration");
  }

  private static ConnectionBehaviorPlan idlePlan(
      TestConnectionSchema schema,
      RecordingConnectionMessageFactory messages,
      Duration pingThreshold,
      Duration reconnectThreshold) {
    var ping = new StubReceiveIdleMessageBehavior(schema, "idle", messages, pingThreshold);
    if (reconnectThreshold == null) {
      return ConnectionBehaviorRegistryFixture.registryFor(schema, ping)
          .planFor(schema, NoConnectionSettings.INSTANCE);
    }
    return ConnectionBehaviorRegistryFixture.registryFor(
            schema,
            ping,
            new StubReceiveIdleReconnectBehavior(schema, "reconnect", reconnectThreshold))
        .planFor(schema, NoConnectionSettings.INSTANCE);
  }
}
