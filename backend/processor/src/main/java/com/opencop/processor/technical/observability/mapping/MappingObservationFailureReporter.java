// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability.mapping;

import com.opencop.processor.ports.business.observability.mapping.MappingObservationFailureSink;
import java.time.Duration;
import java.util.Objects;
import java.util.function.ObjLongConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports observability-path failures as a bounded WARN stream: at most one summary per interval,
 * carrying the triggering exception class and the number of failures suppressed since the last
 * emission. A sink failure consumes only the current interval's emission; reporting resumes with
 * the next interval, so a transient failure cannot silence the reporter for the process lifetime.
 */
public final class MappingObservationFailureReporter implements MappingObservationFailureSink {

  private static final Logger log =
      LoggerFactory.getLogger(MappingObservationFailureReporter.class);

  private final GlobalMappingWarningLimiter limiter;
  private final ObjLongConsumer<String> sink;

  public MappingObservationFailureReporter(Duration reportInterval) {
    this(
        new GlobalMappingWarningLimiter(1, reportInterval),
        (exceptionClass, suppressedCount) ->
            log.warn(
                "REALTIME_MAPPING_OBSERVABILITY_FAILED exceptionClass={} suppressedSinceLastReport={}",
                exceptionClass,
                suppressedCount));
  }

  MappingObservationFailureReporter(
      GlobalMappingWarningLimiter limiter, ObjLongConsumer<String> sink) {
    this.limiter = Objects.requireNonNull(limiter, "limiter");
    this.sink = Objects.requireNonNull(sink, "sink");
  }

  @Override
  public void report(Class<?> exceptionClass) {
    try {
      var name = Objects.requireNonNull(exceptionClass, "exceptionClass").getSimpleName();
      var admission = limiter.admitCandidate();
      if (admission.emitCandidate()) {
        sink.accept(name, admission.priorSuppressedCount());
      }
    } catch (RuntimeException _) {
      // Observability failures must not escape ingestion, including a broken reporting sink.
    }
  }
}
