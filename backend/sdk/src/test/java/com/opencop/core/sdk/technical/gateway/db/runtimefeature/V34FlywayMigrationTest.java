// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.runtimefeature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class V34FlywayMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void v34MigrationSupportsUserGroupsAndEnforcesItsCrossTableContract() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var targetId = UUID.fromString("00000000-0000-4000-8000-000000000201");

      executeAndCommit(
          dataSource,
          "INSERT INTO config.runtime_feature_audience_user_group"
              + " (feature_name, user_group_id) VALUES ('watchbox', '"
              + targetId
              + "')",
          "UPDATE config.runtime_feature SET audience_mode = 'USER_GROUPS'"
              + " WHERE name = 'watchbox'");

      assertThat(
              jdbc.queryForObject(
                  "SELECT audience_mode FROM config.runtime_feature WHERE name = 'watchbox'",
                  String.class))
          .isEqualTo("USER_GROUPS");
      assertThat(
              jdbc.queryForObject(
                  "SELECT user_group_id FROM config.runtime_feature_audience_user_group"
                      + " WHERE feature_name = 'watchbox'",
                  UUID.class))
          .isEqualTo(targetId);

      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "UPDATE config.runtime_feature SET audience_mode = 'LAYERS'"
                          + " WHERE name = 'rtsp'"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO config.runtime_feature_audience_user_group"
                          + " (feature_name, user_group_id) VALUES ('watchbox', ?)",
                      targetId))
          .isInstanceOf(DataIntegrityViolationException.class);

      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM pg_constraint constraint_row"
                      + " JOIN pg_class source_table"
                      + "   ON source_table.oid = constraint_row.conrelid"
                      + " JOIN pg_namespace source_schema"
                      + "   ON source_schema.oid = source_table.relnamespace"
                      + " JOIN pg_class referenced_table"
                      + "   ON referenced_table.oid = constraint_row.confrelid"
                      + " JOIN pg_namespace referenced_schema"
                      + "   ON referenced_schema.oid = referenced_table.relnamespace"
                      + " WHERE constraint_row.contype = 'f'"
                      + "   AND source_schema.nspname = 'config'"
                      + "   AND source_table.relname = 'runtime_feature_audience_user_group'"
                      + "   AND referenced_schema.nspname = 'iam'"
                      + "   AND referenced_table.relname = 'user_group'",
                  Long.class))
          .isZero();

      jdbc.update(
          "INSERT INTO config.runtime_feature"
              + " (name, consumer_class, audience_mode)"
              + " VALUES ('cascade-proof', 'BACKEND_ENFORCED', 'ALL')");
      executeAndCommit(
          dataSource,
          "INSERT INTO config.runtime_feature_audience_user_group"
              + " (feature_name, user_group_id) VALUES ('cascade-proof', '"
              + UUID.fromString("00000000-0000-4000-8000-000000000202")
              + "')",
          "UPDATE config.runtime_feature SET audience_mode = 'USER_GROUPS'"
              + " WHERE name = 'cascade-proof'");
      jdbc.update("DELETE FROM config.runtime_feature WHERE name = 'cascade-proof'");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM config.runtime_feature_audience_user_group"
                      + " WHERE feature_name = 'cascade-proof'",
                  Long.class))
          .isZero();

      assertThatThrownBy(
              () ->
                  executeAndCommit(
                      dataSource,
                      "UPDATE config.runtime_feature SET audience_mode = 'USER_GROUPS'"
                          + " WHERE name = 'rtsp'"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("runtime feature rtsp has USER_GROUPS audience but no targets");
      assertThatThrownBy(
              () ->
                  executeAndCommit(
                      dataSource,
                      "INSERT INTO config.runtime_feature_audience_user_group"
                          + " (feature_name, user_group_id) VALUES ('rtsp', '"
                          + UUID.fromString("00000000-0000-4000-8000-000000000203")
                          + "')"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("runtime feature rtsp has audience targets but mode ALL");
    }
  }

  @Test
  void v34MigrationRejectsMovingTargetsBetweenFeatures() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var sourceTarget = UUID.fromString("00000000-0000-4000-8000-000000000204");
      var destinationTarget = UUID.fromString("00000000-0000-4000-8000-000000000205");

      jdbc.update(
          "INSERT INTO config.runtime_feature (name, consumer_class, audience_mode)"
              + " VALUES ('source-feature', 'BACKEND_ENFORCED', 'ALL'),"
              + " ('destination-feature', 'BACKEND_ENFORCED', 'ALL')");
      executeAndCommit(
          dataSource,
          "INSERT INTO config.runtime_feature_audience_user_group"
              + " (feature_name, user_group_id) VALUES ('source-feature', '"
              + sourceTarget
              + "'), ('destination-feature', '"
              + destinationTarget
              + "')",
          "UPDATE config.runtime_feature SET audience_mode = 'USER_GROUPS'"
              + " WHERE name IN ('source-feature', 'destination-feature')");

      assertThatThrownBy(
              () ->
                  executeAndCommit(
                      dataSource,
                      "UPDATE config.runtime_feature_audience_user_group"
                          + " SET feature_name = 'destination-feature'"
                          + " WHERE feature_name = 'source-feature'"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("runtime feature audience targets cannot move between features");

      assertThat(
              jdbc.queryForList(
                  "SELECT feature_name FROM config.runtime_feature_audience_user_group"
                      + " ORDER BY feature_name",
                  String.class))
          .containsExactly("destination-feature", "source-feature");
    }
  }

  @Test
  void v34MigrationSerializesConcurrentModeAndTargetChecks() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var oldTarget = UUID.fromString("00000000-0000-4000-8000-000000000206");
      var newTarget = UUID.fromString("00000000-0000-4000-8000-000000000207");

      executeAndCommit(
          dataSource,
          "INSERT INTO config.runtime_feature_audience_user_group"
              + " (feature_name, user_group_id) VALUES ('watchbox', '"
              + oldTarget
              + "')",
          "UPDATE config.runtime_feature SET audience_mode = 'USER_GROUPS'"
              + " WHERE name = 'watchbox'");

      try (var parentWriter = dataSource.getConnection();
          var childWriter = dataSource.getConnection();
          var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        parentWriter.setAutoCommit(false);
        childWriter.setAutoCommit(false);
        execute(
            parentWriter,
            "UPDATE config.runtime_feature SET audience_mode = 'NONE'"
                + " WHERE name = 'watchbox'");
        execute(
            parentWriter,
            "DELETE FROM config.runtime_feature_audience_user_group"
                + " WHERE feature_name = 'watchbox'");
        execute(
            childWriter,
            "INSERT INTO config.runtime_feature_audience_user_group"
                + " (feature_name, user_group_id) VALUES ('watchbox', '"
                + newTarget
                + "')");

        var childBackendPid = queryForInt(childWriter, "SELECT pg_backend_pid()");
        var childCheckStarted = new CountDownLatch(1);
        var childConstraintCheck =
            executor.submit(
                () -> {
                  childCheckStarted.countDown();
                  execute(childWriter, "SET CONSTRAINTS ALL IMMEDIATE");
                  return null;
                });
        assertThat(childCheckStarted.await(10, TimeUnit.SECONDS)).isTrue();

        try {
          assertThat(waitUntilBlocked(jdbc, childBackendPid, childConstraintCheck)).isTrue();
        } finally {
          try {
            parentWriter.commit();
          } catch (SQLException exception) {
            parentWriter.rollback();
            throw exception;
          }
        }

        try {
          childConstraintCheck.get(10, TimeUnit.SECONDS);
          throw new AssertionError("Concurrent target check unexpectedly succeeded");
        } catch (java.util.concurrent.ExecutionException exception) {
          assertThat(exception.getCause())
              .isInstanceOf(SQLException.class)
              .hasMessageContaining("runtime feature watchbox has audience targets but mode NONE");
        } finally {
          childWriter.rollback();
        }
      }

      assertThat(
              jdbc.queryForObject(
                  "SELECT audience_mode FROM config.runtime_feature WHERE name = 'watchbox'",
                  String.class))
          .isEqualTo("NONE");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM config.runtime_feature_audience_user_group"
                      + " WHERE feature_name = 'watchbox'",
                  Long.class))
          .isZero();
    }
  }

  private static boolean waitUntilBlocked(JdbcTemplate jdbc, int backendPid, Future<?> operation)
      throws InterruptedException {
    var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!operation.isDone() && System.nanoTime() < deadline) {
      var blockerCount =
          jdbc.queryForObject("SELECT cardinality(pg_blocking_pids(?))", Integer.class, backendPid);
      if (blockerCount != null && blockerCount > 0) {
        return true;
      }
      Thread.sleep(10);
    }
    return false;
  }

  private static int queryForInt(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement();
        var result = statement.executeQuery(sql)) {
      result.next();
      return result.getInt(1);
    }
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.executeUpdate(sql);
    }
  }

  private static void executeAndCommit(DataSource dataSource, String... statements)
      throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement()) {
        for (var sql : statements) {
          statement.executeUpdate(sql);
        }
        connection.commit();
      } catch (SQLException e) {
        connection.rollback();
        throw e;
      }
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
}
