// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.integration;

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

class V57DataSourceSetupPrefillMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();
  private static final String PRE_MIGRATION_VERSION = "56";
  private static final String PREFILL_VERSION = "57";
  private static final String PREFILL =
      "{\"extractorId\": \"00000000-0000-4000-8000-0000000000e1\", \"values\": {\"host\": \"a\"}}";

  @Test
  void storesTheSetupPrefillOfAConnection() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);
      var sourceId = seedConnection(jdbc, seedIntegration(jdbc, 1), 1);

      jdbc.update(
          "UPDATE profiles.data_source SET setup_prefill = ?::jsonb WHERE id = ?",
          PREFILL,
          sourceId);

      assertThat(
              jdbc.queryForObject(
                  "SELECT setup_prefill = ?::jsonb FROM profiles.data_source WHERE id = ?",
                  Boolean.class,
                  PREFILL,
                  sourceId))
          .isTrue();
    }
  }

  @Test
  void rejectsAPrefillThatIsNotAnObject() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);
      var sourceId = seedConnection(jdbc, seedIntegration(jdbc, 1), 1);

      for (var prefill : new String[] {"[]", "\"text\"", "5", "null"}) {
        assertThatThrownBy(
                () ->
                    jdbc.update(
                        "UPDATE profiles.data_source SET setup_prefill = ?::jsonb WHERE id = ?",
                        prefill,
                        sourceId))
            .as(prefill)
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("ck_data_source_setup_prefill_object");
      }
    }
  }

  /** Nothing is backfilled: a connection created before the column has no prefill. */
  @Test
  void leavesExistingConnectionsWithoutAPrefill() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, PRE_MIGRATION_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var sourceId = seedConnection(jdbc, seedIntegration(jdbc, 1), 1);
      var before = jdbc.queryForMap("SELECT * FROM profiles.data_source WHERE id = ?", sourceId);

      migrate(dataSource, PREFILL_VERSION);

      var after = jdbc.queryForMap("SELECT * FROM profiles.data_source WHERE id = ?", sourceId);
      assertThat(after).containsEntry("setup_prefill", null);
      after.remove("setup_prefill");
      assertThat(after).isEqualTo(before);
    }
  }

  private static JdbcTemplate migratedDatabase(EmbeddedPostgres postgres) {
    var dataSource = postgres.getPostgresDatabase();
    migrate(dataSource, PREFILL_VERSION);
    return new JdbcTemplate(dataSource);
  }

  private static UUID seedIntegration(JdbcTemplate jdbc, int schemaVersion) {
    var integrationId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO config.integration (id, name, description) VALUES (?, 'Integration', 'An"
            + " integration.')",
        integrationId);
    jdbc.update(
        "INSERT INTO config.integration_connection_schema (integration_id, schema_version)"
            + " VALUES (?, ?)",
        integrationId,
        schemaVersion);
    return integrationId;
  }

  private static UUID seedConnection(JdbcTemplate jdbc, UUID integrationId, int schemaVersion) {
    var mapperId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO profiles.mapper (id, qualifier) VALUES (?, 'INGRESS'::profiles.mapper_qualifier)",
        mapperId);
    jdbc.update(
        "INSERT INTO profiles.data_mapper (id, version, name, mapper) VALUES (?, 1, ?, '{}'::jsonb)",
        mapperId,
        "mapper-" + mapperId);
    var sourceId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO profiles.data_source
          (id, name, provider, access, ingress_mapper_id, integration_id,
           connection_schema_version, connection_spec)
        VALUES (?, ?, 'TCP', 'R', ?, ?, ?, '{}'::jsonb)
        """,
        sourceId,
        "source-" + sourceId,
        mapperId,
        integrationId,
        schemaVersion);
    return sourceId;
  }

  private static void migrate(DataSource dataSource, String target) {
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
