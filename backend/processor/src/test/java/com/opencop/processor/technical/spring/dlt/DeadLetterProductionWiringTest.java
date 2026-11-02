// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import com.opencop.processor.technical.spring.config.byod.ByodDeadLetterListenerContainerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class DeadLetterProductionWiringTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestDependencies.class, DltScanRoots.class);

  @Test
  void productionScanWiresDltIndexerStoreAndConsumers() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(DeadLetterPayloadStore.class);
          assertThat(context).hasSingleBean(DeadLetterFailureIndexer.class);
          assertThat(context).hasSingleBean(FixedDeadLetterConsumer.class);
          assertThat(context).hasSingleBean(ByodDeadLetterListenerContainerFactory.class);
        });
  }

  @Test
  void dltIndexingIsDisabledUnlessExplicitlyEnabled() {
    contextRunner.run(
        context ->
            assertThat(
                    context.getBean(KafkaConsumersProperties.class).getDltIndexing().getEnabled())
                .isFalse());
  }

  @Configuration
  @ComponentScan(
      basePackages = "com.opencop.processor.technical.spring.dlt",
      excludeFilters =
          @ComponentScan.Filter(
              type = FilterType.ASSIGNABLE_TYPE,
              classes = DeadLetterRepositoryJpaTestConfig.class))
  @Import(ByodDeadLetterListenerContainerFactory.class)
  static class DltScanRoots {}

  @Configuration
  static class TestDependencies {

    @Bean
    BlobClient blobClient() {
      return mock(BlobClient.class);
    }

    @Bean
    EntityManager entityManager() {
      return mock(EntityManager.class);
    }

    @Bean
    NamedParameterJdbcTemplate namedParameterJdbcTemplate() {
      return mock(NamedParameterJdbcTemplate.class);
    }

    @Bean
    BaseKafkaConfig baseKafkaConfig() {
      return new BaseKafkaConfig(Map.of());
    }

    @Bean
    KafkaConsumersProperties kafkaConsumersProperties() {
      return new KafkaConsumersProperties();
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    ProcessorMetrics opencopProcessorMetrics(MeterRegistry meterRegistry) {
      return new ProcessorMetrics(meterRegistry);
    }
  }
}
