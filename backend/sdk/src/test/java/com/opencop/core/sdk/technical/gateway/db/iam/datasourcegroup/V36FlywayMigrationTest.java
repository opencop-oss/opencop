// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class V36FlywayMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void v36Migration_isCollapsedIntoFinalSchemaMigration() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      List<String> appliedScripts =
          jdbc.queryForList(
              """
              SELECT script
              FROM flyway_schema_history
              WHERE version = '36'
              ORDER BY version
              """,
              String.class);

      assertThat(appliedScripts)
          .containsExactly("V36__data_source_groups_and_effective_access.sql");
    }
  }

  @Test
  void v36Migration_createsDataSourceGroupRoleEnumType() throws Exception {
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
              WHERE n.nspname = 'iam' AND t.typname = 'data_source_group_role'
              ORDER BY e.enumsortorder
              """,
              String.class);

      assertThat(enumValues).containsExactly("OWNER", "ADMIN", "MAINTAINER", "MEMBER");
    }
  }

  @Test
  void v36Migration_createsDataSourceGroupTables() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      List<String> tableNames =
          jdbc.queryForList(
              """
              SELECT table_name
              FROM information_schema.tables
              WHERE table_schema = 'iam'
                AND table_name IN (
                  'data_source_group',
                  'data_source_group_member',
                  'data_source_group_data_source',
                  'data_source_group_user_group',
                  'layer_data_source_group'
                )
              ORDER BY table_name
              """,
              String.class);

      assertThat(tableNames)
          .containsExactly(
              "data_source_group",
              "data_source_group_data_source",
              "data_source_group_member",
              "data_source_group_user_group",
              "layer_data_source_group");
    }
  }

  @Test
  void v36Migration_createsDataSourceGroupIndexes() throws Exception {
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
                AND indexname IN (
                  'idx_data_source_group_member_user_id',
                  'idx_data_source_group_data_source_data_source_id',
                  'idx_data_source_group_data_source_group_id',
                  'idx_data_source_group_user_group_user_group_id',
                  'idx_layer_data_source_group_layer_id',
                  'idx_layer_data_source_group_group_id'
                )
              ORDER BY indexname
              """,
              String.class);

      assertThat(indexes)
          .containsExactly(
              "idx_data_source_group_data_source_data_source_id",
              "idx_data_source_group_data_source_group_id",
              "idx_data_source_group_member_user_id",
              "idx_data_source_group_user_group_user_group_id",
              "idx_layer_data_source_group_group_id",
              "idx_layer_data_source_group_layer_id");
    }
  }

  @Test
  void v36Migration_createsDataSourceGroupConstraints() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      List<String> constraints =
          jdbc.queryForList(
              """
              SELECT tc.table_name || ':' || tc.constraint_type || ':' || kcu.column_name
              FROM information_schema.table_constraints tc
              JOIN information_schema.key_column_usage kcu
                ON tc.constraint_schema = kcu.constraint_schema
               AND tc.constraint_name = kcu.constraint_name
               AND tc.table_name = kcu.table_name
              WHERE tc.table_schema = 'iam'
                AND tc.table_name IN (
                  'data_source_group',
                  'data_source_group_member',
                  'data_source_group_data_source',
                  'data_source_group_user_group',
                  'layer_data_source_group'
                )
                AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE', 'FOREIGN KEY')
              ORDER BY tc.table_name, tc.constraint_type, kcu.column_name
              """,
              String.class);

      assertThat(constraints)
          .contains(
              "data_source_group:PRIMARY KEY:id",
              "data_source_group:UNIQUE:name",
              "data_source_group_member:PRIMARY KEY:data_source_group_id",
              "data_source_group_member:PRIMARY KEY:user_id",
              "data_source_group_member:FOREIGN KEY:data_source_group_id",
              "data_source_group_member:FOREIGN KEY:user_id",
              "data_source_group_data_source:PRIMARY KEY:data_source_group_id",
              "data_source_group_data_source:PRIMARY KEY:data_source_id",
              "data_source_group_data_source:FOREIGN KEY:data_source_group_id",
              "data_source_group_data_source:FOREIGN KEY:data_source_id",
              "data_source_group_user_group:PRIMARY KEY:data_source_group_id",
              "data_source_group_user_group:PRIMARY KEY:user_group_id",
              "data_source_group_user_group:FOREIGN KEY:data_source_group_id",
              "data_source_group_user_group:FOREIGN KEY:user_group_id",
              "layer_data_source_group:PRIMARY KEY:layer_id",
              "layer_data_source_group:PRIMARY KEY:data_source_group_id",
              "layer_data_source_group:FOREIGN KEY:layer_id",
              "layer_data_source_group:FOREIGN KEY:data_source_group_id");
    }
  }

  @Test
  void v36Migration_allowsPersistingDirectMemberPath() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var userId = UUID.randomUUID();
      var sourceId = UUID.randomUUID();
      var groupId = UUID.randomUUID();
      seedUser(jdbc, userId);
      seedSource(jdbc, sourceId);

      jdbc.update("INSERT INTO iam.data_source_group(id, name) VALUES (?::uuid, ?)", groupId, "G1");
      jdbc.update(
          """
          INSERT INTO iam.data_source_group_member(data_source_group_id, user_id, role)
          VALUES (?::uuid, ?::uuid, 'OWNER'::iam.data_source_group_role)
          """,
          groupId,
          userId);
      jdbc.update(
          """
          INSERT INTO iam.data_source_group_data_source(data_source_group_id, data_source_id, role)
          VALUES (?::uuid, ?::uuid, 'ADMIN'::iam.server_role)
          """,
          groupId,
          sourceId);

      String role =
          jdbc.queryForObject(
              """
              SELECT role::text
              FROM iam.data_source_group_data_source
              WHERE data_source_group_id = ?::uuid AND data_source_id = ?::uuid
              """,
              String.class,
              groupId,
              sourceId);

      assertThat(role).isEqualTo("ADMIN");
    }
  }

  @Test
  void v36Migration_allowsPersistingUserGroupAndLayerEmbeddingPaths() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var groupId = UUID.randomUUID();
      var userGroupId = UUID.randomUUID();
      var layerId = UUID.randomUUID();
      seedUserGroup(jdbc, userGroupId);
      seedLayer(jdbc, layerId);

      jdbc.update("INSERT INTO iam.data_source_group(id, name) VALUES (?::uuid, ?)", groupId, "G1");
      jdbc.update(
          """
          INSERT INTO iam.data_source_group_user_group(data_source_group_id, user_group_id, role)
          VALUES (?::uuid, ?::uuid, 'OWNER'::iam.data_source_group_role)
          """,
          groupId,
          userGroupId);
      jdbc.update(
          """
          INSERT INTO iam.layer_data_source_group(layer_id, data_source_group_id)
          VALUES (?::uuid, ?::uuid)
          """,
          layerId,
          groupId);

      String role =
          jdbc.queryForObject(
              """
              SELECT role::text
              FROM iam.data_source_group_user_group
              WHERE data_source_group_id = ?::uuid AND user_group_id = ?::uuid
              """,
              String.class,
              groupId,
              userGroupId);
      Integer layerEmbeddingRows =
          jdbc.queryForObject(
              """
              SELECT count(*)
              FROM iam.layer_data_source_group
              WHERE layer_id = ?::uuid AND data_source_group_id = ?::uuid
              """,
              Integer.class,
              layerId,
              groupId);

      assertThat(role).isEqualTo("OWNER");
      assertThat(layerEmbeddingRows).isOne();
    }
  }

  @Test
  void v36Migration_cascadesDirectMemberPathRowsWhenGroupIsDeleted() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var userId = UUID.randomUUID();
      var sourceId = UUID.randomUUID();
      var groupId = UUID.randomUUID();
      seedUser(jdbc, userId);
      seedSource(jdbc, sourceId);
      jdbc.update("INSERT INTO iam.data_source_group(id, name) VALUES (?::uuid, ?)", groupId, "G1");
      jdbc.update(
          """
          INSERT INTO iam.data_source_group_member(data_source_group_id, user_id, role)
          VALUES (?::uuid, ?::uuid, 'OWNER'::iam.data_source_group_role)
          """,
          groupId,
          userId);
      jdbc.update(
          """
          INSERT INTO iam.data_source_group_data_source(data_source_group_id, data_source_id)
          VALUES (?::uuid, ?::uuid)
          """,
          groupId,
          sourceId);

      jdbc.update("DELETE FROM iam.data_source_group WHERE id = ?::uuid", groupId);

      Integer memberRows =
          jdbc.queryForObject(
              "SELECT count(*) FROM iam.data_source_group_member WHERE data_source_group_id = ?::uuid",
              Integer.class,
              groupId);
      Integer sourceRows =
          jdbc.queryForObject(
              "SELECT count(*) FROM iam.data_source_group_data_source WHERE data_source_group_id = ?::uuid",
              Integer.class,
              groupId);

      assertThat(memberRows).isZero();
      assertThat(sourceRows).isZero();
    }
  }

  @Test
  void v36Migration_cascadesUserGroupAndLayerEmbeddingRowsWhenGroupIsDeleted() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var groupId = UUID.randomUUID();
      var userGroupId = UUID.randomUUID();
      var layerId = UUID.randomUUID();
      seedUserGroup(jdbc, userGroupId);
      seedLayer(jdbc, layerId);
      jdbc.update("INSERT INTO iam.data_source_group(id, name) VALUES (?::uuid, ?)", groupId, "G1");
      jdbc.update(
          """
          INSERT INTO iam.data_source_group_user_group(data_source_group_id, user_group_id, role)
          VALUES (?::uuid, ?::uuid, 'OWNER'::iam.data_source_group_role)
          """,
          groupId,
          userGroupId);
      jdbc.update(
          """
          INSERT INTO iam.layer_data_source_group(layer_id, data_source_group_id)
          VALUES (?::uuid, ?::uuid)
          """,
          layerId,
          groupId);

      jdbc.update("DELETE FROM iam.data_source_group WHERE id = ?::uuid", groupId);

      Integer userGroupRows =
          jdbc.queryForObject(
              "SELECT count(*) FROM iam.data_source_group_user_group WHERE data_source_group_id = ?::uuid",
              Integer.class,
              groupId);
      Integer layerEmbeddingRows =
          jdbc.queryForObject(
              "SELECT count(*) FROM iam.layer_data_source_group WHERE data_source_group_id = ?::uuid",
              Integer.class,
              groupId);

      assertThat(userGroupRows).isZero();
      assertThat(layerEmbeddingRows).isZero();
    }
  }

  private static void seedSource(JdbcTemplate jdbc, UUID sourceId) {
    var mapperId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO profiles.mapper(id, qualifier)
        VALUES (?::uuid, 'INGRESS'::profiles.mapper_qualifier)
        ON CONFLICT (id) DO NOTHING
        """,
        mapperId);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper(id, version, name, mapper)
        VALUES (?::uuid, 1, ?, '{}'::jsonb)
        """,
        mapperId,
        "mapper-" + mapperId);
    NativeIntegrationFixture.seed(jdbc);
    jdbc.update(
        """
        INSERT INTO profiles.data_source(
          id, name, tenant, provider, access, ingress_mapper_id,
          connection_spec, integration_id, connection_schema_version
        )
        VALUES (?::uuid, ?, 'default', 'TCP'::profiles.transport_kind, 'R'::profiles.access_mode,
                ?::uuid, '{}'::jsonb, ?, ?)
        """,
        sourceId,
        "source-" + sourceId,
        mapperId,
        NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
        NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion());
  }

  private static void seedUser(JdbcTemplate jdbc, UUID userId) {
    jdbc.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)
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

  private static void seedUserGroup(JdbcTemplate jdbc, UUID userGroupId) {
    jdbc.update(
        "INSERT INTO iam.user_group(id, name) VALUES (?::uuid, ?)",
        userGroupId,
        "group-" + userGroupId);
  }

  private static void seedLayer(JdbcTemplate jdbc, UUID layerId) {
    jdbc.update("INSERT INTO iam.layer(id, name) VALUES (?::uuid, ?)", layerId, "layer-" + layerId);
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
