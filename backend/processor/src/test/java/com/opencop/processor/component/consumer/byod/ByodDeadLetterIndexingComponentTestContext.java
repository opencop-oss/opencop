// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.consumer.byod;

import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.processor.component.support.DeadLetterJpaComponentTestConfig;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import com.opencop.processor.technical.spring.config.byod.ByodConsumerManager;
import com.opencop.processor.technical.spring.config.byod.ByodDeadLetterConsumerManager;
import com.opencop.processor.technical.spring.config.byod.ByodDeadLetterListenerContainerFactory;
import com.opencop.processor.technical.spring.dlt.DeadLetterFailureIndexer;
import com.opencop.processor.technical.spring.dlt.DeadLetterIndexingErrorHandlerFactory;
import com.opencop.processor.technical.spring.dlt.DeadLetterObservability;
import com.opencop.processor.technical.spring.dlt.ProcessorDeadLetterRecordRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

@TestConfiguration
@Import({
  ByodMessageHandlerComponentTestContext.class,
  ByodDeadLetterListenerContainerFactory.class,
  ByodDeadLetterConsumerManager.class,
  ByodConsumerManager.class,
  DeadLetterIndexingErrorHandlerFactory.class,
  ProcessorDeadLetterRecordRepository.class,
  DeadLetterJpaComponentTestConfig.class
})
public class ByodDeadLetterIndexingComponentTestContext {

  private static final String DATABASE_NAME = "byod_dlt_index_component_it";

  @Bean
  BlobClient blobClient() {
    return mock(BlobClient.class);
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
  DataSource dataSource(
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String username,
      @Value("${spring.datasource.password}") String password,
      @Value("${opencop.db.driver}") String driverClassName)
      throws SQLException {
    String databaseUrl = isolatedDatabaseUrl(url, username, password);
    var dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName(driverClassName);
    dataSource.setUrl(databaseUrl);
    dataSource.setUsername(username);
    dataSource.setPassword(password);
    return dataSource;
  }

  @Bean
  InitializingBean byodDltFlywayInitializer(DataSource dataSource) {
    return () ->
        Flyway.configure()
            .dataSource(dataSource)
            .baselineOnMigrate(true)
            .locations("filesystem:" + sdkMigrationPath())
            .placeholders(FlywayGeoFeaturesPlaceholders.defaultPlaceholders())
            .load()
            .migrate();
  }

  @Bean
  NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
    return new NamedParameterJdbcTemplate(dataSource);
  }

  @Bean
  DeadLetterFailureIndexer deadLetterFailureIndexer(
      ProcessorDeadLetterRecordRepository repository,
      ObjectMapper objectMapper,
      DeadLetterObservability observability,
      KafkaConsumersProperties kafkaConsumersProperties) {
    kafkaConsumersProperties.getDltIndexing().setPayloadRetention(Duration.ofDays(7));
    return new DeadLetterFailureIndexer(
        repository, objectMapper, observability, kafkaConsumersProperties);
  }

  @Bean
  ProcessorMetrics opencopProcessorMetrics(MeterRegistry meterRegistry) {
    return new ProcessorMetrics(meterRegistry);
  }

  @Bean
  DeadLetterObservability deadLetterObservability(ProcessorMetrics metrics) {
    return new DeadLetterObservability(metrics);
  }

  private static String isolatedDatabaseUrl(String url, String username, String password)
      throws SQLException {
    try (var connection = DriverManager.getConnection(url, username, password);
        var statement = connection.createStatement()) {
      try {
        statement.execute("CREATE DATABASE " + DATABASE_NAME);
      } catch (SQLException e) {
        if (!"42P04".equals(e.getSQLState())) {
          throw e;
        }
      }
    }
    int databaseNameStart = url.lastIndexOf('/') + 1;
    return url.substring(0, databaseNameStart) + DATABASE_NAME;
  }

  private static String sdkMigrationPath() {
    var processorRelative = Path.of("..", "sdk", "src", "main", "resources", "db", "migration");
    if (Files.isDirectory(processorRelative)) {
      return processorRelative.toAbsolutePath().normalize().toString();
    }
    return Path.of("sdk", "src", "main", "resources", "db", "migration")
        .toAbsolutePath()
        .normalize()
        .toString();
  }
}
