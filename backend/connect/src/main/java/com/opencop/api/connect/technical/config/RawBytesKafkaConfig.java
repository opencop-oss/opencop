// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.config;

import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Kafka configuration for sending raw byte[] payloads.
 *
 * <p>This configuration creates a KafkaTemplate for sending raw byte[] to Kafka. Metadata goes in
 * Kafka headers.
 *
 * <p>Benefits: - Zero serialization overhead (no JSON encoding) - No base64 encoding of byte[]
 * payloads - Faster producer/consumer (direct byte access) - Aligns with processor's decoder
 * detection pattern
 *
 * <p>Usage: - Autowire as: KafkaTemplate<String, byte[]> rawBytesKafkaTemplate - Or
 * use @Qualifier("rawBytesKafkaTemplate") - Send with headers: opencop-source-id, opencop-event-id,
 * opencop-type
 */
@Configuration
public class RawBytesKafkaConfig {

  @Bean(name = "rawBytesKafkaTemplate")
  public KafkaTemplate<String, byte[]> rawBytesKafkaTemplate(BaseKafkaConfig config) {
    return new KafkaTemplate<>(rawBytesProducerFactory(config));
  }

  private ProducerFactory<String, byte[]> rawBytesProducerFactory(BaseKafkaConfig config) {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

    // Keep same producer settings as default KafkaTemplate
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");
    configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
    configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    configProps.putAll(config.props());

    return new DefaultKafkaProducerFactory<>(configProps);
  }
}
