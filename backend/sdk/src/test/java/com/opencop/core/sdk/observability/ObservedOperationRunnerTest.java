// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.observability.ObservedOperationRunner.Observation;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ObservedOperationRunnerTest {

  private final Tracer tracer = mock(Tracer.class);
  private final SpanBuilder spanBuilder = mock(SpanBuilder.class);
  private final Span span = mock(Span.class);
  private final Scope scope = mock(Scope.class);

  @Test
  void run_whenOperationSucceeds_recordsSuccessAndClosesSpan() {
    wireSpan();
    var runner = new ObservedOperationRunner(tracer);
    var successDuration = new AtomicReference<Duration>();
    var errorDuration = new AtomicReference<Duration>();

    String result =
        runner.run(
            new Observation("operation.test", Attributes.empty(), "failed"),
            () -> "ok",
            successDuration::set,
            errorDuration::set,
            error -> "fallback");

    assertEquals("ok", result);
    assertTrue(successDuration.get().compareTo(Duration.ZERO) >= 0);
    assertEquals(null, errorDuration.get());
    verify(tracer).spanBuilder("operation.test");
    verify(spanBuilder).setAllAttributes(Attributes.empty());
    verify(span).setStatus(StatusCode.OK);
    verify(scope).close();
    verify(span).end();
  }

  @Test
  void run_whenOperationFails_recordsErrorAndReturnsFallback() {
    wireSpan();
    var runner = new ObservedOperationRunner(tracer);
    var failure = new IllegalStateException("boom");
    var errorDuration = new AtomicReference<Duration>();

    String result =
        runner.run(
            new Observation("operation.test", Attributes.empty(), "failed"),
            () -> {
              throw failure;
            },
            duration -> {},
            errorDuration::set,
            error -> {
              assertSame(failure, error);
              return "fallback";
            });

    assertEquals("fallback", result);
    assertTrue(errorDuration.get().compareTo(Duration.ZERO) >= 0);
    verify(span).setStatus(StatusCode.ERROR, "failed");
    verify(span).recordException(failure);
    verify(scope).close();
    verify(span).end();
  }

  @Test
  void run_whenTracerIsNull_runsWithoutSpan() {
    var runner = new ObservedOperationRunner(null);
    var successDuration = new AtomicReference<Duration>();

    String result =
        runner.run(
            new Observation("operation.test", null, "failed"),
            () -> "ok",
            successDuration::set,
            duration -> {},
            error -> "fallback");

    assertEquals("ok", result);
    assertTrue(successDuration.get().compareTo(Duration.ZERO) >= 0);
    verify(tracer, never()).spanBuilder(any(String.class));
  }

  @Test
  void run_doesNotCatchJvmErrors() {
    var runner = new ObservedOperationRunner(null);
    var error = new AssertionError("fatal");

    var thrown =
        assertThrows(
            AssertionError.class,
            () ->
                runner.run(
                    new Observation("operation.test", null, "failed"),
                    () -> {
                      throw error;
                    },
                    duration -> {},
                    duration -> {},
                    runtimeException -> "fallback"));

    assertSame(error, thrown);
  }

  private void wireSpan() {
    when(tracer.spanBuilder("operation.test")).thenReturn(spanBuilder);
    when(spanBuilder.setAllAttributes(any(Attributes.class))).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(span);
    when(span.makeCurrent()).thenReturn(scope);
  }
}
