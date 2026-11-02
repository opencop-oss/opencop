// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.mapping.ingress;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

final class RealTimeFailureDebouncer {

  private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);
  private static final long DEFAULT_MAX_SIZE = 10_000;

  private final Cache<FailureKey, Instant> failures;
  private final Clock clock;

  static RealTimeFailureDebouncer withDefaults() {
    return new RealTimeFailureDebouncer(
        DEFAULT_WINDOW, DEFAULT_MAX_SIZE, Clock.systemUTC(), Ticker.systemTicker());
  }

  RealTimeFailureDebouncer(Duration window, long maxSize, Clock clock, Ticker ticker) {
    this.clock = clock;
    this.failures =
        Caffeine.newBuilder().expireAfterWrite(window).maximumSize(maxSize).ticker(ticker).build();
  }

  Instant firstFailureAt(UUID sourceId, UUID mapperId) {
    var key = new FailureKey(sourceId, mapperId);
    var now = Instant.now(clock);
    return failures.asMap().putIfAbsent(key, now) == null ? now : null;
  }

  private record FailureKey(UUID sourceId, UUID mapperId) {}
}
