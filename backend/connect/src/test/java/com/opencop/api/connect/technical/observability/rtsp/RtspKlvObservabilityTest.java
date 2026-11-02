// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.observability.rtsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.api.connect.technical.observability.rtsp.RtspKlvObservability.Outcome;
import com.opencop.api.connect.technical.observability.rtsp.RtspKlvObservability.Reason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RtspKlvObservabilityTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final RtspKlvObservability observability =
      new RtspKlvObservability(
          registry, GlobalOpenTelemetry.getTracer(RtspKlvObservability.TRACER_NAME));

  @Test
  void sessionObservationRecordsOutcomeAndMaintainsActiveStreamGauge() {
    var session = observability.startSession();

    assertThat(registry.get(RtspKlvObservability.ACTIVE_STREAMS).gauge().value()).isEqualTo(1.0d);

    session.close(Outcome.DISCONNECTED, Reason.NONE, null);
    session.close(Outcome.ERROR, Reason.FAILURE, null);

    assertThat(registry.get(RtspKlvObservability.ACTIVE_STREAMS).gauge().value()).isZero();
    assertThat(
            registry
                .get(RtspKlvObservability.SESSIONS_TOTAL)
                .tags("outcome", "disconnected", "reason", "NONE")
                .counter()
                .count())
        .isEqualTo(1.0d);
    assertThat(
            registry
                .get(RtspKlvObservability.SESSION_DURATION)
                .tags("outcome", "disconnected", "reason", "NONE")
                .timer()
                .count())
        .isEqualTo(1L);
  }

  @Test
  void firstUnitAndKlvUnitMetricsRecordBoundedSignalsOnly() {
    observability.recordFirstUnit(Outcome.SUCCESS, Reason.NONE, Duration.ofMillis(25));
    observability.recordKlvUnit(42);

    var firstUnitTimer =
        registry
            .get(RtspKlvObservability.FIRST_UNIT_DURATION)
            .tags("outcome", "success", "reason", "NONE")
            .timer();
    assertThat(firstUnitTimer.count()).isEqualTo(1L);
    assertThat(firstUnitTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(25.0d);

    assertThat(registry.get(RtspKlvObservability.UNITS_TOTAL).counter().count()).isEqualTo(1.0d);
    var unitBytes = registry.get(RtspKlvObservability.UNIT_BYTES).summary();
    assertThat(unitBytes.count()).isEqualTo(1L);
    assertThat(unitBytes.totalAmount()).isEqualTo(42.0d);
    assertThat(unitBytes.getId().getBaseUnit()).isEqualTo("bytes");
  }

  @Test
  void sessionObservationDoesNotRecordUrlBearingThrowableOnSpan() {
    var span = mock(Span.class);
    var spanBuilder = mock(SpanBuilder.class);
    var tracer = mock(Tracer.class);
    when(tracer.spanBuilder(RtspKlvObservability.SESSION_SPAN)).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(span);
    var localObservability = new RtspKlvObservability(new SimpleMeterRegistry(), tracer);
    var throwable =
        new RuntimeException("Failed to open rtsp://user:pass@example.com:8554/path?token=secret");

    localObservability.startSession().close(Outcome.ERROR, Reason.FAILURE, throwable);

    verify(span, never()).recordException(any(Throwable.class));
    verify(span).setStatus(StatusCode.ERROR, "FAILURE");
    verify(span).end();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", " ", "not_allowed", "123e4567-e89b-12d3-a456-426614174000", "45.12,-93.5"})
  void recordSessionRejectsUnboundedOutcomeTags(String outcome) {
    assertThatThrownBy(() -> observability.recordSession(outcome, "NONE", Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(registry.find(RtspKlvObservability.SESSIONS_TOTAL).counter()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", " ", "not_allowed", "123e4567-e89b-12d3-a456-426614174000", "45.12,-93.5"})
  void recordSessionRejectsUnboundedReasonTags(String reason) {
    assertThatThrownBy(() -> observability.recordSession("error", reason, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(registry.find(RtspKlvObservability.SESSIONS_TOTAL).counter()).isNull();
  }
}
