// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.observability;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;

public final class ObservedOperationRunner {

  private final Tracer tracer;

  public ObservedOperationRunner(Tracer tracer) {
    this.tracer = tracer;
  }

  public <T> T run(
      Observation observation,
      Operation<T> operation,
      DurationObserver onSuccess,
      DurationObserver onError,
      Function<RuntimeException, T> fallback) {
    Objects.requireNonNull(observation, "observation");
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(onSuccess, "onSuccess");
    Objects.requireNonNull(onError, "onError");
    Objects.requireNonNull(fallback, "fallback");

    Instant startedAt = Instant.now();
    Span span = null;
    Scope scope = null;
    if (tracer != null) {
      span =
          tracer
              .spanBuilder(observation.spanName())
              .setAllAttributes(observation.attributes())
              .startSpan();
      scope = span.makeCurrent();
    }

    try {
      T result = operation.execute();
      onSuccess.record(Duration.between(startedAt, Instant.now()));
      if (span != null) {
        span.setStatus(StatusCode.OK);
      }
      return result;
    } catch (RuntimeException error) {
      if (span != null) {
        span.setStatus(StatusCode.ERROR, observation.errorDescription());
        span.recordException(error);
      }
      onError.record(Duration.between(startedAt, Instant.now()));
      return fallback.apply(error);
    } finally {
      if (scope != null) {
        scope.close();
      }
      if (span != null) {
        span.end();
      }
    }
  }

  public record Observation(String spanName, Attributes attributes, String errorDescription) {
    public Observation {
      spanName = Objects.requireNonNull(spanName, "spanName");
      attributes = attributes == null ? Attributes.empty() : attributes;
      errorDescription = errorDescription == null ? "" : errorDescription;
    }
  }

  @FunctionalInterface
  public interface Operation<T> {
    T execute();
  }

  @FunctionalInterface
  public interface DurationObserver {
    void record(Duration duration);
  }
}
