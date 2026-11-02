// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.model.history.HistoryEvent;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.gateway.cache.redis.RedisConnectionAutoConfiguration;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobLifecycleUpdater;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.business.usecase.history.HistoricalSubjectLaneScheduler;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionCoordinator;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobService;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.db.HistoricalPostGisChunkReadGateway;
import com.opencop.processor.technical.gateway.history.HistoricalChunkPayloadPersistence;
import com.opencop.processor.technical.gateway.history.HistoricalJobTerminalCoordinator;
import com.opencop.processor.technical.gateway.history.HistoryJobPlanPublisher;
import com.opencop.processor.technical.gateway.history.ProcessorHistoricalJobLifecycleUpdater;
import com.opencop.processor.technical.gateway.history.RedisHistoricalChunkReadyNotifier;
import com.opencop.processor.technical.gateway.history.cancellation.HistoricalJobCancellationFinalizer;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates that production component scanning — not a manually enumerated list of imports —
 * discovers the migrated {@code @Component} collaborators and selects the intended autowired
 * constructors. The scan roots below are the same packages the production
 * {@code @SpringBootApplication} scans through; only true external ports ({@code
 * StringRedisTemplate}, {@code MeterRegistry}, external publishers/templates) are mocked.
 */
class HistoricalExecutionConfigComponentTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(RedisConnectionAutoConfiguration.class))
          .withUserConfiguration(
              TestDependencies.class,
              HistoricalExecutionConfig.class,
              ScanRoots.class,
              // technical.gateway.db contains LayerCopyRepository, which needs a JdbcTemplate that
              // isn't part of the historical-tracks domain; pull just the one historical gateway in
              // by class instead of scanning that package.
              HistoricalPostGisChunkReadGateway.class);

  @Test
  void historicalExecutionConfig_wiresMigratedComponentsThroughScanning() {
    contextRunner.run(
        context -> {
          // The migration moved these onto the implementation classes; scanning must find them.
          assertThat(context).hasSingleBean(HistoricalObservability.class);
          assertThat(context).hasSingleBean(HistoricalSubjectLaneScheduler.class);
          assertThat(context).hasSingleBean(HistoricalPostGisChunkReadGateway.class);
          assertThat(context).hasSingleBean(HistoricalChunkPayloadPersistence.class);
          assertThat(context).hasSingleBean(HistoricalJobTerminalCoordinator.class);
          assertThat(context).hasSingleBean(ProcessorHistoricalJobLifecycleUpdater.class);
          assertThat(context).hasSingleBean(HistoryJobPlanPublisher.class);
          assertThat(context).hasSingleBean(HistoricalJobCancellationFinalizer.class);
          assertThat(context).hasSingleBean(HistoricalJobService.class);
          assertThat(context).hasSingleBean(HistoricalJobStateGateway.class);
          assertThat(context).hasSingleBean(HistoricalJobAdmissionCoordinator.class);

          // The notifier specifically lost a static-factory constructor on this branch; scanning
          // must discover the canonical (CachePublisher, Tracer) constructor and resolve the
          // Tracer from the primary historicalTracer bean rather than from GlobalOpenTelemetry.
          assertThat(context).hasSingleBean(RedisHistoricalChunkReadyNotifier.class);
          RedisHistoricalChunkReadyNotifier notifier =
              context.getBean(RedisHistoricalChunkReadyNotifier.class);
          Tracer primaryTracer = context.getBean(Tracer.class);
          assertThat(notifier).extracting("tracer").isSameAs(primaryTracer);
        });
  }

  /**
   * Mirrors the production scan roots that pick up every migrated {@code @Component} in the
   * historical execution domain (business policies + use cases, observability, Redis-backed
   * technical gateways, and the PostGIS read gateway).
   */
  @Configuration
  @ComponentScan(
      basePackages = {
        "com.opencop.processor.business.policy.history",
        "com.opencop.processor.business.usecase.history",
        "com.opencop.processor.technical.observability.history",
        "com.opencop.processor.technical.gateway.history",
      })
  static class ScanRoots {}

  @Configuration
  static class TestDependencies {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    StringRedisTemplate stringRedisTemplate() {
      return mock(StringRedisTemplate.class);
    }

    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    RedisTemplate<String, byte[]> byteArrayRedisTemplate() {
      return (RedisTemplate<String, byte[]>) (RedisTemplate) mock(RedisTemplate.class);
    }

    @Bean
    NamedParameterJdbcTemplate namedParameterJdbcTemplate() {
      return mock(NamedParameterJdbcTemplate.class);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return mock(PlatformTransactionManager.class);
    }

    @Bean
    @SuppressWarnings("unchecked")
    CachePublisher<HistoryEvent> historyEventsPublisher() {
      return mock(CachePublisher.class);
    }

    @Bean
    JobStatusNotificationPublisher jobStatusNotificationPublisher() {
      return mock(JobStatusNotificationPublisher.class);
    }

    @Bean
    HistoricalJobLifecycleUpdater historicalJobLifecycleUpdater() {
      return mock(HistoricalJobLifecycleUpdater.class);
    }
  }
}
