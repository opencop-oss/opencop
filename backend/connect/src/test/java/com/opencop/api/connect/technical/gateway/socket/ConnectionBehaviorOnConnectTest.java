// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.testing.integration.connection.behavior.ConnectionBehaviorRegistryFixture;
import com.opencop.core.sdk.testing.integration.connection.behavior.LifecycleEventRecorder;
import com.opencop.core.sdk.testing.integration.connection.behavior.RecordingConnectionMessageFactory;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubOnConnectBehavior;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ConnectionBehaviorOnConnectTest {

  private static final UUID SOURCE_ID = UUID.fromString("18d420a8-bc40-4ecb-87ae-38e1a7b8fd48");

  @Test
  void stopDuringOnConnectSendDoesNotAttemptLaterActions() throws Exception {
    var events = new LifecycleEventRecorder();
    var schema =
        new TestConnectionSchema(
            UUID.fromString("2db9c8d8-0570-4572-a452-f6891d3e3e54"), 1, "Test", "Test integration");
    var blockedSend = new RecordingConnectionMessageFactory("blocked-send", new byte[] {1}, events);
    var shouldNotRun =
        new RecordingConnectionMessageFactory("should-not-run", new byte[] {2}, events);
    var plan =
        ConnectionBehaviorRegistryFixture.registryFor(
                schema,
                new StubOnConnectBehavior(schema, "blocked-send", 0, () -> true, blockedSend),
                new StubOnConnectBehavior(schema, "should-not-run", 1, () -> true, shouldNotRun))
            .planFor(schema, NoConnectionSettings.INSTANCE);
    var sendEntered = new CountDownLatch(1);
    var sendInterrupted = new CountDownLatch(1);
    var releaseSend = new CountDownLatch(1);
    var workerCompleted = new CountDownLatch(1);
    var raw = new RecordingRawSocketConnector(SOURCE_ID, events);
    raw.blockSends(sendEntered, sendInterrupted, releaseSend);
    var scheduler = new ScheduledThreadPoolExecutor(1);

    try (var executor = new CoordinatedExecutor()) {
      executor.observeNextCompletion(workerCompleted);
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, Clock.systemUTC())
              .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);
      var starter = Thread.ofVirtual().start(decorated::start);
      try {
        assertThat(sendEntered.await(1, TimeUnit.SECONDS)).isTrue();

        assertThat(decorated.stop()).isTrue();

        assertThat(sendInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
        releaseSend.countDown();
        assertThat(workerCompleted.await(1, TimeUnit.SECONDS)).isTrue();
        starter.join(Duration.ofSeconds(1));
        assertThat(starter.isAlive()).isFalse();
        assertThat(executor.observedCompletionWasInterrupted()).isTrue();
        assertThat(shouldNotRun.invocations()).isEmpty();
        assertThat(events.events())
            .extracting(LifecycleEventRecorder.Event::name)
            .contains("blocked-send")
            .doesNotContain("should-not-run");
      } finally {
        releaseSend.countDown();
        starter.join(Duration.ofSeconds(1));
      }
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void connectedIsForwardedBeforeOrderedOnConnectAttemptsAndRawContinuation() throws Exception {
    var events = new LifecycleEventRecorder();
    var schema =
        new TestConnectionSchema(
            UUID.fromString("c9569448-d9fa-4fde-82af-81338d997dd2"), 1, "Test", "Test integration");
    var first =
        RecordingConnectionMessageFactory.failing(
            "first", new byte[] {1}, events, new IOException("planned failure"));
    var second = new RecordingConnectionMessageFactory("second", new byte[] {2}, events);
    var registry =
        ConnectionBehaviorRegistryFixture.registryFor(
            schema,
            new StubOnConnectBehavior(schema, "first", -1, () -> true, first),
            new StubOnConnectBehavior(schema, "second", 1, () -> true, second));
    var plan = registry.planFor(schema, NoConnectionSettings.INSTANCE);
    var raw = new RecordingRawSocketConnector(SOURCE_ID, events);
    var scheduler = new ScheduledThreadPoolExecutor(1);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var runtime =
          new ConnectionBehaviorRuntime(
              scheduler,
              executor,
              Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC));
      var decorated = runtime.decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);
      decorated.addListener(
          new ConnectorListener() {
            @Override
            public void onStateChange(
                UUID sourceId, com.opencop.core.sdk.ports.gateway.socket.ConnectorState state) {
              if (state == com.opencop.core.sdk.ports.gateway.socket.ConnectorState.CONNECTED) {
                events.record("external-connected");
              }
            }
          });

      decorated.start();

      assertThat(events.events())
          .extracting(LifecycleEventRecorder.Event::name)
          .containsExactly("external-connected", "first", "second", "raw-send", "raw-continuation");
      assertThat(raw.sentPayloads())
          .singleElement()
          .satisfies(bytes -> assertThat(bytes).containsExactly(2));
    } finally {
      scheduler.shutdownNow();
    }

    var attemptStarted = new CountDownLatch(1);
    var attemptInterrupted = new CountDownLatch(1);
    var workerCompleted = new CountDownLatch(1);
    var releaseAttempt = new CountDownLatch(1);
    var cancellationEvents = new LifecycleEventRecorder();
    var shouldNotRun =
        new RecordingConnectionMessageFactory("should-not-run", new byte[] {4}, cancellationEvents);
    var cancellationPlan =
        ConnectionBehaviorRegistryFixture.registryFor(
                schema,
                new StubOnConnectBehavior(
                    schema,
                    "blocked",
                    0,
                    () -> true,
                    (_, _) -> {
                      cancellationEvents.record("blocked");
                      attemptStarted.countDown();
                      try {
                        releaseAttempt.await();
                      } catch (InterruptedException exception) {
                        attemptInterrupted.countDown();
                        throw exception;
                      }
                      return new byte[] {3};
                    }),
                new StubOnConnectBehavior(schema, "should-not-run", 1, () -> true, shouldNotRun))
            .planFor(schema, NoConnectionSettings.INSTANCE);
    var cancellationRaw = new RecordingRawSocketConnector(SOURCE_ID, cancellationEvents);
    var cancellationScheduler = new ScheduledThreadPoolExecutor(1);

    try (var executor = new CoordinatedExecutor()) {
      executor.observeNextCompletion(workerCompleted);
      var decorated =
          new ConnectionBehaviorRuntime(cancellationScheduler, executor, Clock.systemUTC())
              .decorate(
                  cancellationRaw, SOURCE_ID, cancellationPlan, NoConnectionSettings.INSTANCE);
      var starter = Thread.ofVirtual().start(decorated::start);
      try {
        assertThat(attemptStarted.await(1, TimeUnit.SECONDS)).isTrue();

        assertThat(decorated.stop()).isTrue();
        assertThat(decorated.stop()).isTrue();

        assertThat(attemptInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(workerCompleted.await(1, TimeUnit.SECONDS)).isTrue();
        starter.join(Duration.ofSeconds(1));
        assertThat(starter.isAlive()).isFalse();
        assertThat(shouldNotRun.invocations()).isEmpty();
        assertThat(cancellationRaw.sentPayloads()).isEmpty();
        assertThat(cancellationEvents.events())
            .extracting(LifecycleEventRecorder.Event::name)
            .contains("blocked")
            .doesNotContain("should-not-run");
      } finally {
        releaseAttempt.countDown();
        starter.join(Duration.ofSeconds(1));
      }
    } finally {
      cancellationScheduler.shutdownNow();
    }

    var submissionEntered = new CountDownLatch(1);
    var releaseSubmission = new CountDownLatch(1);
    var stopCompleted = new CountDownLatch(1);
    var submissionEvents = new LifecycleEventRecorder();
    var submissionMessages =
        new RecordingConnectionMessageFactory(
            "submission-should-not-run", new byte[] {5}, submissionEvents);
    var submissionPlan =
        ConnectionBehaviorRegistryFixture.registryFor(
                schema,
                new StubOnConnectBehavior(schema, "submission-should-not-run", submissionMessages))
            .planFor(schema, NoConnectionSettings.INSTANCE);
    var submissionRaw = new RecordingRawSocketConnector(SOURCE_ID, submissionEvents);
    var submissionScheduler = new ScheduledThreadPoolExecutor(1);

    try (var executor = new CoordinatedExecutor()) {
      executor.blockNextSubmission(submissionEntered, releaseSubmission);
      var decorated =
          new ConnectionBehaviorRuntime(submissionScheduler, executor, Clock.systemUTC())
              .decorate(submissionRaw, SOURCE_ID, submissionPlan, NoConnectionSettings.INSTANCE);
      var starter = Thread.ofVirtual().start(decorated::start);
      Thread stopper = null;
      try {
        assertThat(submissionEntered.await(1, TimeUnit.SECONDS)).isTrue();
        stopper =
            Thread.ofVirtual()
                .start(
                    () -> {
                      try {
                        decorated.stop();
                      } finally {
                        stopCompleted.countDown();
                      }
                    });
        var stoppedWithoutWaitingForSubmission = stopCompleted.await(1, TimeUnit.SECONDS);
        releaseSubmission.countDown();

        assertThat(stoppedWithoutWaitingForSubmission).isTrue();
        stopper.join(Duration.ofSeconds(1));
        starter.join(Duration.ofSeconds(1));
        assertThat(stopper.isAlive()).isFalse();
        assertThat(starter.isAlive()).isFalse();
        assertThat(submissionMessages.invocations()).isEmpty();
        assertThat(submissionRaw.sentPayloads()).isEmpty();
      } finally {
        releaseSubmission.countDown();
        if (stopper != null) {
          stopper.join(Duration.ofSeconds(1));
        }
        starter.join(Duration.ofSeconds(1));
      }
    } finally {
      submissionScheduler.shutdownNow();
    }
  }
}
