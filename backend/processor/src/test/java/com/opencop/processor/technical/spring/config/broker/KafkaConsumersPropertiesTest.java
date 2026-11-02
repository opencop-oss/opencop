// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class KafkaConsumersPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);
  private final ApplicationContextRunner applicationYamlContextRunner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withPropertyValues("MESSAGING_BROKER_PROVIDER=local")
          .withUserConfiguration(TestConfig.class);

  @Test
  void dltPayloadRetentionDefaultsToSevenDays() {
    var properties = new KafkaConsumersProperties.DeadLetterIndexingProps();

    assertThat(properties.getPayloadRetention()).isEqualTo(Duration.ofDays(7));
  }

  @Test
  void dltMaxPollRecordsDefaultsToFiveThousand() {
    var properties = new KafkaConsumersProperties.DeadLetterIndexingProps();

    assertThat(properties.getMaxPollRecords()).isEqualTo(5000);
  }

  @Test
  void dltAutoOffsetResetDefaultsToLatestForForwardOnlyIndexing() {
    var properties = new KafkaConsumersProperties.DeadLetterIndexingProps();

    assertThat(properties.getAutoOffsetReset()).isEqualTo("latest");
  }

  @Test
  void producerAckTimeoutDefaultsToTenSeconds() {
    var properties = new KafkaConsumersProperties();

    assertThat(properties.getProducerAckTimeout()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  void producerAckTimeoutBindsDuration() {
    contextRunner
        .withPropertyValues("opencop.messaging-broker.producer-ack-timeout=3s")
        .run(
            context ->
                assertThat(context.getBean(KafkaConsumersProperties.class).getProducerAckTimeout())
                    .isEqualTo(Duration.ofSeconds(3)));
  }

  @Test
  void producerAckTimeoutRejectsZeroDuration() {
    contextRunner
        .withPropertyValues("opencop.messaging-broker.producer-ack-timeout=0s")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(KafkaConsumersProperties.class))
                    .hasStackTraceContaining("producerAckTimeout"));
  }

  @Test
  void producerAckTimeoutRejectsNegativeDuration() {
    contextRunner
        .withPropertyValues("opencop.messaging-broker.producer-ack-timeout=-1s")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(KafkaConsumersProperties.class))
                    .hasStackTraceContaining("producerAckTimeout"));
  }

  @Test
  void applicationYamlBindsProducerAckTimeoutEnvironmentOverride() {
    applicationYamlContextRunner
        .withPropertyValues("KAFKA_PRODUCER_ACK_TIMEOUT=4s")
        .run(
            context -> {
              assertThat(
                      context
                          .getEnvironment()
                          .getProperty("opencop.messaging-broker.producer-ack-timeout"))
                  .isEqualTo("4s");
              assertThat(context.getBean(KafkaConsumersProperties.class).getProducerAckTimeout())
                  .isEqualTo(Duration.ofSeconds(4));
            });
  }

  @Configuration
  @EnableConfigurationProperties(KafkaConsumersProperties.class)
  static class TestConfig {}
}
