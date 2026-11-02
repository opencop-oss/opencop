// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.byod;

import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.EventDecoderRegistry;
import com.opencop.processor.component.consumer.realtime.RecordingFeatureProducer;
import com.opencop.processor.technical.spring.config.PipelinePublishProperties;
import com.opencop.processor.technical.spring.config.RealtimeMappingOutcomeConfig;
import com.opencop.processor.technical.spring.config.byod.ByodKafkaConfig;
import com.opencop.processor.technical.spring.config.byod.ByodListenerContainerFactory;
import com.opencop.processor.technical.spring.consumer.ByodMessageHandler;
import com.opencop.processor.technical.spring.consumer.ByodRuntimeObservationCoalescer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.geojson.Feature;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

@TestConfiguration
@Import({
  ByodKafkaConfig.class,
  ByodListenerContainerFactory.class,
  ByodMessageHandler.class,
  RealtimeMappingOutcomeConfig.class
})
public class ByodMessageHandlerComponentTestContext {

  @Bean
  MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  PipelinePublishProperties pipelinePublishProperties() {
    return new PipelinePublishProperties();
  }

  @Bean
  BaseKafkaConfig baseKafkaConfig(EmbeddedKafkaBroker broker) {
    return new BaseKafkaConfig(
        Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()));
  }

  @Bean
  EventDecoderRegistry eventDecoderRegistry() {
    return mock(EventDecoderRegistry.class);
  }

  @Bean
  ByodRuntimeObservationCoalescer byodRuntimeObservationCoalescer() {
    return mock(ByodRuntimeObservationCoalescer.class);
  }

  @Bean
  @Primary
  RecordingFeatureProducer recordingFeatureProducer() {
    return new RecordingFeatureProducer();
  }

  @Bean("mappedProducer")
  MessageBrokerProducer<Feature> mappedProducer(
      @Qualifier("recordingFeatureProducer") RecordingFeatureProducer mappedProducer) {
    return mappedProducer;
  }
}
