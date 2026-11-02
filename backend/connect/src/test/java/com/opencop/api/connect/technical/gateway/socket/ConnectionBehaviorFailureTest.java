// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.testing.integration.connection.behavior.ConnectionBehaviorRegistryFixture;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubOnConnectBehavior;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubReceiveIdleMessageBehavior;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubRecurringBehavior;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConnectionBehaviorFailureTest {

  private static final UUID SOURCE_ID = UUID.fromString("18d420a8-bc40-4ecb-87ae-38e1a7b8fd48");
  private static final Duration WAIT = Duration.ofSeconds(1);

  @Test
  void fatalOnConnectErrorTerminatesARealTcpConnectorAndRemainsUncaught() throws Exception {
    var fatal = new AssertionError("fatal on-connect");
    var closed = new AtomicBoolean();
    var raw =
        new TcpConnector(
            SOURCE_ID,
            _ -> null,
            () ->
                new Session() {
                  @Override
                  public ByteArrayInputStream input() {
                    return new ByteArrayInputStream(new byte[0]);
                  }

                  @Override
                  public ByteArrayOutputStream output() {
                    return new ByteArrayOutputStream();
                  }

                  @Override
                  public void close() {
                    closed.set(true);
                  }
                });
    var plan = onConnectPlan(fatal);
    var scheduler = new ControllableScheduler();
    var lifecycle = new FatalLifecycleRecorder();
    var uncaught = new UncaughtFailureRecorder();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, Clock.systemUTC())
              .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);
      decorated.addListener(lifecycle);
      var group =
          new ThreadGroup("fatal-on-connect") {
            @Override
            public void uncaughtException(Thread thread, Throwable failure) {
              uncaught.record(thread, failure);
            }
          };
      var starter = new Thread(group, decorated::start, "fatal-on-connect-starter");

      starter.start();
      starter.join(WAIT);

      assertThat(lifecycle.awaitFailed()).isTrue();
      assertThat(uncaught.await()).isTrue();
      assertThat(closed).isTrue();
      assertThat(raw.state()).isEqualTo(ConnectorState.STOPPED);
      assertThat(decorated.state()).isEqualTo(ConnectorState.FAILED);
      assertThat(lifecycle.error()).isSameAs(fatal);
      assertThat(uncaught.failure()).isSameAs(fatal);
      assertThat(lifecycle.states())
          .containsExactly(
              ConnectorState.CONNECTING, ConnectorState.CONNECTED, ConnectorState.FAILED);
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void fatalRecurringErrorTerminatesTheConnectionAndEscapesItsTaskWrapper() throws Exception {
    var fatal = new AssertionError("fatal recurring");
    var stopFailure = new IllegalStateException("raw close failed");
    var schema = testIntegration();
    var plan =
        ConnectionBehaviorRegistryFixture.registryFor(
                schema,
                new StubRecurringBehavior(
                    schema,
                    "fatal-recurring",
                    (_, _) -> {
                      throw fatal;
                    },
                    Duration.ofSeconds(1)))
            .planFor(schema, NoConnectionSettings.INSTANCE);
    var scheduler = new ControllableScheduler();
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    raw.failStopsWith(stopFailure);
    var lifecycle = new FatalLifecycleRecorder();
    var uncaught = new UncaughtFailureRecorder();

    try (var executor =
        Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().uncaughtExceptionHandler(uncaught::record).factory())) {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, Clock.systemUTC())
              .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);
      decorated.addListener(lifecycle);
      decorated.start();

      scheduler.runNext();

      assertFatalBackgroundFailure(decorated, raw, lifecycle, uncaught, fatal);
      assertThat(fatal.getSuppressed()).containsExactly(stopFailure);
      assertThat(scheduler.pendingDelays()).isEmpty();
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void fatalIdleErrorTerminatesTheConnectionAndEscapesItsTaskWrapper() throws Exception {
    var fatal = new AssertionError("fatal idle");
    var schema = testIntegration();
    var plan =
        ConnectionBehaviorRegistryFixture.registryFor(
                schema,
                new StubReceiveIdleMessageBehavior(
                    schema,
                    "fatal-idle",
                    (_, _) -> {
                      throw fatal;
                    },
                    Duration.ofSeconds(1)))
            .planFor(schema, NoConnectionSettings.INSTANCE);
    var scheduler = new ControllableScheduler();
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    var lifecycle = new FatalLifecycleRecorder();
    var uncaught = new UncaughtFailureRecorder();
    var clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));

    try (var executor =
        Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().uncaughtExceptionHandler(uncaught::record).factory())) {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, clock)
              .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);
      decorated.addListener(lifecycle);
      decorated.start();
      clock.set(Instant.parse("2026-08-25T12:00:01Z"));

      scheduler.runNext();

      assertFatalBackgroundFailure(decorated, raw, lifecycle, uncaught, fatal);
      assertThat(scheduler.pendingDelays()).isEmpty();
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void fatalOnConnectErrorEscapesTheSynchronousRawCallback() {
    var schema = testIntegration();
    var plan =
        ConnectionBehaviorRegistryFixture.registryFor(
                schema,
                new StubOnConnectBehavior(
                    schema,
                    "fatal",
                    0,
                    () -> true,
                    (_, _) -> {
                      throw new AssertionError("fatal");
                    }))
            .planFor(schema, NoConnectionSettings.INSTANCE);
    var scheduler = new ControllableScheduler();
    var raw = new RecordingRawSocketConnector(SOURCE_ID);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, Clock.systemUTC())
              .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);

      assertThatThrownBy(decorated::start).isInstanceOf(AssertionError.class).hasMessage("fatal");
    } finally {
      scheduler.shutdownNow();
    }
  }

  private static ConnectionBehaviorPlan onConnectPlan(AssertionError fatal) {
    var schema = testIntegration();
    return ConnectionBehaviorRegistryFixture.registryFor(
            schema,
            new StubOnConnectBehavior(
                schema,
                "fatal-on-connect",
                (_, _) -> {
                  throw fatal;
                }))
        .planFor(schema, NoConnectionSettings.INSTANCE);
  }

  private static void assertFatalBackgroundFailure(
      SocketConnector decorated,
      RecordingRawSocketConnector raw,
      FatalLifecycleRecorder lifecycle,
      UncaughtFailureRecorder uncaught,
      AssertionError fatal)
      throws InterruptedException {
    assertThat(lifecycle.awaitFailed()).isTrue();
    assertThat(uncaught.await()).isTrue();
    assertThat(raw.state()).isEqualTo(ConnectorState.STOPPED);
    assertThat(decorated.state()).isEqualTo(ConnectorState.FAILED);
    assertThat(lifecycle.error()).isSameAs(fatal);
    assertThat(uncaught.failure()).isSameAs(fatal);
    assertThat(lifecycle.states()).containsExactly(ConnectorState.CONNECTED, ConnectorState.FAILED);
  }

  private static TestConnectionSchema testIntegration() {
    return new TestConnectionSchema(
        UUID.fromString("c9569448-d9fa-4fde-82af-81338d997dd2"), 1, "Test", "Test integration");
  }

  private static final class FatalLifecycleRecorder implements ConnectorListener {

    private final CopyOnWriteArrayList<ConnectorState> states = new CopyOnWriteArrayList<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final CountDownLatch failed = new CountDownLatch(1);

    @Override
    public void onStateChange(UUID sourceId, ConnectorState state) {
      states.add(state);
      if (state == ConnectorState.FAILED) {
        failed.countDown();
      }
    }

    @Override
    public void onError(UUID sourceId, Throwable failure) {
      error.set(failure);
    }

    boolean awaitFailed() throws InterruptedException {
      return failed.await(WAIT.toNanos(), TimeUnit.NANOSECONDS);
    }

    Throwable error() {
      return error.get();
    }

    List<ConnectorState> states() {
      return List.copyOf(states);
    }
  }

  private static final class UncaughtFailureRecorder {

    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final CountDownLatch recorded = new CountDownLatch(1);

    void record(Thread ignoredThread, Throwable throwable) {
      failure.set(throwable);
      recorded.countDown();
    }

    boolean await() throws InterruptedException {
      return recorded.await(WAIT.toNanos(), TimeUnit.NANOSECONDS);
    }

    Throwable failure() {
      return failure.get();
    }
  }
}
