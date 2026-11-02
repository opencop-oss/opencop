// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.integration.connection.behavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RecordingConnectionMessageFactoryTest {

  @Test
  void recordsSourceTimeOrderAndPayloadWhileReturningFreshArrays() throws Exception {
    var events = new LifecycleEventRecorder();
    var factory = new RecordingConnectionMessageFactory("presence", new byte[] {1, 2}, events);
    var sourceId = UUID.fromString("e94d5a66-b669-42a7-8333-3a9a036df567");
    var firstTime = Instant.parse("2026-08-25T12:00:00Z");
    var secondTime = firstTime.plusSeconds(20);

    var first = factory.create(sourceId, firstTime);
    var second = factory.create(sourceId, secondTime);
    first[0] = 9;

    assertThat(second).containsExactly(1, 2).isNotSameAs(first);
    assertThat(factory.invocations())
        .satisfiesExactly(
            invocation -> {
              assertThat(invocation.sourceId()).isEqualTo(sourceId);
              assertThat(invocation.executionTime()).isEqualTo(firstTime);
              assertThat(invocation.order()).isZero();
              assertThat(invocation.payload()).containsExactly(1, 2);
              assertThat(invocation.failure()).isEmpty();
            },
            invocation -> {
              assertThat(invocation.executionTime()).isEqualTo(secondTime);
              assertThat(invocation.order()).isEqualTo(1);
              assertThat(invocation.payload()).containsExactly(1, 2);
            });
    assertThat(events.events())
        .containsExactly(
            new LifecycleEventRecorder.Event(0, "presence"),
            new LifecycleEventRecorder.Event(1, "presence"));
  }

  @Test
  void recordsAndThrowsTheConfiguredFailure() {
    var failure = new IOException("planned failure");
    var factory =
        RecordingConnectionMessageFactory.failing(
            "failure", new byte[] {3}, new LifecycleEventRecorder(), failure);

    assertThatThrownBy(() -> factory.create(UUID.randomUUID(), Instant.EPOCH)).isSameAs(failure);
    assertThat(factory.invocations())
        .singleElement()
        .satisfies(invocation -> assertThat(invocation.failure()).contains(failure));
  }

  @Test
  void signalsInvocationAndWaitsAtTheConfiguredCompletionGate() throws Exception {
    var invoked = new CountDownLatch(1);
    var completion = new CountDownLatch(1);
    var factory =
        RecordingConnectionMessageFactory.gated(
            "gated", new byte[] {7}, new LifecycleEventRecorder(), invoked, completion);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var result =
          executor.submit(
              (Callable<byte[]>) () -> factory.create(UUID.randomUUID(), Instant.EPOCH));

      try {
        assertThat(invoked.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(result.isDone()).isFalse();
      } finally {
        completion.countDown();
      }
      assertThat(result.get(1, TimeUnit.SECONDS)).containsExactly(7);
    }
  }
}
