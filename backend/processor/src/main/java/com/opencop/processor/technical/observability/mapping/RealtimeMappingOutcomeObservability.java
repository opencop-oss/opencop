// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability.mapping;

import static com.opencop.core.sdk.observability.BoundedMetricTagGuard.allowedValues;
import static com.opencop.core.sdk.observability.BoundedMetricTagGuard.requireAllowed;

import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.processor.business.model.mapping.MappingDecoderKind;
import com.opencop.processor.business.model.mapping.MappingResourceKind;
import com.opencop.processor.business.model.mapping.RealtimeMappingAttempt;
import com.opencop.processor.business.model.mapping.RealtimeMappingEntry;
import com.opencop.processor.business.model.mapping.RealtimeMappingOutcome;
import com.opencop.processor.business.model.mapping.RealtimeMappingOutcomeDecision;
import com.opencop.processor.business.model.mapping.RealtimeMappingReason;
import com.opencop.processor.business.policy.mapping.RealtimeMappingOutcomeClassifier;
import com.opencop.processor.ports.business.observability.mapping.MappingObservationFailureSink;
import com.opencop.processor.ports.business.observability.mapping.RealtimeMappingOutcomeObserver;
import com.opencop.processor.technical.spring.config.RealtimeMappingOutcomeProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RealtimeMappingOutcomeObservability implements RealtimeMappingOutcomeObserver {

  public static final String OUTCOME_METRIC = "opencop_realtime_mapping_outcome_total";
  public static final String PROBLEM_METRIC = "opencop_realtime_mapping_problem_total";
  public static final String OVERFLOW_METRIC =
      "opencop_realtime_mapping_problem_resource_overflow_total";

  private static final Logger log =
      LoggerFactory.getLogger(RealtimeMappingOutcomeObservability.class);
  private static final String TAG_INGRESS = "ingress";
  private static final String TAG_OUTCOME = "outcome";
  private static final String TAG_REASON = "reason";
  private static final String TAG_DECODER_KIND = "decoder_kind";
  private static final String TAG_RESOURCE_KIND = "resource_kind";
  private static final String TAG_RESOURCE_ID = "resource_id";
  private static final RealtimeMappingOutcomeDecision THROWN_DECISION =
      new RealtimeMappingOutcomeDecision(
          RealtimeMappingOutcome.FAILED_THROWN,
          RealtimeMappingReason.THROWN,
          MappingDecoderKind.UNKNOWN);

  private final boolean enabled;
  private final Duration warningInterval;
  private final MeterRegistry meterRegistry;
  private final RealtimeMappingOutcomeClassifier classifier;
  private final ProblemResourceTagLimiter resourceTagLimiter;
  private final RealtimeMappingProblemLogLimiter problemLogLimiter;
  private final MappingObservationFailureSink failureReporter;
  private final Map<AggregateKey, Counter> aggregateCounters = new ConcurrentHashMap<>();
  private final Map<ProblemKey, Counter> problemCounters = new ConcurrentHashMap<>();
  private final Map<OverflowKey, Counter> overflowCounters = new ConcurrentHashMap<>();

  public RealtimeMappingOutcomeObservability(
      MeterRegistry meterRegistry,
      RealtimeMappingOutcomeProperties properties,
      RealtimeMappingOutcomeClassifier classifier,
      ProblemResourceTagLimiter resourceTagLimiter,
      RealtimeMappingProblemLogLimiter problemLogLimiter,
      MappingObservationFailureSink failureReporter) {
    this.meterRegistry = meterRegistry;
    this.enabled = properties.isEnabled();
    this.warningInterval = properties.getWarningSummaryInterval();
    this.classifier = classifier;
    this.resourceTagLimiter = resourceTagLimiter;
    this.problemLogLimiter = problemLogLimiter;
    this.failureReporter = failureReporter;
  }

  @Override
  public void observe(RealtimeMappingAttempt attempt, DecodedMappingResult result) {
    if (!enabled) {
      return;
    }
    final RealtimeMappingOutcomeDecision decision;
    try {
      decision = classifier.classify(result);
    } catch (RuntimeException e) {
      reportFailureSafely(e.getClass());
      return;
    }
    try {
      aggregateCounter(attempt.ingress(), decision).increment();
    } catch (RuntimeException e) {
      reportFailureSafely(e.getClass());
    }
    if (decision.isProblem()) {
      try {
        recordProblem(attempt, decision);
      } catch (RuntimeException e) {
        reportFailureSafely(e.getClass());
      }
    }
    try {
      recordLog(attempt, result, decision);
    } catch (RuntimeException e) {
      reportFailureSafely(e.getClass());
    }
  }

  @Override
  public void observeThrown(RealtimeMappingAttempt attempt) {
    if (!enabled) {
      return;
    }
    try {
      aggregateCounter(attempt.ingress(), THROWN_DECISION).increment();
    } catch (RuntimeException e) {
      reportFailureSafely(e.getClass());
    }
    try {
      recordProblem(attempt, THROWN_DECISION);
    } catch (RuntimeException e) {
      reportFailureSafely(e.getClass());
    }
    // Thrown failures get no WARN lifecycle: the propagated exception already produces error
    // logs and retry/DLT evidence, and no decoded result exists to describe.
  }

  private Counter aggregateCounter(
      RealtimeMappingEntry ingress, RealtimeMappingOutcomeDecision decision) {
    var key =
        new AggregateKey(ingress, decision.outcome(), decision.reason(), decision.decoderKind());
    return aggregateCounters.computeIfAbsent(
        key,
        _ ->
            Counter.builder(OUTCOME_METRIC)
                .tags(
                    TAG_INGRESS,
                    bounded(TAG_INGRESS, ingress.value(), RealtimeMappingEntry.values()),
                    TAG_OUTCOME,
                    bounded(
                        TAG_OUTCOME, decision.outcome().value(), RealtimeMappingOutcome.values()),
                    TAG_REASON,
                    bounded(TAG_REASON, decision.reason().value(), RealtimeMappingReason.values()),
                    TAG_DECODER_KIND,
                    bounded(
                        TAG_DECODER_KIND,
                        decision.decoderKind().value(),
                        MappingDecoderKind.values()))
                .register(meterRegistry));
  }

  private void recordProblem(
      RealtimeMappingAttempt attempt, RealtimeMappingOutcomeDecision decision) {
    var admitted = resourceTagLimiter.admit(attempt.resource());
    var key =
        new ProblemKey(
            attempt.ingress(), admitted.resourceKind(), admitted.resourceId(), decision.outcome());
    problemCounters
        .computeIfAbsent(
            key,
            _ ->
                Counter.builder(PROBLEM_METRIC)
                    .tags(
                        TAG_INGRESS,
                        bounded(
                            TAG_INGRESS, attempt.ingress().value(), RealtimeMappingEntry.values()),
                        TAG_RESOURCE_KIND,
                        bounded(
                            TAG_RESOURCE_KIND,
                            admitted.resourceKind().value(),
                            MappingResourceKind.values()),
                        TAG_RESOURCE_ID,
                        admitted.resourceId(),
                        TAG_OUTCOME,
                        bounded(
                            TAG_OUTCOME,
                            decision.outcome().value(),
                            RealtimeMappingOutcome.values()))
                    .register(meterRegistry))
        .increment();
    if (admitted.overflow()) {
      var overflowKey =
          new OverflowKey(attempt.ingress(), admitted.resourceKind(), decision.outcome());
      overflowCounters
          .computeIfAbsent(
              overflowKey,
              _ ->
                  Counter.builder(OVERFLOW_METRIC)
                      .tags(
                          TAG_INGRESS,
                          bounded(
                              TAG_INGRESS,
                              attempt.ingress().value(),
                              RealtimeMappingEntry.values()),
                          TAG_RESOURCE_KIND,
                          bounded(
                              TAG_RESOURCE_KIND,
                              admitted.resourceKind().value(),
                              MappingResourceKind.values()),
                          TAG_OUTCOME,
                          bounded(
                              TAG_OUTCOME,
                              decision.outcome().value(),
                              RealtimeMappingOutcome.values()))
                      .register(meterRegistry))
          .increment();
    }
  }

  private void recordLog(
      RealtimeMappingAttempt attempt,
      DecodedMappingResult result,
      RealtimeMappingOutcomeDecision outcome) {
    var decision =
        problemLogLimiter.evaluate(
            attempt.ingress(),
            attempt.resource(),
            result.mapperId(),
            result.mapperVersion(),
            outcome);
    if (decision.kind() == RealtimeMappingProblemLogLimiter.DecisionKind.NONE) {
      return;
    }
    if (decision.globalPriorSuppressedCount() > 0) {
      log.warn(
          "REALTIME_MAPPING_WARNING_THROTTLED suppressedCount={} intervalSeconds={} payloadLogged=false",
          decision.globalPriorSuppressedCount(),
          warningInterval.toSeconds());
    }
    if (decision.kind() == RealtimeMappingProblemLogLimiter.DecisionKind.RECOVERED) {
      log.warn(
          "REALTIME_MAPPING_RECOVERED ingress={} resourceKind={} resourceId={} mapperId={} mapperVersion={} priorOutcome={} priorReason={} partition={} offset={} suppressedCount={} payloadLogged=false",
          attempt.ingress().value(),
          attempt.resource().kind().value(),
          attempt.resource().id(),
          result.mapperId(),
          result.mapperVersion(),
          decision.priorOutcome().value(),
          decision.priorReason().value(),
          attempt.partition(),
          attempt.offset(),
          decision.suppressedCount());
      return;
    }
    log.warn(
        "REALTIME_MAPPING_PROBLEM ingress={} resourceKind={} resourceId={} mapperId={} mapperVersion={} outcome={} reason={} decoderKind={} featureCount={} recordCount={} errorCount={} warningCount={} partition={} offset={} suppressedCount={} payloadLogged=false",
        attempt.ingress().value(),
        attempt.resource().kind().value(),
        attempt.resource().id(),
        result.mapperId(),
        result.mapperVersion(),
        outcome.outcome().value(),
        outcome.reason().value(),
        outcome.decoderKind().value(),
        result.featureCount(),
        result.recordCount(),
        result.errorCount(),
        result.totalWarningCount(),
        attempt.partition(),
        attempt.offset(),
        decision.suppressedCount());
  }

  private void reportFailureSafely(Class<?> exceptionClass) {
    try {
      failureReporter.report(exceptionClass);
    } catch (RuntimeException _) {
      // The observer's failure path must remain independent of the injected sink implementation.
    }
  }

  private static <T extends com.opencop.core.sdk.observability.BoundedMetricTagValue>
      String bounded(String tag, String value, T[] values) {
    return requireAllowed(tag, value, allowedValues(values));
  }

  private record AggregateKey(
      RealtimeMappingEntry ingress,
      RealtimeMappingOutcome outcome,
      RealtimeMappingReason reason,
      MappingDecoderKind decoderKind) {}

  private record ProblemKey(
      RealtimeMappingEntry ingress,
      MappingResourceKind resourceKind,
      String resourceId,
      RealtimeMappingOutcome outcome) {}

  private record OverflowKey(
      RealtimeMappingEntry ingress,
      MappingResourceKind resourceKind,
      RealtimeMappingOutcome outcome) {}
}
