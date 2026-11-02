// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.watchbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class V42FlywayMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  private static final UUID WATCHBOX_ID = UUID.fromString("00000000-0000-0000-0000-000000000b42");
  private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-00000000a042");
  private static final UUID LEGACY_ACTOR_ID =
      UUID.fromString("00000000-0000-0000-0000-00000000a142");
  private static final UUID PREVIEW_LAYER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000001b42");

  @Test
  void v42Migration_whenApplied_acceptsCreatePatchAndArchiveShapes() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToVersion(dataSource, "41");
      var jdbc = new JdbcTemplate(dataSource);
      seedUnsupportedPreviewWatchbox(jdbc);
      migrateAll(dataSource);
      assertUnsupportedPreviewWatchboxReset(jdbc);
      seedParents(jdbc);

      insertAudit(
          jdbc, "CREATE", null, snapshot(WATCHBOX_ID, 1), "[]", null, 1L, WATCHBOX_ID, ACTOR_ID);
      insertAudit(
          jdbc,
          "PATCH",
          snapshot(WATCHBOX_ID, 1),
          snapshot(WATCHBOX_ID, 2),
          "[\"POLYGON_CHANGED\"]",
          1L,
          2L,
          WATCHBOX_ID,
          ACTOR_ID);
      insertAudit(
          jdbc, "ARCHIVE", snapshot(WATCHBOX_ID, 2), null, "[]", 2L, null, WATCHBOX_ID, ACTOR_ID);

      assertThat(
              jdbc.queryForList(
                  """
                  SELECT operation
                    || ':' || COALESCE(config_epoch_before::text, '-')
                    || ':' || COALESCE(config_epoch_after::text, '-')
                  FROM watchbox.watchbox_config_audit
                  ORDER BY id
                  """,
                  String.class))
          .containsExactly("CREATE:-:1", "PATCH:1:2", "ARCHIVE:2:-");

      assertAuditIndexes(jdbc);
    }
  }

  @Test
  void v42Migration_reshapesRuntimeFeatureHistoryWithoutChangingLiveConfiguration()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToVersion(dataSource, "41");
      var jdbc = new JdbcTemplate(dataSource);
      seedLegacyRuntimeHistory(jdbc);
      seedRuntimeFeatureConfiguration(dataSource);
      var liveBefore = runtimeFeatureConfiguration(jdbc);

      migrateAll(dataSource);

      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM config.runtime_feature_history", Long.class))
          .isZero();
      assertThat(runtimeFeatureConfiguration(jdbc)).isEqualTo(liveBefore);

      insertRuntimeAudit(
          jdbc,
          "rtsp",
          "FRONTEND_ONLY",
          "CREATE",
          null,
          "{\"mode\":\"NONE\"}",
          null,
          0L,
          "2026-08-20T10:00:00Z",
          LEGACY_ACTOR_ID);
      insertRuntimeAudit(
          jdbc,
          "watchbox",
          "BACKEND_ENFORCED",
          "PATCH",
          "{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\"00000000-0000-4000-8000-000000000042\"]}",
          "{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\"00000000-0000-4000-8000-000000000042\"]}",
          7L,
          7L,
          "2026-08-20T11:00:00Z",
          LEGACY_ACTOR_ID);
      insertRuntimeAudit(
          jdbc,
          "watchbox",
          "BACKEND_ENFORCED",
          "PATCH",
          "{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\"00000000-0000-4000-8000-000000000042\"]}",
          "{\"mode\":\"ALL\"}",
          7L,
          8L,
          "2026-08-20T12:00:00Z",
          LEGACY_ACTOR_ID);

      assertThat(
              jdbc.queryForList(
                  """
                  SELECT id || ':' || operation
                      || ':' || COALESCE(revision_before::text, '-')
                      || ':' || revision_after
                    FROM config.runtime_feature_history
                   ORDER BY id
                  """,
                  String.class))
          .containsExactly("1:CREATE:-:0", "2:PATCH:7:7", "3:PATCH:7:8");

      assertConstraintViolation(
          () ->
              insertRuntimeAudit(
                  jdbc,
                  "rtsp",
                  "BACKEND_ENFORCED",
                  "CREATE",
                  null,
                  "{\"mode\":\"NONE\"}",
                  null,
                  0L,
                  "2026-08-20T13:00:00Z",
                  LEGACY_ACTOR_ID),
          "runtime_feature_history_action_shape");
      assertConstraintViolation(
          () ->
              insertRuntimeAudit(
                  jdbc,
                  "watchbox",
                  "BACKEND_ENFORCED",
                  "PATCH",
                  "{\"mode\":\"NONE\"}",
                  "{\"mode\":\"NONE\"}",
                  8L,
                  9L,
                  "2026-08-20T13:01:00Z",
                  LEGACY_ACTOR_ID),
          "runtime_feature_history_action_shape");
      assertConstraintViolation(
          () ->
              insertRuntimeAudit(
                  jdbc,
                  "watchbox",
                  "BACKEND_ENFORCED",
                  "PATCH",
                  "{\"mode\":\"NONE\"}",
                  "{\"mode\":\"ALL\",\"extra\":true}",
                  8L,
                  9L,
                  "2026-08-20T13:02:00Z",
                  LEGACY_ACTOR_ID),
          "runtime_feature_history_after_audience_shape");
      assertConstraintViolation(
          () ->
              insertRuntimeAudit(
                  jdbc,
                  "watchbox",
                  "BACKEND_ENFORCED",
                  "DELETE",
                  "{\"mode\":\"NONE\"}",
                  "{\"mode\":\"ALL\"}",
                  8L,
                  9L,
                  "2026-08-20T13:03:00Z",
                  LEGACY_ACTOR_ID),
          "runtime_feature_history_");
      assertConstraintViolation(
          () ->
              insertRuntimeAudit(
                  jdbc,
                  "watchbox",
                  "BACKEND_ENFORCED",
                  "PATCH",
                  "{\"mode\":\"NONE\"}",
                  "{\"mode\":\"ALL\"}",
                  8L,
                  9L,
                  "2026-08-20T13:04:00Z",
                  UUID.fromString("00000000-0000-0000-0000-00000000f242")),
          "runtime_feature_history_actor_user_id_fkey");

      assertRuntimeFeatureAuditIndexes(jdbc);
    }
  }

  @Test
  void v42Migration_enforcesLiveAndSnapshotConfigurationBounds() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      seedParents(jdbc);

      jdbc.update(
          "UPDATE watchbox.watchbox SET name = ?, description = ? WHERE id = ?",
          "n".repeat(255),
          "d".repeat(4000),
          WATCHBOX_ID);
      assertConstraintViolation(
          () ->
              jdbc.update(
                  "UPDATE watchbox.watchbox SET name = ? WHERE id = ?",
                  "n".repeat(256),
                  WATCHBOX_ID),
          "watchbox_name_maximum");
      assertConstraintViolation(
          () ->
              jdbc.update(
                  "UPDATE watchbox.watchbox SET description = ? WHERE id = ?",
                  "d".repeat(4001),
                  WATCHBOX_ID),
          "watchbox_description_maximum");

      for (int index = 1; index <= 100; index++) {
        UUID layerId = UUID.fromString("00000000-0000-0000-0001-%012d".formatted(index));
        jdbc.update(
            "INSERT INTO iam.layer (id, name) VALUES (?, ?)", layerId, "v42-layer-" + index);
        jdbc.update(
            "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)",
            WATCHBOX_ID,
            layerId);
      }
      UUID extraLayerId = UUID.fromString("00000000-0000-0000-0001-000000000101");
      jdbc.update("INSERT INTO iam.layer (id, name) VALUES (?, 'v42-layer-101')", extraLayerId);
      assertConstraintViolation(
          () ->
              jdbc.update(
                  "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)",
                  WATCHBOX_ID,
                  extraLayerId),
          "watchbox_layer_maximum");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM watchbox.watchbox_layer WHERE watchbox_id = ?",
                  Long.class,
                  WATCHBOX_ID))
          .isEqualTo(100L);
      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT pg_get_triggerdef(oid)
                    FROM pg_trigger
                   WHERE tgname = 'watchbox_layer_maximum'
                     AND NOT tgisinternal
                  """,
                  String.class))
          .contains("BEFORE INSERT OR UPDATE OF watchbox_id");

      String boundedSnapshot =
          snapshot(
              WATCHBOX_ID, 1, "n".repeat(255), "\"" + "d".repeat(4000) + "\"", layerIdsJson(100));
      insertAudit(jdbc, "CREATE", null, boundedSnapshot, "[]", null, 1L, WATCHBOX_ID, ACTOR_ID);

      assertSnapshotConstraintViolation(
          jdbc, snapshot(WATCHBOX_ID, 1, "n".repeat(256), "null", layerIdsJson(1)));
      assertSnapshotConstraintViolation(
          jdbc,
          snapshot(WATCHBOX_ID, 1, "v42-test", "\"" + "d".repeat(4001) + "\"", layerIdsJson(1)));
      assertSnapshotConstraintViolation(
          jdbc, snapshot(WATCHBOX_ID, 1, "v42-test", "null", layerIdsJson(101)));
    }
  }

  @Test
  void v42Migration_whenConcurrentLayerInsertsReachLimit_serializesOnParentLock() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      seedParents(jdbc);

      for (int index = 1; index <= 101; index++) {
        UUID layerId = layerId(index);
        jdbc.update(
            "INSERT INTO iam.layer (id, name) VALUES (?, ?)",
            layerId,
            "v42-concurrent-layer-" + index);
        if (index <= 99) {
          jdbc.update(
              "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)",
              WATCHBOX_ID,
              layerId);
        }
      }

      try (Connection hundredth = dataSource.getConnection();
          Connection hundredFirst = dataSource.getConnection()) {
        hundredth.setAutoCommit(false);
        hundredFirst.setAutoCommit(false);
        insertLayerRelationship(hundredth, layerId(100));

        var insertStarted = new CountDownLatch(1);
        CompletableFuture<SQLException> blockedInsert =
            CompletableFuture.supplyAsync(
                () -> {
                  insertStarted.countDown();
                  try {
                    insertLayerRelationship(hundredFirst, layerId(101));
                    return null;
                  } catch (SQLException exception) {
                    return exception;
                  }
                });

        assertThat(insertStarted.await(10, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(250);
        assertThat(blockedInsert.isDone()).isFalse();

        hundredth.commit();
        var failure = blockedInsert.get(10, TimeUnit.SECONDS);
        assertThat(failure.getClass()).isEqualTo(PSQLException.class);
        var postgresFailure = (PSQLException) failure;
        assertThat(postgresFailure.getSQLState()).isEqualTo("23514");
        assertThat(postgresFailure.getServerErrorMessage()).isNotNull();
        assertThat(postgresFailure.getServerErrorMessage().getConstraint())
            .isEqualTo("watchbox_layer_maximum");
        hundredFirst.rollback();
      }

      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM watchbox.watchbox_layer WHERE watchbox_id = ?",
                  Long.class,
                  WATCHBOX_ID))
          .isEqualTo(100L);
    }
  }

  @Test
  void v42Migration_whenRowsAreInvalid_enforcesAuditShapeAndRestrictiveParents() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      seedParents(jdbc);

      assertConstraintViolation(
          () ->
              insertAudit(
                  jdbc,
                  "PATCH",
                  snapshot(WATCHBOX_ID, 1).replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                  snapshot(WATCHBOX_ID, 1),
                  "[]",
                  1L,
                  1L,
                  WATCHBOX_ID,
                  ACTOR_ID),
          "watchbox_config_audit_snapshots_are_v1_objects");
      assertConstraintViolation(
          () ->
              insertAudit(
                  jdbc,
                  "PATCH",
                  snapshot(WATCHBOX_ID, 1).replace("\"direction\":\"BOTH\"", "\"direction\":null"),
                  snapshot(WATCHBOX_ID, 1),
                  "[]",
                  1L,
                  1L,
                  WATCHBOX_ID,
                  ACTOR_ID),
          "watchbox_config_audit_snapshots_are_v1_objects");
      assertConstraintViolation(
          () ->
              insertAudit(
                  jdbc,
                  "PATCH",
                  snapshot(WATCHBOX_ID, 1),
                  snapshot(WATCHBOX_ID, 1).replace("\"direction\":\"BOTH\"", "\"direction\":null"),
                  "[]",
                  1L,
                  1L,
                  WATCHBOX_ID,
                  ACTOR_ID),
          "watchbox_config_audit_snapshots_are_v1_objects");
      assertConstraintViolation(
          () ->
              insertAudit(
                  jdbc,
                  "PATCH",
                  snapshot(WATCHBOX_ID, 1)
                      .replace("00000000-0000-0000-0000-000000001a42", "not-a-uuid"),
                  snapshot(WATCHBOX_ID, 1),
                  "[]",
                  1L,
                  1L,
                  WATCHBOX_ID,
                  ACTOR_ID),
          "watchbox_config_audit_snapshots_are_v1_objects");
      assertConstraintViolation(
          () ->
              insertAudit(
                  jdbc,
                  "PATCH",
                  snapshot(WATCHBOX_ID, 1),
                  snapshot(WATCHBOX_ID, 3),
                  "[]",
                  1L,
                  3L,
                  WATCHBOX_ID,
                  ACTOR_ID),
          "watchbox_config_audit_shape");
      assertConstraintViolation(
          () ->
              insertAudit(
                  jdbc,
                  "PATCH",
                  snapshot(WATCHBOX_ID, 1),
                  snapshot(WATCHBOX_ID, 1),
                  "[\"FILTER_CHANGED\"]",
                  1L,
                  1L,
                  WATCHBOX_ID,
                  ACTOR_ID),
          "watchbox_config_audit_reset_reasons_array");
      var missingWatchbox = UUID.fromString("00000000-0000-0000-0000-00000000f042");
      assertConstraintViolation(
          () ->
              insertAudit(
                  jdbc,
                  "PATCH",
                  snapshot(missingWatchbox, 1),
                  snapshot(missingWatchbox, 1),
                  "[]",
                  1L,
                  1L,
                  missingWatchbox,
                  ACTOR_ID),
          "watchbox_config_audit_watchbox_id_fkey");
      assertConstraintViolation(
          () ->
              insertAudit(
                  jdbc,
                  "PATCH",
                  snapshot(WATCHBOX_ID, 1),
                  snapshot(WATCHBOX_ID, 1),
                  "[]",
                  1L,
                  1L,
                  WATCHBOX_ID,
                  UUID.fromString("00000000-0000-0000-0000-00000000f142")),
          "watchbox_config_audit_actor_user_id_fkey");

      insertAudit(
          jdbc,
          "PATCH",
          snapshot(WATCHBOX_ID, 1),
          snapshot(WATCHBOX_ID, 1),
          "[]",
          1L,
          1L,
          WATCHBOX_ID,
          ACTOR_ID);

      assertConstraintViolation(
          () -> jdbc.update("DELETE FROM watchbox.watchbox WHERE id = ?", WATCHBOX_ID),
          "watchbox_config_audit_watchbox_id_fkey");
      assertConstraintViolation(
          () -> jdbc.update("DELETE FROM opencop_user WHERE id = ?", ACTOR_ID),
          "watchbox_config_audit_actor_user_id_fkey");
    }
  }

  private static void assertConstraintViolation(Runnable action, String constraintName) {
    assertThatThrownBy(action::run)
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(constraintName);
  }

  private static void assertAuditIndexes(JdbcTemplate jdbc) {
    List<IndexColumn> columns =
        jdbc.query(
            """
            WITH target_indexes AS (
              SELECT index_class.relname AS index_name,
                     index_definition.indexrelid,
                     index_definition.indnatts,
                     index_definition.indoption
              FROM pg_index index_definition
              JOIN pg_class index_class ON index_class.oid = index_definition.indexrelid
              JOIN pg_namespace index_namespace
                ON index_namespace.oid = index_class.relnamespace
              JOIN pg_class table_class ON table_class.oid = index_definition.indrelid
              JOIN pg_namespace table_namespace
                ON table_namespace.oid = table_class.relnamespace
              WHERE index_namespace.nspname = 'watchbox'
                AND table_namespace.nspname = 'watchbox'
                AND table_class.relname = 'watchbox_config_audit'
                AND index_class.relname IN (
                  'watchbox_config_audit_latest_idx',
                  'watchbox_config_audit_watchbox_latest_idx',
                  'watchbox_config_audit_actor_latest_idx',
                  'watchbox_config_audit_operation_latest_idx'
                )
            )
            SELECT index_name,
                   ordinal_position,
                   pg_get_indexdef(indexrelid, ordinal_position, true) AS column_definition,
                   (indoption[ordinal_position - 1] & 1) = 1 AS descending
            FROM target_indexes
            CROSS JOIN LATERAL generate_series(1, indnatts) ordinal_position
            ORDER BY index_name, ordinal_position
            """,
            (resultSet, _) ->
                new IndexColumn(
                    resultSet.getString("index_name"),
                    resultSet.getInt("ordinal_position"),
                    resultSet.getString("column_definition"),
                    resultSet.getBoolean("descending")));

    assertThat(columns)
        .containsExactly(
            new IndexColumn("watchbox_config_audit_actor_latest_idx", 1, "actor_user_id", false),
            new IndexColumn("watchbox_config_audit_actor_latest_idx", 2, "occurred_at", true),
            new IndexColumn("watchbox_config_audit_actor_latest_idx", 3, "id", true),
            new IndexColumn("watchbox_config_audit_latest_idx", 1, "occurred_at", true),
            new IndexColumn("watchbox_config_audit_latest_idx", 2, "id", true),
            new IndexColumn("watchbox_config_audit_operation_latest_idx", 1, "operation", false),
            new IndexColumn("watchbox_config_audit_operation_latest_idx", 2, "occurred_at", true),
            new IndexColumn("watchbox_config_audit_operation_latest_idx", 3, "id", true),
            new IndexColumn("watchbox_config_audit_watchbox_latest_idx", 1, "watchbox_id", false),
            new IndexColumn("watchbox_config_audit_watchbox_latest_idx", 2, "occurred_at", true),
            new IndexColumn("watchbox_config_audit_watchbox_latest_idx", 3, "id", true));
  }

  private static void seedParents(JdbcTemplate jdbc) {
    jdbc.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username
        ) VALUES (?, 'v42-test-idp', 'v42-test-user', 'https://issuer.test', 'v42-test-user')
        """,
        ACTOR_ID);
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox (
          id, name, direction, polygon_json, filter_json
        ) VALUES (?, 'v42-test-watchbox', 'BOTH', '{}'::jsonb, '{"predicates":[]}'::jsonb)
        """,
        WATCHBOX_ID);
  }

  private static void seedUnsupportedPreviewWatchbox(JdbcTemplate jdbc) {
    jdbc.update(
        "INSERT INTO iam.layer (id, name) VALUES (?, 'v42-preview-layer')", PREVIEW_LAYER_ID);
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox (
          id, name, direction, polygon_json, filter_json
        ) VALUES (?, 'v42-preview-watchbox', 'BOTH', '{}'::jsonb, '{"predicates":[]}'::jsonb)
        """,
        WATCHBOX_ID);
    jdbc.update(
        "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)",
        WATCHBOX_ID,
        PREVIEW_LAYER_ID);
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox_detection_heartbeat (
          instance_id, listener_started_at, last_heartbeat_at,
          heartbeat_expires_at, heartbeat_sequence
        ) VALUES (
          'v42-preview-instance', now(), now(), now() + interval '1 minute', 1
        )
        """);
  }

  private static void assertUnsupportedPreviewWatchboxReset(JdbcTemplate jdbc) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM watchbox.watchbox WHERE id = ?", Long.class, WATCHBOX_ID))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_layer", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_source_status", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM watchbox.watchbox_transition_state", Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_detection", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_alert_outbox", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM watchbox.watchbox_detection_heartbeat WHERE instance_id = 'v42-preview-instance'",
                Long.class))
        .isEqualTo(1L);
  }

  private static UUID layerId(int index) {
    return UUID.fromString("00000000-0000-0000-0004-%012d".formatted(index));
  }

  private static void insertLayerRelationship(Connection connection, UUID layerId)
      throws SQLException {
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)")) {
      statement.setObject(1, WATCHBOX_ID);
      statement.setObject(2, layerId);
      statement.executeUpdate();
    }
  }

  private static void seedLegacyRuntimeHistory(JdbcTemplate jdbc) {
    jdbc.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username
        ) VALUES (?, 'v42-legacy-idp', 'v42-legacy-user', 'https://issuer.test', 'v42-legacy-user')
        """,
        LEGACY_ACTOR_ID);
    jdbc.update(
        """
        INSERT INTO config.runtime_feature_history (
          feature_name, previous_audience, new_audience, resulting_revision,
          changed_at, actor_user_id, actor_email
        ) VALUES (
          'watchbox', '{"mode":"NONE"}'::jsonb, '{"mode":"ALL"}'::jsonb, 1,
          '2026-08-19T10:00:00Z'::timestamptz, ?, 'legacy@example.test'
        )
        """,
        LEGACY_ACTOR_ID);
  }

  private static void seedRuntimeFeatureConfiguration(DataSource dataSource) throws SQLException {
    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var update =
              connection.prepareStatement(
                  """
                  UPDATE config.runtime_feature
                     SET audience_mode = 'USER_GROUPS', revision = 7,
                         created_at = '2026-08-18T09:00:00Z'::timestamptz,
                         updated_at = '2026-08-19T09:00:00Z'::timestamptz
                   WHERE name = 'watchbox'
                  """);
          var target =
              connection.prepareStatement(
                  """
                  INSERT INTO config.runtime_feature_audience_user_group (
                    feature_name, user_group_id
                  ) VALUES ('watchbox', '00000000-0000-4000-8000-000000000042'::uuid)
                  """)) {
        update.executeUpdate();
        target.executeUpdate();
        connection.commit();
      } catch (SQLException exception) {
        connection.rollback();
        throw exception;
      }
    }
  }

  private static String runtimeFeatureConfiguration(JdbcTemplate jdbc) {
    return jdbc.queryForObject(
        """
        SELECT jsonb_build_object(
          'features', (
            SELECT jsonb_agg(to_jsonb(feature) ORDER BY feature.name)
              FROM config.runtime_feature feature
          ),
          'targets', (
            SELECT COALESCE(
              jsonb_agg(to_jsonb(target) ORDER BY target.feature_name, target.user_group_id),
              '[]'::jsonb)
              FROM config.runtime_feature_audience_user_group target
          )
        )::text
        """,
        String.class);
  }

  private static void insertRuntimeAudit(
      JdbcTemplate jdbc,
      String featureName,
      String consumerClass,
      String operation,
      String beforeAudience,
      String afterAudience,
      Long revisionBefore,
      Long revisionAfter,
      String occurredAt,
      UUID actorId) {
    jdbc.update(
        """
        INSERT INTO config.runtime_feature_history (
          feature_name, consumer_class, operation,
          before_audience, after_audience,
          revision_before, revision_after, occurred_at,
          actor_user_id, actor_full_name, actor_email, actor_global_admin
        ) VALUES (
          ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?::timestamptz,
          ?, NULL, 'v42-runtime@example.test', true
        )
        """,
        featureName,
        consumerClass,
        operation,
        beforeAudience,
        afterAudience,
        revisionBefore,
        revisionAfter,
        occurredAt,
        actorId);
  }

  private static void assertRuntimeFeatureAuditIndexes(JdbcTemplate jdbc) {
    List<IndexColumn> columns =
        jdbc.query(
            """
            WITH target_indexes AS (
              SELECT index_class.relname AS index_name,
                     index_definition.indexrelid,
                     index_definition.indnatts,
                     index_definition.indoption
              FROM pg_index index_definition
              JOIN pg_class index_class ON index_class.oid = index_definition.indexrelid
              JOIN pg_namespace index_namespace
                ON index_namespace.oid = index_class.relnamespace
              JOIN pg_class table_class ON table_class.oid = index_definition.indrelid
              JOIN pg_namespace table_namespace
                ON table_namespace.oid = table_class.relnamespace
              WHERE index_namespace.nspname = 'config'
                AND table_namespace.nspname = 'config'
                AND table_class.relname = 'runtime_feature_history'
                AND index_class.relname IN (
                  'runtime_feature_history_latest_idx',
                  'runtime_feature_history_feature_latest_idx',
                  'runtime_feature_history_actor_latest_idx',
                  'runtime_feature_history_operation_latest_idx'
                )
            )
            SELECT index_name,
                   ordinal_position,
                   pg_get_indexdef(indexrelid, ordinal_position, true) AS column_definition,
                   (indoption[ordinal_position - 1] & 1) = 1 AS descending
            FROM target_indexes
            CROSS JOIN LATERAL generate_series(1, indnatts) ordinal_position
            ORDER BY index_name, ordinal_position
            """,
            (resultSet, _) ->
                new IndexColumn(
                    resultSet.getString("index_name"),
                    resultSet.getInt("ordinal_position"),
                    resultSet.getString("column_definition"),
                    resultSet.getBoolean("descending")));

    assertThat(columns)
        .containsExactly(
            new IndexColumn("runtime_feature_history_actor_latest_idx", 1, "actor_user_id", false),
            new IndexColumn("runtime_feature_history_actor_latest_idx", 2, "occurred_at", true),
            new IndexColumn("runtime_feature_history_actor_latest_idx", 3, "id", true),
            new IndexColumn("runtime_feature_history_feature_latest_idx", 1, "feature_name", false),
            new IndexColumn("runtime_feature_history_feature_latest_idx", 2, "occurred_at", true),
            new IndexColumn("runtime_feature_history_feature_latest_idx", 3, "id", true),
            new IndexColumn("runtime_feature_history_latest_idx", 1, "occurred_at", true),
            new IndexColumn("runtime_feature_history_latest_idx", 2, "id", true),
            new IndexColumn("runtime_feature_history_operation_latest_idx", 1, "operation", false),
            new IndexColumn("runtime_feature_history_operation_latest_idx", 2, "occurred_at", true),
            new IndexColumn("runtime_feature_history_operation_latest_idx", 3, "id", true));
  }

  private static String snapshot(UUID watchboxId, long configEpoch) {
    return snapshot(
        watchboxId, configEpoch, "v42-test", "null", "[\"00000000-0000-0000-0000-000000001a42\"]");
  }

  private static String snapshot(
      UUID watchboxId, long configEpoch, String name, String descriptionJson, String layerIdsJson) {
    return """
        {
          "schemaVersion":1,
          "id":"%s",
          "name":"%s",
          "description":%s,
          "enabled":true,
          "direction":"BOTH",
          "polygon":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]},
          "filter":{"combine":"ALL","predicates":[]},
          "layerIds":%s,
          "configEpoch":%d,
          "createdAt":"2026-08-19T12:00:00Z",
          "updatedAt":"2026-08-19T12:05:00Z"
        }
        """
        .formatted(watchboxId, name, descriptionJson, layerIdsJson, configEpoch);
  }

  private static String layerIdsJson(int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(index -> "\"00000000-0000-0000-0002-%012d\"".formatted(index))
        .collect(java.util.stream.Collectors.joining(",", "[", "]"));
  }

  private static void assertSnapshotConstraintViolation(JdbcTemplate jdbc, String snapshot) {
    assertConstraintViolation(
        () -> insertAudit(jdbc, "PATCH", snapshot, snapshot, "[]", 1L, 1L, WATCHBOX_ID, ACTOR_ID),
        "watchbox_config_audit_snapshots_are_v1_objects");
  }

  private static void insertAudit(
      JdbcTemplate jdbc,
      String operation,
      String beforeSnapshot,
      String afterSnapshot,
      String resetReasons,
      Long configEpochBefore,
      Long configEpochAfter,
      UUID watchboxId,
      UUID actorId) {
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox_config_audit (
          watchbox_id,
          operation,
          before_snapshot,
          after_snapshot,
          reset_reasons,
          config_epoch_before,
          config_epoch_after,
          occurred_at,
          actor_user_id,
          actor_full_name,
          actor_email,
          actor_global_admin
        ) VALUES (
          ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?,
          '2026-08-19T12:00:00Z'::timestamptz,
          ?, 'V42 Test Actor', 'v42@example.test', true
        )
        """,
        watchboxId,
        operation,
        beforeSnapshot,
        afterSnapshot,
        resetReasons,
        configEpochBefore,
        configEpochAfter,
        actorId);
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

  private record IndexColumn(
      String indexName, int ordinalPosition, String columnDefinition, boolean descending) {}
}
