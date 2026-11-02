// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableTransactionManagement
class DeadLetterRepositoryJpaTestConfig {

  @Bean(destroyMethod = "close")
  EmbeddedPostgres embeddedPostgres() throws Exception {
    return EmbeddedPostgres.builder().start();
  }

  @Bean
  DataSource dataSource(EmbeddedPostgres postgres) {
    return postgres.getPostgresDatabase();
  }

  @Bean
  JdbcTemplate jdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean
  NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
    return new NamedParameterJdbcTemplate(dataSource);
  }

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  InitializingBean flywayInitializer(DataSource dataSource) {
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
  LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
    var factory = new LocalContainerEntityManagerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setPackagesToScan("com.opencop.processor.technical.spring.dlt");
    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
    factory.setJpaPropertyMap(
        Map.of(
            "hibernate.hbm2ddl.auto", "none",
            "hibernate.show_sql", "false"));
    return factory;
  }

  @Bean
  PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
    return new JpaTransactionManager(entityManagerFactory);
  }

  @Bean
  ProcessorDeadLetterRecordRepository processorDeadLetterRecordRepository(
      EntityManagerFactory entityManagerFactory,
      NamedParameterJdbcTemplate jdbc,
      DeadLetterObservability observability) {
    return new ProcessorDeadLetterRecordRepository(
        SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory),
        jdbc,
        observability);
  }

  @Bean
  MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean
  ProcessorMetrics opencopProcessorMetrics(MeterRegistry meterRegistry) {
    return new ProcessorMetrics(meterRegistry);
  }

  @Bean
  DeadLetterObservability deadLetterObservability(ProcessorMetrics metrics) {
    return new DeadLetterObservability(metrics);
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
