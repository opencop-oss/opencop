// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GlobalMappingWarningLimiterTest {

  @Test
  void capsCandidatesAndReportsPriorSuppressionOnRollover() {
    var nanos = new AtomicLong();
    var limiter = new GlobalMappingWarningLimiter(2, Duration.ofSeconds(10), nanos::get);

    assertThat(limiter.admitCandidate().emitCandidate()).isTrue();
    assertThat(limiter.admitCandidate().emitCandidate()).isTrue();
    assertThat(limiter.admitCandidate().emitCandidate()).isFalse();
    assertThat(limiter.admitCandidate().emitCandidate()).isFalse();
    nanos.set(Duration.ofSeconds(11).toNanos());

    var rollover = limiter.admitCandidate();
    assertThat(rollover.emitCandidate()).isTrue();
    assertThat(rollover.priorSuppressedCount()).isEqualTo(2);
  }

  @Test
  void concurrentRolloverSnapshotsSuppressionOnlyOnce() {
    var nanos = new AtomicLong();
    var limiter = new GlobalMappingWarningLimiter(1, Duration.ofSeconds(1), nanos::get);
    limiter.admitCandidate();
    limiter.admitCandidate();
    nanos.set(Duration.ofSeconds(2).toNanos());

    var reported =
        IntStream.range(0, 100)
            .parallel()
            .mapToObj(_ -> limiter.admitCandidate())
            .filter(admission -> admission.priorSuppressedCount() > 0)
            .toList();

    assertThat(reported).hasSize(1);
    assertThat(reported.getFirst().priorSuppressedCount()).isEqualTo(1);
  }
}
