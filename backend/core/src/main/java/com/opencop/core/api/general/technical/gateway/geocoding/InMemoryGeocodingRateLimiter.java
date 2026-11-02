// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.geocoding;

import com.opencop.core.api.general.ports.gateway.geocoding.GeocodingRateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A token bucket per user, held in memory because Core runs as a single instance.
 *
 * <p>Each user may spend up to {@code burst} searches at once, and earns back {@code
 * requestsPerSecond} of them every second. A bucket left alone long enough to refill completely is
 * indistinguishable from a new one, so it is forgotten after that long, keeping memory bounded by
 * the users active recently.
 */
public class InMemoryGeocodingRateLimiter implements GeocodingRateLimiter {

  private static final double NANOS_PER_SECOND = 1_000_000_000d;

  private final double tokensPerNano;
  private final int burst;
  private final Duration evictionWindow;
  private final Clock clock;
  private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();
  private volatile Instant lastEviction;

  public InMemoryGeocodingRateLimiter(int requestsPerSecond, int burst, Clock clock) {
    if (requestsPerSecond < 1) {
      throw new IllegalArgumentException("requestsPerSecond must be positive");
    }
    if (burst < 1) {
      throw new IllegalArgumentException("burst must be positive");
    }
    this.tokensPerNano = requestsPerSecond / NANOS_PER_SECOND;
    this.burst = burst;
    this.evictionWindow = Duration.ofNanos((long) Math.ceil(burst / tokensPerNano));
    this.clock = clock;
    this.lastEviction = clock.instant();
  }

  @Override
  public boolean tryAcquire(UUID internalUserId) {
    var now = clock.instant();
    evictIdleBuckets(now);
    return buckets.compute(internalUserId, (userId, bucket) -> take(bucket, now)).granted();
  }

  int trackedUsers() {
    return buckets.size();
  }

  /**
   * A bucket's time never moves backwards: if the clock steps back, the bucket keeps its later
   * time, so the stretch the clock replays is not credited twice.
   */
  private Bucket take(Bucket bucket, Instant now) {
    if (bucket == null) {
      return new Bucket(burst - 1, now, true);
    }
    var tokens = refilled(bucket, now);
    var updatedAt = latest(bucket.updatedAt(), now);
    if (tokens < 1) {
      return new Bucket(tokens, updatedAt, false);
    }
    return new Bucket(tokens - 1, updatedAt, true);
  }

  private static Instant latest(Instant first, Instant second) {
    return first.isAfter(second) ? first : second;
  }

  private double refilled(Bucket bucket, Instant now) {
    var elapsed = Duration.between(bucket.updatedAt(), now);
    if (elapsed.isNegative()) {
      return bucket.tokens();
    }
    return Math.min(burst, bucket.tokens() + elapsed.toNanos() * tokensPerNano);
  }

  /**
   * Sweeps at most once per eviction window, so the cost stays proportional to the users seen in
   * that window rather than to the request rate. The removal only succeeds for a bucket nobody
   * updated since it was read, so a user searching during the sweep keeps their bucket.
   *
   * <p>A last sweep that lies in the future means the clock stepped back; sweeping then restarts
   * the schedule rather than pausing eviction until the clock catches up.
   */
  private void evictIdleBuckets(Instant now) {
    var sinceLastEviction = Duration.between(lastEviction, now);
    if (!sinceLastEviction.isNegative() && sinceLastEviction.compareTo(evictionWindow) < 0) {
      return;
    }
    lastEviction = now;
    buckets.values().removeIf(bucket -> isIdle(bucket, now));
  }

  private boolean isIdle(Bucket bucket, Instant now) {
    return Duration.between(bucket.updatedAt(), now).compareTo(evictionWindow) >= 0;
  }

  /**
   * @param granted whether the attempt that produced this state was allowed
   */
  private record Bucket(double tokens, Instant updatedAt, boolean granted) {}
}
