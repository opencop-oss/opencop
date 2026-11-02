// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.cachehydration;

import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.processor.business.usecase.event.GeoCacheHydrationHandler;
import com.opencop.processor.business.usecase.event.JobHandlerRegistry;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.component.support.ProcessorKafkaComponentTestConfig;
import com.opencop.processor.ports.business.usecase.event.GeoCacheHydrator;
import com.opencop.processor.ports.business.usecase.event.JobHandler;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import com.opencop.processor.ports.technical.gateway.lock.geo.GeoCacheHydrationLock;
import com.opencop.processor.technical.gateway.db.GeoCacheHydrationRepository;
import com.opencop.processor.technical.gateway.lock.geo.RedisGeoCacheHydrationLock;
import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import com.opencop.processor.technical.spring.config.broker.KafkaListenerFactoryBuilder;
import com.opencop.processor.technical.spring.consumer.GeoCacheHydrationConsumer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

@TestConfiguration
@Import({
  GeoCacheHydrationConsumer.class,
  GeoCacheHydrationRepository.class,
  ProcessorKafkaComponentTestConfig.class
})
public class GeoCacheHydrationConsumerComponentTestContext {

  private final KafkaListenerFactoryBuilder kafkaListenerFactoryBuilder =
      new KafkaListenerFactoryBuilder();

  @Bean
  EventGeoCacheKeyLayout eventGeoCacheKeyLayout() {
    return EventGeoCacheKeyLayout.STANDALONE;
  }

  @Bean
  GeoCacheHydrator geoCacheHydrator(
      GeoCacheHydrationRepository repository,
      GeoCacheGateway geoCacheGateway,
      GeoCacheHydrationLock distributedLockGateway) {
    return new GeoCacheHydrationHandler(repository, geoCacheGateway, distributedLockGateway);
  }

  @Bean
  JobHandlerRegistry jobHandlerRegistry(List<JobHandler<? extends JobSubmission>> handlers) {
    return new JobHandlerRegistry(handlers);
  }

  @Bean
  JobStatusNotificationPublisher jobStatusNotificationPublisher(ObjectMapper objectMapper) {
    @SuppressWarnings("unchecked")
    MessageBrokerProducer<byte[]> notificationProducer = Mockito.mock(MessageBrokerProducer.class);
    return new JobStatusNotificationPublisher(notificationProducer, objectMapper);
  }

  @Bean
  RecordingGeoCacheGateway recordingGeoCacheGateway() {
    return new RecordingGeoCacheGateway();
  }

  @Bean
  GeoCacheGateway geoCacheGateway(RecordingGeoCacheGateway recordingGeoCacheGateway) {
    return recordingGeoCacheGateway;
  }

  @Bean
  DataSource dataSource(
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String username,
      @Value("${spring.datasource.password}") String password,
      @Value("${opencop.db.driver}") String driverClassName) {
    var dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName(driverClassName);
    dataSource.setUrl(url);
    dataSource.setUsername(username);
    dataSource.setPassword(password);
    return dataSource;
  }

  @Bean
  NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
    return new NamedParameterJdbcTemplate(dataSource);
  }

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  StringRedisTemplate stringRedisTemplate() {
    StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
    var locks = new ConcurrentHashMap<String, String>();

    Mockito.when(template.opsForValue()).thenReturn(valueOperations);
    Mockito.when(
            valueOperations.setIfAbsent(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
        .thenAnswer(
            invocation ->
                locks.putIfAbsent(
                        invocation.getArgument(0, String.class),
                        invocation.getArgument(1, String.class))
                    == null);
    Mockito.when(template.execute(Mockito.any(), Mockito.anyList(), Mockito.anyString()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              List<String> keys = invocation.getArgument(1, List.class);
              String key = keys.getFirst();
              String owner = invocation.getArgument(2, String.class);
              return locks.remove(key, owner) ? 1L : 0L;
            });
    return template;
  }

  @Bean
  GeoCacheHydrationLock distributedLockGateway(
      StringRedisTemplate stringRedisTemplate, EventGeoCacheKeyLayout keyLayout) {
    return new RedisGeoCacheHydrationLock(stringRedisTemplate, keyLayout);
  }

  @Bean
  PlatformTransactionManager transactionManager(DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }

  @Bean(name = "jobsFactory")
  ConcurrentKafkaListenerContainerFactory<String, byte[]> jobsFactory(
      org.springframework.kafka.test.EmbeddedKafkaBroker broker,
      @Value("${opencop.test.kafka.group-id:processor-cache-hydration-it}") String groupId,
      @Value("${opencop.test.kafka.concurrency:1}") int concurrency,
      @Value("${opencop.test.kafka.max-poll-records:10}") int maxPollRecords,
      @Value("${opencop.test.kafka.max-retries:2}") int maxRetries,
      @Value("${opencop.test.kafka.max-wait-ms:500}") int maxWaitMs,
      @Value("${opencop.test.kafka.min-size-bytes:1}") int minSizeBytes,
      @Value("${opencop.test.kafka.max-size-bytes:1048576}") int maxSizeBytes,
      @Value("${opencop.test.kafka.poll-timeout-ms:250}") long pollTimeoutMs,
      @Qualifier("componentByteArrayDltKafkaTemplate")
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
    listenerProps.setBatch(false);
    listenerProps.setMaxPollRecords(maxPollRecords);
    listenerProps.setMaxRetries(maxRetries);
    listenerProps.setMaxWaitMs(maxWaitMs);
    listenerProps.setMinSizeBytes(minSizeBytes);
    listenerProps.setMaxSizeBytes(maxSizeBytes);
    listenerProps.setValueType("raw");
    return listenerProps;
  }
}
