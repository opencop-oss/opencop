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
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubRecurringBehavior;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConnectionBehaviorConnectorTest {

  private static final UUID SOURCE_ID = UUID.fromString("18d420a8-bc40-4ecb-87ae-38e1a7b8fd48");

  /** An empty plan is still wrapped, so a later settings change can add behaviors. */
  @Test
  void emptyPlanStillDecoratesTheRawConnector() {
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    var scheduler = new ScheduledThreadPoolExecutor(1);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var runtime = new ConnectionBehaviorRuntime(scheduler, executor, Clock.systemUTC());

      var decorated =
          runtime.decorate(
              raw, SOURCE_ID, ConnectionBehaviorPlan.empty(), NoConnectionSettings.INSTANCE);

      assertThat(decorated).isInstanceOf(ConnectionBehaviorSocketConnector.class);
      assertThat(raw.listenerCount()).isOne();
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void stopCancelsPendingAndRunningWorkWithoutShuttingDownSharedResources() throws Exception {
    var schema = testIntegration();
    var invoked = new CountDownLatch(1);
    var interrupted = new CountDownLatch(1);
    var workerCompleted = new CountDownLatch(1);
    var completion = new CountDownLatch(1);
    var plan =
        ConnectionBehaviorRegistryFixture.registryFor(
                schema,
                new StubRecurringBehavior(
                    schema,
                    "recurring",
                    (_, _) -> {
                      invoked.countDown();
                      try {
                        completion.await();
                      } catch (InterruptedException exception) {
                        interrupted.countDown();
                        throw exception;
                      }
                      return new byte[] {8};
                    },
                    Duration.ofSeconds(4)))
            .planFor(schema, NoConnectionSettings.INSTANCE);
    var scheduler = new ControllableScheduler();
    var raw = new RecordingRawSocketConnector(SOURCE_ID);

    var executor = new CoordinatedExecutor();
    try {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, Clock.systemUTC())
              .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);
      decorated.start();
      executor.observeNextCompletion(workerCompleted);
      scheduler.runNext();
      assertThat(invoked.await(1, TimeUnit.SECONDS)).isTrue();

      assertThat(decorated.stop()).isTrue();
      assertThat(decorated.stop()).isTrue();

      assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(workerCompleted.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(executor.observedCompletionWasInterrupted()).isTrue();
      assertThat(scheduler.awaitScheduleCount(2, Duration.ofMillis(100))).isFalse();
      assertThat(scheduler.pendingDelays()).isEmpty();
      assertThat(scheduler.isShutdown()).isFalse();
      assertThat(executor.isShutdown()).isFalse();
    } finally {
      completion.countDown();
      executor.close();
      scheduler.shutdownNow();
    }
  }

  @Test
  void terminalRawStateCancelsPendingRecurrence() {
    var schema = testIntegration();
    var messages =
        new RecordingConnectionMessageFactory(
            "recurring", new byte[] {9}, new LifecycleEventRecorder());
    var scheduler = new ControllableScheduler();
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    var states = new CopyOnWriteArrayList<ConnectorState>();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, Clock.systemUTC())
              .decorate(
                  raw,
                  SOURCE_ID,
                  recurringPlan(schema, messages, Duration.ofSeconds(6)),
                  NoConnectionSettings.INSTANCE);
      decorated.addListener(
          new ConnectorListener() {
            @Override
            public void onStateChange(UUID sourceId, ConnectorState state) {
              states.add(state);
            }
          });
      decorated.start();

      raw.emitState(ConnectorState.DISCONNECTED);
      raw.emitState(ConnectorState.FAILED);
      decorated.stop();

      assertThat(states).containsExactly(ConnectorState.CONNECTED, ConnectorState.DISCONNECTED);
      assertThat(scheduler.pendingDelays()).isEmpty();
      assertThat(scheduler.isShutdown()).isFalse();
      assertThat(executor.isShutdown()).isFalse();
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void terminalCancellationDoesNotWaitForExternalListeners() throws Exception {
    var schema = testIntegration();
    var messages =
        new RecordingConnectionMessageFactory(
            "recurring", new byte[] {13}, new LifecycleEventRecorder());
    var scheduler = new ControllableScheduler();
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    var listenerEntered = new CountDownLatch(1);
    var releaseListener = new CountDownLatch(1);
    var states = new CopyOnWriteArrayList<ConnectorState>();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, Clock.systemUTC())
              .decorate(
                  raw,
                  SOURCE_ID,
                  recurringPlan(schema, messages, Duration.ofSeconds(6)),
                  NoConnectionSettings.INSTANCE);
      decorated.addListener(
          new ConnectorListener() {
            @Override
            public void onStateChange(UUID sourceId, ConnectorState state) {
              states.add(state);
              if (state == ConnectorState.FAILED) {
                listenerEntered.countDown();
                try {
                  releaseListener.await();
                } catch (InterruptedException _) {
                  Thread.currentThread().interrupt();
                }
              }
            }
          });
      decorated.start();

      var terminalNotification =
          Thread.ofVirtual().start(() -> raw.emitState(ConnectorState.FAILED));
      assertThat(listenerEntered.await(1, TimeUnit.SECONDS)).isTrue();

      var competingTerminal =
          Thread.ofVirtual().start(() -> raw.emitState(ConnectorState.DISCONNECTED));
      competingTerminal.join(Duration.ofSeconds(1));

      assertThat(competingTerminal.isAlive()).isFalse();
      assertThat(decorated.stop()).isTrue();
      assertThat(scheduler.pendingDelays()).isEmpty();
      assertThat(states).containsExactly(ConnectorState.CONNECTED, ConnectorState.FAILED);
      assertThat(decorated.state()).isEqualTo(ConnectorState.FAILED);

      releaseListener.countDown();
      terminalNotification.join(Duration.ofSeconds(1));
      assertThat(terminalNotification.isAlive()).isFalse();
    } finally {
      releaseListener.countDown();
      scheduler.shutdownNow();
    }
  }

  @Test
  void rawEventsAreForwardedAndInboundTrafficDoesNotChangeRecurrence() {
    var schema = testIntegration();
    var messages =
        new RecordingConnectionMessageFactory(
            "recurring", new byte[] {10}, new LifecycleEventRecorder());
    var scheduler = new ControllableScheduler();
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    var received = new AtomicReference<byte[]>();
    var receivedError = new AtomicReference<Throwable>();
    var receivedRestart = new AtomicReference<Throwable>();
    var error = new IOException("raw failure");
    var restart = new IllegalStateException("restart");

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, Clock.systemUTC())
              .decorate(
                  raw,
                  SOURCE_ID,
                  recurringPlan(schema, messages, Duration.ofSeconds(8)),
                  NoConnectionSettings.INSTANCE);
      decorated.addListener(
          new ConnectorListener() {
            @Override
            public void onMessage(UUID sourceId, byte[] data) {
              throw new IllegalStateException("planned message-listener failure");
            }

            @Override
            public void onError(UUID sourceId, Throwable cause) {
              throw new IllegalStateException("planned error-listener failure");
            }

            @Override
            public void onRestartRequested(UUID sourceId, Throwable reason) {
              throw new IllegalStateException("planned restart-listener failure");
            }
          });
      decorated.addListener(
          new ConnectorListener() {
            @Override
            public void onMessage(UUID sourceId, byte[] data) {
              received.set(data);
            }

            @Override
            public void onError(UUID sourceId, Throwable cause) {
              receivedError.set(cause);
            }

            @Override
            public void onRestartRequested(UUID sourceId, Throwable reason) {
              receivedRestart.set(reason);
            }
          });
      decorated.start();

      raw.emitMessage(new byte[] {42});
      raw.emitError(error);
      raw.emitRestartRequested(restart);
      raw.emitState(ConnectorState.CONNECTED);

      assertThat(received.get()).containsExactly(42);
      assertThat(receivedError.get()).isSameAs(error);
      assertThat(receivedRestart.get()).isSameAs(restart);
      assertThat(scheduler.pendingDelays()).containsExactly(Duration.ofSeconds(8));
    } finally {
      scheduler.shutdownNow();
    }
  }

  private static TestConnectionSchema testIntegration() {
    return new TestConnectionSchema(
        UUID.fromString("c9569448-d9fa-4fde-82af-81338d997dd2"), 1, "Test", "Test integration");
  }

  private static ConnectionBehaviorPlan recurringPlan(
      TestConnectionSchema schema, RecordingConnectionMessageFactory messages, Duration delay) {
    return ConnectionBehaviorRegistryFixture.registryFor(
            schema, new StubRecurringBehavior(schema, "recurring", messages, delay))
        .planFor(schema, NoConnectionSettings.INSTANCE);
  }
}
