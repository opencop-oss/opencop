// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability.mapping;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class GlobalMappingWarningLimiter {

  private final Object lock = new Object();
  private final int maximumPerInterval;
  private final long intervalNanos;
  private final LongSupplier nanoTime;
  private Window window;

  public GlobalMappingWarningLimiter(int maximumPerInterval, Duration interval) {
    this(maximumPerInterval, interval, System::nanoTime);
  }

  GlobalMappingWarningLimiter(int maximumPerInterval, Duration interval, LongSupplier nanoTime) {
    if (maximumPerInterval <= 0 || maximumPerInterval > 100) {
      throw new IllegalArgumentException("maximumPerInterval must be between 1 and 100");
    }
    Objects.requireNonNull(interval, "interval");
    if (interval.isZero() || interval.isNegative()) {
      throw new IllegalArgumentException("interval must be positive");
    }
    this.maximumPerInterval = maximumPerInterval;
    this.intervalNanos = interval.toNanos();
    this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
  }

  public Admission admitCandidate() {
    synchronized (lock) {
      var now = nanoTime.getAsLong();
      if (window == null) {
        window = new Window(now, 1, 0);
        return new Admission(true, 0);
      }
      if (now - window.startedAtNanos() >= intervalNanos) {
        var priorSuppressed = window.suppressed();
        window = new Window(now, 1, 0);
        return new Admission(true, priorSuppressed);
      }
      if (window.admitted() < maximumPerInterval) {
        window = new Window(window.startedAtNanos(), window.admitted() + 1, window.suppressed());
        return new Admission(true, 0);
      }
      window = new Window(window.startedAtNanos(), window.admitted(), window.suppressed() + 1);
      return new Admission(false, 0);
    }
  }

  private record Window(long startedAtNanos, int admitted, long suppressed) {}

  public record Admission(boolean emitCandidate, long priorSuppressedCount) {}
}
