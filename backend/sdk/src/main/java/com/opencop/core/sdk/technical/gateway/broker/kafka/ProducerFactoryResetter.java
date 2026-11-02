// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

import java.util.function.Supplier;

@FunctionalInterface
public interface ProducerFactoryResetter {

  void reset();

  default long generation() {
    return 0L;
  }

  default <T> T resetAndRun(long observedGeneration, Supplier<T> retry) {
    reset();
    return retry.get();
  }
}
