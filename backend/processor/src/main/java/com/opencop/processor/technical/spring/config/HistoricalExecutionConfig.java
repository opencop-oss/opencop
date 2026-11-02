// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import com.opencop.core.sdk.model.history.HistoryEvent;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobLifecycleUpdater;
import com.opencop.core.sdk.technical.gateway.history.HistoryJobDeliveryCoordinator;
import com.opencop.core.sdk.technical.gateway.history.RedisHistoricalJobDeliveryRepository;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPayloadBuilder;
import com.opencop.processor.business.policy.history.HistoricalCacheIdentity;
import com.opencop.processor.business.policy.history.HistoricalChunkFailurePolicy;
import com.opencop.processor.business.policy.history.HistoricalJobAdmissionRecoveryPolicy;
import com.opencop.processor.business.usecase.history.HistoricalChunkCancellationGuard;
import com.opencop.processor.business.usecase.history.HistoricalChunkComputeKeepalive;
import com.opencop.processor.business.usecase.history.HistoricalChunkDeliveryProgressReporter;
import com.opencop.processor.business.usecase.history.HistoricalChunkOperationExecutor;
import com.opencop.processor.business.usecase.history.HistoricalJobAdmissionRecoveryFactory;
import com.opencop.processor.business.usecase.history.HistoricalParallelChunkExecutor;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Spring wiring for the historical execution domain.
 *
 * <p>Wiring rule: only declare an {@code @Bean} method here when the bean genuinely needs
 * configuration logic — translating {@link HistoricalExecutionProperties} values into constructor
 * arguments, calling a static factory ({@code GlobalOpenTelemetry.getTracer}, {@code
 * Clock.systemUTC()}, {@code HistoricalChunkPayloadBuilder.create}), resolving an ambiguous
 * dependency with {@code @Qualifier}, or owning an executor lifecycle ({@code destroyMethod}). Pure
 * compositions that just take their collaborators and call {@code new} belong on the implementation
 * class as {@code @Component} instead — Spring auto-wires the same graph with zero configuration
 * code.
 */
@Configuration
@EnableConfigurationProperties(HistoricalExecutionProperties.class)
public class HistoricalExecutionConfig {
  public static final String PROCESSOR_CONSUMER_TRACER_BEAN = "processorConsumerTracer";

  private static final String JOB_EXECUTOR_THREAD_PREFIX = "history-job-";
  private static final String JOB_SCHEDULER_THREAD_PREFIX = "history-job-scheduler-";
  private static final String CHUNK_EXECUTOR_THREAD_PREFIX = "history-chunk-";

  // === Tracers + Clock (static factories) ===

  @Bean
  @Primary
  Tracer historicalTracer() {
    return GlobalOpenTelemetry.getTracer(HistoricalTelemetry.HISTORICAL_TRACER_NAME);
  }

  @Bean(PROCESSOR_CONSUMER_TRACER_BEAN)
  Tracer processorConsumerTracer() {
    return GlobalOpenTelemetry.getTracer(HistoricalTelemetry.PROCESSOR_CONSUMER_TRACER_NAME);
  }

  @Bean
  Clock historicalJobClock() {
    return Clock.systemUTC();
  }

  // === Property-bridge factories ===

  @Bean
  HistoricalChunkPayloadBuilder historicalChunkPayloadBuilder(
      tools.jackson.databind.ObjectMapper objectMapper,
      HistoricalCacheIdentity historicalCacheIdentity,
      HistoricalExecutionProperties properties) {
    return HistoricalChunkPayloadBuilder.create(
        objectMapper,
        historicalCacheIdentity,
        properties.getMaxPayloadPartBytes(),
        properties.getMaxChunkPayloadBytes());
  }

  @Bean
  HistoricalChunkFailurePolicy historicalChunkFailurePolicy(
      HistoricalExecutionProperties properties) {
    return new HistoricalChunkFailurePolicy(
        properties.getMaxConsecutiveChunkFailures(), properties.getMaxFailureRatio());
  }

  @Bean
  HistoricalJobAdmissionRecoveryFactory historicalJobAdmissionRecoveryFactory(
      HistoricalExecutionProperties properties,
      HistoricalJobAdmissionRecoveryPolicy historicalJobAdmissionRecoveryPolicy) {
    return new HistoricalJobAdmissionRecoveryFactory(
        properties.getAdmissionRecoveryInterval(),
        properties.getTtlRefreshInterval(),
        properties.getMaxRuntime(),
        properties.getJobStateTtl(),
        historicalJobAdmissionRecoveryPolicy);
  }

  // === SDK collaborators (not annotated; constructed here) ===

  @Bean
  RedisHistoricalJobDeliveryRepository historicalJobDeliveryRepository(
      StringRedisTemplate stringRedisTemplate) {
    return new RedisHistoricalJobDeliveryRepository(stringRedisTemplate);
  }

