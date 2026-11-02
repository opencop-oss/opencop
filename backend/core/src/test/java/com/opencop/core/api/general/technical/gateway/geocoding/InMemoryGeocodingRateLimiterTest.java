// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Eviction is invisible through the use case by design, so it is checked here. */
class InMemoryGeocodingRateLimiterTest {

  private static final int REQUESTS_PER_SECOND = 10;
  private static final int BURST = 20;
  private static final Duration FULL_REFILL = Duration.ofSeconds(BURST / REQUESTS_PER_SECOND);
  private static final UUID USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
  private static final UUID OTHER_USER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
  private static final UUID THIRD_USER_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T08:00:00Z"));
  private final InMemoryGeocodingRateLimiter limiter =
      new InMemoryGeocodingRateLimiter(REQUESTS_PER_SECOND, BURST, clock);

  @Test
  void forgetsAUserIdleLongEnoughToRefillCompletely() {
    limiter.tryAcquire(USER_ID);

    clock.advance(FULL_REFILL);
    limiter.tryAcquire(OTHER_USER_ID);

    assertThat(limiter.trackedUsers()).isEqualTo(1);
  }

  @Test
  void remembersAUserWhoseBucketIsStillRefilling() {
    limiter.tryAcquire(USER_ID);

    clock.advance(FULL_REFILL.minusNanos(1));
    limiter.tryAcquire(OTHER_USER_ID);

    assertThat(limiter.trackedUsers()).isEqualTo(2);
  }

  @Test
  void keepsTheBudgetOfAUserWhoKeepsSearching() {
    for (int i = 0; i < BURST; i++) {
      limiter.tryAcquire(USER_ID);
    }

    clock.advance(FULL_REFILL);
    limiter.tryAcquire(USER_ID);

    assertThat(limiter.trackedUsers()).isEqualTo(1);
    assertThat(limiter.tryAcquire(USER_ID)).isTrue();
  }

  @Test
  void neverRefillsWhenTheClockStepsBack() {
    for (int i = 0; i < BURST; i++) {
      limiter.tryAcquire(USER_ID);
    }

    clock.advance(Duration.ofSeconds(-30));

    assertThat(limiter.tryAcquire(USER_ID)).isFalse();
  }

  @Test
  void creditsNoTimeTwiceWhenTheClockStepsBackAndRecovers() {
    for (int i = 0; i < BURST; i++) {
      limiter.tryAcquire(USER_ID);
    }

    clock.advance(Duration.ofSeconds(-30));
    limiter.tryAcquire(USER_ID);
    clock.advance(Duration.ofSeconds(30));

    assertThat(limiter.tryAcquire(USER_ID)).isFalse();
  }

  @Test
  void keepsEvictingAfterTheClockStepsBackFromAJumpAhead() {
    clock.advance(Duration.ofHours(1));
    limiter.tryAcquire(USER_ID);
    clock.advance(Duration.ofHours(-1));
    limiter.tryAcquire(OTHER_USER_ID);

    clock.advance(FULL_REFILL);
    limiter.tryAcquire(THIRD_USER_ID);

    assertThat(limiter.trackedUsers()).isEqualTo(2);
  }

  @Test
  void rejectsANonPositiveRateOrBurst() {
    assertThatThrownBy(() -> new InMemoryGeocodingRateLimiter(0, BURST, clock))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InMemoryGeocodingRateLimiter(REQUESTS_PER_SECOND, 0, clock))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static final class MutableClock extends Clock {

    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    private void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
