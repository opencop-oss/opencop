// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.PriorityQueue;

public final class ProtocolViolationTracker {

  private final int limit;
  private final Duration window;
  private final PriorityQueue<Instant> timestamps = new PriorityQueue<>();
  private Instant greatestObservedTimestamp;
  private boolean thresholdReached;

  public ProtocolViolationTracker(int limit, Duration window) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be positive");
    }
    if (window == null || !window.isPositive()) {
      throw new IllegalArgumentException("window must be positive");
    }
    this.limit = limit;
    this.window = window;
  }

  public synchronized Result record(Instant now) {
    Objects.requireNonNull(now, "now");
    if (thresholdReached) {
      return new Result(Decision.ALREADY_REACHED, limit);
    }

    if (greatestObservedTimestamp == null || now.isAfter(greatestObservedTimestamp)) {
      greatestObservedTimestamp = now;
    }
    Instant expiryBoundary = greatestObservedTimestamp.minus(window);
    while (!timestamps.isEmpty() && !timestamps.peek().isAfter(expiryBoundary)) {
      timestamps.remove();
    }

    if (!now.isAfter(expiryBoundary)) {
      return new Result(Decision.BELOW_THRESHOLD, timestamps.size());
    }

    timestamps.add(now);
    int count = timestamps.size();
    if (count == limit) {
      thresholdReached = true;
      return new Result(Decision.THRESHOLD_REACHED, count);
    }
    return new Result(Decision.BELOW_THRESHOLD, count);
  }

  public synchronized void terminate() {
    timestamps.clear();
    thresholdReached = true;
  }

  public enum Decision {
    BELOW_THRESHOLD,
    THRESHOLD_REACHED,
    ALREADY_REACHED
  }

  public record Result(Decision decision, int count) {}
}
