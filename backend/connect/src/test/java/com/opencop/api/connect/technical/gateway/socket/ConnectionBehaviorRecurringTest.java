// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.testing.integration.connection.behavior.ConnectionBehaviorRegistryFixture;
import com.opencop.core.sdk.testing.integration.connection.behavior.LifecycleEventRecorder;
import com.opencop.core.sdk.testing.integration.connection.behavior.RecordingConnectionMessageFactory;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubRecurringBehavior;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ConnectionBehaviorRecurringTest {

  private static final UUID SOURCE_ID = UUID.fromString("18d420a8-bc40-4ecb-87ae-38e1a7b8fd48");

  @Test
  void recurringActionWaitsItsFullDelayAndRearmsOnlyAfterCompletionWithFreshTime()
      throws Exception {
    var schema =
        new TestConnectionSchema(
            UUID.fromString("c9569448-d9fa-4fde-82af-81338d997dd2"), 1, "Test", "Test integration");
    var invoked = new CountDownLatch(1);
    var completion = new CountDownLatch(1);
    var events = new LifecycleEventRecorder();
    var messages =
        RecordingConnectionMessageFactory.gated(
            "recurring", new byte[] {3}, events, invoked, completion);
    var plan =
        ConnectionBehaviorRegistryFixture.registryFor(
                schema,
                new StubRecurringBehavior(schema, "recurring", messages, Duration.ofSeconds(7)))
            .planFor(schema, NoConnectionSettings.INSTANCE);
    var scheduler = new ControllableScheduler();
    var clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
    var raw = new RecordingRawSocketConnector(SOURCE_ID);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var decorated =
          new ConnectionBehaviorRuntime(scheduler, executor, clock)
              .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE);
      decorated.start();

      assertThat(scheduler.delays()).containsExactly(Duration.ofSeconds(7));
      assertThat(messages.invocations()).isEmpty();

      var executionTime = Instant.parse("2026-08-25T12:00:07Z");
      clock.set(executionTime);
      scheduler.runNext();
      assertThat(invoked.await(1, TimeUnit.SECONDS)).isTrue();
      assertThat(scheduler.delays()).containsExactly(Duration.ofSeconds(7));

      completion.countDown();
      assertThat(raw.awaitSentCount(1, Duration.ofSeconds(1))).isTrue();
      assertThat(scheduler.awaitScheduleCount(2, Duration.ofSeconds(1))).isTrue();
      assertThat(scheduler.delays()).containsExactly(Duration.ofSeconds(7), Duration.ofSeconds(7));
      assertThat(messages.invocations())
          .singleElement()
          .satisfies(
              invocation -> {
                assertThat(invocation.sourceId()).isEqualTo(SOURCE_ID);
                assertThat(invocation.executionTime()).isEqualTo(executionTime);
              });
    } finally {
      completion.countDown();
      scheduler.shutdownNow();
    }
  }

  @Test
  void recurringActionsRunIndependently() throws Exception {
    var schema = testIntegration();
    var firstInvoked = new CountDownLatch(1);
    var firstCompletion = new CountDownLatch(1);
    var first =
        RecordingConnectionMessageFactory.gated(
            "first-recurring",
            new byte[] {4},
            new LifecycleEventRecorder(),
            firstInvoked,
            firstCompletion);
    var second =
        new RecordingConnectionMessageFactory(
            "second-recurring", new byte[] {5}, new LifecycleEventRecorder());
    var plan =
        ConnectionBehaviorRegistryFixture.registryFor(
                schema,
                new StubRecurringBehavior(schema, "first-recurring", first, Duration.ofSeconds(3)),
                new StubRecurringBehavior(
                    schema, "second-recurring", second, Duration.ofSeconds(11)))
            .planFor(schema, NoConnectionSettings.INSTANCE);
    var scheduler = new ControllableScheduler();
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    var executor = Executors.newVirtualThreadPerTaskExecutor();

    try {
      new ConnectionBehaviorRuntime(scheduler, executor, Clock.systemUTC())
          .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE)
          .start();

      scheduler.runNext();
      assertThat(firstInvoked.await(1, TimeUnit.SECONDS)).isTrue();
      scheduler.runNext();

      assertThat(raw.awaitSentCount(1, Duration.ofSeconds(1))).isTrue();
      assertThat(raw.sentPayloads())
          .singleElement()
          .satisfies(payload -> assertThat(payload).containsExactly(5));
      assertThat(scheduler.awaitScheduleCount(3, Duration.ofSeconds(1))).isTrue();
      assertThat(scheduler.delays())
          .containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(11), Duration.ofSeconds(11));
    } finally {
      firstCompletion.countDown();
      executor.close();
      scheduler.shutdownNow();
    }
  }

  @Test
  void expectedConstructionAndSendFailuresDoNotPreventRearming() throws Exception {
    var schema = testIntegration();
    var constructionFailure =
        RecordingConnectionMessageFactory.failing(
            "construction-failure",
            new byte[] {6},
            new LifecycleEventRecorder(),
            new IOException("cannot serialize"));
    var sendFailure =
        new RecordingConnectionMessageFactory(
            "send-failure", new byte[] {7}, new LifecycleEventRecorder());
    var plan =
        ConnectionBehaviorRegistryFixture.registryFor(
                schema,
                new StubRecurringBehavior(
                    schema, "construction-failure", constructionFailure, Duration.ofSeconds(2)),
                new StubRecurringBehavior(
                    schema, "send-failure", sendFailure, Duration.ofSeconds(5)))
            .planFor(schema, NoConnectionSettings.INSTANCE);
    var scheduler = new ControllableScheduler();
    var raw = new RecordingRawSocketConnector(SOURCE_ID);
    raw.failSendsWith(new IOException("socket unavailable"));

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      new ConnectionBehaviorRuntime(scheduler, executor, Clock.systemUTC())
          .decorate(raw, SOURCE_ID, plan, NoConnectionSettings.INSTANCE)
          .start();

      scheduler.runNext();
      scheduler.runNext();

      assertThat(raw.awaitSendAttemptCount(1, Duration.ofSeconds(1))).isTrue();
      assertThat(scheduler.awaitScheduleCount(4, Duration.ofSeconds(1))).isTrue();
      assertThat(raw.sentPayloads()).isEmpty();
    } finally {
      scheduler.shutdownNow();
    }
  }

  private static TestConnectionSchema testIntegration() {
    return new TestConnectionSchema(
        UUID.fromString("c9569448-d9fa-4fde-82af-81338d997dd2"), 1, "Test", "Test integration");
  }
}
