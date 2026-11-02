// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class V22ScimGroupIdentityMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();
  private static final String GROUP_ID = "00000000-0000-0000-0000-000000000101";
  private static final String OTHER_GROUP_ID = "00000000-0000-0000-0000-000000000102";
  private static final String ISSUER = "issuer-a";
  private static final String EXTERNAL_GROUP_ID = "external-group-a";

  @Test
  void migratedUserGroup_hasNullableScimIdentityColumns() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      List<ColumnMetadata> columns =
          jdbc.query(
              """
              SELECT column_name, is_nullable
              FROM information_schema.columns
              WHERE table_schema = 'iam'
                AND table_name = 'user_group'
                AND column_name IN ('idp_issuer', 'idp_group_id')
              ORDER BY column_name
              """,
              (rs, rowNum) ->
                  new ColumnMetadata(rs.getString("column_name"), rs.getString("is_nullable")));

      assertThat(columns)
          .containsExactly(
              new ColumnMetadata("idp_group_id", "YES"), new ColumnMetadata("idp_issuer", "YES"));
    }
  }

  @Test
  void migratedUserGroup_rejectsDuplicateScimIdentityForSameIssuer() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      insertScimGroup(jdbc, GROUP_ID, "Operators", ISSUER, EXTERNAL_GROUP_ID);

      assertThatThrownBy(
              () ->
                  insertScimGroup(
                      jdbc, OTHER_GROUP_ID, "Operators Copy", ISSUER, EXTERNAL_GROUP_ID))
          .hasMessageContaining("uk_user_group_idp_issuer_group_id");
    }
  }

  @Test
  void migratedUserGroup_allowsAdminCreatedGroupsWithoutScimIdentity() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      insertAdminGroup(jdbc, GROUP_ID, "Local operators");
      insertAdminGroup(jdbc, OTHER_GROUP_ID, "Local analysts");

      Integer groupCount =
          jdbc.queryForObject(
              """
              SELECT COUNT(*)
              FROM iam.user_group
              WHERE idp_issuer IS NULL
                AND idp_group_id IS NULL
              """,
              Integer.class);

      assertThat(groupCount).isEqualTo(2);
    }
  }

  @Test
  void migratedUserGroup_allowsSameExternalGroupIdForDifferentIssuers() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      insertScimGroup(jdbc, GROUP_ID, "Issuer A operators", ISSUER, EXTERNAL_GROUP_ID);
      insertScimGroup(jdbc, OTHER_GROUP_ID, "Issuer B operators", "issuer-b", EXTERNAL_GROUP_ID);

      List<String> issuers =
          jdbc.queryForList(
              """
              SELECT idp_issuer
              FROM iam.user_group
              WHERE idp_group_id = ?
              ORDER BY idp_issuer
              """,
              String.class,
              EXTERNAL_GROUP_ID);

      assertThat(issuers).containsExactly(ISSUER, "issuer-b");
    }
  }

  @Test
  void migratedUserGroup_rejectsDuplicateNameAmongAdminGroups() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      insertAdminGroup(jdbc, GROUP_ID, "Operators");

      assertThatThrownBy(() -> insertAdminGroup(jdbc, OTHER_GROUP_ID, "Operators"))
          .hasMessageContaining("uk_user_group_admin_name");
    }
  }

  @Test
  void migratedUserGroup_rejectsDuplicateNameWithinSameIssuer() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      insertScimGroup(jdbc, GROUP_ID, "Operators", ISSUER, EXTERNAL_GROUP_ID);

      assertThatThrownBy(
              () -> insertScimGroup(jdbc, OTHER_GROUP_ID, "Operators", ISSUER, "ext-other"))
          .hasMessageContaining("uk_user_group_issuer_name");
    }
  }

  @Test
  void migratedUserGroup_allowsSameNameAcrossIssuersAndAdmin() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      insertAdminGroup(jdbc, GROUP_ID, "Operators");
      insertScimGroup(jdbc, OTHER_GROUP_ID, "Operators", ISSUER, EXTERNAL_GROUP_ID);
      insertScimGroup(
          jdbc, "00000000-0000-0000-0000-000000000103", "Operators", "issuer-b", "ext-other");

      Integer count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM iam.user_group WHERE name = ?", Integer.class, "Operators");
      assertThat(count).isEqualTo(3);
    }
  }

  private static void insertScimGroup(
      JdbcTemplate jdbc, String id, String name, String issuer, String externalGroupId) {
    jdbc.update(
        """
        INSERT INTO iam.user_group (id, name, idp_issuer, idp_group_id)
        VALUES (?::uuid, ?, ?, ?)
        """,
        id,
        name,
        issuer,
        externalGroupId);
  }

  private static void insertAdminGroup(JdbcTemplate jdbc, String id, String name) {
    jdbc.update("INSERT INTO iam.user_group (id, name) VALUES (?::uuid, ?)", id, name);
  }

  private static void migrateAll(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();
  }

  private record ColumnMetadata(String name, String nullable) {}
}
