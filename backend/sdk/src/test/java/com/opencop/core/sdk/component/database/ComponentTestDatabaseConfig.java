// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.database;

import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@TestConfiguration(proxyBeanMethods = false)
public class ComponentTestDatabaseConfig {

  @Bean
  Flyway componentTestFlyway(DataSource dataSource, Environment environment) {
    String[] locations =
        environment
            .getProperty("spring.flyway.locations", "classpath:db/migration")
            .split("\\s*,\\s*");
    Map<String, String> placeholders =
        Binder.get(environment)
            .bind("spring.flyway.placeholders", Bindable.mapOf(String.class, String.class))
            .orElseGet(Map::of);

    return Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(
            environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class, true))
        .locations(locations)
        .placeholders(placeholders)
        .load();
  }

  @Bean("componentTestFlywayInitializer")
  InitializingBean componentTestFlywayInitializer(
      Flyway componentTestFlyway, DataSource dataSource) {
    return () -> {
      componentTestFlyway.migrate();
      PostgisTestSupport.createCompatibilityFunctions(dataSource);
    };
  }

  @Bean
  static BeanFactoryPostProcessor entityManagerDependsOnFlyway() {
    return beanFactory -> {
      if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
        beanFactory
            .getBeanDefinition("entityManagerFactory")
            .setDependsOn("componentTestFlywayInitializer");
      }
    };
  }
}
