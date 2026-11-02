// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka.config;

import com.opencop.core.sdk.technical.gateway.broker.kafka.MessageBrokerProvider;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.LocalProviderProperties;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "opencop.messaging-broker.provider", havingValue = "local")
@EnableConfigurationProperties(LocalProviderProperties.class)
class LocalProviderConfig {

  LocalProviderConfig(LocalProviderProperties provider) {
    if (!MessageBrokerProvider.LOCAL.name().equals(provider.name())) {
      throw new IllegalStateException(
          "Kafka configuration loaded the local configuration when but the provider is set to "
              + provider.name());
    }
    if (provider.bootstrapUrl() == null || provider.bootstrapUrl().isBlank()) {
      throw new IllegalStateException(
          "Kafka configuration loaded the local configuration when but the bootstrap property is invalid");
    }
  }

  @Bean
  public BaseKafkaConfig kafkaConfig(LocalProviderProperties locale) {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, locale.bootstrapUrl());
    return new BaseKafkaConfig(props);
  }

  @Bean
  AdminClient byodAdminClient(LocalProviderProperties localConfig) {
    return AdminClient.create(
        Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, localConfig.bootstrapUrl()));
  }
}
