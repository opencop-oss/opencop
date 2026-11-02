// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config.broker.azure;

import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.eventhubs.EventHubsManager;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.technical.config.broker.DltIndexingProperties;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.AzureProviderProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@ConditionalOnProperty(name = "opencop.messaging-broker.provider", havingValue = "azure-event-hub")
@Configuration
@EnableConfigurationProperties(DltIndexingProperties.class)
public class EventHubProvisioningConfig {

  @Bean
  KafkaProvisioningService kafkaProvisioningService(
      AzureProviderProperties azure,
      SecretStore secretStore,
      DltIndexingProperties dltIndexingProperties) {
    var credential =
        new ClientSecretCredentialBuilder()
            .tenantId(azure.tenantId())
            .clientId(azure.clientId())
            .clientSecret(azure.clientSecret())
            .build();
    var azureEnv = azure.govCloud() ? AzureEnvironment.AZURE_US_GOVERNMENT : AzureEnvironment.AZURE;
    var profile = new AzureProfile(azure.tenantId(), azure.subscriptionId(), azureEnv);
    var manager = EventHubsManager.authenticate(credential, profile);
    return new AzureEventHubsProvisioner(
        manager,
        azure.resourceGroup(),
        azure.namespace(),
        azure.bootstrapUrl(),
        secretStore,
        azure.topicPartitions(),
        dltIndexingProperties.getPayloadRetention());
  }
}
