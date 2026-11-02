// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.watchbox;

import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionHeartbeatWriter;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.config.broker.MessageBrokerConfig;
import com.opencop.processor.technical.spring.config.broker.WatchboxRetainedRetryTracker;
import com.opencop.processor.technical.spring.consumer.WatchboxConsumer;
import com.opencop.processor.technical.spring.consumer.WatchboxDetectionHeartbeatPublisher;
import com.opencop.processor.technical.spring.dlt.DeadLetterIndexingErrorHandlerFactory;
import com.opencop.processor.technical.spring.dlt.DeadLetterObservability;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import tools.jackson.databind.ObjectMapper;

/**
 * The Watchbox listener wired the way production wires it: this context declares no
 * {@code @Bean("watchboxFactory")} and imports the real {@link MessageBrokerConfig}, so the
 * listener factory — its {@code DefaultErrorHandler}, backoff, infrastructure-retry routing,
 * retained-retry tracker, recoverer, and idle-event configuration — is built and registered by
 * production code. A test that declares its own factory cannot validate that assembly; it would
 * also silently dodge the registerSingleton lifecycle defect {@code
 * MessageBrokerProductionWiringTest} pins.
 *
 * <p>The {@link com.opencop.processor.business.usecase.watchbox.WatchboxEventHandler} is
 * deliberately absent: each IT declares its own (throwing, outage-then-recovery, ...) so the error
 * handler under test sees exactly the failure mode the scenario needs.
 */
@TestConfiguration
@Import({MessageBrokerConfig.class, WatchboxConsumer.class})
public class WatchboxComponentTestContext {

  /**
   * Points the production configuration at the embedded broker. Everything else about the listener
   * — concurrency, batching, deserializers, error handling — comes from {@code application.yml} and
   * {@code MessageBrokerConfig}, unchanged.
   */
  @Bean
  BaseKafkaConfig baseKafkaConfig(EmbeddedKafkaBroker broker) {
    return new BaseKafkaConfig(Map.of("bootstrap.servers", broker.getBrokersAsString()));
  }

  /**
   * Recording rather than no-op, so ITs can assert on the facts each heartbeat write would carry -
   * in particular the retained-retry aggregate. ITs that do not care simply never read it.
   */
  @Bean
  RecordingWatchboxDetectionHeartbeatWriter watchboxDetectionHeartbeatWriter() {
    return new RecordingWatchboxDetectionHeartbeatWriter();
  }

  /** Keeps non-time-sensitive Watchbox ITs on the same injected Clock path as production. */
  @Bean
  Clock watchboxComponentClock() {
    return Clock.systemUTC();
  }

  @Bean
  WatchboxRetainedRetryTracker watchboxRetainedRetryTracker(
      @Value("${opencop.messaging-broker.listeners.watchbox.topic}") String watchboxTopic,
      Clock clock,
      ProcessorMetrics metrics) {
    return new WatchboxRetainedRetryTracker(watchboxTopic, clock, metrics);
  }

  @Bean
  WatchboxDetectionHeartbeatPublisher watchboxDetectionHeartbeatPublisher(
      WatchboxDetectionHeartbeatWriter writer,
      ObjectProvider<KafkaListenerEndpointRegistry> registry,
      WatchboxRetainedRetryTracker tracker) {
    return new WatchboxDetectionHeartbeatPublisher(writer, registry, tracker);
  }

  @Bean
  ObjectMapper watchboxComponentObjectMapper() {
    return new ObjectMapper();
  }

  @Bean
  DeadLetterIndexingErrorHandlerFactory deadLetterIndexingErrorHandlerFactory() {
    return new DeadLetterIndexingErrorHandlerFactory(mock(DeadLetterObservability.class));
  }

  @Bean
  ProcessorMetrics processorMetrics() {
    return ProcessorMetrics.noop();
  }

  /** Thread-safe capture: the publisher's scheduler thread writes while the test thread reads. */
  public static class RecordingWatchboxDetectionHeartbeatWriter
      implements WatchboxDetectionHeartbeatWriter {

    private final List<WatchboxDetectionHeartbeat> heartbeats = new CopyOnWriteArrayList<>();

    @Override
    public void writeHeartbeat(WatchboxDetectionHeartbeat heartbeat, Duration ttl) {
      heartbeats.add(heartbeat);
    }

    @Override
    public void markStopped(String instanceId) {}

    public List<WatchboxDetectionHeartbeat> heartbeats() {
      return List.copyOf(heartbeats);
    }
  }
}
