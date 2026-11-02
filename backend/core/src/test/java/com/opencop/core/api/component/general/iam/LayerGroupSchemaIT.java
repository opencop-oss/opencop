// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class LayerGroupSchemaIT {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void migration_createsAllThreeLayerGroupTables() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var tables =
          jdbc.queryForList(
              """
              SELECT table_name
              FROM information_schema.tables
              WHERE table_schema = 'iam'
                AND table_name IN ('layer_group', 'layer_group_member', 'layer_group_layer')
              ORDER BY table_name
              """,
              String.class);

      assertEquals(List.of("layer_group", "layer_group_layer", "layer_group_member"), tables);
    }
  }

  @Test
  void layerGroup_whenNameDuplicated_rejectsWithUniqueConstraint() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      insertLayerGroup(jdbc, UUID.randomUUID(), "Ops");

      var error =
          assertThrows(
              DataIntegrityViolationException.class,
              () -> insertLayerGroup(jdbc, UUID.randomUUID(), "Ops"));
      var sqlCause = (PSQLException) NestedExceptionUtils.getMostSpecificCause(error);
      assertEquals("uk_layer_group_name", sqlCause.getServerErrorMessage().getConstraint());
    }
  }

  @Test
  void layerGroupMember_whenParentDeleted_cascadesToMemberAndLayerRows() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var groupId = UUID.randomUUID();
      var userId = UUID.randomUUID();
      var layerId = UUID.randomUUID();
      insertUser(jdbc, userId);
      insertLayerGroup(jdbc, groupId, "ToDelete");
      insertLayer(jdbc, layerId, "LayerForCascade");
      insertLayerGroupMember(jdbc, groupId, userId, "MEMBER");
      insertLayerGroupLayer(jdbc, groupId, layerId, null);

      jdbc.update("DELETE FROM iam.layer_group WHERE id = ?", groupId);

      var memberCount =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM iam.layer_group_member WHERE layer_group_id = ?",
              Integer.class,
              groupId);
      assertEquals(0, memberCount);

      var layerLinkCount =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM iam.layer_group_layer WHERE layer_group_id = ?",
              Integer.class,
              groupId);
      assertEquals(0, layerLinkCount);
    }
  }

  @Test
  void layerGroupLayer_whenRoleOmitted_defaultsToMember() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var groupId = UUID.randomUUID();
      var layerId = UUID.randomUUID();
      insertLayerGroup(jdbc, groupId, "DefaultRoleGroup");
      insertLayer(jdbc, layerId, "DefaultRoleLayer");

      // Insert without specifying role — relies on DEFAULT 'MEMBER'
      jdbc.update(
          "INSERT INTO iam.layer_group_layer (layer_group_id, layer_id) VALUES (?, ?)",
          groupId,
          layerId);

      var role =
          jdbc.queryForObject(
              "SELECT role::text FROM iam.layer_group_layer WHERE layer_group_id = ? AND layer_id = ?",
              String.class,
              groupId,
              layerId);
      assertEquals("MEMBER", role);
    }
  }

  @Test
  void layerGroupMember_whenSamePairInsertedTwice_rejectsWithPrimaryKeyViolation()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var groupId = UUID.randomUUID();
      var userId = UUID.randomUUID();
      insertUser(jdbc, userId);
      insertLayerGroup(jdbc, groupId, "DupePKGroup");
      insertLayerGroupMember(jdbc, groupId, userId, "MEMBER");

      assertThrows(
          DataIntegrityViolationException.class,
          () -> insertLayerGroupMember(jdbc, groupId, userId, "ADMIN"));
    }
  }

  @Test
  void layerGroupMember_whenLayerGroupIdDoesNotExist_rejectsWithForeignKeyViolation()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var orphanGroupId = UUID.randomUUID();
      var userId = UUID.randomUUID();
      insertUser(jdbc, userId);

      // No parent row in iam.layer_group for orphanGroupId — must raise FK violation.
      assertThrows(
          DataIntegrityViolationException.class,
          () -> insertLayerGroupMember(jdbc, orphanGroupId, userId, "MEMBER"),
          "Expected FK violation when layer_group_id has no parent row in iam.layer_group");

      // Positive-control: a valid insert (with a real parent) succeeds and the row is persisted.
      var realGroupId = UUID.randomUUID();
      insertLayerGroup(jdbc, realGroupId, "ValidGroup");
      insertLayerGroupMember(jdbc, realGroupId, userId, "MEMBER");
      var count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM iam.layer_group_member WHERE layer_group_id = ?",
              Integer.class,
              realGroupId);
      assertEquals(1, count, "Valid insert should persist one row");
    }
  }

  @Test
  void layerGroupTables_haveExpectedPerformanceIndexes() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var indexes =
          jdbc.queryForList(
              """
              SELECT indexname
              FROM pg_indexes
              WHERE schemaname = 'iam'
                AND tablename IN ('layer_group_member', 'layer_group_layer')
                AND indexname LIKE 'idx_%'
              ORDER BY indexname
              """,
              String.class);

      assertTrue(
          indexes.contains("idx_layer_group_layer_group_id"),
          "Expected idx_layer_group_layer_group_id but got: " + indexes);
      assertTrue(
          indexes.contains("idx_layer_group_layer_layer_id"),
          "Expected idx_layer_group_layer_layer_id but got: " + indexes);
      assertTrue(
          indexes.contains("idx_layer_group_member_user_id"),
          "Expected idx_layer_group_member_user_id but got: " + indexes);
    }
  }

  @Test
  void migration_addsAccessScopeColumnWithPrivateDefault() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var rows =
          jdbc.queryForList(
              """
              SELECT udt_name, is_nullable, column_default
              FROM information_schema.columns
              WHERE table_schema = 'iam'
                AND table_name = 'layer_group'
                AND column_name = 'access_scope'
              """);

      assertEquals(1, rows.size(), "Expected exactly one row for access_scope column");
      var col = rows.get(0);
      assertEquals("layer_group_access_scope", col.get("udt_name"));
      assertEquals("NO", col.get("is_nullable"));
      assertEquals("'PRIVATE'::iam.layer_group_access_scope", col.get("column_default"));
    }
  }

  @Test
  void migration_accessScopeDefaultsToPrivateWhenOmitted() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var id = UUID.randomUUID();
      jdbc.update("INSERT INTO iam.layer_group (id, name) VALUES (?, ?)", id, "TestGroup-" + id);

      var scope =
          jdbc.queryForObject(
              "SELECT access_scope::text FROM iam.layer_group WHERE id = ?", String.class, id);

      assertEquals("PRIVATE", scope, "Rows inserted without access_scope must default to PRIVATE");
    }
  }

  @Test
  void migration_createsGlobalAccessScopePartialIndex() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var indexDefs =
          jdbc.queryForList(
              """
              SELECT indexdef
              FROM pg_indexes
              WHERE schemaname = 'iam'
                AND tablename = 'layer_group'
                AND indexname = 'idx_layer_group_global_access_scope'
              """,
              String.class);

      assertEquals(
          1, indexDefs.size(), "Expected exactly one row for idx_layer_group_global_access_scope");
      var indexDef = indexDefs.get(0);
      assertTrue(
          indexDef.contains("WHERE")
              && indexDef.contains("access_scope")
              && indexDef.contains("'GLOBAL'"),
          "Index definition must include the partial-index predicate filtering on access_scope = GLOBAL. Got: "
              + indexDef);
    }
  }

  @Test
  void layerGroup_accessScopeRoundTrip_globalPersistedAndReadBack() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var id = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO iam.layer_group (id, name, access_scope)"
              + " VALUES (?, ?, 'GLOBAL'::iam.layer_group_access_scope)",
          id,
          "GlobalGroup-" + id);

      var scope =
          jdbc.queryForObject(
              "SELECT access_scope::text FROM iam.layer_group WHERE id = ?", String.class, id);

      assertEquals("GLOBAL", scope, "Persisted GLOBAL access_scope must be read back as GLOBAL");
    }
  }

  @Test
  void layerGroup_accessScopeEnumRejectsInvalidValues() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      var id = UUID.randomUUID();
      assertThrows(
          Exception.class,
          () ->
              jdbc.update(
                  "INSERT INTO iam.layer_group (id, name, access_scope)"
                      + " VALUES (?, ?, 'UNKNOWN'::iam.layer_group_access_scope)",
                  id,
                  "BadGroup-" + id),
          "Invalid enum value must be rejected by the database");

      // Positive-control: valid PRIVATE value is accepted.
      var validId = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO iam.layer_group (id, name, access_scope)"
              + " VALUES (?, ?, 'PRIVATE'::iam.layer_group_access_scope)",
          validId,
          "ValidGroup-" + validId);
      var scope =
          jdbc.queryForObject(
              "SELECT access_scope::text FROM iam.layer_group WHERE id = ?", String.class, validId);
      assertEquals("PRIVATE", scope, "PRIVATE value must round-trip correctly");
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static void migrateAll(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();
  }

  private static void insertLayerGroup(JdbcTemplate jdbc, UUID id, String name) {
    jdbc.update("INSERT INTO iam.layer_group (id, name) VALUES (?, ?)", id, name);
  }

  private static void insertLayerGroupMember(
      JdbcTemplate jdbc, UUID groupId, UUID userId, String role) {
    jdbc.update(
        "INSERT INTO iam.layer_group_member (layer_group_id, user_id, role) VALUES (?, ?, CAST(? AS iam.layer_group_role))",
        groupId,
        userId,
        role);
  }

  private static void insertLayerGroupLayer(
      JdbcTemplate jdbc, UUID groupId, UUID layerId, String role) {
    if (role == null) {
      jdbc.update(
          "INSERT INTO iam.layer_group_layer (layer_group_id, layer_id) VALUES (?, ?)",
          groupId,
          layerId);
    } else {
      jdbc.update(
          "INSERT INTO iam.layer_group_layer (layer_group_id, layer_id, role) VALUES (?, ?, CAST(? AS iam.layer_role))",
          groupId,
          layerId,
          role);
    }
  }

  private static void insertLayer(JdbcTemplate jdbc, UUID id, String name) {
    jdbc.update("INSERT INTO iam.layer (id, name) VALUES (?, ?)", id, name);
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
}
