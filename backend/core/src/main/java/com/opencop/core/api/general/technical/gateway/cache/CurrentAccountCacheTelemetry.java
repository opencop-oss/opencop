// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.cache;

import com.opencop.core.sdk.observability.BoundedMetricTagGuard;
import com.opencop.core.sdk.observability.BoundedMetricTagValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccountCacheTelemetry {

  static final String CACHE_OUTCOMES_TOTAL = "opencop_current_account_cache_outcomes_total";
  static final String CACHE_MUTATIONS_TOTAL = "opencop_current_account_cache_mutations_total";

  private static final String NONE = "none";
  private static final String TAG_OUTCOME = "outcome";
  private static final String TAG_REASON = "reason";
  private static final String TAG_STAGE = "stage";
  private static final Set<String> CACHE_OUTCOME_VALUES =
      BoundedMetricTagGuard.allowedValues(CacheOutcome.values());
  private static final Set<String> CACHE_REASON_VALUES =
      Stream.concat(
              Stream.of(NONE),
              BoundedMetricTagGuard.allowedValues(InvalidValueReason.values()).stream())
          .collect(Collectors.toUnmodifiableSet());
  private static final Set<String> MUTATION_STAGE_VALUES =
      BoundedMetricTagGuard.allowedValues(MutationStage.values());
  private static final Set<String> MUTATION_OUTCOME_VALUES =
      BoundedMetricTagGuard.allowedValues(MutationOutcome.values());
  private static final Logger log = LoggerFactory.getLogger(CurrentAccountCacheTelemetry.class);

  private final MeterRegistry meterRegistry;
  private final Set<WarningKey> emittedWarnings = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean telemetryFailureWarningEmitted = new AtomicBoolean();

  public CurrentAccountCacheTelemetry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  void recordCacheOutcome(CacheOutcome outcome) {
    recordSafely(() -> incrementCacheOutcome(outcome, NONE));
  }

  void recordCacheFailure(CacheOutcome outcome, RuntimeException exception) {
    recordSafely(
        () -> {
          incrementCacheOutcome(outcome, NONE);
          warnCacheOnce(outcome, NONE, exception.getClass().getSimpleName());
        });
  }

  void recordInvalidValue(InvalidValueReason reason) {
    recordInvalidValue(reason, NONE);
  }

  void recordInvalidValue(InvalidValueReason reason, RuntimeException exception) {
    recordInvalidValue(reason, exception.getClass().getSimpleName());
  }

  void recordMutationSuccess(MutationStage stage) {
    recordSafely(() -> incrementMutation(stage, MutationOutcome.SUCCEEDED));
  }

  void recordMutationFailure(
      MutationStage stage, MutationOutcome outcome, RuntimeException exception) {
    recordSafely(
        () -> {
          incrementMutation(stage, outcome);
          warnMutationOnce(stage, outcome, exception.getClass().getSimpleName());
        });
  }

  private void recordInvalidValue(InvalidValueReason reason, String exceptionClass) {
    recordSafely(
        () -> {
          incrementCacheOutcome(CacheOutcome.INVALID_VALUE, reason.value());
          warnCacheOnce(CacheOutcome.INVALID_VALUE, reason.value(), exceptionClass);
        });
  }

  private void recordSafely(Runnable recording) {
    try {
      recording.run();
    } catch (RuntimeException exception) {
      warnTelemetryFailureOnce(exception.getClass().getSimpleName());
    }
  }

  private void warnTelemetryFailureOnce(String exceptionClass) {
    try {
      if (log.isWarnEnabled() && telemetryFailureWarningEmitted.compareAndSet(false, true)) {
        log.warn("CURRENT_ACCOUNT_CACHE_TELEMETRY_FAILED exception_class={}", exceptionClass);
      }
    } catch (RuntimeException _) {
      // Telemetry must never alter cache or transaction behavior.
    }
  }

  private void incrementCacheOutcome(CacheOutcome outcome, String reason) {
    meterRegistry
        .counter(
            CACHE_OUTCOMES_TOTAL,
            TAG_OUTCOME,
            BoundedMetricTagGuard.requireAllowed(
                TAG_OUTCOME, outcome.value(), CACHE_OUTCOME_VALUES),
            TAG_REASON,
            BoundedMetricTagGuard.requireAllowed(TAG_REASON, reason, CACHE_REASON_VALUES))
        .increment();
  }

  private void incrementMutation(MutationStage stage, MutationOutcome outcome) {
    meterRegistry
        .counter(
            CACHE_MUTATIONS_TOTAL,
            TAG_STAGE,
            BoundedMetricTagGuard.requireAllowed(TAG_STAGE, stage.value(), MUTATION_STAGE_VALUES),
            TAG_OUTCOME,
            BoundedMetricTagGuard.requireAllowed(
                TAG_OUTCOME, outcome.value(), MUTATION_OUTCOME_VALUES))
        .increment();
  }

  private void warnCacheOnce(CacheOutcome outcome, String reason, String exceptionClass) {
    var warningKey = new WarningKey("cache", outcome.value(), reason);
    if (log.isWarnEnabled() && emittedWarnings.add(warningKey)) {
      log.warn(
          "CURRENT_ACCOUNT_CACHE outcome={} reason={} stage={} exception_class={} trace_id={}",
          outcome.value(),
          reason,
          outcome.stage(),
          exceptionClass,
          currentTraceId());
    }
  }

  private void warnMutationOnce(
      MutationStage stage, MutationOutcome outcome, String exceptionClass) {
    var warningKey = new WarningKey("mutation", stage.value(), outcome.value());
    if (log.isWarnEnabled() && emittedWarnings.add(warningKey)) {
      log.warn(
          "CURRENT_ACCOUNT_CACHE_MUTATION stage={} outcome={} exception_class={} trace_id={}",
          stage.value(),
          outcome.value(),
          exceptionClass,
          currentTraceId());
    }
  }

  private static String currentTraceId() {
    var spanContext = Span.current().getSpanContext();
    return spanContext.isValid() ? spanContext.getTraceId() : "unavailable";
  }

  enum CacheOutcome implements BoundedMetricTagValue {
    HIT("hit", "read"),
    MISS("miss", "read"),
    READ_FAILURE("read_failure", "read"),
    POPULATE_FAILURE("populate_failure", "populate"),
    INVALID_VALUE("invalid_value", "read");

    private final String code;
    private final String stage;

    CacheOutcome(String code, String stage) {
      this.code = code;
      this.stage = stage;
    }

    @Override
    public String value() {
      return code;
    }

    String stage() {
      return stage;
    }
  }

  enum InvalidValueReason implements BoundedMetricTagValue {
    UNREADABLE_JSON("unreadable_json"),
    NOT_OBJECT("not_object"),
    MISSING_FIELD("missing_field"),
    WRONG_TYPE("wrong_type"),
    UNSUPPORTED_VERSION("unsupported_version"),
    USER_ID_MISMATCH("user_id_mismatch"),
    NEGATIVE_VALUE("negative_value");

    private final String code;

    InvalidValueReason(String code) {
      this.code = code;
    }

    @Override
    public String value() {
      return code;
    }
  }

  enum MutationStage implements BoundedMetricTagValue {
    REQUIRED_PRECOMMIT_EVICT("required_precommit_evict"),
    BEST_EFFORT_POSTCOMMIT_EVICT("best_effort_postcommit_evict");

    private final String code;

    MutationStage(String code) {
      this.code = code;
    }

    @Override
    public String value() {
      return code;
    }
  }

  enum MutationOutcome implements BoundedMetricTagValue {
    SUCCEEDED("succeeded"),
    ROLLED_BACK("rolled_back"),
    COMMITTED_REPAIR_FAILED("committed_repair_failed");

    private final String code;

    MutationOutcome(String code) {
      this.code = code;
    }

    @Override
    public String value() {
      return code;
    }
  }

  private record WarningKey(String category, String classification, String detail) {}
}
