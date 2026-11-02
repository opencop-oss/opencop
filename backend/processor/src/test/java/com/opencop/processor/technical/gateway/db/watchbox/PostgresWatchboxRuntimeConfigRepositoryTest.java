// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.LAYER_ID;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.SOURCE_ID;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.WATCHBOX_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusReason;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class PostgresWatchboxRuntimeConfigRepositoryTest {

  private static final UUID OTHER_WATCHBOX_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000b12");
  private static final UUID OTHER_SOURCE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000005012");
  private static final UUID OTHER_LAYER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000001a12");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000911");
  private static final String POLYGON_JSON =
      "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}";

  private static EmbeddedPostgres postgres;
  private static JdbcTemplate jdbc;
  private SimpleMeterRegistry meterRegistry;
  private PostgresWatchboxRuntimeConfigRepository repository;

  @BeforeAll
  static void startPostgres() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    DataSource dataSource = postgres.getPostgresDatabase();
    WatchboxPostgresTestSupport.migrateAndSeed(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    jdbc.update(
        """
        INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer, username)
        VALUES (?, 'wb-runtime-user', 'wb-runtime-user', 'wb-runtime-user', 'wb-runtime-user')
        """,
        USER_ID);
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM watchbox.watchbox_transition_state");
    jdbc.update("DELETE FROM watchbox.watchbox_source_status");
    jdbc.update("DELETE FROM watchbox.watchbox_layer");
    jdbc.update(
        "DELETE FROM iam.server_layer_access WHERE server_id <> ? OR layer_id <> ?",
        SOURCE_ID,
        LAYER_ID);
    jdbc.update("DELETE FROM watchbox.watchbox");
    jdbc.update("DELETE FROM iam.layer WHERE id <> ?", LAYER_ID);
    jdbc.update("DELETE FROM profiles.data_source WHERE id <> ?", SOURCE_ID);
    // Tracking generations cannot move backward, so recreate the fixture row at epoch 1.
    insertWatchbox(WATCHBOX_ID, "wb-test-box");
    jdbc.update(
        "UPDATE iam.layer SET lifecycle_state = 'ACTIVE', access_scope = 'PRIVATE' WHERE id = ?",
        LAYER_ID);
    jdbc.update(
        "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)",
        WATCHBOX_ID,
        LAYER_ID);
    jdbc.update("UPDATE profiles.data_source SET enabled = true WHERE id = ?", SOURCE_ID);
    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "NONE");
    meterRegistry = new SimpleMeterRegistry();
    repository =
        new PostgresWatchboxRuntimeConfigRepository(
            jdbc, new ObjectMapper(), new ProcessorMetrics(meterRegistry));
  }

  @Test
  void featureFlagOffReturnsEmptySnapshot() {
    assertThat(repository.load().isEmpty()).isTrue();
  }

  /**
   * A group-scoped rollout must still run background detection. Members of the targeted groups can
   * create watchboxes, and those rows are real and enabled; narrowing the Processor to {@code ALL}
   * would let a pilot group create watchboxes that silently never fire.
   *
   * <p>The Processor deliberately does not mirror Core's caller-scoped availability check, which
   * resolves {@code USER_GROUPS} against a specific caller. The Processor has no caller and is
   * asking whether the feature is live for anybody.
   */
  @Test
  void groupScopedAudienceStillEnablesBackgroundDetection() {
    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "USER_GROUPS");

    var snapshot = repository.load();

    assertThat(snapshot.isEmpty()).isFalse();
    assertThat(snapshot.targetsFor(SOURCE_ID))
        .extracting("watchboxId")
        .containsExactly(WATCHBOX_ID);
  }

  @Test
  void expandsSharedLayersAndCollapsesDuplicateWatchboxSourcePaths() {
    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "ALL");
    insertSource(OTHER_SOURCE_ID, "wb-other-source");
    jdbc.update(
        "INSERT INTO iam.server_layer_access (server_id, layer_id, role) VALUES (?, ?, 'VIEWER')",
        OTHER_SOURCE_ID,
        LAYER_ID);
    insertWatchbox(OTHER_WATCHBOX_ID, "wb-other-box");
    jdbc.update(
        "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)",
        OTHER_WATCHBOX_ID,
        LAYER_ID);
    WatchboxPostgresTestSupport.seedLayerChain(jdbc, WATCHBOX_ID, OTHER_LAYER_ID, SOURCE_ID);

    var snapshot = repository.load();

    assertThat(snapshot.targetsFor(SOURCE_ID))
        .extracting(target -> target.watchboxId())
        .containsExactlyInAnyOrder(WATCHBOX_ID, OTHER_WATCHBOX_ID);
    assertThat(snapshot.targetsFor(OTHER_SOURCE_ID))
        .extracting(target -> target.watchboxId())
        .containsExactlyInAnyOrder(WATCHBOX_ID, OTHER_WATCHBOX_ID);
    var baseTarget =
        snapshot.targetsFor(SOURCE_ID).stream()
            .filter(target -> target.watchboxId().equals(WATCHBOX_ID))
            .findFirst()
            .orElseThrow();
    var point = new GeometryFactory().createPoint(new Coordinate(0.5, 0.5));
    assertThat(baseTarget.geometry().covers(point)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void load_globalLayerSources_expandsUnionAndDeduplicates(boolean allGlobal) {
    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "ALL");
    insertSource(OTHER_SOURCE_ID, "global-only-source");
    WatchboxPostgresTestSupport.seedLayerChain(jdbc, WATCHBOX_ID, OTHER_LAYER_ID, OTHER_SOURCE_ID);
    jdbc.update("UPDATE iam.layer SET access_scope = 'GLOBAL' WHERE id = ?", OTHER_LAYER_ID);
    if (allGlobal) {
      jdbc.update("UPDATE iam.layer SET access_scope = 'GLOBAL' WHERE id = ?", LAYER_ID);
    }
    jdbc.update(
        "INSERT INTO iam.server_layer_access (server_id, layer_id, role) VALUES (?, ?, 'VIEWER')",
        SOURCE_ID,
        OTHER_LAYER_ID);

    var snapshot = repository.load();

    // OTHER_SOURCE_ID is reachable only through GLOBAL; SOURCE_ID has two layer paths.
    assertThat(snapshot.targetsFor(OTHER_SOURCE_ID))
        .extracting(target -> target.watchboxId())
        .containsExactly(WATCHBOX_ID);
    assertThat(snapshot.targetsFor(SOURCE_ID))
        .extracting(target -> target.watchboxId())
        .containsExactly(WATCHBOX_ID);
  }

  @Test
  void currentEpochExceptionFreezesTargetAndEpochOrDirectionEditsRideNextLoad() {
    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "ALL");
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox_source_status
          (watchbox_id, config_epoch, source_id, status, reason_code)
        VALUES (?, 1, ?, 'DEGRADED', 'WATCHBOX_OBJECT_ID_CHURN_DETECTED')
        """,
        WATCHBOX_ID,
        SOURCE_ID);

    var frozen = repository.load().targetsFor(SOURCE_ID).getFirst();
    assertThat(frozen.frozen()).isTrue();
    assertThat(frozen.frozenReason())
        .isEqualTo(WatchboxStatusReason.WATCHBOX_OBJECT_ID_CHURN_DETECTED);

    jdbc.update(
        "UPDATE watchbox.watchbox SET config_epoch = 2, direction = 'LEAVE' WHERE id = ?",
        WATCHBOX_ID);
    var edited = repository.load().targetsFor(SOURCE_ID).getFirst();
    assertThat(edited.configEpoch()).isEqualTo(2);
    assertThat(edited.direction()).isEqualTo(WatchboxDirection.LEAVE);
    assertThat(edited.frozen()).isFalse();
  }

  @Test
  void disabledArchivedAndNonActiveLayerTargetsAreExcluded() {
    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "ALL");

    jdbc.update("UPDATE watchbox.watchbox SET enabled = false WHERE id = ?", WATCHBOX_ID);
    assertThat(repository.load().isEmpty()).isTrue();

    jdbc.update(
        "UPDATE watchbox.watchbox SET enabled = true, archived_at = now(), archived_by = ? WHERE id = ?",
        USER_ID,
        WATCHBOX_ID);
    assertThat(repository.load().isEmpty()).isTrue();

    jdbc.update(
        "UPDATE watchbox.watchbox SET archived_at = NULL, archived_by = NULL WHERE id = ?",
        WATCHBOX_ID);
    WatchboxPostgresTestSupport.forceLayerLifecycle(jdbc, LAYER_ID, "MERGE_SOURCE_LOCKED");
    assertThat(repository.load().isEmpty()).isTrue();
  }

  @Test
  void invalidPolygonSkipsOnlyThatWatchboxAndCountsOncePerLoad() {
    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "ALL");
    insertWatchbox(OTHER_WATCHBOX_ID, "wb-valid-other-box");
    jdbc.update(
        "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)",
        OTHER_WATCHBOX_ID,
        LAYER_ID);
    jdbc.update(
        "UPDATE watchbox.watchbox SET polygon_json = '{\"bad\":true}'::jsonb WHERE id = ?",
        WATCHBOX_ID);

    var snapshot = repository.load();

    assertThat(snapshot.targetsFor(SOURCE_ID))
        .extracting(target -> target.watchboxId())
        .containsExactly(OTHER_WATCHBOX_ID);
    assertThat(meterRegistry.counter("opencop.watchbox.pipeline.config.parse_failures").count())
        .isEqualTo(1.0);
  }

  private static void insertSource(UUID sourceId, String name) {
    jdbc.update(
        """
        INSERT INTO profiles.data_source
          (id, name, ingress_mapper_id, connection_spec, integration_id, connection_schema_version)
        SELECT ?, ?, ingress_mapper_id, '{}'::jsonb, integration_id, connection_schema_version
        FROM profiles.data_source WHERE id = ?
        """,
        sourceId,
        name,
        SOURCE_ID);
  }

  private static void insertWatchbox(UUID watchboxId, String name) {
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox
          (id, name, direction, polygon_json, filter_json)
        VALUES (?, ?, 'BOTH', ?::jsonb, '{"predicates":[]}'::jsonb)
        """,
        watchboxId,
        name,
        POLYGON_JSON);
  }
}
