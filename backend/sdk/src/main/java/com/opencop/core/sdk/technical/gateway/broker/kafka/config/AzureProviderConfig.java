// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka.config;

import com.opencop.core.sdk.technical.gateway.broker.kafka.MessageBrokerProvider;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.AzureProviderProperties;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "opencop.messaging-broker.provider", havingValue = "azure-event-hub")
@EnableConfigurationProperties(AzureProviderProperties.class)
class AzureProviderConfig {

  AzureProviderConfig(AzureProviderProperties provider) {
    if (!MessageBrokerProvider.AZURE_EVENT_HUB.name().equals(provider.name())) {
      throw new IllegalStateException(
          "Kafka configuration loaded the azure event hub configuration when but the provider is set to "
              + provider.name());
    }
    if (provider.connectionString() == null || provider.connectionString().isBlank()) {
      throw new IllegalStateException(
          "Kafka configuration loaded the event hub configuration when but the connection string property is invalid");
    }
  }

  @Bean
  public BaseKafkaConfig kafkaConfig(AzureProviderProperties azure) {
    Map<String, Object> props = new HashMap<>(azure.saslProperties());
    return new BaseKafkaConfig(props);
  }
}
