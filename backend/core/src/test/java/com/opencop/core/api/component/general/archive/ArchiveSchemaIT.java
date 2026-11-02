// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class ArchiveSchemaIT {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void insertLayer_whenSecondActiveRowHasSameName_rejectsWithActiveUniqueIndex() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      insertLayer(jdbc, UUID.randomUUID(), "Ops", null, null);

      var error =
          assertThrows(
              DataIntegrityViolationException.class,
              () -> insertLayer(jdbc, UUID.randomUUID(), "Ops", null, null));
      var sqlCause = (PSQLException) NestedExceptionUtils.getMostSpecificCause(error);
      assertNotNull(sqlCause);
      assertEquals("uk_layer_name_active", sqlCause.getServerErrorMessage().getConstraint());
    }
  }

  @Test
  void insertDataSource_whenArchivedAndActiveRowsShareName_acceptsActiveRow() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var mapperId = createMapper(jdbc);
      var archivedBy = UUID.randomUUID();
      insertUser(jdbc, archivedBy);
      insertDataSource(
          jdbc,
          UUID.randomUUID(),
          "Alpha",
          mapperId,
          Instant.parse("2026-03-12T10:00:00Z"),
          archivedBy);
      insertDataSource(jdbc, UUID.randomUUID(), "Alpha", mapperId, null, null);

      var activeCount =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM profiles.data_source WHERE name = ? AND archived_at IS NULL",
              Integer.class,
              "Alpha");
      assertEquals(1, activeCount);

      var archivedRow =
          jdbc.queryForMap(
              "SELECT archived_at, archived_by FROM profiles.data_source WHERE name = ? AND archived_at IS NOT NULL",
              "Alpha");
      assertNotNull(archivedRow.get("archived_at"));
      assertNotNull(archivedRow.get("archived_by"));
    }
  }

  @Test
  void insertLayer_whenArchivedAtSetAndArchivedByNull_rejectsWithArchiveActorConstraint()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var error =
          assertThrows(
              DataIntegrityViolationException.class,
              () ->
                  insertLayer(
                      jdbc, UUID.randomUUID(), "Ops-archived-missing-actor", Instant.now(), null));
      var sqlCause = (PSQLException) NestedExceptionUtils.getMostSpecificCause(error);
      assertNotNull(sqlCause);
      assertEquals(
          "ck_layer_archive_actor_consistency", sqlCause.getServerErrorMessage().getConstraint());
    }
  }

  @Test
  void insertDataSource_whenArchivedBySetAndArchivedAtNull_rejectsWithArchiveActorConstraint()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = createMapper(jdbc);
      var archivedBy = UUID.randomUUID();
      insertUser(jdbc, archivedBy);

      var error =
          assertThrows(
              DataIntegrityViolationException.class,
              () ->
                  insertDataSource(
                      jdbc, UUID.randomUUID(), "Alpha-actor-only", mapperId, null, archivedBy));
      var sqlCause = (PSQLException) NestedExceptionUtils.getMostSpecificCause(error);
      assertNotNull(sqlCause);
      assertEquals(
          "ck_data_source_archive_actor_consistency",
          sqlCause.getServerErrorMessage().getConstraint());
    }
  }

  @Test
  void insertLayerAndDataSource_whenArchiveColumnsAreBothNullOrBothSet_acceptsRows()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = createMapper(jdbc);
      var archivedAt = Instant.parse("2026-03-12T10:00:00Z");
      var archivedBy = UUID.randomUUID();
      insertUser(jdbc, archivedBy);

      insertLayer(jdbc, UUID.randomUUID(), "Ops-active-both-null", null, null);
      insertLayer(jdbc, UUID.randomUUID(), "Ops-archived-both-set", archivedAt, archivedBy);
      insertDataSource(jdbc, UUID.randomUUID(), "Alpha-active-both-null", mapperId, null, null);
      insertDataSource(
          jdbc, UUID.randomUUID(), "Alpha-archived-both-set", mapperId, archivedAt, archivedBy);
    }
  }

  @Test
  void insertLayer_whenArchivedByDoesNotReferenceUser_rejectsWithForeignKeyConstraint()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var error =
          assertThrows(
              DataIntegrityViolationException.class,
              () ->
                  insertLayer(
                      jdbc,
                      UUID.randomUUID(),
                      "Ops-archived-missing-user",
                      Instant.now(),
                      UUID.randomUUID()));
      var sqlCause = (PSQLException) NestedExceptionUtils.getMostSpecificCause(error);
      assertNotNull(sqlCause);
      assertEquals("fk_layer_archived_by_user", sqlCause.getServerErrorMessage().getConstraint());
    }
  }

  @Test
  void insertDataSource_whenArchivedByDoesNotReferenceUser_rejectsWithForeignKeyConstraint()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = createMapper(jdbc);

      var error =
          assertThrows(
              DataIntegrityViolationException.class,
              () ->
                  insertDataSource(
                      jdbc,
                      UUID.randomUUID(),
                      "Alpha-archived-missing-user",
                      mapperId,
                      Instant.now(),
                      UUID.randomUUID()));
      var sqlCause = (PSQLException) NestedExceptionUtils.getMostSpecificCause(error);
      assertNotNull(sqlCause);
      assertEquals(
          "fk_data_source_archived_by_user", sqlCause.getServerErrorMessage().getConstraint());
    }
  }

  private static void migrateAll(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();
  }

  private static void insertLayer(
      JdbcTemplate jdbc, UUID id, String name, Instant archivedAt, UUID archivedBy) {
    jdbc.update(
        """
        INSERT INTO iam.layer (id, name, archived_at, archived_by, lifecycle_state)
        VALUES (?, ?, ?, ?, ?::iam.layer_lifecycle_state)
        """,
        id,
        name,
        archivedAt == null ? null : Timestamp.from(archivedAt),
        archivedBy,
        archivedAt == null ? "ACTIVE" : "ARCHIVED");
  }

  private static UUID createMapper(JdbcTemplate jdbc) {
    var mapperId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO profiles.mapper (id, qualifier) VALUES (?, 'INGRESS'::profiles.mapper_qualifier) ON CONFLICT (id) DO NOTHING",
        mapperId);
    jdbc.update(
        "INSERT INTO profiles.data_mapper (id, version, name, mapper) VALUES (?, ?, ?, CAST(? AS jsonb))",
        mapperId,
        1,
        "mapper-" + mapperId,
        "{}");
    return mapperId;
  }

  private static void insertUser(JdbcTemplate jdbc, UUID userId) {
    jdbc.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "https://issuer.test",
        "user-" + userId,
        "user@example.test",
        "Test",
        "User",
        "Test User");
  }

  private static void insertDataSource(
      JdbcTemplate jdbc, UUID id, String name, UUID mapperId, Instant archivedAt, UUID archivedBy) {
    NativeIntegrationFixture.seed(jdbc);
    jdbc.update(
        """
        INSERT INTO profiles.data_source (
            id, name, ingress_mapper_id, connection_spec, enabled, access, archived_at,
            archived_by, integration_id, connection_schema_version
        ) VALUES (?, ?, ?, CAST(? AS jsonb), ?,
                  CAST(? AS profiles.access_mode), ?, ?, ?, ?)
        """,
        id,
        name,
        mapperId,
        "{\"transport\":{\"kind\":\"TCP\",\"host\":\"127.0.0.1\",\"port\":9000,\"timeout\":1000},\"security\":{\"kind\":\"NONE\"},\"auth\":{\"kind\":\"NONE\"}}",
        true,
        "R",
        archivedAt == null ? null : Timestamp.from(archivedAt),
        archivedBy,
        NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
        NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion());
  }
}