  @Bean
  HistoryJobDeliveryCoordinator historyJobDeliveryCoordinator(
      RedisHistoricalJobDeliveryRepository historicalJobDeliveryRepository,
      HistoricalJobLifecycleUpdater historicalJobLifecycleUpdater,
      CachePublisher<HistoryEvent> historyEventsPublisher,
      HistoricalExecutionProperties properties) {
    return new HistoryJobDeliveryCoordinator(
        historicalJobDeliveryRepository,
        historicalJobLifecycleUpdater,
        historyEventsPublisher,
        properties.getJobStateTtl());
  }

  // === Qualifier-resolving wiring ===

  @Bean
  HistoricalParallelChunkExecutor historicalParallelChunkExecutor(
      @Qualifier("historicalChunkExecutorFactory")
          Supplier<ExecutorService> historicalChunkExecutorFactory,
      HistoricalExecutionProperties properties,
      HistoricalChunkFailurePolicy historicalChunkFailurePolicy,
      HistoricalChunkCancellationGuard historicalChunkCancellationGuard,
      HistoricalChunkDeliveryProgressReporter historicalChunkDeliveryProgressReporter,
      HistoricalChunkOperationExecutor historicalChunkOperationExecutor,
      HistoricalChunkComputeKeepalive historicalChunkComputeKeepalive) {
    return new HistoricalParallelChunkExecutor(
        historicalChunkExecutorFactory,
        properties.getMaxConcurrentChunksPerJob(),
        historicalChunkFailurePolicy,
        historicalChunkCancellationGuard,
        historicalChunkDeliveryProgressReporter,
        historicalChunkOperationExecutor,
        historicalChunkComputeKeepalive);
  }

  // === Optional<…> injection bridge for the delivery progress reporter ===

  @Bean
  HistoricalChunkDeliveryProgressReporter historicalChunkDeliveryProgressReporter(
      HistoryEventPublisher historyEventPublisher,
      Optional<HistoryJobDeliveryCoordinator> historyJobDeliveryCoordinator) {
    return new HistoricalChunkDeliveryProgressReporter(
        historyEventPublisher, historyJobDeliveryCoordinator.orElse(null));
  }

  // === Executor lifecycles ===

  @Bean(name = "historicalJobExecutor", destroyMethod = "shutdown")
  ExecutorService historicalJobExecutor(HistoricalExecutionProperties properties) {
    BlockingQueue<Runnable> queue =
        new LinkedBlockingQueue<>(Math.max(1, properties.getQueueCapacity()));
    return new ThreadPoolExecutor(
        Math.max(1, properties.getWorkerPoolSize()),
        Math.max(1, properties.getWorkerPoolSize()),
        0L,
        TimeUnit.MILLISECONDS,
        queue,
        new NamedThreadFactory(JOB_EXECUTOR_THREAD_PREFIX),
        new ThreadPoolExecutor.AbortPolicy());
  }

  @Bean(name = "historicalLaneJobExecutor", destroyMethod = "shutdown")
  ExecutorService historicalLaneJobExecutor(HistoricalExecutionProperties properties) {
    BlockingQueue<Runnable> queue =
        new LinkedBlockingQueue<>(Math.max(1, properties.getQueueCapacity()));
    return new ThreadPoolExecutor(
        Math.max(1, properties.getWorkerPoolSize()),
        Math.max(1, properties.getWorkerPoolSize()),
        0L,
        TimeUnit.MILLISECONDS,
        queue,
        new NamedThreadFactory(JOB_EXECUTOR_THREAD_PREFIX + "lane-"),
        new ThreadPoolExecutor.AbortPolicy());
  }

  @Bean(name = "historicalJobScheduler", destroyMethod = "shutdownNow")
  ScheduledExecutorService historicalJobScheduler() {
    return Executors.newSingleThreadScheduledExecutor(
        new NamedThreadFactory(JOB_SCHEDULER_THREAD_PREFIX));
  }

  @Bean(name = "historicalChunkExecutorFactory")
  Supplier<ExecutorService> historicalChunkExecutorFactory(
      HistoricalExecutionProperties properties) {
    return () ->
        new ThreadPoolExecutor(
            Math.max(1, properties.getMaxConcurrentChunksPerJob()),
            Math.max(1, properties.getMaxConcurrentChunksPerJob()),
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(Math.max(1, properties.getQueueCapacity())),
            new NamedThreadFactory(CHUNK_EXECUTOR_THREAD_PREFIX),
            new ThreadPoolExecutor.AbortPolicy());
  }

  private static final class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger(1);

    private NamedThreadFactory(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
      Thread thread = new Thread(runnable, prefix + counter.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    }
  }
}
