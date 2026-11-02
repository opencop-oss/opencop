// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component("opencopProcessorMetrics")
public class ProcessorMetrics {

  private final MeterRegistry meterRegistry;

  public ProcessorMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public static ProcessorMetrics noop() {
    return new ProcessorMetrics(null);
  }

  public void incrementCounter(String name, Tags tags) {
    incrementCounter(name, tags, 1);
  }

  public void incrementCounter(String name, Tags tags, double amount) {
    withRegistry(registry -> registry.counter(name, tags).increment(amount));
  }

  public void recordTimer(String name, Tags tags, Duration duration) {
    withRegistry(registry -> registry.timer(name, tags).record(duration));
  }

  public void recordSummary(String name, Tags tags, double amount) {
    withRegistry(registry -> registry.summary(name, tags).record(amount));
  }

  public void registerGauge(String name, Tags tags, AtomicLong value) {
    withRegistry(registry -> registry.gauge(name, tags, value));
  }

  private void withRegistry(Consumer<MeterRegistry> action) {
    Optional.ofNullable(meterRegistry).ifPresent(action);
  }
}
