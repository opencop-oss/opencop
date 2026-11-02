// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config.broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class DltIndexingPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(
              context -> {
                try {
                  var resource = new ClassPathResource("application.yml");
                  var loader = new YamlPropertySourceLoader();
                  for (var propertySource : loader.load("core-application", resource)) {
                    context.getEnvironment().getPropertySources().addLast(propertySource);
                  }
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              })
          .withUserConfiguration(TestDltIndexingPropertiesConfig.class);

  @Test
  void payloadRetentionDefaultsToSevenDays() {
    var properties = new DltIndexingProperties();

    assertThat(properties.getPayloadRetention()).isEqualTo(Duration.ofDays(7));
  }

  @Test
  void payloadRetentionBindsFromDltPayloadRetentionEnvMapping() {
    contextRunner
        .withPropertyValues("DLT_PAYLOAD_RETENTION=30d", "MESSAGING_BROKER_PROVIDER=local")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(DltIndexingProperties.class).getPayloadRetention())
                  .isEqualTo(Duration.ofDays(30));
            });
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableConfigurationProperties(DltIndexingProperties.class)
  static class TestDltIndexingPropertiesConfig {}
}
