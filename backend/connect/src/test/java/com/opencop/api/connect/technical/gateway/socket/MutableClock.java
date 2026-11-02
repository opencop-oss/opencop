// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

final class MutableClock extends Clock {

  private volatile Instant instant;
  private final AtomicReference<ClockBlock> nextBlock = new AtomicReference<>();

  MutableClock(Instant instant) {
    this.instant = instant;
  }

  void set(Instant instant) {
    this.instant = instant;
  }

  void blockNextInvocation(CountDownLatch entered, CountDownLatch release) {
    nextBlock.set(new ClockBlock(entered, release));
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    var block = nextBlock.getAndSet(null);
    if (block != null) {
      block.entered().countDown();
      try {
        block.release().await();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while controlling the test clock", exception);
      }
    }
    return instant;
  }

  private record ClockBlock(CountDownLatch entered, CountDownLatch release) {}
}
