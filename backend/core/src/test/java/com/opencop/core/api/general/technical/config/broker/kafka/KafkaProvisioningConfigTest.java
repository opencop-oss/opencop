// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config.broker.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.LocalProviderProperties;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class KafkaProvisioningConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(KafkaProvisioningConfig.class, Collaborators.class);

  @Test
  void vaultPath_usesSharedKafkaDataSourceTemplate() {
    assertThat(KafkaProvisioningService.VAULT_PATH).isEqualTo("opencop/shared/kafka-ds/%s");
  }

  @Test
  void localProvider_withoutLegacyByodEnabledProperty_createsProvisioningService() {
    contextRunner
        .withPropertyValues("opencop.messaging-broker.provider=local")
        .run(context -> assertThat(context).hasSingleBean(KafkaProvisioningService.class));
  }

  @Test
  void nonLocalProvider_doesNotCreateProvisioningService() {
    contextRunner
        .withPropertyValues("opencop.messaging-broker.provider=azure-event-hub")
        .run(context -> assertThat(context).doesNotHaveBean(KafkaProvisioningService.class));
  }

  @Configuration(proxyBeanMethods = false)
  static class Collaborators {

    @Bean
    LocalProviderProperties localProviderProperties() {
      return new LocalProviderProperties("localhost:9092", "localhost:9094", 1_048_576, 10);
    }

    @Bean
    SecretStore secretStore() {
      return mock(SecretStore.class);
    }

    @Bean
    AdminClient adminClient() {
      return mock(AdminClient.class);
    }
  }
}
