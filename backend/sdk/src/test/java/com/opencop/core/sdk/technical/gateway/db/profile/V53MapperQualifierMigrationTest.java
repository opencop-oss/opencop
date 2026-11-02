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

class V53MapperQualifierMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();
  private static final String PRE_MIGRATION_VERSION = "52";
  private static final String MAPPER_QUALIFIER_VERSION = "53";
  private static final String INGRESS = "INGRESS";
  private static final String EGRESS = "EGRESS";

  @Test
  void backfillsEveryMapperAsAnUnownedIngressMapperAndEveryIntegrationIdentity() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, PRE_MIGRATION_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = seedMapperVersion(jdbc, UUID.randomUUID(), 1);
      seedMapperVersion(jdbc, mapperId, 2);
      var integrationId = UUID.randomUUID();
      seedIntegrationType(jdbc, integrationId, 1);
      seedIntegrationType(jdbc, integrationId, 2);

      migrate(dataSource, MAPPER_QUALIFIER_VERSION);

      var mappers =
          jdbc.queryForList(
              "SELECT id, qualifier::text AS qualifier, integration_id FROM profiles.mapper");
      assertThat(mappers).hasSize(1);
      assertThat(mappers.getFirst())
          .containsEntry("id", mapperId)
          .containsEntry("qualifier", INGRESS)
          .containsEntry("integration_id", null);
      assertThat(jdbc.queryForList("SELECT id FROM config.integration", UUID.class))
          .containsExactly(integrationId);
    }
  }

  @Test
  void existingWritableConnectionsSurviveTheMigrationWithTheirMapperAsIngress() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, PRE_MIGRATION_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = seedMapperVersion(jdbc, UUID.randomUUID(), 1);
      var sourceId = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO profiles.data_source (id, name, provider, access, mapper_id, connection_spec)
          VALUES (?, ?, 'TCP', 'RW', ?, '{}'::jsonb)
          """,
          sourceId,
          "source-" + sourceId,
          mapperId);

      migrate(dataSource, MAPPER_QUALIFIER_VERSION);

      assertThat(
              jdbc.queryForMap(
                  "SELECT ingress_mapper_id, egress_mapper_id FROM profiles.data_source WHERE id = ?",
                  sourceId))
          .containsEntry("ingress_mapper_id", mapperId)
          .containsEntry("egress_mapper_id", null);
    }
  }

  @Test
  void aWritableConnectionWithoutAnEgressMapperCanStillBeUpdated() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, PRE_MIGRATION_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = seedMapperVersion(jdbc, UUID.randomUUID(), 1);
      var sourceId = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO profiles.data_source (id, name, provider, access, mapper_id, connection_spec)
          VALUES (?, ?, 'TCP', 'RW', ?, '{}'::jsonb)
          """,
          sourceId,
          "source-" + sourceId,
          mapperId);

      migrate(dataSource, MAPPER_QUALIFIER_VERSION);

      // Core's startup backfills update such rows before any egress mapper is assigned.
      assertThat(
              jdbc.update(
                  "UPDATE profiles.data_source SET name = 'renamed' WHERE id = ?", sourceId))
          .isOne();
    }
  }

  @Test
  void aConnectionCannotHoldAMapperOfTheWrongDirection() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);
      var ingress = seedMapper(jdbc, INGRESS);
      var egress = seedMapper(jdbc, EGRESS);

      assertThatThrownBy(() -> insertSource(jdbc, "R", egress, null))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("fk_data_source_ingress_mapper");
      assertThatThrownBy(() -> insertSource(jdbc, "W", null, ingress))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("fk_data_source_egress_mapper");
    }
  }

  @Test
  void aDataProfileCannotSelectAnEgressMapper() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);
      var egress = seedMapper(jdbc, EGRESS);
      var delimiterId = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO profiles.data_delimiter (id, name) VALUES (?, ?)",
          delimiterId,
          "delimiter-" + delimiterId);

      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      INSERT INTO profiles.data_profile
                        (id, name, delimiter_id, mapper_id, mapper_version)
                      VALUES (?, 'profile', ?, ?, 1)
                      """,
                      UUID.randomUUID(),
                      delimiterId,
                      egress))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("fk_data_profile_ingress_mapper");
    }
  }

  @Test
  void aMapperVersionNeedsItsMapperIdentity() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);

      assertThatThrownBy(() -> seedMapperVersion(jdbc, UUID.randomUUID(), 1))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("fk_data_mapper_mapper");
    }
  }

  @Test
  void anIntegrationSchemaNeedsItsIntegrationIdentity() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var jdbc = migratedDatabase(postgres);

      assertThatThrownBy(() -> seedIntegrationType(jdbc, UUID.randomUUID(), 1))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("fk_integration_type_integration");
    }
  }

  private static JdbcTemplate migratedDatabase(EmbeddedPostgres postgres) {
    var dataSource = postgres.getPostgresDatabase();
    migrate(dataSource, MAPPER_QUALIFIER_VERSION);
    return new JdbcTemplate(dataSource);
  }

  private static UUID seedMapper(JdbcTemplate jdbc, String qualifier) {
    var mapperId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO profiles.mapper (id, qualifier) VALUES (?, ?::profiles.mapper_qualifier)",
        mapperId,
        qualifier);
    return seedMapperVersion(jdbc, mapperId, 1);
  }

  private static UUID seedMapperVersion(JdbcTemplate jdbc, UUID mapperId, int version) {
    jdbc.update(
        "INSERT INTO profiles.data_mapper (id, version, name, mapper) VALUES (?, ?, ?, '{}'::jsonb)",
        mapperId,
        version,
        "mapper-" + mapperId);
    return mapperId;
  }

  private static void seedIntegrationType(JdbcTemplate jdbc, UUID id, int schemaVersion) {
    jdbc.update(
        """
        INSERT INTO config.integration_type (id, schema_version, name, description)
        VALUES (?, ?, 'Integration', 'An integration.')
        """,
        id,
        schemaVersion);
  }

  private static void insertSource(
      JdbcTemplate jdbc, String access, UUID ingressMapperId, UUID egressMapperId) {
    var sourceId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO profiles.data_source
          (id, name, provider, access, ingress_mapper_id, egress_mapper_id, connection_spec)
        VALUES (?, ?, 'TCP', ?::profiles.access_mode, ?, ?, '{}'::jsonb)
        """,
        sourceId,
        "source-" + sourceId,
        access,
        ingressMapperId,
        egressMapperId);
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
