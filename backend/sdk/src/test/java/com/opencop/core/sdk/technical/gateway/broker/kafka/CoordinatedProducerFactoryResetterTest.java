// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CoordinatedProducerFactoryResetterTest {

  @Test
  void resetAndRunHoldsCoordinationUntilRetryFinishes() throws Exception {
    var resetCount = new AtomicInteger();
    var resetter = new CoordinatedProducerFactoryResetter(resetCount::incrementAndGet);
    var retryStarted = new CountDownLatch(1);
    var releaseRetry = new CountDownLatch(1);
    var secondRetryStarted = new CountDownLatch(1);

    CompletableFuture<Void> first =
        CompletableFuture.runAsync(
            () ->
                resetter.resetAndRun(
                    resetter.generation(),
                    () -> {
                      retryStarted.countDown();
                      await(releaseRetry);
                      return null;
                    }));
    assertThat(retryStarted.await(1, TimeUnit.SECONDS)).isTrue();

    CompletableFuture<Void> second =
        CompletableFuture.runAsync(
            () ->
                resetter.resetAndRun(
                    resetter.generation(),
                    () -> {
                      secondRetryStarted.countDown();
                      return null;
                    }));

    assertThat(secondRetryStarted.await(50, TimeUnit.MILLISECONDS)).isFalse();
    releaseRetry.countDown();
    first.get(1, TimeUnit.SECONDS);
    second.get(1, TimeUnit.SECONDS);
    assertThat(resetCount).hasValue(2);
  }

  @Test
  void resetAndRunSkipsResetWhenGenerationAlreadyAdvanced() {
    var resetCount = new AtomicInteger();
    var resetter = new CoordinatedProducerFactoryResetter(resetCount::incrementAndGet);
    long staleGeneration = resetter.generation();

    resetter.reset();
    resetter.resetAndRun(staleGeneration, () -> null);

    assertThat(resetCount).hasValue(1);
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}
