// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class V20FlywayMigrationIT {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void v20Migration_createsLayerGroupRoleEnumType() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      List<String> enumValues =
          jdbc.queryForList(
              """
              SELECT e.enumlabel
              FROM pg_type t
              JOIN pg_namespace n ON t.typnamespace = n.oid
              JOIN pg_enum e ON e.enumtypid = t.oid
              WHERE n.nspname = 'iam' AND t.typname = 'layer_group_role'
              ORDER BY e.enumsortorder
              """,
              String.class);

      assertThat(enumValues).containsExactly("OWNER", "ADMIN", "MAINTAINER", "MEMBER");
    }
  }

  @Test
  void v20Migration_createsLayerGroupUserGroupTable() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      List<String> columns =
          jdbc.queryForList(
              """
              SELECT column_name
              FROM information_schema.columns
              WHERE table_schema = 'iam'
                AND table_name = 'layer_group_user_group'
              ORDER BY ordinal_position
              """,
              String.class);

      assertThat(columns)
          .containsExactlyInAnyOrder("layer_group_id", "user_group_id", "role", "assigned_at");
    }
  }

  @Test
  void v20Migration_tableHasPrimaryKey() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      List<String> pkColumns =
          jdbc.queryForList(
              """
              SELECT kcu.column_name
              FROM information_schema.table_constraints tc
              JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
              WHERE tc.constraint_type = 'PRIMARY KEY'
                AND tc.table_schema = 'iam'
                AND tc.table_name = 'layer_group_user_group'
              ORDER BY kcu.ordinal_position
              """,
              String.class);

      assertThat(pkColumns).containsExactlyInAnyOrder("layer_group_id", "user_group_id");
    }
  }

  @Test
  void v20Migration_tableHasUserGroupIdIndex() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      List<String> indexes =
          jdbc.queryForList(
              """
              SELECT indexname
              FROM pg_indexes
              WHERE schemaname = 'iam'
                AND tablename = 'layer_group_user_group'
                AND indexname = 'idx_layer_group_user_group_user_group_id'
              """,
              String.class);

      assertThat(indexes).hasSize(1);
    }
  }

  @Test
  void v20Migration_insertAndQueryByUserGroupId_returnsCorrectLayerGroupIds() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      String layerGroupId1 = "00000000-0000-0000-0000-000000000010";
      String layerGroupId2 = "00000000-0000-0000-0000-000000000011";
      String layerGroupId3 = "00000000-0000-0000-0000-000000000012";
      String userGroupId1 = "00000000-0000-0000-0000-000000000020";
      String userGroupId2 = "00000000-0000-0000-0000-000000000021";

      jdbc.update(
          "INSERT INTO iam.layer_group (id, name) VALUES (?::uuid, ?)", layerGroupId1, "LG1");
      jdbc.update(
          "INSERT INTO iam.layer_group (id, name) VALUES (?::uuid, ?)", layerGroupId2, "LG2");
      jdbc.update(
          "INSERT INTO iam.layer_group (id, name) VALUES (?::uuid, ?)", layerGroupId3, "LG3");
      jdbc.update("INSERT INTO iam.user_group (id, name) VALUES (?::uuid, ?)", userGroupId1, "UG1");
      jdbc.update("INSERT INTO iam.user_group (id, name) VALUES (?::uuid, ?)", userGroupId2, "UG2");

      jdbc.update(
          "INSERT INTO iam.layer_group_user_group (layer_group_id, user_group_id, role)"
              + " VALUES (?::uuid, ?::uuid, 'MAINTAINER'::iam.layer_group_role)",
          layerGroupId1,
          userGroupId1);
      jdbc.update(
          "INSERT INTO iam.layer_group_user_group (layer_group_id, user_group_id, role)"
              + " VALUES (?::uuid, ?::uuid, 'MEMBER'::iam.layer_group_role)",
          layerGroupId2,
          userGroupId1);
      jdbc.update(
          "INSERT INTO iam.layer_group_user_group (layer_group_id, user_group_id, role)"
              + " VALUES (?::uuid, ?::uuid, 'ADMIN'::iam.layer_group_role)",
          layerGroupId3,
          userGroupId2);

      List<String> result =
          jdbc.queryForList(
              "SELECT layer_group_id::text FROM iam.layer_group_user_group"
                  + " WHERE user_group_id = ?::uuid",
              String.class,
              userGroupId1);

      assertThat(result).containsExactlyInAnyOrder(layerGroupId1, layerGroupId2);
      assertThat(result).doesNotContain(layerGroupId3);
    }
  }

  @Test
  void v20Migration_insertedRoleIsReadBackCorrectly() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      String layerGroupId = "00000000-0000-0000-0001-000000000010";
      String userGroupId = "00000000-0000-0000-0001-000000000020";

      jdbc.update(
          "INSERT INTO iam.layer_group (id, name) VALUES (?::uuid, ?)", layerGroupId, "LG-persist");
      jdbc.update(
          "INSERT INTO iam.user_group (id, name) VALUES (?::uuid, ?)", userGroupId, "UG-persist");

      jdbc.update(
          "INSERT INTO iam.layer_group_user_group (layer_group_id, user_group_id, role)"
              + " VALUES (?::uuid, ?::uuid, 'MAINTAINER'::iam.layer_group_role)",
          layerGroupId,
          userGroupId);

      String role =
          jdbc.queryForObject(
              "SELECT role::text FROM iam.layer_group_user_group"
                  + " WHERE layer_group_id = ?::uuid AND user_group_id = ?::uuid",
              String.class,
              layerGroupId,
              userGroupId);

      assertThat(role).isEqualTo("MAINTAINER");

      String assignedAt =
          jdbc.queryForObject(
              "SELECT assigned_at::text FROM iam.layer_group_user_group"
                  + " WHERE layer_group_id = ?::uuid AND user_group_id = ?::uuid",
              String.class,
              layerGroupId,
              userGroupId);

      assertThat(assignedAt).isNotNull();
    }
  }

  @Test
  void v20Migration_remapsEditorToMaintainerAndPreservesOtherValues() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateUpTo18(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      String lgId = "00000000-0000-0000-0000-000000000001";
      String ownerId = "00000000-0000-0000-0000-000000000010";
      String adminId = "00000000-0000-0000-0000-000000000011";
      String editorId = "00000000-0000-0000-0000-000000000012";
      String memberId = "00000000-0000-0000-0000-000000000013";

      jdbc.update("INSERT INTO iam.layer_group (id, name) VALUES (?::uuid, 'G1')", lgId);
      seedUser(jdbc, ownerId);
      seedUser(jdbc, adminId);
      seedUser(jdbc, editorId);
      seedUser(jdbc, memberId);

      jdbc.update(
          "INSERT INTO iam.layer_group_member (layer_group_id, user_id, role)"
              + " VALUES (?::uuid, ?::uuid, 'OWNER'::iam.layer_role)",
          lgId,
          ownerId);
      jdbc.update(
          "INSERT INTO iam.layer_group_member (layer_group_id, user_id, role)"
              + " VALUES (?::uuid, ?::uuid, 'ADMIN'::iam.layer_role)",
          lgId,
          adminId);
      jdbc.update(
          "INSERT INTO iam.layer_group_member (layer_group_id, user_id, role)"
              + " VALUES (?::uuid, ?::uuid, 'EDITOR'::iam.layer_role)",
          lgId,
          editorId);
      jdbc.update(
          "INSERT INTO iam.layer_group_member (layer_group_id, user_id, role)"
              + " VALUES (?::uuid, ?::uuid, 'MEMBER'::iam.layer_role)",
          lgId,
          memberId);

      migrateAll(dataSource);

      String columnType =
          jdbc.queryForObject(
              """
              SELECT data_type
              FROM information_schema.columns
              WHERE table_schema = 'iam'
                AND table_name   = 'layer_group_member'
                AND column_name  = 'role'
              """,
              String.class);
      assertThat(columnType).isEqualTo("USER-DEFINED");

      String udType =
          jdbc.queryForObject(
              """
              SELECT udt_name
              FROM information_schema.columns
              WHERE table_schema = 'iam'
                AND table_name   = 'layer_group_member'
                AND column_name  = 'role'
              """,
              String.class);
      assertThat(udType).isEqualTo("layer_group_role");

      Map<String, String> roleByUser = new LinkedHashMap<>();
      jdbc.query(
          "SELECT user_id::text, role::text FROM iam.layer_group_member WHERE layer_group_id = ?::uuid",
          rs -> {
            roleByUser.put(rs.getString("user_id"), rs.getString("role"));
          },
          lgId);

      assertThat(roleByUser.get(ownerId)).isEqualTo("OWNER");
      assertThat(roleByUser.get(adminId)).isEqualTo("ADMIN");
      assertThat(roleByUser.get(editorId)).isEqualTo("MAINTAINER");
      assertThat(roleByUser.get(memberId)).isEqualTo("MEMBER");
    }
  }

  @Test
  void v20Migration_unexpectedRoleValueRaisesException() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateUpTo18(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      String lgId = "00000000-0000-0000-0001-000000000001";
      String userId = "00000000-0000-0000-0001-000000000010";

      jdbc.update("INSERT INTO iam.layer_group (id, name) VALUES (?::uuid, 'G2')", lgId);
      seedUser(jdbc, userId);
      jdbc.update(
          "INSERT INTO iam.layer_group_member (layer_group_id, user_id, role)"
              + " VALUES (?::uuid, ?::uuid, 'OWNER'::iam.layer_role)",
          lgId,
          userId);

      jdbc.update("ALTER TYPE iam.layer_role ADD VALUE IF NOT EXISTS 'SURPRISE'");
      jdbc.update(
          "UPDATE iam.layer_group_member SET role = 'SURPRISE'::iam.layer_role"
              + " WHERE layer_group_id = ?::uuid AND user_id = ?::uuid",
          lgId,
          userId);

      assertThatThrownBy(() -> migrateAll(dataSource)).hasMessageContaining("SURPRISE");
    }
  }

  @Test
  void v20Migration_createsLayerGroupAccessScopeEnumType() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      List<String> enumValues =
          jdbc.queryForList(
              """
              SELECT e.enumlabel
              FROM pg_type t
              JOIN pg_namespace n ON t.typnamespace = n.oid
              JOIN pg_enum e ON e.enumtypid = t.oid
              WHERE n.nspname = 'iam' AND t.typname = 'layer_group_access_scope'
              ORDER BY e.enumsortorder
              """,
              String.class);

      assertThat(enumValues).containsExactly("PRIVATE", "GLOBAL");
    }
  }

  @Test
  void v20Migration_addsAccessScopeColumnWithPrivateDefault() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      List<Map<String, Object>> rows =
          jdbc.queryForList(
              """
              SELECT udt_name, is_nullable, column_default
              FROM information_schema.columns
              WHERE table_schema = 'iam'
                AND table_name = 'layer_group'
                AND column_name = 'access_scope'
              """);

      assertThat(rows).hasSize(1);
      var col = rows.get(0);
      assertThat(col.get("udt_name")).isEqualTo("layer_group_access_scope");
      assertThat(col.get("is_nullable")).isEqualTo("NO");
      assertThat(col.get("column_default")).isEqualTo("'PRIVATE'::iam.layer_group_access_scope");
    }
  }

  @Test
  void v20Migration_createsGlobalAccessScopePartialIndex() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      List<String> indexDefs =
          jdbc.queryForList(
              """
              SELECT indexdef
              FROM pg_indexes
              WHERE schemaname = 'iam'
                AND tablename = 'layer_group'
                AND indexname = 'idx_layer_group_global_access_scope'
              """,
              String.class);

      assertThat(indexDefs).hasSize(1);
      var indexDef = indexDefs.get(0);
      assertThat(indexDef).contains("WHERE");
      assertThat(indexDef).contains("access_scope");
      assertThat(indexDef).contains("'GLOBAL'");
    }
  }

  @Test
  void v20Migration_accessScopeDefaultsToPrivateForNewRows() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      String layerGroupId = "00000000-0000-0000-0002-000000000001";
      jdbc.update(
          "INSERT INTO iam.layer_group (id, name) VALUES (?::uuid, ?)",
          layerGroupId,
          "DefaultScopeGroup");

      String scope =
          jdbc.queryForObject(
              "SELECT access_scope::text FROM iam.layer_group WHERE id = ?::uuid",
              String.class,
              layerGroupId);

      assertThat(scope).isEqualTo("PRIVATE");

      // Positive-control: GLOBAL value is also accepted and round-trips correctly.
      String globalGroupId = "00000000-0000-0000-0002-000000000002";
      jdbc.update(
          "INSERT INTO iam.layer_group (id, name, access_scope)"
              + " VALUES (?::uuid, ?, 'GLOBAL'::iam.layer_group_access_scope)",
          globalGroupId,
          "GlobalScopeGroup");

      String globalScope =
          jdbc.queryForObject(
              "SELECT access_scope::text FROM iam.layer_group WHERE id = ?::uuid",
              String.class,
              globalGroupId);

      assertThat(globalScope).isEqualTo("GLOBAL");
    }
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private static void seedUser(JdbcTemplate jdbc, String userId) {
    jdbc.update(
        """
        INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer,
                                  username, email, first_name, last_name, display_name)
        VALUES (?::uuid, ?, ?, 'https://test.issuer', ?, ?, 'F', 'L', 'FL')
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "user-" + userId,
        "u" + userId + "@test");
  }

  private static void migrateUpTo18(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .target("18")
        .load()
        .migrate();
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
}
