// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.validation;

import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.processor.business.usecase.event.ValidateGeoFeature;
import com.opencop.processor.component.consumer.realtime.RecordingFeatureProducer;
import com.opencop.processor.ports.business.usecase.event.ValidateEvent;
import com.opencop.processor.technical.spring.consumer.GeoValidationConsumer;
import org.geojson.Feature;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

@TestConfiguration
@Import(GeoValidationConsumer.class)
public class GeoValidationConsumerComponentTestContext {

  @Bean
  @Primary
  RecordingFeatureProducer validatedProducer() {
    return new RecordingFeatureProducer();
  }

  @Bean
  ValidateEvent<Feature> validateEvent(
      @Qualifier("validatedProducer") MessageBrokerProducer<Feature> validatedProducer) {
    return new ValidateGeoFeature(validatedProducer, true);
  }

  @Bean(name = "validationFactory")
  ConcurrentKafkaListenerContainerFactory<String, Feature> validationFactory(
      @Qualifier("componentFeatureBatchFactory")
          ConcurrentKafkaListenerContainerFactory<String, Feature> factory) {
    return factory;
  }
}
