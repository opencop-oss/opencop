// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.observability.rtsp;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class RtspKlvObservability {
  public static final String TRACER_NAME = "opencop.connect-rtsp-klv";

  static final String SESSION_SPAN = "connect.rtsp_klv.session";
  static final String SESSIONS_TOTAL = "opencop_connect_rtsp_klv_sessions_total";
  static final String SESSION_DURATION = "opencop_connect_rtsp_klv_session_duration_seconds";
  static final String FIRST_UNIT_DURATION = "opencop_connect_rtsp_klv_first_unit_duration_seconds";
  static final String UNITS_TOTAL = "opencop_connect_rtsp_klv_units_total";
  static final String UNIT_BYTES = "opencop_connect_rtsp_klv_unit_bytes";
  static final String ACTIVE_STREAMS = "opencop_connect_rtsp_klv_active_streams";

  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "^[\\da-fA-F]{8}-[\\da-fA-F]{4}-[\\da-fA-F]{4}-[\\da-fA-F]{4}-[\\da-fA-F]{12}$");
  private static final Pattern COORDINATE_PATTERN =
      Pattern.compile("^[+-]?\\d{1,3}(?:\\.\\d+)?(?:,[+-]?\\d{1,3}(?:\\.\\d+)?)?$");
  private static final Map<TagKey, Set<String>> TAG_VALUE_ALLOWLIST =
      Map.of(
          TagKey.OUTCOME, tagValues(Outcome.values()), TagKey.REASON, tagValues(Reason.values()));

  private final MeterRegistry meterRegistry;
  private final Tracer tracer;
  private final AtomicInteger activeStreams = new AtomicInteger();
  private final Counter klvUnitsTotal;
  private final DistributionSummary klvUnitBytes;

  public RtspKlvObservability(MeterRegistry meterRegistry, Tracer tracer) {
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    this.tracer = Objects.requireNonNull(tracer, "tracer");
    klvUnitsTotal = this.meterRegistry.counter(UNITS_TOTAL);
    klvUnitBytes =
        DistributionSummary.builder(UNIT_BYTES).baseUnit("bytes").register(this.meterRegistry);
    Gauge.builder(ACTIVE_STREAMS, activeStreams, AtomicInteger::get).register(this.meterRegistry);
  }

  public SessionObservation startSession() {
    var span = tracer.spanBuilder(SESSION_SPAN).startSpan();
    activeStreams.incrementAndGet();
    return new SessionObservation(span, System.nanoTime());
  }

  public void recordFirstUnit(Outcome outcome, Reason reason, Duration duration) {
    recordFirstUnit(outcome.value(), reason.value(), duration);
  }

  void recordFirstUnit(String outcome, String reason, Duration duration) {
    var boundedOutcome = requireBounded(TagKey.OUTCOME, outcome);
    var boundedReason = requireBounded(TagKey.REASON, reason);
    Timer.builder(FIRST_UNIT_DURATION)
        .tags(
            List.of(
                Tag.of(TagKey.OUTCOME.value(), boundedOutcome),
                Tag.of(TagKey.REASON.value(), boundedReason)))
        .register(meterRegistry)
        .record(nonNegative(duration));
  }

  public void recordKlvUnit(int bytes) {
    klvUnitsTotal.increment();
    klvUnitBytes.record(Math.max(bytes, 0));
  }

  void recordSession(String outcome, String reason, Duration duration) {
    var boundedOutcome = requireBounded(TagKey.OUTCOME, outcome);
    var boundedReason = requireBounded(TagKey.REASON, reason);
    meterRegistry
        .counter(
            SESSIONS_TOTAL,
            TagKey.OUTCOME.value(),
            boundedOutcome,
            TagKey.REASON.value(),
            boundedReason)
        .increment();
    Timer.builder(SESSION_DURATION)
        .tags(
            List.of(
                Tag.of(TagKey.OUTCOME.value(), boundedOutcome),
                Tag.of(TagKey.REASON.value(), boundedReason)))
        .register(meterRegistry)
        .record(nonNegative(duration));
  }

  private void recordSession(Outcome outcome, Reason reason, Duration duration) {
    recordSession(outcome.value(), reason.value(), duration);
  }

  private static Duration nonNegative(Duration duration) {
    if (duration == null || duration.isNegative()) {
      return Duration.ZERO;
    }
    return duration;
  }

  private static String requireBounded(TagKey tagKey, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(tagKey.value() + " must be non-empty");
    }
    if (UUID_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(tagKey.value() + " must not be a UUID-like value");
    }
    if (COORDINATE_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(tagKey.value() + " must not be a coordinate-like value");
    }
    var allowedValues = TAG_VALUE_ALLOWLIST.get(tagKey);
    if (allowedValues == null || !allowedValues.contains(value)) {
      throw new IllegalArgumentException(tagKey.value() + " must be one of " + allowedValues);
    }
    return value;
  }

  private static Set<String> tagValues(BoundedTagValue... values) {
    return Arrays.stream(values)
        .map(BoundedTagValue::value)
        .collect(Collectors.toUnmodifiableSet());
  }

  public enum Outcome implements BoundedTagValue {
    SUCCESS("success"),
    DISCONNECTED("disconnected"),
    STOPPED("stopped"),
    INCOMPATIBLE("incompatible"),
    ERROR("error");

    private final String value;

    Outcome(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  public enum Reason implements BoundedTagValue {
    NONE("NONE"),
    NO_KLV_BEFORE_DEADLINE("NO_KLV_BEFORE_DEADLINE"),
    STREAM_ENDED_BEFORE_KLV("STREAM_ENDED_BEFORE_KLV"),
    START_ABORTED("START_ABORTED"),
    FAILURE("FAILURE");

    private final String value;

    Reason(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  public final class SessionObservation implements AutoCloseable {
    private final Span span;
    private final long startedNanos;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private SessionObservation(Span span, long startedNanos) {
      this.span = span;
      this.startedNanos = startedNanos;
    }

    public Scope makeCurrent() {
      return span.makeCurrent();
    }

    public void close(Outcome outcome, Reason reason, Throwable ignored) {
      if (!closed.compareAndSet(false, true)) {
        return;
      }
      try {
        recordSession(outcome, reason, Duration.ofNanos(System.nanoTime() - startedNanos));
        // JavaCV/FFmpeg exception messages can include full RTSP URLs with query auth tokens.
        // Keep session spans to bounded reason codes instead of parsing exception text here.
        if (outcome == Outcome.ERROR || outcome == Outcome.INCOMPATIBLE) {
          span.setStatus(StatusCode.ERROR, reason.value());
        } else {
          span.setStatus(StatusCode.OK, outcome.value());
        }
      } finally {
        span.end();
        activeStreams.decrementAndGet();
      }
    }

    @Override
    public void close() {
      close(Outcome.ERROR, Reason.FAILURE, null);
    }
  }

  private enum TagKey {
    OUTCOME("outcome"),
    REASON("reason");

    private final String value;

    TagKey(String value) {
      this.value = value;
    }

    private String value() {
      return value;
    }
  }

  private interface BoundedTagValue {
    String value();
  }
}
