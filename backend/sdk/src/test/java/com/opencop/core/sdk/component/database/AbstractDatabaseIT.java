// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.database;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.sql.SQLException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractDatabaseIT {

  private static final String DRIVER = "org.postgresql.Driver";
  private static final String USERNAME = "postgres";
  private static final String PASSWORD = "postgres";

  private static final EmbeddedPostgres POSTGRES = startEmbeddedPostgres();

  @DynamicPropertySource
  static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", AbstractDatabaseIT::jdbcUrl);
    registry.add("spring.datasource.username", () -> USERNAME);
    registry.add("spring.datasource.password", () -> PASSWORD);
    registry.add("spring.datasource.driver-class-name", () -> DRIVER);
    registry.add("opencop.db.driver", () -> DRIVER);
  }

  private static EmbeddedPostgres startEmbeddedPostgres() {
    try {
      return EmbeddedPostgres.builder().start();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start embedded PostgreSQL for component tests", e);
    }
  }

  private static String jdbcUrl() {
    try (var connection = POSTGRES.getPostgresDatabase().getConnection()) {
      return connection.getMetaData().getURL();
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to determine embedded PostgreSQL JDBC URL", e);
    }
  }
}
