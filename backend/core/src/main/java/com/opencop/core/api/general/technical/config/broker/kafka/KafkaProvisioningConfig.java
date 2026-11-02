// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config.broker.kafka;

import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.technical.config.broker.DltIndexingProperties;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.LocalProviderProperties;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(name = "opencop.messaging-broker.provider", havingValue = "local")
@Configuration
@EnableConfigurationProperties(DltIndexingProperties.class)
public class KafkaProvisioningConfig {

  @Bean
  KafkaProvisioningService kafkaProvisioningService(
      LocalProviderProperties local,
      SecretStore secretStore,
      ObjectProvider<AdminClient> adminClientProvider,
      DltIndexingProperties dltIndexingProperties) {

    return new KafkaAdminClientProvisioner(
        adminClientProvider.getObject(),
        local.saslBootstrapUrl(),
        secretStore,
        local.defaultProducerByteRate(),
        local.topicPartitions(),
        dltIndexingProperties.getPayloadRetention());
  }
}
