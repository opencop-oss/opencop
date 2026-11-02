// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.SOURCE_ID;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.WATCHBOX_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.processor.business.model.watchbox.AcceptedDetection;
import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.business.model.watchbox.WatchboxTransition;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresWatchboxDetectionRepositoryTest {

  private static EmbeddedPostgres postgres;
  private static DataSource dataSource;
  private static JdbcTemplate jdbc;
  private static PostgresWatchboxDetectionRepository repository;

  @BeforeAll
  static void startPostgres() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    dataSource = postgres.getPostgresDatabase(Map.of("reWriteBatchedInserts", "true"));
    WatchboxPostgresTestSupport.migrateAndSeed(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    repository = new PostgresWatchboxDetectionRepository(jdbc);
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void clearRows() {
    WatchboxPostgresTestSupport.clearRuntimeRows(jdbc);
  }

  @Test
  void writesAllRowsWithAlignedColumnsAndNullableDisplayName() {
    var enter = transition(1, "object-1", WatchboxTransitionKind.ENTER, "Object One");
    var lost = transition(2, "object-2", WatchboxTransitionKind.FILTER_MATCH_LOST, null);

    assertThat(repository.write(List.of(lost, enter)))
        .extracting(AcceptedDetection::transition)
        .containsExactlyInAnyOrder(lost, enter);
    assertThat(readRows())
        .containsExactly(
            Map.of(
                "object_id",
                "object-1",
                "event_id",
                eventId(1),
                "transition",
                "ENTER",
                "event_time",
                Timestamp.from(eventTime(1)),
                "object_display_name",
                "Object One"),
            nullableRow("object-2", eventId(2), "FILTER_MATCH_LOST", eventTime(2)));
  }

  @Test
  void preExistingDedupeKeyReturnsOneLessWithoutThrowing() {
    var enter = transition(1, "object-1", WatchboxTransitionKind.ENTER, "Object One");
    var leave = transition(2, "object-1", WatchboxTransitionKind.LEAVE, "Object One");
    insertDirectly(enter);

    // Only the accepted (novel) row comes back: the deduplicated candidate must never
    // become delivery work.
    assertThat(repository.write(List.of(enter, leave)))
        .extracting(AcceptedDetection::transition)
        .containsExactly(leave);
    assertThat(readRows()).hasSize(2);
  }

  @Test
  void craftedIntraBatchDuplicateInsertsOnce() {
    var enter = transition(1, "object-1", WatchboxTransitionKind.ENTER, "Object One");

    assertThat(repository.write(List.of(enter, enter)))
        .extracting(AcceptedDetection::transition)
        .containsExactly(enter);
    assertThat(readRows()).hasSize(1);
  }

  @Test
  void sameReplayFieldsAtDifferentEventTimesDedupeToOneDetection() {
    // The unique index is (watchbox_id, config_epoch, event_id, transition): event_id determines
    // event_time in honest input, so a same-id re-emission with a shifted timestamp is producer
    // bad data and must not raise a second alert. The partitioned ancestor of this schema kept
    // both rows only because the partition key forced event_time into its unique index.
    var first =
        transitionAt(
            "same-replay-fields",
            eventId(10),
            Instant.parse("2026-07-18T23:59:59Z"),
            WatchboxTransitionKind.ENTER,
            null);
    var invalidSourceCorrection =
        transitionAt(
            "same-replay-fields",
            eventId(10),
            Instant.parse("2026-07-19T00:00:00Z"),
            WatchboxTransitionKind.ENTER,
            null);

    assertThat(repository.write(List.of(first, invalidSourceCorrection)))
        .extracting(AcceptedDetection::transition)
        .hasSize(1);
    assertThat(readRows()).hasSize(1);
  }

  @Test
  void adjacentMidnightBatchReturnsExactCount() {
    var beforeMidnight =
        transitionAt(
            "before-midnight",
            eventId(11),
            Instant.parse("2026-07-18T23:59:59Z"),
            WatchboxTransitionKind.LEAVE,
            null);
    var atMidnight =
        transitionAt(
            "at-midnight",
            eventId(12),
            Instant.parse("2026-07-19T00:00:00Z"),
            WatchboxTransitionKind.ENTER,
            null);

    assertThat(repository.write(List.of(atMidnight, beforeMidnight)))
        .extracting(AcceptedDetection::transition)
        .containsExactlyInAnyOrder(atMidnight, beforeMidnight);
    assertThat(readRows()).hasSize(2);
  }

  @Test
  void emptyWriteReturnsNothingWithoutDatabaseWork() {
    assertThat(repository.write(List.of())).extracting(AcceptedDetection::transition).isEmpty();
    assertThat(readRows()).isEmpty();
  }

  @Test
  void write_returnsStorageIdsMatchingRows() {
    var enter = transition(1, "object-1", WatchboxTransitionKind.ENTER, "Object One");
    var leave = transition(2, "object-2", WatchboxTransitionKind.LEAVE, null);

    var accepted = repository.write(List.of(leave, enter));

    assertThat(accepted).hasSize(2);
    for (var row : accepted) {
      assertThat(row.id())
          .isEqualTo(
              jdbc.queryForObject(
                  "SELECT id FROM watchbox.watchbox_detection WHERE event_id = ?",
                  UUID.class,
                  row.transition().eventId()));
    }
  }

  private static List<Map<String, Object>> readRows() {
    return jdbc.queryForList(
        """
        SELECT object_id, event_id, transition, event_time, object_display_name
        FROM watchbox.watchbox_detection
        ORDER BY event_id
        """);
  }

  private static Map<String, Object> nullableRow(
      String objectId, UUID eventId, String transition, Instant eventTime) {
    var row = new java.util.LinkedHashMap<String, Object>();
    row.put("object_id", objectId);
    row.put("event_id", eventId);
    row.put("transition", transition);
    row.put("event_time", Timestamp.from(eventTime));
    row.put("object_display_name", null);
    return row;
  }

  private static void insertDirectly(WatchboxTransition transition) {
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox_detection
          (watchbox_id, config_epoch, source_id, object_id, event_id,
           transition, event_time, object_display_name)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        transition.key().watchboxId(),
        transition.key().configEpoch(),
        transition.key().sourceId(),
        transition.key().objectId(),
        transition.eventId(),
        transition.kind().name(),
        Timestamp.from(transition.effectiveEventTime()),
        transition.objectDisplayName());
  }

  private static WatchboxTransition transition(
      long value, String objectId, WatchboxTransitionKind kind, String displayName) {
    return transitionAt(objectId, eventId(value), eventTime(value), kind, displayName);
  }

  private static WatchboxTransition transitionAt(
      String objectId,
      UUID eventId,
      Instant eventTime,
      WatchboxTransitionKind kind,
      String displayName) {
    return new WatchboxTransition(
        new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, objectId),
        eventId,
        eventTime,
        kind,
        displayName);
  }

  private static UUID eventId(long value) {
    return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value));
  }

  private static Instant eventTime(long value) {
    return Instant.parse("2026-07-18T12:00:00Z").plusSeconds(value);
  }
}
