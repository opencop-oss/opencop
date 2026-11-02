// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.byod;

import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@EnableConfigurationProperties({ByodConsumerProperties.class, ByodRuntimeProperties.class})
public class ByodKafkaConfig {

  private final BaseKafkaConfig baseKafkaConfig;

  public ByodKafkaConfig(BaseKafkaConfig baseKafkaConfig) {
    this.baseKafkaConfig = baseKafkaConfig;
  }

  @Bean("byodDltKafkaTemplate")
  KafkaTemplate<String, byte[]> byodDltKafkaTemplate() {
    var props =
        new HashMap<>(
            Map.<String, Object>of(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
    props.putAll(baseKafkaConfig.props());
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }
}
