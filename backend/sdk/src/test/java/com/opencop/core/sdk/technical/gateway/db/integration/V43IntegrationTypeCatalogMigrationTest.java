// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class V43IntegrationTypeCatalogMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();
  private static final String V43 = "43";

  @Test
  void v43MigrationCreatesTheImmutableCompositeIntegrationCatalogShape() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      // Stop at V43 so this test keeps asserting the shape V43 itself creates; later migrations
      // that extend the catalog are covered by their own migration tests.
      migrateTo(dataSource, V43);
      var jdbc = new JdbcTemplate(dataSource);

      assertThat(
              jdbc.queryForList(
                  """
                  SELECT column_name
                  FROM information_schema.columns
                  WHERE table_schema = 'config' AND table_name = 'integration_type'
                  ORDER BY ordinal_position
                  """,
                  String.class))
          .containsExactly("id", "schema_version", "name", "description", "created_at");
      assertThat(
              jdbc.queryForList(
                  """
                  SELECT column_name
                  FROM information_schema.key_column_usage
                  WHERE constraint_schema = 'config'
                    AND table_name = 'integration_type'
                    AND constraint_name = 'integration_type_pkey'
                  ORDER BY ordinal_position
                  """,
                  String.class))
          .containsExactly("id", "schema_version");
      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT count(*)
                  FROM pg_trigger trigger_row
                  JOIN pg_class target_table ON target_table.oid = trigger_row.tgrelid
                  JOIN pg_namespace target_schema ON target_schema.oid = target_table.relnamespace
                  WHERE target_schema.nspname = 'config'
                    AND target_table.relname = 'integration_type'
                    AND NOT trigger_row.tgisinternal
                  """,
                  Long.class))
          .isZero();
    }
  }

  @Test
  void v43MigrationRejectsNonPositiveVersionsAndBlankDisplayMetadata() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateTo(dataSource, V43);
      var jdbc = new JdbcTemplate(dataSource);

      assertThatThrownBy(() -> insert(jdbc, UUID.randomUUID(), 0, "Example", "Example integration"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> insert(jdbc, UUID.randomUUID(), 1, "   ", "Example integration"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> insert(jdbc, UUID.randomUUID(), 1, "Example", "\t"))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  void v43MigrationGeneratesCreationTimeInTheDatabase() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateTo(dataSource, V43);
      var jdbc = new JdbcTemplate(dataSource);
      var id = UUID.randomUUID();

      insert(jdbc, id, 1, "Example", "Example integration");

      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT created_at IS NOT NULL
                  FROM config.integration_type
                  WHERE id = ? AND schema_version = 1
                  """,
                  Boolean.class,
                  id))
          .isTrue();
    }
  }

  private static void insert(
      JdbcTemplate jdbc, UUID id, int schemaVersion, String name, String description) {
    jdbc.update(
        """
        INSERT INTO config.integration_type (id, schema_version, name, description)
        VALUES (?, ?, ?, ?)
        """,
        id,
        schemaVersion,
        name,
        description);
  }

  private static void migrateTo(DataSource dataSource, String version) {
    migrate(dataSource, configuration -> configuration.target(version));
  }

  private static void migrate(
      DataSource dataSource, UnaryOperator<FluentConfiguration> customizer) {
    customizer
        .apply(
            Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .locations("classpath:db/migration")
                .placeholders(FLYWAY_PLACEHOLDERS))
        .load()
        .migrate();
  }
}
