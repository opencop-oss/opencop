// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.raw;

import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.EventDecoderRegistry;
import com.opencop.processor.business.usecase.event.MapRawToGeoHandler;
import com.opencop.processor.component.consumer.realtime.RecordingFeatureProducer;
import com.opencop.processor.ports.business.usecase.event.RawEventHandler;
import com.opencop.processor.technical.observability.mapping.MappingObservationFailureReporter;
import com.opencop.processor.technical.observability.mapping.RealtimeMappingOutcomeObservability;
import com.opencop.processor.technical.spring.config.RealtimeMappingOutcomeConfig;
import com.opencop.processor.technical.spring.consumer.RawIngestConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.geojson.Feature;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import tools.jackson.databind.ObjectMapper;

@TestConfiguration
@Import({RawIngestConsumer.class, RealtimeMappingOutcomeConfig.class})
public class RawIngestConsumerComponentTestContext {

  @Bean
  MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  EventDecoderRegistry eventDecoderRegistry() {
    return mock(EventDecoderRegistry.class);
  }

  @Bean
  @Primary
  RecordingFeatureProducer mappedProducer() {
    return new RecordingFeatureProducer();
  }

  @Bean
  MessageBrokerProducer<byte[]> rawDltProducer() {
    return mock(MessageBrokerProducer.class);
  }

  @Bean
  RawEventHandler<byte[]> rawEventHandler(
      EventDecoderRegistry eventDecoderRegistry,
      @Qualifier("mappedProducer") MessageBrokerProducer<Feature> mappedProducer,
      MessageBrokerProducer<byte[]> rawDltProducer,
      RealtimeMappingOutcomeObservability mappingObservability,
      MappingObservationFailureReporter observationFailureReporter) {
    return new MapRawToGeoHandler(
        eventDecoderRegistry,
        mappedProducer,
        new ObjectMapper(),
        mock(BlobClient.class),
        rawDltProducer,
        mappingObservability,
        observationFailureReporter);
  }

  @Bean(name = "rawFactory")
  ConcurrentKafkaListenerContainerFactory<String, byte[]> rawFactory(
      @Qualifier("componentByteArrayBatchFactory")
          ConcurrentKafkaListenerContainerFactory<String, byte[]> factory) {
    return factory;
  }
}
