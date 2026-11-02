// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.general.technical.gateway.runtime.PostgresResourceActivityMaintenance;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Proves the activity cleanup against a real PostgreSQL schema.
 *
 * <p>Retention here is a correctness floor rather than housekeeping: pruning inside the replay
 * horizon lets a replayed batch stop conflicting with the row that already recorded it. The orphan
 * sweep exists because {@code resource_id} is polymorphic and therefore carries no foreign key, so
 * nothing cascades when a resource is deleted.
 *
 * <p>Requires the Bash sandbox to be disabled — embedded Postgres cannot bind a socket inside it.
 */
class ResourceActivityCleanupIT {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  private static EmbeddedPostgres postgres;
  private static JdbcTemplate jdbc;
  private static PostgresResourceActivityMaintenance maintenance;

  @BeforeAll
  static void startDatabase() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    var dataSource = postgres.getPostgresDatabase();
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();

    jdbc = new JdbcTemplate(dataSource);
    maintenance =
        new PostgresResourceActivityMaintenance(new NamedParameterJdbcTemplate(dataSource));
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
    jdbc.execute(
        """
        TRUNCATE profiles.data_source, profiles.data_mapper CASCADE
        """);
  }

  @Test
  void deletesLedgerRowsPastRetentionAndKeepsTheRest() {
    var now = Instant.now();
    insertLedgerRow("old-1", now.minus(10, ChronoUnit.DAYS));
    insertLedgerRow("old-2", now.minus(8, ChronoUnit.DAYS));
    insertLedgerRow("recent", now.minus(1, ChronoUnit.DAYS));

    var deleted = maintenance.deleteActivityOlderThan(Duration.ofDays(7));

    assertEquals(2, deleted);
    assertEquals(1, ledgerCount());
  }

  @Test
  void keepsEverythingWhenNothingIsPastRetention() {
    insertLedgerRow("recent", Instant.now().minus(1, ChronoUnit.HOURS));

    assertEquals(0, maintenance.deleteActivityOlderThan(Duration.ofDays(7)));
    assertEquals(1, ledgerCount());
  }

  @Test
  void discriminatesTheRetentionCutoffToWithinASecond() {
    // Both rows are placed relative to the database's own clock rather than the JVM's: `now()` is
    // re-evaluated inside the delete, so a row positioned from Java at exactly the cutoff lands
    // microseconds outside it and the assertion would be a coin flip. One second either side is
    // enough to catch a flipped comparison or an interval-scale error without that race.
    insertLedgerRowNearCutoff("inside-by-a-second", "7 days", "-1 second");
    insertLedgerRowNearCutoff("outside-by-a-second", "7 days", "1 second");

    assertEquals(1, maintenance.deleteActivityOlderThan(Duration.ofDays(7)));
    assertEquals(
        "inside-by-a-second",
        jdbc.queryForObject("SELECT activity_id FROM activity.recorded_activity", String.class),
        "the surviving row must be the one still inside the replay horizon");
  }

  @Test
  void sweepsProjectionRowsWhoseResourceIsGone() {
    // No foreign key can express this: resource_id points at three different tables depending on
    // resource_kind, so a deleted layer, source or mapper leaves its projection row behind.
    insertProjection("LAYER", UUID.randomUUID());
    insertProjection("DATA_SOURCE", UUID.randomUUID());
    insertProjection("MAPPER", UUID.randomUUID());

    var deleted = maintenance.deleteOrphanedProjections();

    assertEquals(3, deleted);
    assertEquals(0, projectionCount());
  }

  @Test
  void keepsProjectionRowsWhoseResourceStillExists() {
    var layerId = UUID.randomUUID();
    jdbc.update("INSERT INTO iam.layer (id, name) VALUES (?, ?)", layerId, "layer-" + layerId);
    insertProjection("LAYER", layerId);
    insertProjection("LAYER", UUID.randomUUID());

    assertEquals(1, maintenance.deleteOrphanedProjections());
    assertEquals(
        layerId,
        jdbc.queryForObject("SELECT resource_id FROM activity.resource_activity", UUID.class),
        "the surviving row must be the one whose layer still exists");
  }

  @Test
  void keepsProjectionRowsForEveryResourceKindThatStillExists() {
    // Each kind is checked against its own table in a separate OR arm, so a wrong table or column
    // in one arm would delete live projection rows for that kind alone. The aggregate
    // "3 orphans deleted" case cannot see that; this seeds a real resource of every kind beside an
    // orphan of the same kind and pins which of the two survives.
    var layerId = insertLayer();
    var mapperId = insertMapper();
    var sourceId = insertDataSource(mapperId);
    insertProjection("LAYER", layerId);
    insertProjection("MAPPER", mapperId);
    insertProjection("DATA_SOURCE", sourceId);
    insertProjection("LAYER", UUID.randomUUID());
    insertProjection("MAPPER", UUID.randomUUID());
    insertProjection("DATA_SOURCE", UUID.randomUUID());

    assertEquals(3, maintenance.deleteOrphanedProjections());
    assertEquals(
        List.of(sourceId, layerId, mapperId),
        jdbc.queryForList(
            """
            SELECT resource_id
              FROM activity.resource_activity
             ORDER BY resource_kind
            """,
            UUID.class),
        "every kind's live resource must survive, and only its orphan be swept");
  }

  @Test
  void aDeletedLayerLosesItsProjectionRowButNotTheLedgerHistory() {
    var layerId = UUID.randomUUID();
    jdbc.update("INSERT INTO iam.layer (id, name) VALUES (?, ?)", layerId, "layer-" + layerId);
    insertProjection("LAYER", layerId);
    insertLedgerRow("copy-1", Instant.now(), "LAYER", layerId);

    jdbc.update("DELETE FROM iam.layer WHERE id = ?", layerId);
    maintenance.deleteOrphanedProjections();

    assertEquals(0, projectionCount());
    assertTrue(
        ledgerCount() > 0,
        "ledger rows are pruned by age, not by whether the resource still exists — they are the"
            + " record of what happened, and a deleted layer does not unhappen it");
  }

  private void insertLedgerRow(String activityId, Instant effectiveAt) {
    insertLedgerRow(activityId, effectiveAt, "MAPPER", UUID.randomUUID());
  }

  /**
   * Inserts a ledger row offset from the <em>database's</em> clock, so a boundary assertion cannot
   * race the JVM clock. {@code offsetFromCutoff} is added to the cutoff: negative is inside
   * retention, positive is outside it.
   */
  private void insertLedgerRowNearCutoff(
      String activityId, String retention, String offsetFromCutoff) {
    jdbc.update(
        """
        INSERT INTO activity.recorded_activity (
          activity_kind, activity_id, resource_kind, resource_id, reported_at, effective_at)
        VALUES ('MAPPING_EXECUTION', ?, 'MAPPER', ?, now(),
                now() - CAST(? AS interval) - CAST(? AS interval))
        """,
        activityId,
        UUID.randomUUID(),
        retention,
        offsetFromCutoff);
  }

  private void insertLedgerRow(
      String activityId, Instant effectiveAt, String resourceKind, UUID resourceId) {
    jdbc.update(
        """
        INSERT INTO activity.recorded_activity (
          activity_kind, activity_id, resource_kind, resource_id, reported_at, effective_at)
        VALUES ('MAPPING_EXECUTION', ?, ?, ?, ?, ?)
        """,
        activityId,
        resourceKind,
        resourceId,
        Timestamp.from(effectiveAt),
        Timestamp.from(effectiveAt));
  }

  private UUID insertLayer() {
    var layerId = UUID.randomUUID();
    jdbc.update("INSERT INTO iam.layer (id, name) VALUES (?, ?)", layerId, "layer-" + layerId);
    return layerId;
  }

  private UUID insertMapper() {
    var mapperId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO profiles.mapper (id, qualifier)
        VALUES (?, 'INGRESS'::profiles.mapper_qualifier)
        ON CONFLICT (id) DO NOTHING
        """,
        mapperId);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper (id, version, name, mapper)
        VALUES (?, 1, ?, CAST('{}' AS jsonb))
        """,
        mapperId,
        "mapper-" + mapperId);
    return mapperId;
  }

  private UUID insertDataSource(UUID mapperId) {
    var sourceId = UUID.randomUUID();
    NativeIntegrationFixture.seed(jdbc);
    jdbc.update(
        """
        INSERT INTO profiles.data_source
          (id, name, tenant, provider, ingress_mapper_id, connection_spec,
           integration_id, connection_schema_version)
        VALUES (?, ?, 'default', CAST('TCP' AS profiles.transport_kind), ?, CAST('{}' AS jsonb),
                ?, ?)
        """,
        sourceId,
        "source-" + sourceId,
        mapperId,
        NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
        NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion());
    return sourceId;
  }

  private void insertProjection(String resourceKind, UUID resourceId) {
    jdbc.update(
        """
        INSERT INTO activity.resource_activity (resource_kind, resource_id, last_event_at)
        VALUES (?, ?, now())
        """,
        resourceKind,
        resourceId);
  }

  private int ledgerCount() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM activity.recorded_activity", Integer.class);
  }

  private int projectionCount() {
    return jdbc.queryForObject("SELECT COUNT(*) FROM activity.resource_activity", Integer.class);
  }
}
