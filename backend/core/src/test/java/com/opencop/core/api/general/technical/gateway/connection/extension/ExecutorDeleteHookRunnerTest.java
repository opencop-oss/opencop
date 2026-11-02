// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.ports.gateway.connection.extension.DeleteHookRunner.Outcome;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExecutorDeleteHookRunnerTest {

  private static final Duration LIMIT = Duration.ofMillis(200);

  private final CountDownLatch released = new CountDownLatch(1);
  private final ExecutorDeleteHookRunner runner =
      new ExecutorDeleteHookRunner(Executors.newVirtualThreadPerTaskExecutor());

  @AfterEach
  void releaseHangingHooks() {
    released.countDown();
  }

  @Test
  void aHookThatReturnsCompleted() {
    assertThat(runner.run(() -> {}, LIMIT)).isEqualTo(Outcome.COMPLETED);
  }

  @Test
  void aHookThatThrowsFailedWithItsCause() {
    var cause = new IllegalStateException("remote refused");

    assertThat(
            runner.run(
                () -> {
                  throw cause;
                },
                LIMIT))
        .isEqualTo(new Outcome.Failed(cause));
  }

  @Test
  void aHookPastItsLimitTimedOutWithoutWaitingForIt() {
    var started = System.nanoTime();

    var outcome = runner.run(released::await, LIMIT);

    assertThat(outcome).isEqualTo(Outcome.TIMED_OUT);
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
  }

  @Test
  void anInterruptedWaitKeepsTheInterrupt() {
    Thread.currentThread().interrupt();
    try {
      assertThat(runner.run(released::await, LIMIT)).isEqualTo(Outcome.INTERRUPTED);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void aHookThatCannotStartIsRefused() {
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    executor.shutdownNow();

    assertThatThrownBy(() -> new ExecutorDeleteHookRunner(executor).run(() -> {}, LIMIT))
        .isInstanceOf(RejectedExecutionException.class);
  }
}
