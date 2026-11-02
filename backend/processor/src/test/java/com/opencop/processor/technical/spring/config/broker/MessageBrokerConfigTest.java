// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import com.opencop.core.sdk.technical.gateway.broker.kafka.AcknowledgingKafkaProducer;
import com.opencop.core.sdk.technical.gateway.broker.kafka.KafkaProducer;
import com.opencop.core.sdk.technical.gateway.broker.kafka.ProducerFactoryResetter;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.dlt.DeadLetterIndexingErrorHandlerFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.InvalidPidMappingException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class MessageBrokerConfigTest {

  @Test
  void mappedAndValidatedProducersUseSharedAcknowledgedFeatureTemplateAndTimeout() {
    MessageBrokerConfig config = config(Duration.ofMillis(250));
    @SuppressWarnings("unchecked")
    DefaultKafkaProducerFactory<String, Feature> featureFactory =
        spy(new DefaultKafkaProducerFactory<String, Feature>(producerProps()));
    KafkaTemplate<String, Feature> featureTemplate =
        config.acknowledgedFeatureKafkaTemplate(featureFactory);
    ProducerFactoryResetter featureResetter =
        config.acknowledgedFeatureProducerFactoryResetter(featureFactory);

    MessageBrokerProducer<Feature> mapped =
        config.mappedProducer("mapped-topic", featureTemplate, featureResetter);
    MessageBrokerProducer<Feature> validated =
        config.validatedProducer("validated-topic", featureTemplate, featureResetter);

    assertThat(mapped).isInstanceOf(AcknowledgingKafkaProducer.class);
    assertThat(validated).isInstanceOf(AcknowledgingKafkaProducer.class);
    assertThat(field(mapped, "kafkaTemplate")).isSameAs(featureTemplate);
    assertThat(field(validated, "kafkaTemplate")).isSameAs(featureTemplate);
    assertThat(field(mapped, "producerFactoryResetter")).isSameAs(featureResetter);
    assertThat(field(validated, "producerFactoryResetter")).isSameAs(featureResetter);
    assertThat(featureTemplate.getProducerFactory()).isSameAs(featureFactory);
    assertThat(field(mapped, "acknowledgementTimeout")).isEqualTo(Duration.ofMillis(250));
    assertThat(field(validated, "acknowledgementTimeout")).isEqualTo(Duration.ofMillis(250));
  }

  @Test
  void mappedProducerResetHandlePointsAtSharedFeatureProducerFactory() {
    MessageBrokerConfig config = config(Duration.ofSeconds(1));
    @SuppressWarnings("unchecked")
    DefaultKafkaProducerFactory<String, Feature> featureFactory =
        spy(new DefaultKafkaProducerFactory<String, Feature>(producerProps()));
    KafkaTemplate<String, Feature> featureTemplate = kafkaTemplate();
    when(featureTemplate.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.failedFuture(new InvalidPidMappingException("invalid producer id")))
        .thenReturn(CompletableFuture.completedFuture(null));
    ProducerFactoryResetter featureResetter =
        config.acknowledgedFeatureProducerFactoryResetter(featureFactory);
    MessageBrokerProducer<Feature> mapped =
        config.mappedProducer("mapped-topic", featureTemplate, featureResetter);

    mapped.send(new Feature());

    verify(featureFactory).reset();
  }

  @Test
  void rawDltProducerUsesAcknowledgedByteArrayTemplateFactoryAndRawDltTopic() {
    MessageBrokerConfig config = config(Duration.ofMillis(300));
    @SuppressWarnings("unchecked")
    DefaultKafkaProducerFactory<String, byte[]> byteArrayFactory =
        spy(new DefaultKafkaProducerFactory<String, byte[]>(producerProps()));
    KafkaTemplate<String, byte[]> byteArrayTemplate =
        config.acknowledgedByteArrayKafkaTemplate(byteArrayFactory);
    ProducerFactoryResetter byteArrayResetter =
        config.acknowledgedByteArrayProducerFactoryResetter(byteArrayFactory);

    MessageBrokerProducer<byte[]> rawDlt =
        config.rawDltProducer("raw-topic", byteArrayTemplate, byteArrayResetter);

    assertThat(rawDlt).isInstanceOf(AcknowledgingKafkaProducer.class);
    assertThat(field(rawDlt, "topic")).isEqualTo("raw-topic.dlt");
    assertThat(field(rawDlt, "kafkaTemplate")).isSameAs(byteArrayTemplate);
    assertThat(field(rawDlt, "producerFactoryResetter")).isSameAs(byteArrayResetter);
    assertThat(byteArrayTemplate.getProducerFactory()).isSameAs(byteArrayFactory);
    assertThat(field(rawDlt, "acknowledgementTimeout")).isEqualTo(Duration.ofMillis(300));
  }

  @Test
  void rawDltProducerResetHandlePointsAtAcknowledgedByteArrayProducerFactory() {
    MessageBrokerConfig config = config(Duration.ofSeconds(1));
    @SuppressWarnings("unchecked")
    DefaultKafkaProducerFactory<String, byte[]> byteArrayFactory =
        spy(new DefaultKafkaProducerFactory<String, byte[]>(producerProps()));
    KafkaTemplate<String, byte[]> byteArrayTemplate = kafkaTemplate();
    when(byteArrayTemplate.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.failedFuture(new InvalidPidMappingException("invalid producer id")))
        .thenReturn(CompletableFuture.completedFuture(null));
    ProducerFactoryResetter byteArrayResetter =
        config.acknowledgedByteArrayProducerFactoryResetter(byteArrayFactory);
    MessageBrokerProducer<byte[]> rawDlt =
        config.rawDltProducer("raw-topic", byteArrayTemplate, byteArrayResetter);

    rawDlt.send(new byte[] {1});

    verify(byteArrayFactory).reset();
  }

  @Test
  void notificationProducerKeepsFireAndForgetKafkaProducer() {
    MessageBrokerConfig config = config(Duration.ofSeconds(1));

    MessageBrokerProducer<byte[]> producer =
        config.userNotificationProducer("notification-topic", kafkaTemplate());

    assertThat(producer).isInstanceOf(KafkaProducer.class);
  }

  @Test
  void acknowledgedProducerFactoriesPinSafetySettingsAndTimeouts() {
    MessageBrokerConfig config =
        config(
            Duration.ofMillis(1234),
            Map.of(
                ProducerConfig.ACKS_CONFIG,
                "1",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                false,
                ProducerConfig.RETRIES_CONFIG,
                0,
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
                10,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                "60000",
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                120000));

    DefaultKafkaProducerFactory<String, Feature> featureFactory =
        config.acknowledgedFeatureProducerFactory();
    DefaultKafkaProducerFactory<String, byte[]> byteArrayFactory =
        config.acknowledgedByteArrayProducerFactory();

    assertThat(featureFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.ACKS_CONFIG, "all");
    assertThat(byteArrayFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.ACKS_CONFIG, "all");
    assertThat(featureFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    assertThat(byteArrayFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    assertThat(featureFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.RETRIES_CONFIG, 3);
    assertThat(byteArrayFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.RETRIES_CONFIG, 3);
    assertThat(featureFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    assertThat(byteArrayFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    assertThat(featureFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1234L);
    assertThat(byteArrayFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1234L);
    assertThat(featureFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1234);
    assertThat(byteArrayFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1234);
    assertThat(featureFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1234);
    assertThat(byteArrayFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1234);
    assertThat(featureFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.LINGER_MS_CONFIG, 0L);
    assertThat(byteArrayFactory.getConfigurationProperties())
        .containsEntry(ProducerConfig.LINGER_MS_CONFIG, 0L);
  }

  private static MessageBrokerConfig config(Duration acknowledgementTimeout) {
    return config(acknowledgementTimeout, Map.of());
  }

  private static MessageBrokerConfig config(
      Duration acknowledgementTimeout, Map<String, Object> baseKafkaProps) {
    var properties = new KafkaConsumersProperties();
    properties.setProducerAckTimeout(acknowledgementTimeout);
    return new MessageBrokerConfig(
        properties,
        new BaseKafkaConfig(baseKafkaProps),
        mock(ObjectProvider.class),
        mock(ObjectProvider.class),
        mock(DeadLetterIndexingErrorHandlerFactory.class),
        new ObjectMapper(),
        ProcessorMetrics.noop(),
        new WatchboxRetainedRetryTracker(
            "opencop.geo.validated", Clock.systemUTC(), ProcessorMetrics.noop()));
  }

  @SuppressWarnings("unchecked")
  private static <T> KafkaTemplate<String, T> kafkaTemplate() {
    return mock(KafkaTemplate.class);
  }

  private static Map<String, Object> producerProps() {
    return Map.of(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        ByteArraySerializer.class);
  }

  private static Object field(Object target, String name) {
    return ReflectionTestUtils.getField(target, name);
  }
}
