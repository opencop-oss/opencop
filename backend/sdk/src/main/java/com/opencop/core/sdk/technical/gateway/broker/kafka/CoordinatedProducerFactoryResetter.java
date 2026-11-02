// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

import java.util.Objects;
import java.util.function.Supplier;

public final class CoordinatedProducerFactoryResetter implements ProducerFactoryResetter {

  private final Runnable reset;
  private volatile long generation;

  public CoordinatedProducerFactoryResetter(Runnable reset) {
    this.reset = Objects.requireNonNull(reset, "reset");
  }

  @Override
  public synchronized void reset() {
    reset.run();
    generation++;
  }

  @Override
  public long generation() {
    return generation;
  }

  @Override
  public synchronized <T> T resetAndRun(long observedGeneration, Supplier<T> retry) {
    if (generation == observedGeneration) {
      reset();
    }
    return retry.get();
  }
}
