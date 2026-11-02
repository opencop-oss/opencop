// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.iam.technical.gateway.db.layers.layer.PostgresLayerLastEventWriter;
import com.opencop.core.sdk.technical.gateway.audit.LastEventAtCoalescer;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Proves the activity schema against a real PostgreSQL instance — the seam where the promotion
 * trigger, the idempotency contract on {@code activity.recorded_activity}, and the monotonic rule
 * on {@code activity.resource_activity} have to agree.
 *
 * <p>A mocked JDBC template cannot observe a trigger firing or an {@code ON CONFLICT DO NOTHING}
 * declining to return a row, so these guarantees are only testable here. Modelled on {@code
 * JdbcDataSourceRepositoryConflictIT}: plain JUnit, no Spring context, collaborators instantiated
 * directly.
 *
 * <p>Requires the Bash sandbox to be disabled — embedded Postgres cannot bind a socket inside it,
 * and the failure ("Socket Operation not permitted") looks like a test bug rather than an
 * environment one.
 */
class ResourceActivitySchemaIT {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  /**
   * Derived from now, not a literal: the promotion trigger clamps anything ahead of {@code
   * statement_timestamp()}, so a hard-coded instant silently becomes a clamp test the day the clock
   * passes it.
   *
   * <p>Aligned to the start of a view bucket, because the layer writer's activity identity is
   * {@code epochSecond / flushInterval}. An unaligned instant lands within 500ms of a boundary
   * roughly one run in thirty, and a test adding a sub-second offset to it would then cross into
   * the next bucket and see two ledger rows where it expects one. Aligning also lands on a whole
   * second, which survives the {@code timestamptz} round trip that a nanosecond instant would not.
   */
  private static final Instant OBSERVED = bucketStart(Instant.now().minus(1, ChronoUnit.HOURS));

  /**
   * The instant beginning the bucket {@code raw} falls in, matching the layer writer's identity.
   */
  private static Instant bucketStart(Instant raw) {
    var bucketSeconds = Math.max(1L, LastEventAtCoalescer.DEFAULT_FLUSH_INTERVAL.toSeconds());
    return Instant.ofEpochSecond(raw.getEpochSecond() / bucketSeconds * bucketSeconds);
  }

  private static final Instant EARLIER = OBSERVED.minus(1, ChronoUnit.HOURS);

  private static EmbeddedPostgres postgres;
  private static JdbcTemplate jdbc;
  private static PostgresLayerLastEventWriter layerWriter;
  private static UUID editor;

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
    layerWriter = new PostgresLayerLastEventWriter(new NamedParameterJdbcTemplate(dataSource));

