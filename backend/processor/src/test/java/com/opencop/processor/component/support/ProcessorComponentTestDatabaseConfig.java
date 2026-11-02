// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.support;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@TestConfiguration(proxyBeanMethods = false)
@ActiveProfiles("it")
public class ProcessorComponentTestDatabaseConfig {

  @Bean
  Flyway processorComponentTestFlyway(DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FlywayGeoFeaturesPlaceholders.defaultPlaceholders())
        .load();
  }

  @Bean("processorComponentTestFlywayInitializer")
  InitializingBean processorComponentTestFlywayInitializer(
      Flyway processorComponentTestFlyway, DataSource dataSource) {
    return () -> {
      processorComponentTestFlyway.migrate();
      createPostgisCompatibilityFunctions(dataSource);
    };
  }

  @Bean
  static BeanFactoryPostProcessor entityManagerDependsOnFlyway() {
    return beanFactory -> {
      if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
        beanFactory
            .getBeanDefinition("entityManagerFactory")
            .setDependsOn("processorComponentTestFlywayInitializer");
      }
    };
  }

  private static void createPostgisCompatibilityFunctions(DataSource dataSource) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute(
        "CREATE OR REPLACE FUNCTION public.ST_GeomFromGeoJSON(text) RETURNS text "
            + "LANGUAGE SQL IMMUTABLE AS $$ SELECT $1; $$");
    jdbcTemplate.execute(
        "CREATE OR REPLACE FUNCTION public.ST_SetSRID(text, integer) RETURNS text "
            + "LANGUAGE SQL IMMUTABLE AS $$ SELECT $1; $$");
    jdbcTemplate.execute(
        "CREATE OR REPLACE FUNCTION public.ST_Force3D(text) RETURNS text "
            + "LANGUAGE SQL IMMUTABLE AS $$ SELECT $1; $$");
  }
}
