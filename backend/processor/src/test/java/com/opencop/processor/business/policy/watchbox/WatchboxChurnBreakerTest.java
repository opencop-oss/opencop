// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.watchbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.processor.business.model.watchbox.WatchboxScopeKey;
import com.opencop.processor.business.policy.watchbox.WatchboxAdmissionGate.Admission;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class WatchboxChurnBreakerTest {

  private static final WatchboxScopeKey SCOPE =
      new WatchboxScopeKey(
          UUID.fromString("00000000-0000-0000-0000-000000000b11"),
          1,
          UUID.fromString("00000000-0000-0000-0000-000000005011"));
  private static final Instant START = Instant.parse("2026-07-16T12:00:00Z");

  @Test
  void stationaryBeaconRefreshKeepsMembershipBeyondRetention() {
    var breaker = new WatchboxChurnBreaker(true, 1, Duration.ofHours(1));
    confirm(breaker, "stationary", START);
    confirm(breaker, "stationary", START.plus(Duration.ofMinutes(45)));
    confirm(breaker, "stationary", START.plus(Duration.ofMinutes(90)));

    var tripBatch = breaker.begin();
    assertThat(breaker.admit(tripBatch, SCOPE, "new-object", START.plus(Duration.ofMinutes(91))))
        .isEqualTo(Admission.TRIPPED);
  }

  @Test
  void idleMembershipExpiresAndFreesRoom() {
    var breaker = new WatchboxChurnBreaker(true, 1, Duration.ofHours(1));
    confirm(breaker, "idle", START);

    var batch = breaker.begin();
    assertThat(breaker.admit(batch, SCOPE, "replacement", START.plus(Duration.ofHours(2))))
        .isEqualTo(Admission.ADMITTED);
  }

  @Test
  void capPlusOneTripsAndCommitFreezesOnlyThatEpoch() {
    var breaker = new WatchboxChurnBreaker(true, 1, Duration.ofHours(1));
    confirm(breaker, "first", START);
    var tripBatch = breaker.begin();
    assertThat(breaker.admit(tripBatch, SCOPE, "second", START.plusSeconds(1)))
        .isEqualTo(Admission.TRIPPED);
    breaker.confirm(tripBatch);

    var frozenBatch = breaker.begin();
    assertThat(breaker.admit(frozenBatch, SCOPE, "first", START.plusSeconds(2)))
        .isEqualTo(Admission.FROZEN);
    var freshEpoch = new WatchboxScopeKey(SCOPE.watchboxId(), 2, SCOPE.sourceId());
    assertThat(breaker.admit(frozenBatch, freshEpoch, "first", START.plusSeconds(2)))
        .isEqualTo(Admission.ADMITTED);
  }

  @Test
  void concurrentCapEdgeNeverOverAdmitsAndTripsOnce() throws Exception {
    var cap = 10;
    var contenders = 40;
    var breaker = new WatchboxChurnBreaker(true, cap, Duration.ofHours(1));
    var ready = new CountDownLatch(contenders);
    var start = new CountDownLatch(1);
    var decisions = java.util.Collections.synchronizedList(new ArrayList<Admission>());
    var batches =
        java.util.Collections.synchronizedList(
            new ArrayList<WatchboxAdmissionGate.AdmissionBatch>());
    try (var executor = Executors.newFixedThreadPool(contenders)) {
      for (int index = 0; index < contenders; index++) {
        var objectId = "object-" + index;
        executor.submit(
            () -> {
              var batch = breaker.begin();
              batches.add(batch);
              ready.countDown();
              start.await();
              decisions.add(breaker.admit(batch, SCOPE, objectId, START));
              return null;
            });
      }
      ready.await();
      start.countDown();
    }

    assertThat(decisions).filteredOn(Admission.ADMITTED::equals).hasSize(cap);
    assertThat(decisions).filteredOn(Admission.TRIPPED::equals).hasSize(1);
    assertThat(decisions).filteredOn(Admission.FROZEN::equals).hasSize(contenders - cap - 1);
  }

  @Test
  void rollbackReleasesStagedAdmissionsAndTripWithoutPhantomFreeze() {
    var breaker = new WatchboxChurnBreaker(true, 1, Duration.ofHours(1));
    var admissionBatch = breaker.begin();
    assertThat(breaker.admit(admissionBatch, SCOPE, "first", START)).isEqualTo(Admission.ADMITTED);
    var tripBatch = breaker.begin();
    assertThat(breaker.admit(tripBatch, SCOPE, "second", START)).isEqualTo(Admission.TRIPPED);

    breaker.release(tripBatch);
    breaker.release(admissionBatch);
    var replacement = breaker.begin();
    assertThat(breaker.admit(replacement, SCOPE, "replacement", START))
        .isEqualTo(Admission.ADMITTED);
  }

  @Test
  void trippedScopeUntouchedPastCutoffIsPrunedAndReturningTrafficRecounts() {
    var registry = new SimpleMeterRegistry();
    var breaker =
        new WatchboxChurnBreaker(true, 1, Duration.ofHours(1), new ProcessorMetrics(registry));
    confirm(breaker, "first", START);
    var tripBatch = breaker.begin();
    assertThat(breaker.admit(tripBatch, SCOPE, "second", START.plusSeconds(1)))
        .isEqualTo(Admission.TRIPPED);
    breaker.confirm(tripBatch);

    // The scope goes quiet — an epoch bump moved admissions to a new key, or the handler-level
    // frozen-target screen stopped forwarding its traffic — and ages past the cutoff.
    assertThat(breaker.pruneIdleScopes(START.plus(Duration.ofHours(2)))).isEqualTo(1);
    assertThat(gauge(registry, WatchboxChurnBreaker.TRACKED_SCOPES_GAUGE)).isZero();
    assertThat(gauge(registry, WatchboxChurnBreaker.TRACKED_OBJECTS_TOTAL_GAUGE)).isZero();
    assertThat(gauge(registry, WatchboxChurnBreaker.MAX_RETAINED_ACTIVE_GAUGE)).isZero();

    // Removal is a scoped restart: returning traffic re-counts toward the cap instead of staying
    // frozen; the persisted DEGRADED row is what keeps screening delivery.
    var replay = breaker.begin();
    assertThat(breaker.admit(replay, SCOPE, "first", START.plus(Duration.ofHours(3))))
        .isEqualTo(Admission.ADMITTED);
    breaker.confirm(replay);
    assertThat(gauge(registry, WatchboxChurnBreaker.TRACKED_SCOPES_GAUGE)).isEqualTo(1.0);
  }

  @Test
  void trippedScopeTouchedWithinWindowIsRetainedEvenByFrozenAdmissions() {
    var breaker = new WatchboxChurnBreaker(true, 1, Duration.ofHours(1));
    confirm(breaker, "first", START);
    var tripBatch = breaker.begin();
    assertThat(breaker.admit(tripBatch, SCOPE, "second", START.plusSeconds(1)))
        .isEqualTo(Admission.TRIPPED);
    breaker.confirm(tripBatch);

    // Traffic still reaches admit() while a stale runtime snapshot lets it through; the frozen
    // return refreshes the touch watermark.
    var frozenBatch = breaker.begin();
    assertThat(breaker.admit(frozenBatch, SCOPE, "third", START.plus(Duration.ofMinutes(30))))
        .isEqualTo(Admission.FROZEN);
    breaker.release(frozenBatch);

    // Cutoff between the trip and the frozen touch: the scope stays tripped.
    assertThat(breaker.pruneIdleScopes(START.plus(Duration.ofMinutes(15)))).isZero();
    var stillFrozen = breaker.begin();
    assertThat(breaker.admit(stillFrozen, SCOPE, "fourth", START.plus(Duration.ofMinutes(31))))
        .isEqualTo(Admission.FROZEN);
  }

  @Test
  void disabledBreakerAlwaysAdmits() {
    var breaker = new WatchboxChurnBreaker(false, 1, Duration.ofHours(1));
    var batch = breaker.begin();
    for (int index = 0; index < 100; index++) {
      assertThat(breaker.admit(batch, SCOPE, "object-" + index, START))
          .isEqualTo(Admission.ADMITTED);
    }
    breaker.confirm(batch);
    assertThat(breaker.pruneIdleScopes(START.plus(Duration.ofDays(1)))).isZero();
  }

  @Test
  void calibrationGaugesTrackAdmissionsAndPruning() {
    var registry = new SimpleMeterRegistry();
    var breaker =
        new WatchboxChurnBreaker(true, 3, Duration.ofHours(1), new ProcessorMetrics(registry));

    confirm(breaker, "first", START);
    confirm(breaker, "second", START.plusSeconds(1));

    assertThat(gauge(registry, WatchboxChurnBreaker.MAX_RETAINED_ACTIVE_GAUGE)).isEqualTo(2.0);
    assertThat(gauge(registry, WatchboxChurnBreaker.TRACKED_OBJECTS_TOTAL_GAUGE)).isEqualTo(2.0);
    assertThat(gauge(registry, WatchboxChurnBreaker.TRACKED_SCOPES_GAUGE)).isEqualTo(1.0);

    assertThat(breaker.pruneIdleScopes(START.plus(Duration.ofHours(2)))).isEqualTo(1);
    assertThat(gauge(registry, WatchboxChurnBreaker.MAX_RETAINED_ACTIVE_GAUGE)).isZero();
    assertThat(gauge(registry, WatchboxChurnBreaker.TRACKED_OBJECTS_TOTAL_GAUGE)).isZero();
    assertThat(gauge(registry, WatchboxChurnBreaker.TRACKED_SCOPES_GAUGE)).isZero();
  }

  @Test
  void concurrentAdmitAndPruneSettleToZeroAccounting() throws Exception {
    var registry = new SimpleMeterRegistry();
    var breaker =
        new WatchboxChurnBreaker(true, 100, Duration.ofHours(1), new ProcessorMetrics(registry));
    var iterations = 2_000;
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var admitter =
          executor.submit(
              () -> {
                start.await();
                for (int index = 0; index < iterations; index++) {
                  var batch = breaker.begin();
                  assertThat(
                          breaker.admit(
                              batch, SCOPE, "object-" + (index % 5), START.plusSeconds(index)))
                      .isEqualTo(Admission.ADMITTED);
                  breaker.confirm(batch);
                }
                return null;
              });
      var pruner =
          executor.submit(
              () -> {
                start.await();
                for (int index = 0; index < iterations; index++) {
                  breaker.pruneIdleScopes(START.plus(Duration.ofDays(1)));
                }
                return null;
              });
      start.countDown();
      admitter.get();
      pruner.get();
    }
    breaker.pruneIdleScopes(START.plus(Duration.ofDays(2)));

    assertThat(gauge(registry, WatchboxChurnBreaker.TRACKED_OBJECTS_TOTAL_GAUGE)).isZero();
    assertThat(gauge(registry, WatchboxChurnBreaker.TRACKED_SCOPES_GAUGE)).isZero();
  }

  private static void confirm(WatchboxChurnBreaker breaker, String objectId, Instant observedAt) {
    var batch = breaker.begin();
    assertThat(breaker.admit(batch, SCOPE, objectId, observedAt)).isEqualTo(Admission.ADMITTED);
    breaker.confirm(batch);
  }

  private static double gauge(SimpleMeterRegistry registry, String name) {
    return registry.get(name).gauge().value();
  }
}
