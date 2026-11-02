// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.watchbox;

import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.core.sdk.observability.BoundedMetricTagGuard;
import com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome;
import com.opencop.processor.business.model.watchbox.WatchboxBatchResult;
import com.opencop.processor.business.model.watchbox.WatchboxObservation;
import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.business.model.watchbox.WatchboxStateLockResult;
import com.opencop.processor.business.model.watchbox.WatchboxStateRow;
import com.opencop.processor.business.model.watchbox.WatchboxTransition;
import com.opencop.processor.business.policy.watchbox.WatchboxAdmissionGate;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxAlertOutboxRepository;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxDetectionRepository;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxSourceStatusRepository;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxTransitionStateRepository;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.Tags;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

public class WatchboxTransitionEngine {

  private static final int MAX_ATTEMPTS = 3;
  private static final Set<String> FOLD_OUTCOMES =
      Set.of(
          "seed",
          "reseed_expired",
          "enter",
          "leave",
          "filter_match_gained",
          "filter_match_lost",
          "refreshed",
          "stale_dropped",
          "stale_dropped_transition",
          "duplicate_dropped",
          "frozen_dropped",
          "suppressed_by_direction");
  private static final Set<String> DETECTION_OUTCOMES =
      Set.of("emitted", "suppressed_duplicate", "suppressed_lost_fold");
  private static final Comparator<WatchboxObservation> OBSERVATION_ORDER =
      Comparator.comparing(WatchboxObservation::effectiveEventTime)
          .thenComparing(WatchboxObservation::eventId);

  private final WatchboxTransitionStateRepository stateRepository;
  private final WatchboxSourceStatusRepository statusRepository;
  private final WatchboxDetectionRepository detectionRepository;
  private final WatchboxAlertOutboxRepository outboxRepository;
  private final WatchboxAdmissionGate admissionGate;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;
  private final Duration retention;
  private final ProcessorMetrics metrics;

  public WatchboxTransitionEngine(
      WatchboxTransitionStateRepository stateRepository,
      WatchboxSourceStatusRepository statusRepository,
      WatchboxDetectionRepository detectionRepository,
      WatchboxAlertOutboxRepository outboxRepository,
      WatchboxAdmissionGate admissionGate,
      TransactionTemplate transactionTemplate,
      Clock clock,
      Duration retention,
      ProcessorMetrics metrics) {
    this.stateRepository = stateRepository;
    this.statusRepository = statusRepository;
    this.detectionRepository = detectionRepository;
    this.outboxRepository = outboxRepository;
    this.admissionGate = admissionGate;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
    this.retention = retention;
    this.metrics = metrics;
  }

