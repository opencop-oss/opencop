// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProtocolViolationTrackerTest {

  @Test
  void reachesTheConfiguredThresholdExactlyOnceAndKeepsRetentionBounded() {
    var tracker = new ProtocolViolationTracker(10, Duration.ofSeconds(60));
    Instant now = Instant.parse("2026-08-13T12:00:00Z");

    for (int count = 1; count < 10; count++) {
      assertThat(tracker.record(now.plusMillis(count)))
          .isEqualTo(
              new ProtocolViolationTracker.Result(
                  ProtocolViolationTracker.Decision.BELOW_THRESHOLD, count));
    }
    assertThat(tracker.record(now.plusMillis(10)))
        .isEqualTo(
            new ProtocolViolationTracker.Result(
                ProtocolViolationTracker.Decision.THRESHOLD_REACHED, 10));
    assertThat(tracker.record(now.plusMillis(11)))
        .isEqualTo(
            new ProtocolViolationTracker.Result(
                ProtocolViolationTracker.Decision.ALREADY_REACHED, 10));
  }

  @Test
  void discardsViolationsAtTheExactWindowBoundaryBeforeCounting() {
    var tracker = new ProtocolViolationTracker(2, Duration.ofSeconds(10));
    Instant first = Instant.parse("2026-08-13T12:00:00Z");

    assertThat(tracker.record(first).count()).isEqualTo(1);

    var atBoundary = tracker.record(first.plusSeconds(10));

    assertThat(atBoundary.decision()).isEqualTo(ProtocolViolationTracker.Decision.BELOW_THRESHOLD);
    assertThat(atBoundary.count()).isEqualTo(1);
  }

  @Test
  void sparseViolationsNeverAccumulateAndValidTrafficNeedsNoTrackerMutation() {
    var tracker = new ProtocolViolationTracker(10, Duration.ofSeconds(60));
    Instant now = Instant.parse("2026-08-13T12:00:00Z");

    for (int interval = 0; interval < 20; interval++) {
      assertThat(tracker.record(now.plus(Duration.ofMinutes(30L * interval))).count()).isEqualTo(1);
    }
  }

  @Test
  void limitOneClosesOnTheFirstViolation() {
    var tracker = new ProtocolViolationTracker(1, Duration.ofHours(1));

    assertThat(tracker.record(Instant.parse("2026-08-13T12:00:00Z")))
        .isEqualTo(
            new ProtocolViolationTracker.Result(
                ProtocolViolationTracker.Decision.THRESHOLD_REACHED, 1));
  }

  @Test
  void terminationRemovesHistoryAndKeepsTheTrackerTerminal() {
    var tracker = new ProtocolViolationTracker(2, Duration.ofMinutes(1));
    Instant now = Instant.parse("2026-08-13T12:00:00Z");
    tracker.record(now);
    tracker.record(now.plusMillis(1));

    tracker.terminate();

    assertThat(tracker.record(now.plusMillis(2)))
        .isEqualTo(
            new ProtocolViolationTracker.Result(
                ProtocolViolationTracker.Decision.ALREADY_REACHED, 2));
  }

  @Test
  void outOfOrderConcurrentTimestampsCannotRetainAnExpiredTail() {
    var tracker = new ProtocolViolationTracker(3, Duration.ofSeconds(10));
    Instant origin = Instant.parse("2026-08-13T12:00:00Z");
    tracker.record(origin.plusSeconds(20));
    tracker.record(origin);

    var result = tracker.record(origin.plusSeconds(25));

    assertThat(result)
        .isEqualTo(
            new ProtocolViolationTracker.Result(
                ProtocolViolationTracker.Decision.BELOW_THRESHOLD, 2));
  }

  @Test
  void outOfOrderTimestampAtOrOutsideGreatestObservedWindowDoesNotReachThreshold() {
    Instant origin = Instant.parse("2026-08-13T12:00:00Z");

    for (long separationSeconds : new long[] {60, 100}) {
      var tracker = new ProtocolViolationTracker(2, Duration.ofSeconds(60));
      tracker.record(origin.plusSeconds(separationSeconds));

      assertThat(tracker.record(origin))
          .as("separation of %s seconds", separationSeconds)
          .isEqualTo(
              new ProtocolViolationTracker.Result(
                  ProtocolViolationTracker.Decision.BELOW_THRESHOLD, 1));
    }
  }

  @Test
  void delayedTimestampInsideTheWindowRetainsItsExactExpiryTime() {
    var tracker = new ProtocolViolationTracker(4, Duration.ofSeconds(10));
    Instant origin = Instant.parse("2026-08-13T12:00:00Z");

    tracker.record(origin.plusSeconds(20));
    tracker.record(origin.plusSeconds(15));
    tracker.record(origin.plusSeconds(24));

    assertThat(tracker.record(origin.plusSeconds(25)))
        .isEqualTo(
            new ProtocolViolationTracker.Result(
                ProtocolViolationTracker.Decision.BELOW_THRESHOLD, 3));
  }

  @Test
  void highLimitMaintenanceDoesNotRescanTheRetainedHistory() {
    int supportedLimit = 10_000;
    int attempts = supportedLimit * 10;
    var tracker = new ProtocolViolationTracker(attempts + 1, Duration.ofHours(1));
    Instant now = Instant.parse("2026-08-13T12:00:00Z");

    assertTimeout(
        Duration.ofSeconds(2),
        () -> {
          ProtocolViolationTracker.Result result = null;
          for (int attempt = 0; attempt < attempts; attempt++) {
            result = tracker.record(now.plusNanos(attempt));
          }
          assertThat(result)
              .isEqualTo(
                  new ProtocolViolationTracker.Result(
                      ProtocolViolationTracker.Decision.BELOW_THRESHOLD, attempts));
        });
  }

  @Test
  void concurrentCallbacksProduceOneThresholdDecisionWithoutUndercounting() throws Exception {
    int limit = 50;
    int attempts = 100;
    var tracker = new ProtocolViolationTracker(limit, Duration.ofMinutes(1));
    Instant now = Instant.parse("2026-08-13T12:00:00Z");
    var start = new CountDownLatch(1);
    var results =
        java.util.Collections.synchronizedList(new ArrayList<ProtocolViolationTracker.Result>());

    try (var executor = Executors.newFixedThreadPool(20)) {
      for (int attempt = 0; attempt < attempts; attempt++) {
        executor.submit(
            () -> {
              start.await();
              results.add(tracker.record(now));
              return null;
            });
      }
      start.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(results).hasSize(attempts);
    assertThat(
            results.stream()
                .filter(
                    result ->
                        result.decision() == ProtocolViolationTracker.Decision.BELOW_THRESHOLD))
        .hasSize(limit - 1);
    assertThat(
            results.stream()
                .filter(
                    result ->
                        result.decision() == ProtocolViolationTracker.Decision.THRESHOLD_REACHED))
        .hasSize(1);
    assertThat(
            results.stream()
                .filter(
                    result ->
                        result.decision() == ProtocolViolationTracker.Decision.ALREADY_REACHED))
        .hasSize(attempts - limit);
    assertThat(results).allSatisfy(result -> assertThat(result.count()).isLessThanOrEqualTo(limit));
  }
}
