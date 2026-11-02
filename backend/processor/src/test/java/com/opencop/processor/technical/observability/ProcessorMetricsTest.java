// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.system.SystemMetricsAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

class ProcessorMetricsTest {

  @Test
  void recordsCommonMicrometerMeterTypes() {
    var registry = new SimpleMeterRegistry();
    var metrics = new ProcessorMetrics(registry);
    var tags = Tags.of("surface", "dlt");
    var gaugeValue = new AtomicLong(42);

    metrics.incrementCounter("opencop_test_counter_total", tags);
    metrics.recordTimer("opencop_test_timer", tags, Duration.ofMillis(25));
    metrics.recordSummary("opencop_test_summary", tags, 11);
    metrics.registerGauge("opencop_test_gauge", tags, gaugeValue);

    assertThat(registry.counter("opencop_test_counter_total", tags).count()).isEqualTo(1.0);
    assertThat(registry.timer("opencop_test_timer", tags).count()).isEqualTo(1);
    assertThat(registry.summary("opencop_test_summary", tags).totalAmount()).isEqualTo(11.0);
    assertThat(registry.find("opencop_test_gauge").tags(tags).gauge().value()).isEqualTo(42.0);
  }

  @Test
  void componentBeanNameDoesNotCollideWithSpringBootSystemProcessorMetrics() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SystemMetricsAutoConfiguration.class))
        .withUserConfiguration(ProcessorMetricsScan.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(ProcessorMetrics.class);
              assertThat(context).hasBean("opencopProcessorMetrics");
              assertThat(context).hasBean("processorMetrics");
            });
  }

  @Configuration
  @ComponentScan(basePackageClasses = ProcessorMetrics.class)
  static class ProcessorMetricsScan {

    @Bean
    SimpleMeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
