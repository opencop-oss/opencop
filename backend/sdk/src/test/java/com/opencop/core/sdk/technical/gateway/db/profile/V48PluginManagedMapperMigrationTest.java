// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class V48PluginManagedMapperMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void v48MigrationMarksExistingMappersAsNotPluginManaged() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateTo(dataSource, "45");
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = UUID.randomUUID();
      insertMapper(jdbc, mapperId);

      migrateTo(dataSource, "48");

      assertThat(
              jdbc.queryForObject(
                  "SELECT plugin_managed FROM profiles.data_mapper WHERE id = ?",
                  Boolean.class,
                  mapperId))
          .isFalse();
    }
  }

  @Test
  void v48MigrationDefaultsNewMappersToNotPluginManaged() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateTo(dataSource, "48");
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = UUID.randomUUID();

      insertMapper(jdbc, mapperId);

      assertThat(
              jdbc.queryForObject(
                  "SELECT plugin_managed FROM profiles.data_mapper WHERE id = ?",
                  Boolean.class,
                  mapperId))
          .isFalse();
    }
  }

  @Test
  void v48MigrationRejectsNullPluginOwnership() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateTo(dataSource, "48");
      var jdbc = new JdbcTemplate(dataSource);

      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      INSERT INTO profiles.data_mapper
                        (id, version, name, editable, mapper, plugin_managed)
                      VALUES (?, 1, 'test mapper', true, '{}'::jsonb, NULL)
                      """,
                      UUID.randomUUID()))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  private static void insertMapper(JdbcTemplate jdbc, UUID mapperId) {
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper (id, version, name, editable, mapper)
        VALUES (?, 1, 'test mapper', true, '{}'::jsonb)
        """,
        mapperId);
  }

  private static void migrateTo(DataSource dataSource, String target) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .target(target)
        .load()
        .migrate();
  }
}
