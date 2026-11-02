// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

public final class WatchboxPostgresTestSupport {

  public static final UUID WATCHBOX_ID = UUID.fromString("00000000-0000-0000-0000-000000000b11");
  public static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005011");
  public static final UUID LAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000001a11");

  private static final UUID MAPPER_ID = UUID.fromString("00000000-0000-0000-0000-00000000a211");

  /**
   * Placeholder audience target. V34 deliberately omits an FK so hard-deleted groups can linger.
   */
  public static final UUID AUDIENCE_GROUP_ID =
      UUID.fromString("00000000-0000-0000-0000-0000000ac001");

  private WatchboxPostgresTestSupport() {}

  public static void migrateAndSeed(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FlywayGeoFeaturesPlaceholders.defaultPlaceholders())
        .load()
        .migrate();
    var jdbc = new JdbcTemplate(dataSource);
    jdbc.update(
        """
        INSERT INTO profiles.mapper (id, qualifier)
        VALUES (?, 'INGRESS'::profiles.mapper_qualifier)
        ON CONFLICT (id) DO NOTHING
        """,
        MAPPER_ID);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper (id, version, name, mapper)
        VALUES (?, 1, ?, '{}'::jsonb)
        """,
        MAPPER_ID,
        "wb-test-mapper");
    NativeIntegrationFixture.seed(jdbc);
    jdbc.update(
        """
        INSERT INTO profiles.data_source
          (id, name, ingress_mapper_id, connection_spec, integration_id, connection_schema_version)
        VALUES (?, ?, ?, '{}'::jsonb, ?, ?)
        """,
        SOURCE_ID,
        "wb-test-source",
        MAPPER_ID,
        NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
        NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion());
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox
          (id, name, direction, polygon_json, filter_json)
        VALUES (?, ?, 'BOTH', ?::jsonb, '{"predicates":[]}'::jsonb)
        """,
        WATCHBOX_ID,
        "wb-test-box",
        "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}");
    seedLayerChain(jdbc, WATCHBOX_ID, LAYER_ID, SOURCE_ID);
  }

  public static void seedLayerChain(
      JdbcTemplate jdbc, UUID watchboxId, UUID layerId, UUID sourceId) {
    jdbc.update("INSERT INTO iam.layer (id, name) VALUES (?, ?)", layerId, "wb-layer-" + layerId);
    jdbc.update(
        "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)",
        watchboxId,
        layerId);
    jdbc.update(
        "INSERT INTO iam.server_layer_access (server_id, layer_id, role) VALUES (?, ?, 'VIEWER')",
        sourceId,
        layerId);
  }

  /**
   * Sets the Watchbox runtime-feature audience, keeping the mode and its target rows consistent.
   *
   * <p>V34 installs deferred constraint triggers requiring {@code USER_GROUPS} to have at least one
   * target and every other mode to have none. Because those triggers are {@code INITIALLY DEFERRED}
   * they fire at commit, so the mode change and the target change must share one transaction —
   * autocommit statements would trip the constraint in either order.
   */
  public static void setWatchboxFeatureAudience(JdbcTemplate jdbc, String audienceMode) {
    jdbc.execute(
        (ConnectionCallback<Void>)
            connection -> {
              var restoreAutoCommit = connection.getAutoCommit();
              connection.setAutoCommit(false);
              try {
                try (var targets =
                    connection.prepareStatement(
                        "DELETE FROM config.runtime_feature_audience_user_group"
                            + " WHERE feature_name = 'watchbox'")) {
                  targets.executeUpdate();
                }
                if ("USER_GROUPS".equals(audienceMode)) {
                  try (var insert =
                      connection.prepareStatement(
                          "INSERT INTO config.runtime_feature_audience_user_group"
                              + " (feature_name, user_group_id) VALUES ('watchbox', ?)")) {
                    insert.setObject(1, AUDIENCE_GROUP_ID);
                    insert.executeUpdate();
                  }
                }
                try (var mode =
                    connection.prepareStatement(
                        "UPDATE config.runtime_feature SET audience_mode = ?"
                            + " WHERE name = 'watchbox'")) {
                  mode.setString(1, audienceMode);
                  mode.executeUpdate();
                }
                connection.commit();
              } catch (RuntimeException | java.sql.SQLException failure) {
                connection.rollback();
                throw failure;
              } finally {
                connection.setAutoCommit(restoreAutoCommit);
              }
              return null;
            });
  }

  public static void clearRuntimeRows(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM watchbox.watchbox_alert_outbox");
    jdbc.update("DELETE FROM watchbox.watchbox_detection");
    jdbc.update("DELETE FROM watchbox.watchbox_source_status");
    jdbc.update("DELETE FROM watchbox.watchbox_transition_state");
  }

  /** Restores the shared seeded fixture between tests that intentionally advance its epoch. */
  public static void resetSeededWatchboxConfigEpoch(JdbcTemplate jdbc) {
    jdbc.execute(
        "ALTER TABLE watchbox.watchbox DISABLE TRIGGER watchbox_config_epoch_non_decreasing");
    try {
      jdbc.update("UPDATE watchbox.watchbox SET config_epoch = 1 WHERE id = ?", WATCHBOX_ID);
    } finally {
      jdbc.execute(
          "ALTER TABLE watchbox.watchbox ENABLE TRIGGER watchbox_config_epoch_non_decreasing");
    }
  }

  /**
   * Forces a layer lifecycle state past V37's reference guard, which otherwise rejects taking a
   * layer out of ACTIVE while a live watchbox references it. Tests use this to construct the
   * pre-guard residue state and prove the downstream defensive behavior (the runtime snapshot
   * excludes non-ACTIVE layer targets) still holds for databases that raced before the guard.
   */
  public static void forceLayerLifecycle(JdbcTemplate jdbc, UUID layerId, String lifecycleState) {
    jdbc.execute("ALTER TABLE iam.layer DISABLE TRIGGER layer_lifecycle_blocked_by_watchboxes");
    try {
      jdbc.update(
          "UPDATE iam.layer SET lifecycle_state = ?::iam.layer_lifecycle_state WHERE id = ?",
          lifecycleState,
          layerId);
    } finally {
      jdbc.execute("ALTER TABLE iam.layer ENABLE TRIGGER layer_lifecycle_blocked_by_watchboxes");
    }
  }
}