  public WatchboxBatchResult process(List<WatchboxObservation> batch) {
    if (batch.isEmpty()) {
      return new MutableResult().toResult(0);
    }
    var grouped = groupAndSort(batch);
    Set<WatchboxTransition> attemptEmissions = new HashSet<>();
    var startedAt = clock.instant();
    try {
      for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        var admissionBatch = admissionGate.begin();
        try {
          var result =
              transactionTemplate.execute(
                  _ -> processAttempt(grouped, admissionBatch, clock.instant(), attemptEmissions));
          if (result == null) {
            throw new IllegalStateException("watchbox transaction returned no result");
          }
          admissionGate.confirm(admissionBatch);
          var committedEmissions = Set.copyOf(result.emissions);
          long lostFold =
              attemptEmissions.stream()
                  .filter(emission -> !committedEmissions.contains(emission))
                  .count();
          var completed = result.toResult(lostFold);
          recordOutcomes(completed);
          return completed;
        } catch (RuntimeException exception) {
          admissionGate.release(admissionBatch);
          if (attempt == MAX_ATTEMPTS || !isRetryable(exception)) {
            throw exception;
          }
          metrics.incrementCounter("opencop.watchbox.state.batch.retries", Tags.empty());
        }
      }
      throw new IllegalStateException("watchbox retry loop exhausted without result");
    } finally {
      metrics.recordTimer(
          "opencop.watchbox.state.batch.duration",
          Tags.empty(),
          Duration.between(startedAt, clock.instant()));
    }
  }

  private MutableResult processAttempt(
      Map<WatchboxStateKey, List<WatchboxObservation>> grouped,
      WatchboxAdmissionGate.AdmissionBatch admissionBatch,
      Instant observedAt,
      Set<WatchboxTransition> attemptEmissions) {
    var existing =
        switch (stateRepository.lockCurrentAndSelectForUpdate(grouped.keySet())) {
          case WatchboxStateLockResult.Current(var rows) -> rows;
          case WatchboxStateLockResult.GenerationChanged _ ->
              throw new WatchboxGenerationChangedException();
        };
    var result = new MutableResult();
    Map<WatchboxStateKey, List<WatchboxObservation>> admitted = new TreeMap<>();
    for (var entry : grouped.entrySet()) {
      for (var observation : entry.getValue()) {
        var admission =
            admissionGate.admit(
                admissionBatch,
                observation.key().scope(),
                observation.key().objectId(),
                observedAt);
        switch (admission) {
          case ADMITTED ->
              admitted.computeIfAbsent(observation.key(), _ -> new ArrayList<>()).add(observation);
          case FROZEN -> result.increment(WatchboxBatchOutcome.FROZEN_DROPPED);
          case TRIPPED -> {
            result.increment(WatchboxBatchOutcome.TRIPPED);
            statusRepository.upsertChurnTripped(
                observation.key().scope(), admissionGate.cap() + 1, admissionGate.cap());
          }
        }
      }
    }

    var newRows = new ArrayList<WatchboxStateRow>();
    var updatedRows = new ArrayList<WatchboxStateRow>();
    for (var entry : admitted.entrySet()) {
      var key = entry.getKey();
      var fold = fold(existing.get(key), entry.getValue(), observedAt, result);
      if (!fold.accepted()) {
        continue;
      }
      if (existing.containsKey(key)) {
        updatedRows.add(fold.row());
      } else {
        newRows.add(fold.row());
      }
    }

    attemptEmissions.addAll(result.emissions);
    var expectedInsertedKeys =
        newRows.stream().map(WatchboxStateRow::key).collect(Collectors.toUnmodifiableSet());
    if (!stateRepository.insertAbsent(newRows).equals(expectedInsertedKeys)) {
      throw new ConcurrentStateCreationException();
    }
    stateRepository.upsertAll(updatedRows);
    var acceptedDetections = detectionRepository.write(result.emissions);
    // Same transaction as the detection insert: the delivery work commits or rolls back with the
    // detections it mirrors. Keyed to the insert's RETURNING set, so replay candidates the
    // database deduplicated never create work. Child-table inserts only — no watchbox parent
    // locks beyond the FK KEY SHARE the detection insert already holds, so the generation
    // fence/lock protocol is untouched.
    outboxRepository.insertAll(acceptedDetections);
    result.set(WatchboxBatchOutcome.DETECTIONS_EMITTED, acceptedDetections.size());
    result.set(
        WatchboxBatchOutcome.DETECTIONS_SUPPRESSED_DUPLICATE,
        result.emissions.size() - acceptedDetections.size());
    return result;
  }

  private FoldResult fold(
      WatchboxStateRow stored,
      List<WatchboxObservation> observations,
      Instant observedAt,
      MutableResult result) {
    var cutoff = observedAt.minus(retention);
    var expired = stored != null && stored.lastObservedAt().isBefore(cutoff);
    var current = expired ? null : stored;
    var accepted = false;

    for (var observation : observations) {
      if (current == null) {
        current = stateFrom(observation, observedAt);
        accepted = true;
        if (expired) {
          result.increment(WatchboxBatchOutcome.RESEEDED_EXPIRED);
          expired = false;
        } else {
          result.increment(WatchboxBatchOutcome.SEEDED);
        }
        continue;
      }

      var order = compare(observation, current);
      if (order == 0) {
        result.increment(WatchboxBatchOutcome.DUPLICATE_DROPPED);
        continue;
      }
      if (order < 0) {
        // A stale drop that disagrees with the stored state discards transition evidence, so it
        // counts separately from the intended late-data policy. With the validated stream keyed by
        // object (opencop.pipeline.publish.keyed, default on), this outcome should sit at zero
        // outside retry/redelivery windows; sustained movement means per-object ordering broke
        // (topic resize, the keyed-publish kill switch off, a non-conforming producer key) or the
        // residual validation-stage race is real.
        if (observation.inside() != current.lastInside()) {
          result.increment(WatchboxBatchOutcome.STALE_DROPPED_TRANSITION);
        } else {
          result.increment(WatchboxBatchOutcome.STALE_DROPPED);
        }
        continue;
      }

      accepted = true;
      var previousGeometryInside = current.lastGeometryInside();
      var previousInside = current.lastInside();
      var geometryInside = observation.geometryInside();
      var inside = observation.inside();
      if (previousGeometryInside && geometryInside) {
        result.increment(WatchboxBatchOutcome.INSIDE_COMPARISONS);
      }
      if (inside == previousInside) {
        result.increment(WatchboxBatchOutcome.REFRESHED);
      } else {
        // Which fact flipped names the transition (see WatchboxStateRow): a geometry flip is
        // motion, an effective-only flip is the filter re-classifying an object already inside
        // the polygon.
        var kind =
            geometryInside != previousGeometryInside
                ? (inside ? WatchboxTransitionKind.ENTER : WatchboxTransitionKind.LEAVE)
                : (inside
                    ? WatchboxTransitionKind.FILTER_MATCH_GAINED
                    : WatchboxTransitionKind.FILTER_MATCH_LOST);
        switch (kind) {
          case ENTER -> result.increment(WatchboxBatchOutcome.ENTERED);
          case LEAVE -> result.increment(WatchboxBatchOutcome.LEFT);
          case FILTER_MATCH_GAINED -> result.increment(WatchboxBatchOutcome.FILTER_MATCH_GAINED);
          case FILTER_MATCH_LOST -> result.increment(WatchboxBatchOutcome.FILTER_MATCH_LOST);
        }
        var transition =
            new WatchboxTransition(
                observation.key(),
                observation.eventId(),
                observation.effectiveEventTime(),
                kind,
                observation.objectDisplayName());
        var motion = kind == WatchboxTransitionKind.ENTER || kind == WatchboxTransitionKind.LEAVE;
        if (!motion || directionMatches(observation.direction(), kind)) {
          result.emissions.add(transition);
        } else {
          result.increment(WatchboxBatchOutcome.SUPPRESSED_BY_DIRECTION);
        }
      }
      current = stateFrom(observation, observedAt);
    }
    return new FoldResult(current, accepted);
  }

  private static Map<WatchboxStateKey, List<WatchboxObservation>> groupAndSort(
      List<WatchboxObservation> batch) {
    Map<WatchboxStateKey, List<WatchboxObservation>> grouped = new TreeMap<>();
    for (var observation : batch) {
      grouped.computeIfAbsent(observation.key(), _ -> new ArrayList<>()).add(observation);
    }
    grouped.values().forEach(observations -> observations.sort(OBSERVATION_ORDER));
    return grouped;
  }

  // Equal event times are broken by event-ID order, not arrival order, so the same winner
  // emerges on every replica and replay regardless of delivery interleaving. A permitted
  // equal-time successor with a smaller ID is therefore dropped as stale (metered); with keyed
  // publishes that is a redelivery-window edge, not a steady-state path.
  private static int compare(WatchboxObservation observation, WatchboxStateRow current) {
    var timeOrder = observation.effectiveEventTime().compareTo(current.lastEffectiveEventTime());
    return timeOrder != 0 ? timeOrder : observation.eventId().compareTo(current.lastEventId());
  }

  private static WatchboxStateRow stateFrom(WatchboxObservation observation, Instant observedAt) {
    return new WatchboxStateRow(
        observation.key(),
        observation.inside(),
        observation.geometryInside(),
        observation.eventId(),
        observation.effectiveEventTime(),
        observedAt);
  }

  private static boolean directionMatches(
      WatchboxDirection direction, WatchboxTransitionKind kind) {
    return switch (direction) {
      case BOTH -> true;
      case ENTER -> kind == WatchboxTransitionKind.ENTER;
      case LEAVE -> kind == WatchboxTransitionKind.LEAVE;
    };
  }

  private void recordOutcomes(WatchboxBatchResult result) {
    for (var outcome : WatchboxBatchOutcome.values()) {
      recordOutcome(outcome, result.count(outcome));
    }
  }

  // A switch expression must be exhaustive, so a new WatchboxBatchOutcome constant fails
  // compilation here until its reporting shape is chosen. (A switch statement would not: the
  // JLS exempts statements from exhaustiveness checking.)
  private void recordOutcome(WatchboxBatchOutcome outcome, long count) {
    if (count == 0) {
      return;
    }
    var meter =
        switch (outcome) {
          case SEEDED -> foldOutcomeMeter("seed");
          case RESEEDED_EXPIRED -> foldOutcomeMeter("reseed_expired");
          case ENTERED -> foldOutcomeMeter("enter");
          case LEFT -> foldOutcomeMeter("leave");
          case FILTER_MATCH_GAINED -> foldOutcomeMeter("filter_match_gained");
          case FILTER_MATCH_LOST -> foldOutcomeMeter("filter_match_lost");
          case REFRESHED -> foldOutcomeMeter("refreshed");
          case STALE_DROPPED -> foldOutcomeMeter("stale_dropped");
          case STALE_DROPPED_TRANSITION -> foldOutcomeMeter("stale_dropped_transition");
          case DUPLICATE_DROPPED -> foldOutcomeMeter("duplicate_dropped");
          case FROZEN_DROPPED -> foldOutcomeMeter("frozen_dropped");
          case SUPPRESSED_BY_DIRECTION -> foldOutcomeMeter("suppressed_by_direction");
          case INSIDE_COMPARISONS ->
              new MeterId("opencop.watchbox.state.filter.inside_comparisons", Tags.empty());
          case TRIPPED -> new MeterId("opencop.watchbox.state.breaker.trips", Tags.empty());
          case DETECTIONS_EMITTED -> detectionOutcomeMeter("emitted");
          case DETECTIONS_SUPPRESSED_DUPLICATE -> detectionOutcomeMeter("suppressed_duplicate");
          case DETECTIONS_SUPPRESSED_LOST_FOLD -> detectionOutcomeMeter("suppressed_lost_fold");
        };
    metrics.incrementCounter(meter.name(), meter.tags(), count);
  }

  private static MeterId foldOutcomeMeter(String outcome) {
    var validated = BoundedMetricTagGuard.requireAllowed("outcome", outcome, FOLD_OUTCOMES);
    return new MeterId("opencop.watchbox.state.fold.outcome", Tags.of("outcome", validated));
  }

  private static MeterId detectionOutcomeMeter(String outcome) {
    var validated = BoundedMetricTagGuard.requireAllowed("outcome", outcome, DETECTION_OUTCOMES);
    return new MeterId("opencop.watchbox.detection.outcome", Tags.of("outcome", validated));
  }

  private record MeterId(String name, Tags tags) {}

  private static boolean isRetryable(Throwable throwable) {
    var current = throwable;
    while (current != null) {
      if (current instanceof ConcurrentStateCreationException
          || current instanceof PessimisticLockingFailureException) {
        return true;
      }
      if (current instanceof SQLException sqlException
          && ("40001".equals(sqlException.getSQLState())
              || "40P01".equals(sqlException.getSQLState()))) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private record FoldResult(WatchboxStateRow row, boolean accepted) {}

  private static final class ConcurrentStateCreationException extends RuntimeException {}

  private static final class MutableResult {
    private final List<WatchboxTransition> emissions = new ArrayList<>();
    private final EnumMap<WatchboxBatchOutcome, Long> counts =
        new EnumMap<>(WatchboxBatchOutcome.class);

    private void increment(WatchboxBatchOutcome outcome) {
      counts.merge(outcome, 1L, Long::sum);
    }

    private void set(WatchboxBatchOutcome outcome, long count) {
      counts.put(outcome, count);
    }

    private WatchboxBatchResult toResult(long detectionsSuppressedLostFold) {
      set(WatchboxBatchOutcome.DETECTIONS_SUPPRESSED_LOST_FOLD, detectionsSuppressedLostFold);
      return new WatchboxBatchResult(emissions, counts);
    }
  }
}
