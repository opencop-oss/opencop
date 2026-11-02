// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LocalProviderConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(LocalProviderConfig.class);

  @Test
  void localProvider_withoutLegacyByodEnabledProperty_createsAdminClient() {
    contextRunner
        .withPropertyValues(
            "opencop.messaging-broker.provider=local",
            "opencop.messaging-broker.properties.bootstrap-url=localhost:9092",
            "opencop.messaging-broker.properties.sasl-bootstrap-url=localhost:9094")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AdminClient.class);
              assertThat(context).hasSingleBean(BaseKafkaConfig.class);
            });
  }

  @Test
  void nonLocalProvider_doesNotCreateLocalBeans() {
    contextRunner
        .withPropertyValues("opencop.messaging-broker.provider=azure-event-hub")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(AdminClient.class);
              assertThat(context).doesNotHaveBean(BaseKafkaConfig.class);
            });
  }
}
