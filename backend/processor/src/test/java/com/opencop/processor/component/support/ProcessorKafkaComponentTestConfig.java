// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.support;

import com.opencop.core.sdk.technical.model.serializer.kafka.KafkaGeoJsonSerializer;
import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import com.opencop.processor.technical.spring.config.broker.KafkaListenerFactoryBuilder;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.geojson.Feature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.test.context.ActiveProfiles;

@TestConfiguration
@EnableKafka
@ActiveProfiles("it")
public class ProcessorKafkaComponentTestConfig {

  private final KafkaListenerFactoryBuilder kafkaListenerFactoryBuilder =
      new KafkaListenerFactoryBuilder();

  @Bean
  ProducerFactory<String, byte[]> producerFactory(EmbeddedKafkaBroker broker) {
    return new DefaultKafkaProducerFactory<>(
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            broker.getBrokersAsString(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            ByteArraySerializer.class));
  }

  @Bean
  KafkaTemplate<String, byte[]> kafkaTemplate(ProducerFactory<String, byte[]> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean("componentByteArrayDltKafkaTemplate")
  KafkaTemplate<String, byte[]> componentByteArrayDltKafkaTemplate(
      ProducerFactory<String, byte[]> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
  }

  @Bean
  ProducerFactory<String, Feature> featureProducerFactory(EmbeddedKafkaBroker broker) {
    return new DefaultKafkaProducerFactory<>(
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            broker.getBrokersAsString(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            KafkaGeoJsonSerializer.class));
  }

  @Bean
  KafkaTemplate<String, Feature> featureKafkaTemplate(
      ProducerFactory<String, Feature> featureProducerFactory) {
    return new KafkaTemplate<>(featureProducerFactory);
  }

  @Bean("componentFeatureDltKafkaTemplate")
  KafkaTemplate<String, Feature> componentFeatureDltKafkaTemplate(
      ProducerFactory<String, Feature> featureProducerFactory) {
    return new KafkaTemplate<>(featureProducerFactory);
  }

  @Bean("componentByteArrayBatchFactory")
  ConcurrentKafkaListenerContainerFactory<String, byte[]> componentByteArrayBatchFactory(
      EmbeddedKafkaBroker broker,
      @Value("${opencop.test.kafka.group-id:processor-component-it}") String groupId,
      @Value("${opencop.test.kafka.concurrency:1}") int concurrency,
      @Value("${opencop.test.kafka.max-poll-records:100}") int maxPollRecords,
      @Value("${opencop.test.kafka.max-retries:2}") int maxRetries,
      @Value("${opencop.test.kafka.max-wait-ms:500}") int maxWaitMs,
      @Value("${opencop.test.kafka.min-size-bytes:1}") int minSizeBytes,
      @Value("${opencop.test.kafka.max-size-bytes:1048576}") int maxSizeBytes,
      @Value("${opencop.test.kafka.poll-timeout-ms:250}") long pollTimeoutMs,
      KafkaTemplate<String, byte[]> componentByteArrayDltKafkaTemplate) {
    return kafkaListenerFactoryBuilder.byteArrayFactory(
        Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()),
        listenerProps(
            groupId,
            concurrency,
            maxPollRecords,
            maxRetries,
            maxWaitMs,
            minSizeBytes,
            maxSizeBytes),
        pollTimeoutMs,
        componentByteArrayDltKafkaTemplate);
  }

  @Bean("componentFeatureBatchFactory")
  ConcurrentKafkaListenerContainerFactory<String, Feature> componentFeatureBatchFactory(
      EmbeddedKafkaBroker broker,
      @Value("${opencop.test.kafka.group-id:processor-component-it}") String groupId,
      @Value("${opencop.test.kafka.concurrency:1}") int concurrency,
      @Value("${opencop.test.kafka.max-poll-records:100}") int maxPollRecords,
      @Value("${opencop.test.kafka.max-retries:2}") int maxRetries,
      @Value("${opencop.test.kafka.max-wait-ms:500}") int maxWaitMs,
      @Value("${opencop.test.kafka.min-size-bytes:1}") int minSizeBytes,
      @Value("${opencop.test.kafka.max-size-bytes:1048576}") int maxSizeBytes,
      @Value("${opencop.test.kafka.poll-timeout-ms:250}") long pollTimeoutMs,
      @Value("${opencop.test.kafka.value-type:feature}") String valueType,
      KafkaTemplate<String, Feature> componentFeatureDltKafkaTemplate) {
    return kafkaListenerFactoryBuilder.featureFactory(
        Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()),
        listenerProps(
            groupId,
            concurrency,
            maxPollRecords,
            maxRetries,
            maxWaitMs,
            minSizeBytes,
            maxSizeBytes,
            valueType),
        pollTimeoutMs,
        componentFeatureDltKafkaTemplate);
  }

  private KafkaConsumersProperties.ListenerProps listenerProps(
      String groupId,
      int concurrency,
      int maxPollRecords,
      int maxRetries,
      int maxWaitMs,
      int minSizeBytes,
      int maxSizeBytes) {
    return listenerProps(
        groupId,
        concurrency,
        maxPollRecords,
        maxRetries,
        maxWaitMs,
        minSizeBytes,
        maxSizeBytes,
        "raw");
  }

  private KafkaConsumersProperties.ListenerProps listenerProps(
      String groupId,
      int concurrency,
      int maxPollRecords,
      int maxRetries,
      int maxWaitMs,
      int minSizeBytes,
      int maxSizeBytes,
      String valueType) {
    var listenerProps = new KafkaConsumersProperties.ListenerProps();
    listenerProps.setGroupId(groupId);
    listenerProps.setConcurrency(concurrency);
    listenerProps.setMaxPollRecords(maxPollRecords);
    listenerProps.setMaxRetries(maxRetries);
    listenerProps.setMaxWaitMs(maxWaitMs);
    listenerProps.setMinSizeBytes(minSizeBytes);
    listenerProps.setMaxSizeBytes(maxSizeBytes);
    listenerProps.setValueType(valueType);
    return listenerProps;
  }
}
