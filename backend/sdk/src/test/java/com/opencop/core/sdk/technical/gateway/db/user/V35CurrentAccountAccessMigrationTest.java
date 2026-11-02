// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class V35CurrentAccountAccessMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void migration_backfillsExistingUsersAndKeepsZeroDefaultsForNewUsers() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToVersion(dataSource, "33");
      var jdbc = new JdbcTemplate(dataSource);
      UUID existingUserId = UUID.fromString("a9acbb51-52ea-4a8e-8b14-44a5b11ba900");
      insertUser(jdbc, existingUserId);

      migrateAll(dataSource);

      assertThat(loadAccountVersions(jdbc, existingUserId)).containsExactly(0L, 0L);
      assertColumnIsRequiredWithZeroDefault(jdbc, "session_generation");
      assertColumnIsRequiredWithZeroDefault(jdbc, "account_access_revision");

      UUID newUserId = UUID.fromString("f36e69c2-a578-45dd-b3e9-49fdb7090d60");
      insertUser(jdbc, newUserId);
      assertThat(loadAccountVersions(jdbc, newUserId)).containsExactly(0L, 0L);
    }
  }

  private static void insertUser(JdbcTemplate jdbc, UUID userId) {
    jdbc.update(
        """
        INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer, username)
        VALUES (?, ?, ?, ?, ?)
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "https://issuer.test",
        "user-" + userId);
  }

  private static java.util.List<Long> loadAccountVersions(JdbcTemplate jdbc, UUID userId) {
    return jdbc.queryForObject(
        """
        SELECT session_generation, account_access_revision
        FROM opencop_user
        WHERE id = ?
        """,
        (resultSet, rowNumber) -> java.util.List.of(resultSet.getLong(1), resultSet.getLong(2)),
        userId);
  }

  private static void assertColumnIsRequiredWithZeroDefault(JdbcTemplate jdbc, String columnName) {
    var metadata =
        jdbc.queryForMap(
            """
            SELECT is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'opencop_user'
              AND column_name = ?
            """,
            columnName);
    assertThat(metadata.get("is_nullable")).isEqualTo("NO");
    assertThat(metadata.get("column_default").toString()).startsWith("0");
  }

  private static void migrateToVersion(DataSource dataSource, String targetVersion) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .target(targetVersion)
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
