// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.mapping.ingress;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealTimeFailureDebouncerTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-000000000201");
  private static final UUID MAPPER_ID = UUID.fromString("00000000-0000-4000-8000-000000000301");

  @Test
  void acceptsTheSameFailureKeyAgainAfterTheWindowExpires() {
    var time = new MutableTimeSource(Instant.parse("2026-03-26T18:29:31Z"));
    var debouncer =
        new RealTimeFailureDebouncer(Duration.ofMinutes(1), 10_000, time.clock(), time.ticker());

    assertThat(debouncer.firstFailureAt(SOURCE_ID, MAPPER_ID)).isEqualTo(time.instant());
    assertThat(debouncer.firstFailureAt(SOURCE_ID, MAPPER_ID)).isNull();

    time.advance(Duration.ofMinutes(1).plusSeconds(1));

    assertThat(debouncer.firstFailureAt(SOURCE_ID, MAPPER_ID)).isEqualTo(time.instant());
  }

  private static final class MutableTimeSource {

    private Instant instant;
    private long nanos;

    private MutableTimeSource(Instant instant) {
      this.instant = instant;
    }

    Instant instant() {
      return instant;
    }

    Clock clock() {
      return new Clock() {
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
          return instant;
        }
      };
    }

    Ticker ticker() {
      return () -> nanos;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
      nanos += duration.toNanos();
    }
  }
}
