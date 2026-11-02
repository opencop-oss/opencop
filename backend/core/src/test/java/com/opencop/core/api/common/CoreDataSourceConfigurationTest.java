// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class CoreDataSourceConfigurationTest {

  @Test
  void defaultHikariConfiguration_whenLoaded_suppressesPostgresServerErrorDetail() {
    var hikari = loadHikariConfiguration();

    assertThat(hikari.getDataSourceProperties())
        .containsEntry("tcpKeepAlive", "true")
        .containsEntry("logServerErrorDetail", "false");
  }

  @Test
  void defaultHikariConfiguration_whenConstraintFails_retainsNameWithoutFailingRowValues()
      throws Exception {
    // This setting bounds pgJDBC exception rendering in Core. PostgreSQL server-side statement and
    // error logging are a separate operator-controlled concern.
    var sentinel = "detail-probe-value";
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var hikari = loadHikariConfiguration();
      try (var bootstrapConnection = postgres.getPostgresDatabase().getConnection()) {
        hikari.setJdbcUrl(bootstrapConnection.getMetaData().getURL());
      }
      hikari.setUsername("postgres");
      hikari.setPassword("postgres");

      try (var dataSource = new HikariDataSource(hikari);
          var connection = dataSource.getConnection();
          var statement = connection.createStatement()) {
        statement.execute(
            "CREATE TEMP TABLE core_error_detail_probe ("
                + "secret text NOT NULL, accepted boolean CONSTRAINT "
                + "core_error_detail_probe_accepted CHECK (accepted))");

        var failure =
            assertThrows(
                PSQLException.class,
                () ->
                    statement.execute(
                        "INSERT INTO core_error_detail_probe VALUES ('" + sentinel + "', false)"));

        assertThat(failure.getServerErrorMessage()).isNotNull();
        assertThat(failure.getServerErrorMessage().getConstraint())
            .isEqualTo("core_error_detail_probe_accepted");
        assertThat(failure.getMessage())
            .contains("core_error_detail_probe_accepted")
            .doesNotContain(sentinel)
            .doesNotContain("Failing row contains");
      }
    }
  }

  private static HikariConfig loadHikariConfiguration() {
    var environment = new StandardEnvironment();
    var resource = new ClassPathResource("application.yml");
    var loader = new YamlPropertySourceLoader();
    try {
      for (var propertySource : loader.load("application.yml", resource)) {
        environment.getPropertySources().addFirst(propertySource);
      }
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }

    return Binder.get(environment)
        .bind("spring.datasource.hikari", HikariConfig.class)
        .orElseThrow(() -> new IllegalStateException("Missing Core Hikari configuration"));
  }
}
