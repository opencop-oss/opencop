// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.persistence;

import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import com.opencop.processor.component.support.ProcessorKafkaComponentTestConfig;
import com.opencop.processor.technical.spring.config.PostGisConfig;
import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import com.opencop.processor.technical.spring.config.broker.KafkaListenerFactoryBuilder;
import com.opencop.processor.technical.spring.consumer.PersistenceConsumer;
import com.opencop.processor.technical.spring.consumer.RetryingPersistenceService;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.geojson.Feature;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

@TestConfiguration
@Import({
  PersistenceConsumer.class,
  RetryingPersistenceService.class,
  PostGisConfig.class,
  ProcessorKafkaComponentTestConfig.class
})
public class PersistenceConsumerComponentTestContext {

  private final KafkaListenerFactoryBuilder kafkaListenerFactoryBuilder =
      new KafkaListenerFactoryBuilder();

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
    return new NamedParameterJdbcTemplate(dataSource);
  }

  @Bean
  MediaUrlRepository mediaUrlRepository() {
    return Mockito.mock(MediaUrlRepository.class);
  }

  @Bean
  EventAttachmentRepository eventAttachmentRepository() {
    return Mockito.mock(EventAttachmentRepository.class);
  }

  @Bean
  PlatformTransactionManager transactionManager(DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }

  @Bean
  ProducerFactory<String, Feature> persistenceFeatureDltProducerFactory(
      EmbeddedKafkaBroker broker) {
    return new DefaultKafkaProducerFactory<>(
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            broker.getBrokersAsString(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            JacksonJsonSerializer.class));
  }

  @Bean(name = {"persistenceFeatureDltKafkaTemplate", "featureDltTemplate"})
  KafkaTemplate<String, Feature> persistenceFeatureDltKafkaTemplate(
      ProducerFactory<String, Feature> persistenceFeatureDltProducerFactory) {
    return new KafkaTemplate<>(persistenceFeatureDltProducerFactory);
  }

  @Bean(name = "persistFactory")
  ConcurrentKafkaListenerContainerFactory<String, Feature> persistFactory(
      EmbeddedKafkaBroker broker,
      @Value("${opencop.test.kafka.group-id:processor-component-it}") String groupId,
      @Value("${opencop.test.kafka.concurrency:1}") int concurrency,
      @Value("${opencop.test.kafka.max-poll-records:100}") int maxPollRecords,
      @Value("${opencop.test.kafka.max-retries:2}") int maxRetries,
      @Value("${opencop.test.kafka.max-wait-ms:500}") int maxWaitMs,
      @Value("${opencop.test.kafka.min-size-bytes:1}") int minSizeBytes,
      @Value("${opencop.test.kafka.max-size-bytes:1048576}") int maxSizeBytes,
      @Value("${opencop.test.kafka.poll-timeout-ms:250}") long pollTimeoutMs,
      @Qualifier("featureDltTemplate")
          KafkaTemplate<String, Feature> persistenceFeatureDltKafkaTemplate) {
    return kafkaListenerFactoryBuilder.featureFactory(
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
        persistenceFeatureDltKafkaTemplate);
  }

  private KafkaConsumersProperties.ListenerProps listenerProps(
      String groupId,
      int concurrency,
      int maxPollRecords,
      int maxRetries,
      int maxWaitMs,
      int minSizeBytes,
      int maxSizeBytes) {
    var listenerProps = new KafkaConsumersProperties.ListenerProps();
    listenerProps.setGroupId(groupId);
    listenerProps.setConcurrency(concurrency);
    listenerProps.setMaxPollRecords(maxPollRecords);
    listenerProps.setMaxRetries(maxRetries);
    listenerProps.setMaxWaitMs(maxWaitMs);
    listenerProps.setMinSizeBytes(minSizeBytes);
    listenerProps.setMaxSizeBytes(maxSizeBytes);
    listenerProps.setValueType("feature");
    return listenerProps;
  }
}