    editor = UUID.randomUUID();
    insertUser(editor);
  }

  @AfterAll
  static void stopDatabase() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void clearTables() {
    // The activity tables carry no foreign keys, so CASCADE from the resource tables does not
    // reach them - the whole point of the polymorphic key. Truncate them explicitly.
    jdbc.execute("TRUNCATE activity.recorded_activity");
    jdbc.execute("TRUNCATE activity.resource_activity");
    jdbc.execute("TRUNCATE profiles.data_source CASCADE");
    jdbc.execute("TRUNCATE iam.layer CASCADE");
  }

  // ── The schema move ────────────────────────────────────────────────────────

  @Test
  void theOldPerResourceColumnsAreGone() {
    assertFalse(columnExists("iam", "layer", "last_event_at"));
    assertFalse(columnExists("profiles", "data_source", "last_event_at"));
    assertFalse(columnExists("profiles", "data_mapper", "last_event_at"));
  }

  @Test
  void theOtherAuditColumnsSurvive() {
    assertTrue(columnExists("iam", "layer", "created_by"));
    assertTrue(columnExists("profiles", "data_source", "updated_by"));
    assertTrue(columnExists("profiles", "data_mapper", "created_at"));
  }

  // ── The promotion trigger ──────────────────────────────────────────────────

  @Test
  void aNewMessageTokenPromotesActivity() {
    var sourceId = insertDataSource();

    observeMessage(sourceId, "token-1", OBSERVED);

    assertEquals(OBSERVED, activity("DATA_SOURCE", sourceId));
    assertEquals(1, ledgerCount("SOURCE_RECEIPT", "token-1"));
  }

  @Test
  void anUnchangedTokenIsANoOp() {
    var sourceId = insertDataSource();
    observeMessage(sourceId, "token-1", OBSERVED);

    // A heartbeat with no new message re-reports the same token, which is the common case: with
    // N sources across M instances this fires every 15 seconds and must cost nothing.
    observeMessage(sourceId, "token-1", OBSERVED.plus(5, ChronoUnit.MINUTES));

    assertEquals(OBSERVED, activity("DATA_SOURCE", sourceId));
    assertEquals(1, ledgerCount("SOURCE_RECEIPT", "token-1"));
  }

  @Test
  void anOlderOutOfOrderTokenCannotRegressActivity() {
    var sourceId = insertDataSource();
    observeMessage(sourceId, "token-2", OBSERVED);

    observeMessage(sourceId, "token-1", EARLIER);

    assertEquals(
        OBSERVED,
        activity("DATA_SOURCE", sourceId),
        "a late-arriving older observation must not move activity backwards");
  }

  @Test
  void aFutureDatedTokenIsClampedOnceAndNotReclampedOnReplay() {
    var sourceId = insertDataSource();
    var farFuture = Instant.now().plus(365, ChronoUnit.DAYS);

    observeMessage(sourceId, "skewed", farFuture);
    var clamped = activity("DATA_SOURCE", sourceId);

    assertTrue(
        clamped.isBefore(farFuture),
        "a skewed host clock must be clamped, or the projection is pinned in the future forever");

    // Replaying the same token must not recompute the clamp: the ledger row already exists, so
    // the insert declines to return and the projection is left alone.
    observeMessage(sourceId, "skewed", farFuture);
    assertEquals(clamped, activity("DATA_SOURCE", sourceId));
    assertEquals(1, ledgerCount("SOURCE_RECEIPT", "skewed"));
  }

  @Test
  void aCorrectedClockAdvancesThroughANewToken() {
    var sourceId = insertDataSource();
    observeMessage(sourceId, "skewed", Instant.now().plus(365, ChronoUnit.DAYS));
    var clamped = activity("DATA_SOURCE", sourceId);

    // The host's clock is fixed and a genuinely new message arrives with an honest timestamp.
    // The clamp bounded the skewed value at the database's own clock, so this one is newer and
    // must advance - which is the point of clamping rather than rejecting: a skewed host costs
    // one message's precision, not the source's activity forever.
    observeMessage(sourceId, "corrected", Instant.now());

    assertTrue(
        activity("DATA_SOURCE", sourceId).isAfter(clamped),
        "a corrected clock must be able to advance past a previously clamped value");
    assertEquals(1, ledgerCount("SOURCE_RECEIPT", "corrected"));
  }

  @Test
  void multipleInstancesConvergeOnTheLatest() {
    var sourceId = insertDataSource();

    observeMessage(sourceId, "connect-1", "token-a", EARLIER);
    observeMessage(sourceId, "connect-2", "token-b", OBSERVED);

    assertEquals(OBSERVED, activity("DATA_SOURCE", sourceId));
  }

  @Test
  void observationCleanupDoesNotEraseActivity() {
    var sourceId = insertDataSource();
    observeMessage(sourceId, "token-1", OBSERVED);

    jdbc.update("DELETE FROM profiles.source_runtime_observation WHERE source_id = ?", sourceId);

    assertEquals(
        OBSERVED,
        activity("DATA_SOURCE", sourceId),
        "observations are pruned on a 24h retention; the durable projection must outlive them");
  }

  @Test
  void promotingActivityLeavesTheEditRecordAlone() {
    var sourceId = insertDataSource();
    jdbc.update("UPDATE profiles.data_source SET updated_by = ? WHERE id = ?", editor, sourceId);
    var updatedAtBefore = updatedAt(sourceId);

    observeMessage(sourceId, "token-1", OBSERVED);

    // Structural now rather than trigger-conditional: activity never touches the resource row, so
    // there is no way for it to forge an edit timestamp against a stale updated_by.
    assertEquals(updatedAtBefore, updatedAt(sourceId));
    assertEquals(
        editor,
        jdbc.queryForObject(
            "SELECT updated_by FROM profiles.data_source WHERE id = ?", UUID.class, sourceId));
  }

  // ── The layer view writer ──────────────────────────────────────────────────

  @Test
  void openingAnEventDetailAdvancesTheLayer() {
    var layerId = insertLayer();

    layerWriter.write(layerId, OBSERVED);

    assertEquals(OBSERVED, activity("LAYER", layerId));
  }

  @Test
  void openingAnEventDetailIsRecordedInTheLedgerNotOnlyTheProjection() {
    var layerId = insertLayer();

    layerWriter.write(layerId, OBSERVED);

    // Without a ledger row the LAYER_EVENT_VIEW kind would be dead schema, and "why did this layer
    // advance" would be unanswerable for views while working for every other kind.
    assertEquals(1, viewLedgerCount(layerId));
  }

  @Test
  void repeatedViewsWithinOneFlushIntervalAreRecordedOnce() {
    var layerId = insertLayer();

    // The detail panel polls while it stays open, and a failed flush retries with the same pending
    // instant. Both land in the same bucket, so the episode is recorded once.
    layerWriter.write(layerId, OBSERVED);
    layerWriter.write(layerId, OBSERVED.plusMillis(500));

    assertEquals(1, viewLedgerCount(layerId));
  }

  @Test
  void aDataSourceIdReachingTheLayerWriterRecordsNoLedgerRowEither() {
    var sourceId = insertDataSource();

    layerWriter.write(sourceId, OBSERVED);

    assertEquals(0, viewLedgerCount(sourceId));
  }

  @Test
  void theLayerWriterNeverRegresses() {
    var layerId = insertLayer();
    layerWriter.write(layerId, OBSERVED);

    layerWriter.write(layerId, EARLIER);

    assertEquals(OBSERVED, activity("LAYER", layerId));
  }

  @Test
  void theLayerWriterClampsAFutureInstant() {
    var layerId = insertLayer();
    var farFuture = Instant.now().plus(365, ChronoUnit.DAYS);

    layerWriter.write(layerId, farFuture);

    assertTrue(activity("LAYER", layerId).isBefore(farFuture));
  }

  @Test
  void aDataSourceIdReachingTheLayerWriterCreatesNothing() {
    var sourceId = insertDataSource();

    // An event's source may be a layer or a data source and the endpoint does not distinguish.
    // The old writer relied on an UPDATE matching no row; an upsert needs the EXISTS guard, or it
    // silently accumulates rows keyed (LAYER, <data-source-id>) that no read can ever surface.
    layerWriter.write(sourceId, OBSERVED);

    assertNull(activity("LAYER", sourceId));
  }

  // ── Fixtures ───────────────────────────────────────────────────────────────

  private static void insertUser(UUID id) {
    jdbc.update(
        """
        INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer, username)
        VALUES (?, ?, ?, 'https://issuer.test', ?)
        """,
        id,
        "idp-" + id,
        "user-" + id,
        "user-" + id);
  }

  private UUID insertDataSource() {
    var mapperId = insertMapper();
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

  private UUID insertLayer() {
    var layerId = UUID.randomUUID();
    jdbc.update("INSERT INTO iam.layer (id, name) VALUES (?, ?)", layerId, "layer-" + layerId);
    return layerId;
  }

  private void observeMessage(UUID sourceId, String token, Instant lastMessageAt) {
    observeMessage(sourceId, "connect-1", token, lastMessageAt);
  }

  private void observeMessage(
      UUID sourceId, String instanceId, String token, Instant lastMessageAt) {
    jdbc.update(
        """
        INSERT INTO profiles.source_runtime_observation
          (source_id, observed_instance_id, state, last_message_at, last_message_token,
           observed_at, observation_expires_at)
        VALUES (?, ?, 'CONNECTED', ?, ?, now(), now() + INTERVAL '90 seconds')
            ON CONFLICT (source_id, observed_instance_id) DO UPDATE
           SET last_message_at    = EXCLUDED.last_message_at,
               last_message_token = EXCLUDED.last_message_token
        """,
        sourceId,
        instanceId,
        Timestamp.from(lastMessageAt),
        token);
  }

  /** The projected activity for a resource, or null when it has never been active. */
  private Instant activity(String resourceKind, UUID resourceId) {
    var rows =
        jdbc.queryForList(
            """
            SELECT last_event_at
              FROM activity.resource_activity
             WHERE resource_kind = ?
               AND resource_id = ?
            """,
            Timestamp.class,
            resourceKind,
            resourceId);
    return rows.isEmpty() ? null : rows.getFirst().toInstant();
  }

  /** View rows are keyed by layer plus time bucket, so they are counted by resource instead. */
  private int viewLedgerCount(UUID layerId) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
          FROM activity.recorded_activity
         WHERE activity_kind = 'LAYER_EVENT_VIEW'
           AND resource_kind = 'LAYER'
           AND resource_id = ?
        """,
        Integer.class,
        layerId);
  }

  private int ledgerCount(String activityKind, String activityId) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
          FROM activity.recorded_activity
         WHERE activity_kind = ?
           AND activity_id = ?
        """,
        Integer.class,
        activityKind,
        activityId);
  }

  private Instant updatedAt(UUID sourceId) {
    var value =
        jdbc.queryForObject(
            "SELECT updated_at FROM profiles.data_source WHERE id = ?", Timestamp.class, sourceId);
    return value == null ? null : value.toInstant();
  }

  private boolean columnExists(String schema, String table, String column) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1 FROM information_schema.columns
               WHERE table_schema = ? AND table_name = ? AND column_name = ?
            )
            """,
            Boolean.class,
            schema,
            table,
            column));
  }
}
