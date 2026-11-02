// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability.mapping;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.opencop.processor.business.model.mapping.MappingResourceRef;
import com.opencop.processor.business.model.mapping.RealtimeMappingEntry;
import com.opencop.processor.business.model.mapping.RealtimeMappingOutcome;
import com.opencop.processor.business.model.mapping.RealtimeMappingOutcomeDecision;
import com.opencop.processor.business.model.mapping.RealtimeMappingReason;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class RealtimeMappingProblemLogLimiter {

  private final Cache<Key, State> states;
  private final long summaryIntervalNanos;
  private final GlobalMappingWarningLimiter globalLimiter;
  private final Ticker ticker;

  public RealtimeMappingProblemLogLimiter(
      int maximumStates, Duration summaryInterval, GlobalMappingWarningLimiter globalLimiter) {
    this(maximumStates, summaryInterval, globalLimiter, Ticker.systemTicker());
  }

  RealtimeMappingProblemLogLimiter(
      int maximumStates,
      Duration summaryInterval,
      GlobalMappingWarningLimiter globalLimiter,
      Ticker ticker) {
    if (maximumStates <= 0) {
      throw new IllegalArgumentException("maximumStates must be positive");
    }
    Objects.requireNonNull(summaryInterval, "summaryInterval");
    if (summaryInterval.isZero() || summaryInterval.isNegative()) {
      throw new IllegalArgumentException("summaryInterval must be positive");
    }
    this.states = Caffeine.newBuilder().maximumSize(maximumStates).build();
    this.summaryIntervalNanos = summaryInterval.toNanos();
    this.globalLimiter = Objects.requireNonNull(globalLimiter, "globalLimiter");
    this.ticker = Objects.requireNonNull(ticker, "ticker");
  }

  /**
   * Decides and commits at most one emission per call atomically. Global admission is consulted
   * inside the per-resource state transition, so a globally rejected candidate leaves its
   * transition uncommitted and re-candidates on the next record for that resource; a rejected
   * recovery cannot delete the state it failed to announce.
   */
  public Decision evaluate(
      RealtimeMappingEntry ingress,
      MappingResourceRef resource,
      UUID mapperId,
      int mapperVersion,
      RealtimeMappingOutcomeDecision outcome) {
    var key = new Key(ingress, resource);
    var now = ticker.read();
    var decision = new AtomicReference<>(Decision.none());
    states
        .asMap()
        .compute(
            key,
            (_, prior) -> {
              if (!outcome.isProblem()) {
                if (outcome.outcome() == RealtimeMappingOutcome.EMITTED && prior != null) {
                  var admission = globalLimiter.admitCandidate();
                  if (!admission.emitCandidate()) {
                    return prior;
                  }
                  decision.set(
                      Decision.recovered(
                          prior.outcome(),
                          prior.reason(),
                          prior.suppressedCount(),
                          admission.priorSuppressedCount()));
                  return null;
                }
                return prior;
              }
              if (prior == null) {
                var admission = globalLimiter.admitCandidate();
                if (!admission.emitCandidate()) {
                  return null;
                }
                decision.set(Decision.problem(0, admission.priorSuppressedCount()));
                return State.from(mapperId, mapperVersion, outcome, now);
              }
              var changed =
                  !prior.mapperId().equals(mapperId)
                      || prior.mapperVersion() != mapperVersion
                      || prior.outcome() != outcome.outcome()
                      || prior.reason() != outcome.reason();
              if (changed || now - prior.lastEmissionNanos() >= summaryIntervalNanos) {
                var admission = globalLimiter.admitCandidate();
                if (!admission.emitCandidate()) {
                  return suppressed(prior);
                }
                decision.set(
                    Decision.problem(prior.suppressedCount(), admission.priorSuppressedCount()));
                return State.from(mapperId, mapperVersion, outcome, now);
              }
              return suppressed(prior);
            });
    return decision.get();
  }

  int estimatedStateCount() {
    states.cleanUp();
    return Math.toIntExact(states.estimatedSize());
  }

  private static State suppressed(State prior) {
    return new State(
        prior.mapperId(),
        prior.mapperVersion(),
        prior.outcome(),
        prior.reason(),
        prior.lastEmissionNanos(),
        prior.suppressedCount() + 1);
  }

  private record Key(RealtimeMappingEntry ingress, MappingResourceRef resource) {
    private Key {
      Objects.requireNonNull(ingress, "ingress");
      Objects.requireNonNull(resource, "resource");
    }
  }

  private record State(
      UUID mapperId,
      int mapperVersion,
      RealtimeMappingOutcome outcome,
      RealtimeMappingReason reason,
      long lastEmissionNanos,
      long suppressedCount) {

    private static State from(
        UUID mapperId, int mapperVersion, RealtimeMappingOutcomeDecision outcome, long now) {
      return new State(mapperId, mapperVersion, outcome.outcome(), outcome.reason(), now, 0);
    }
  }

  public enum DecisionKind {
    NONE,
    PROBLEM,
    RECOVERED
  }

  public record Decision(
      DecisionKind kind,
      RealtimeMappingOutcome priorOutcome,
      RealtimeMappingReason priorReason,
      long suppressedCount,
      long globalPriorSuppressedCount) {

    private static Decision none() {
      return new Decision(DecisionKind.NONE, null, null, 0, 0);
    }

    private static Decision problem(long suppressedCount, long globalPriorSuppressedCount) {
      return new Decision(
          DecisionKind.PROBLEM, null, null, suppressedCount, globalPriorSuppressedCount);
    }

    private static Decision recovered(
        RealtimeMappingOutcome priorOutcome,
        RealtimeMappingReason priorReason,
        long suppressedCount,
        long globalPriorSuppressedCount) {
      return new Decision(
          DecisionKind.RECOVERED,
          priorOutcome,
          priorReason,
          suppressedCount,
          globalPriorSuppressedCount);
    }
  }
}
