// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the clone/merge copy activity against a real PostgreSQL schema.
 *
 * <p>Tests the writer directly rather than driving {@code geo.clone_source_to_layer}: the
 * procedures are unchanged by this work, and what needs proving is that the copy is credited to the
 * target layer and that the projection can only be advanced by a ledger row that was accepted.
 */
class LayerCopyActivityWriterIT {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  private static EmbeddedPostgres postgres;
  private static JdbcTemplate jdbc;

  @BeforeAll
  static void startDatabase() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    var dataSource = postgres.getPostgresDatabase();
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration", sdkMigrationLocation())
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterAll
  static void stopDatabase() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void clearTables() {
    jdbc.execute("TRUNCATE activity.recorded_activity");
    jdbc.execute("TRUNCATE activity.resource_activity");
    jdbc.execute("TRUNCATE iam.layer CASCADE");
  }

  @Test
  void recordsTheCopyAgainstTheTargetLayer() {
    var layerId = insertLayer();
    var before = Instant.now();

    LayerCopyActivityWriter.record(jdbc, layerId);

    assertEquals(1, ledgerCount(layerId));
    var recorded = activity(layerId);
    assertTrue(
        !recorded.isBefore(before.minusSeconds(1)),
        "the copy operation time should be around now, not a carried ingress time");
  }

  @Test
  void eachCopyIsItsOwnOperation() {
    var layerId = insertLayer();

    LayerCopyActivityWriter.record(jdbc, layerId);
    LayerCopyActivityWriter.record(jdbc, layerId);

    // Unlike an ingest batch, a copy is a deliberate action: doing it twice is two operations, so
    // each gets its own ledger row rather than being deduplicated as a replay.
    assertEquals(2, ledgerCount(layerId));
    assertEquals(1, projectionCount(layerId));
  }

  @Test
  void doesNotCreditAnyMapper() {
    var layerId = insertLayer();

    LayerCopyActivityWriter.record(jdbc, layerId);

    // A copy moves features some mapper produced earlier; that is not the mapper running again.
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM activity.resource_activity WHERE resource_kind = 'MAPPER'",
            Integer.class));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM activity.recorded_activity WHERE activity_kind <> 'LAYER_COPY'",
            Integer.class));
  }

  @Test
  void neverMovesActivityBackwards() {
    var layerId = insertLayer();
    var future = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MICROS);
    jdbc.update(
        """
        INSERT INTO activity.resource_activity (resource_kind, resource_id, last_event_at)
        VALUES ('LAYER', ?, ?)
        """,
        layerId,
        java.sql.Timestamp.from(future));

    LayerCopyActivityWriter.record(jdbc, layerId);

    assertEquals(
        future,
        activity(layerId),
        "a copy recorded now must not lower a layer already reporting a later time");
  }

  private static UUID insertLayer() {
    var layerId = UUID.randomUUID();
    jdbc.update("INSERT INTO iam.layer (id, name) VALUES (?, ?)", layerId, "layer-" + layerId);
    return layerId;
  }

  private static Instant activity(UUID layerId) {
    return jdbc.queryForObject(
            """
            SELECT last_event_at
              FROM activity.resource_activity
             WHERE resource_kind = 'LAYER' AND resource_id = ?
            """,
            java.sql.Timestamp.class,
            layerId)
        .toInstant();
  }

  private static int ledgerCount(UUID layerId) {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*)
          FROM activity.recorded_activity
         WHERE activity_kind = 'LAYER_COPY' AND resource_kind = 'LAYER' AND resource_id = ?
        """,
        Integer.class,
        layerId);
  }

  private static int projectionCount(UUID layerId) {
    return jdbc.queryForObject(
        """
        SELECT COUNT(*)
          FROM activity.resource_activity
         WHERE resource_kind = 'LAYER' AND resource_id = ?
        """,
        Integer.class,
        layerId);
  }

  private static String sdkMigrationLocation() {
    return "filesystem:"
        + Path.of("..", "sdk", "src", "main", "resources", "db", "migration").toAbsolutePath();
  }
}
