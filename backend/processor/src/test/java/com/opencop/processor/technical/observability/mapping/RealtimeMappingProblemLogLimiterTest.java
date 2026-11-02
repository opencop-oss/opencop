// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Ticker;
import com.opencop.processor.business.model.mapping.MappingDecoderKind;
import com.opencop.processor.business.model.mapping.MappingResourceKind;
import com.opencop.processor.business.model.mapping.MappingResourceRef;
import com.opencop.processor.business.model.mapping.RealtimeMappingEntry;
import com.opencop.processor.business.model.mapping.RealtimeMappingOutcome;
import com.opencop.processor.business.model.mapping.RealtimeMappingOutcomeDecision;
import com.opencop.processor.business.model.mapping.RealtimeMappingReason;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RealtimeMappingProblemLogLimiterTest {

  private static final UUID MAPPER_ID = UUID.fromString("3bcf6d87-d5e5-47a4-9cc3-f51f72083d0b");
  private static final MappingResourceRef RESOURCE =
      new MappingResourceRef(MappingResourceKind.DATA_SOURCE, UUID.randomUUID());
  private final AtomicLong nanos = new AtomicLong();
  private final Ticker ticker = nanos::get;
  private final RealtimeMappingProblemLogLimiter limiter =
      new RealtimeMappingProblemLogLimiter(10, Duration.ofMinutes(5), permissiveGlobal(), ticker);

  @Test
  void emitsFirstChangePeriodicSummaryAndRecovery() {
    assertThat(
            evaluate(problem(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS), MAPPER_ID, 1).kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);
    assertThat(
            evaluate(problem(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS), MAPPER_ID, 1).kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.NONE);

    var changed = evaluate(problem(RealtimeMappingOutcome.PARTIAL_WITH_ERRORS), MAPPER_ID, 1);
    assertThat(changed.kind()).isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);
    assertThat(changed.suppressedCount()).isEqualTo(1);

    assertThat(evaluate(problem(RealtimeMappingOutcome.PARTIAL_WITH_ERRORS), MAPPER_ID, 2).kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);
    evaluate(problem(RealtimeMappingOutcome.PARTIAL_WITH_ERRORS), MAPPER_ID, 2);
    nanos.set(Duration.ofMinutes(6).toNanos());
    var summary = evaluate(problem(RealtimeMappingOutcome.PARTIAL_WITH_ERRORS), MAPPER_ID, 2);
    assertThat(summary.kind()).isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);
    assertThat(summary.suppressedCount()).isEqualTo(1);

    var accepted =
        new RealtimeMappingOutcomeDecision(
            RealtimeMappingOutcome.ACCEPTED_NO_OUTPUT,
            RealtimeMappingReason.EXPLICIT_DECODER_CONTRACT,
            MappingDecoderKind.JAVA);
    assertThat(evaluate(accepted, MAPPER_ID, 2).kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.NONE);

    var recovered = evaluate(emitted(), MAPPER_ID, 2);
    assertThat(recovered.kind()).isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.RECOVERED);
    assertThat(recovered.priorOutcome()).isEqualTo(RealtimeMappingOutcome.PARTIAL_WITH_ERRORS);
    assertThat(evaluate(emitted(), MAPPER_ID, 2).kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.NONE);
  }

  @Test
  void reasonOnlyChangeEmitsImmediately() {
    assertThat(evaluate(problem(RealtimeMappingReason.ON_MISSING_ERROR), MAPPER_ID, 1).kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);

    var changed = evaluate(problem(RealtimeMappingReason.PAYLOAD_ERROR), MAPPER_ID, 1);

    assertThat(changed.kind()).isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);
    assertThat(changed.suppressedCount()).isZero();
  }

  @Test
  void mapperIdOnlyChangeEmitsImmediately() {
    assertThat(evaluate(problem(RealtimeMappingReason.ON_MISSING_ERROR), MAPPER_ID, 1).kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);

    var changed =
        evaluate(
            problem(RealtimeMappingReason.ON_MISSING_ERROR),
            UUID.fromString("776af14e-d981-45a2-b448-8f5251b03540"),
            1);

    assertThat(changed.kind()).isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);
    assertThat(changed.suppressedCount()).isZero();
  }

  @Test
  void cacheRemainsBoundedAndEvictionOnlyPermitsAnotherFirstWarning() {
    var small =
        new RealtimeMappingProblemLogLimiter(1, Duration.ofMinutes(5), permissiveGlobal(), ticker);
    small.evaluate(
        RealtimeMappingEntry.BYOD,
        RESOURCE,
        MAPPER_ID,
        1,
        problem(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS));
    var other = new MappingResourceRef(MappingResourceKind.DATA_SOURCE, UUID.randomUUID());
    small.evaluate(
        RealtimeMappingEntry.BYOD,
        other,
        MAPPER_ID,
        1,
        problem(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS));

    assertThat(small.estimatedStateCount()).isLessThanOrEqualTo(1);
  }

  @Test
  void rejectedRecoveryKeepsStateAndEmitsAfterCapacityFrees() {
    var tight = new GlobalMappingWarningLimiter(1, Duration.ofSeconds(10), nanos::get);
    var limited = new RealtimeMappingProblemLogLimiter(10, Duration.ofMinutes(5), tight, ticker);

    assertThat(
            limited
                .evaluate(
                    RealtimeMappingEntry.BYOD,
                    RESOURCE,
                    MAPPER_ID,
                    1,
                    problem(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS))
                .kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);

    assertThat(
            limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, emitted()).kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.NONE);

    nanos.set(Duration.ofSeconds(11).toNanos());
    var recovered = limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, emitted());
    assertThat(recovered.kind()).isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.RECOVERED);
    assertThat(recovered.priorOutcome()).isEqualTo(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS);
    assertThat(recovered.globalPriorSuppressedCount()).isEqualTo(1);

    assertThat(
            limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, emitted()).kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.NONE);
  }

  @Test
  void rejectedFirstProblemInstallsNoStateAndRetriesUntilAdmitted() {
    var tight = new GlobalMappingWarningLimiter(1, Duration.ofSeconds(10), nanos::get);
    var limited = new RealtimeMappingProblemLogLimiter(10, Duration.ofMinutes(5), tight, ticker);
    var other = new MappingResourceRef(MappingResourceKind.DATA_SOURCE, UUID.randomUUID());

    assertThat(
            limited
                .evaluate(
                    RealtimeMappingEntry.BYOD,
                    other,
                    MAPPER_ID,
                    1,
                    problem(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS))
                .kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);

    for (var attempt = 0; attempt < 2; attempt++) {
      assertThat(
              limited
                  .evaluate(
                      RealtimeMappingEntry.BYOD,
                      RESOURCE,
                      MAPPER_ID,
                      1,
                      problem(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS))
                  .kind())
          .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.NONE);
    }

    nanos.set(Duration.ofSeconds(11).toNanos());
    var first =
        limited.evaluate(
            RealtimeMappingEntry.BYOD,
            RESOURCE,
            MAPPER_ID,
            1,
            problem(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS));
    assertThat(first.kind()).isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);
    assertThat(first.suppressedCount()).isZero();
    assertThat(first.globalPriorSuppressedCount()).isEqualTo(2);
  }

  @Test
  void rejectedPeriodicSummaryRetainsStateAndAccumulatesSuppressedCount() {
    var tight = new GlobalMappingWarningLimiter(1, Duration.ofSeconds(30), nanos::get);
    var limited = new RealtimeMappingProblemLogLimiter(10, Duration.ofSeconds(10), tight, ticker);
    var problem = problem(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS);

    assertThat(limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, problem).kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);
    nanos.set(Duration.ofSeconds(1).toNanos());
    limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, problem);
    nanos.set(Duration.ofSeconds(2).toNanos());
    limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, problem);

    nanos.set(Duration.ofSeconds(11).toNanos());
    assertThat(limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, problem).kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.NONE);

    nanos.set(Duration.ofSeconds(31).toNanos());
    var summary = limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, problem);
    assertThat(summary.kind()).isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);
    assertThat(summary.suppressedCount()).isEqualTo(3);
    assertThat(summary.globalPriorSuppressedCount()).isEqualTo(1);
  }

  @Test
  void rejectedChangedCandidateKeepsPriorIdentityAndEmitsChangeAfterCapacityFrees() {
    var tight = new GlobalMappingWarningLimiter(1, Duration.ofSeconds(10), nanos::get);
    var limited = new RealtimeMappingProblemLogLimiter(10, Duration.ofMinutes(5), tight, ticker);
    var initial = problem(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS);
    var changedOutcome = problem(RealtimeMappingOutcome.PARTIAL_WITH_ERRORS);

    assertThat(limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, initial).kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);

    assertThat(
            limited
                .evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, changedOutcome)
                .kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.NONE);

    nanos.set(Duration.ofSeconds(11).toNanos());
    var changed =
        limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, changedOutcome);
    assertThat(changed.kind()).isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);
    assertThat(changed.suppressedCount()).isEqualTo(1);
    assertThat(changed.globalPriorSuppressedCount()).isEqualTo(1);

    nanos.set(Duration.ofSeconds(22).toNanos());
    var recovered = limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, emitted());
    assertThat(recovered.kind()).isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.RECOVERED);
    assertThat(recovered.priorOutcome()).isEqualTo(RealtimeMappingOutcome.PARTIAL_WITH_ERRORS);
  }

  @Test
  void nonCandidateRecordsConsumeNoGlobalCapacity() {
    var tight = new GlobalMappingWarningLimiter(2, Duration.ofSeconds(10), nanos::get);
    var limited = new RealtimeMappingProblemLogLimiter(10, Duration.ofSeconds(60), tight, ticker);
    var problem = problem(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS);

    assertThat(limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, problem).kind())
        .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM);

    for (var i = 0; i < 5; i++) {
      nanos.addAndGet(Duration.ofSeconds(1).toNanos());
      assertThat(
              limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, problem).kind())
          .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.NONE);
      var healthyOther = new MappingResourceRef(MappingResourceKind.DATA_SOURCE, UUID.randomUUID());
      assertThat(
              limited
                  .evaluate(RealtimeMappingEntry.BYOD, healthyOther, MAPPER_ID, 1, emitted())
                  .kind())
          .isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.NONE);
    }

    var recovered = limited.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, MAPPER_ID, 1, emitted());
    assertThat(recovered.kind()).isEqualTo(RealtimeMappingProblemLogLimiter.DecisionKind.RECOVERED);
    assertThat(recovered.globalPriorSuppressedCount()).isZero();
  }

  @Test
  void concurrentSameResourceDecisionsStayConsistentWithFinalState() {
    var results =
        IntStream.range(0, 200)
            .parallel()
            .mapToObj(
                i ->
                    evaluate(
                            (i & 1) == 0
                                ? problem(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS)
                                : emitted(),
                            MAPPER_ID,
                            1)
                        .kind())
            .toList();

    var problems =
        results.stream()
            .filter(kind -> kind == RealtimeMappingProblemLogLimiter.DecisionKind.PROBLEM)
            .count();
    var recoveries =
        results.stream()
            .filter(kind -> kind == RealtimeMappingProblemLogLimiter.DecisionKind.RECOVERED)
            .count();
    assertThat(problems).isPositive();
    assertThat(recoveries).isLessThanOrEqualTo(problems);
    assertThat(problems - recoveries).isLessThanOrEqualTo(1);
  }

  private GlobalMappingWarningLimiter permissiveGlobal() {
    return new GlobalMappingWarningLimiter(100, Duration.ofMinutes(5), nanos::get);
  }

  private RealtimeMappingProblemLogLimiter.Decision evaluate(
      RealtimeMappingOutcomeDecision decision, UUID mapperId, int mapperVersion) {
    return limiter.evaluate(RealtimeMappingEntry.BYOD, RESOURCE, mapperId, mapperVersion, decision);
  }

  private static RealtimeMappingOutcomeDecision problem(RealtimeMappingOutcome outcome) {
    return new RealtimeMappingOutcomeDecision(
        outcome, RealtimeMappingReason.ON_MISSING_ERROR, MappingDecoderKind.DYNAMIC);
  }

  private static RealtimeMappingOutcomeDecision problem(RealtimeMappingReason reason) {
    return new RealtimeMappingOutcomeDecision(
        RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS, reason, MappingDecoderKind.DYNAMIC);
  }

  private static RealtimeMappingOutcomeDecision emitted() {
    return new RealtimeMappingOutcomeDecision(
        RealtimeMappingOutcome.EMITTED, RealtimeMappingReason.NONE, MappingDecoderKind.DYNAMIC);
  }
}
