// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RealtimeMappingOutcomePropertiesTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Test
  void defaultsAreBoundedAndEnabled() {
    runner.run(
        context -> {
          var properties = context.getBean(RealtimeMappingOutcomeProperties.class);
          assertThat(properties.isEnabled()).isTrue();
          assertThat(properties.getMaxProblemResourceTags()).isEqualTo(100);
          assertThat(properties.getWarningSummaryInterval()).isEqualTo(Duration.ofMinutes(5));
          assertThat(properties.getWarningStateMax()).isEqualTo(1_000);
          assertThat(properties.getWarningGlobalMaxPerInterval()).isEqualTo(100);
        });
  }

  @Test
  void oneAndOneHundredBindForHardCappedValues() {
    runner
        .withPropertyValues(
            "opencop.realtime-mapping-outcomes.max-problem-resource-tags=1",
            "opencop.realtime-mapping-outcomes.warning-global-max-per-interval=100")
        .run(
            context -> {
              var properties = context.getBean(RealtimeMappingOutcomeProperties.class);
              assertThat(properties.getMaxProblemResourceTags()).isEqualTo(1);
              assertThat(properties.getWarningGlobalMaxPerInterval()).isEqualTo(100);
            });
  }

  @Test
  void zeroAndOneHundredOneFailBinding() {
    assertBindingFails("max-problem-resource-tags", "0");
    assertBindingFails("max-problem-resource-tags", "101");
    assertBindingFails("warning-global-max-per-interval", "0");
    assertBindingFails("warning-global-max-per-interval", "101");
    assertBindingFails("warning-summary-interval", "0s");
  }

  @Test
  void applicationYamlExposesEnvironmentBackedValues() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues(
            "MESSAGING_BROKER_PROVIDER=local",
            "REALTIME_MAPPING_OUTCOMES_ENABLED=false",
            "REALTIME_MAPPING_OUTCOME_MAX_RESOURCE_TAGS=7")
        .withUserConfiguration(TestConfig.class)
        .run(
            context -> {
              var properties = context.getBean(RealtimeMappingOutcomeProperties.class);
              assertThat(properties.isEnabled()).isFalse();
              assertThat(properties.getMaxProblemResourceTags()).isEqualTo(7);
            });
  }

  private void assertBindingFails(String property, String value) {
    runner
        .withPropertyValues("opencop.realtime-mapping-outcomes." + property + "=" + value)
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(RealtimeMappingOutcomeProperties.class))
                    .hasStackTraceContaining("Could not bind properties"));
  }

  @Configuration
  @EnableConfigurationProperties(RealtimeMappingOutcomeProperties.class)
  static class TestConfig {}
}
