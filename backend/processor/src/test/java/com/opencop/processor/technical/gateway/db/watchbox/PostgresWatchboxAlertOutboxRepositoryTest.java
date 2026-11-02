// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.SOURCE_ID;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.WATCHBOX_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.model.watchbox.WatchboxAlertEvent;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.processor.business.model.watchbox.AcceptedDetection;
import com.opencop.processor.business.model.watchbox.WatchboxAlertOutboxRow;
import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.business.model.watchbox.WatchboxTransition;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PostgresWatchboxAlertOutboxRepositoryTest {

  private static EmbeddedPostgres postgres;
  private static DataSource dataSource;
  private static JdbcTemplate jdbc;
  private static PostgresWatchboxAlertOutboxRepository repository;
  private static TransactionTemplate transactionTemplate;

  @BeforeAll
  static void startPostgres() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    dataSource = postgres.getPostgresDatabase();
    WatchboxPostgresTestSupport.migrateAndSeed(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    repository = new PostgresWatchboxAlertOutboxRepository(jdbc);
    transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void clearRows() {
    WatchboxPostgresTestSupport.resetSeededWatchboxConfigEpoch(jdbc);
    WatchboxPostgresTestSupport.clearRuntimeRows(jdbc);
  }

  @Test
  void insertedRowsClaimInIdOrderCarryingTheFullWireEvent() {
    var enter = transition(1, "object-1", WatchboxTransitionKind.ENTER, "Object One");
    var leave = transition(2, "object-1", WatchboxTransitionKind.LEAVE);
    var enterId = UUID.fromString("00000000-0000-0000-0000-00000000dd01");
    var leaveId = UUID.fromString("00000000-0000-0000-0000-00000000dd02");
    repository.insertAll(
        List.of(new AcceptedDetection(enterId, enter), new AcceptedDetection(leaveId, leave)));

    var claimed = repository.claimBatch(10);

    assertThat(claimed).hasSize(2);
    assertThat(claimed.get(0).id()).isLessThan(claimed.get(1).id());
    assertThat(claimed.stream().map(WatchboxAlertOutboxRow::event))
        .containsExactly(
            new WatchboxAlertEvent(
                WATCHBOX_ID,
                1,
                SOURCE_ID,
                "object-1",
                eventId(1),
                eventTime(1),
                WatchboxTransitionKind.ENTER,
                enterId,
                "Object One"),
            new WatchboxAlertEvent(
                WATCHBOX_ID,
                1,
                SOURCE_ID,
                "object-1",
                eventId(2),
                eventTime(2),
                WatchboxTransitionKind.LEAVE,
                leaveId,
                null));
  }

  @Test
  void claimBatchHonorsTheLimit() {
    repository.insertAll(
        List.of(
                transition(1, "object-1", WatchboxTransitionKind.ENTER),
                transition(2, "object-2", WatchboxTransitionKind.ENTER),
                transition(3, "object-3", WatchboxTransitionKind.ENTER))
            .stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());

    assertThat(repository.claimBatch(2)).hasSize(2);
  }

  @Test
  void rowsLockedByAnotherClaimantAreSkippedNotWaitedOn() throws Exception {
    repository.insertAll(
        List.of(transition(1, "object-1", WatchboxTransitionKind.ENTER)).stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());

    try (Connection claimant = dataSource.getConnection()) {
      claimant.setAutoCommit(false);
      try (var statement = claimant.createStatement()) {
        statement.execute(
            "SELECT id FROM watchbox.watchbox_alert_outbox ORDER BY id FOR UPDATE SKIP LOCKED");
      }

      // The concurrent claimant holds the row lock: this claim must return empty immediately
      // instead of blocking behind it.
      assertThat(repository.claimBatch(10)).isEmpty();

      claimant.rollback();
    }

    // The claimant's rollback releases the row for the next drain.
    assertThat(repository.claimBatch(10)).hasSize(1);
  }

  @Test
  void claimHoldsTheCurrentParentGenerationLockThroughTheTransaction() {
    repository.insertAll(
        List.of(transition(1, "object-1", WatchboxTransitionKind.ENTER)).stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());

    transactionTemplate.executeWithoutResult(
        _ -> {
          assertThat(repository.claimBatch(10)).hasSize(1);

          try (Connection updater = dataSource.getConnection()) {
            updater.setAutoCommit(false);
            try (var statement = updater.createStatement()) {
              statement.execute("SET LOCAL lock_timeout = '200ms'");
              assertThatThrownBy(
                      () ->
                          statement.executeUpdate(
                              "UPDATE watchbox.watchbox SET config_epoch = 2 WHERE id = '"
                                  + WATCHBOX_ID
                                  + "'"))
                  .isInstanceOfSatisfying(
                      SQLException.class,
                      failure -> assertThat(failure.getSQLState()).isEqualTo("55P03"));
            } finally {
              updater.rollback();
            }
          } catch (SQLException failure) {
            throw new AssertionError("failed to exercise concurrent epoch update", failure);
          }
        });

    assertThat(
            jdbc.update("UPDATE watchbox.watchbox SET config_epoch = 2 WHERE id = ?", WATCHBOX_ID))
        .isOne();
  }

  @Test
  void deleteRemovesOnlyTheGivenIds() {
    repository.insertAll(
        List.of(
                transition(1, "object-1", WatchboxTransitionKind.ENTER),
                transition(2, "object-2", WatchboxTransitionKind.ENTER))
            .stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    var claimed = repository.claimBatch(10);

    repository.deleteAll(List.of(claimed.get(0).id()));

    var remaining = repository.claimBatch(10);
    assertThat(remaining).hasSize(1);
    assertThat(remaining.getFirst().id()).isEqualTo(claimed.get(1).id());
  }

  @Test
  void oldestCreatedAtIsEmptyForADrainedOutbox() {
    assertThat(repository.oldestCreatedAt()).isEmpty();
  }

  @Test
  void oldestCreatedAtReportsTheEarliestPendingRow() {
    repository.insertAll(
        List.of(transition(1, "object-1", WatchboxTransitionKind.ENTER)).stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());
    repository.insertAll(
        List.of(transition(2, "object-2", WatchboxTransitionKind.ENTER)).stream()
            .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
            .toList());

    var earliest =
        jdbc.queryForObject(
            "SELECT min(created_at) FROM watchbox.watchbox_alert_outbox", Timestamp.class);

    assertThat(earliest).isNotNull();
    assertThat(repository.oldestCreatedAt()).contains(earliest.toInstant());
  }

  @Test
  void duplicateDetectionIdentityFailsLoudlyInsteadOfDuplicatingDeliveryWork() {
    var enter = transition(1, "object-1", WatchboxTransitionKind.ENTER);
    repository.insertAll(
        List.of(enter).stream().map(t -> new AcceptedDetection(UUID.randomUUID(), t)).toList());

    // Outbox rows exist only for freshly accepted detections, so this can never happen in
    // correct operation; the unique index turns the broken invariant into a loud failure.
    assertThatThrownBy(
            () ->
                repository.insertAll(
                    List.of(enter).stream()
                        .map(t -> new AcceptedDetection(UUID.randomUUID(), t))
                        .toList()))
        .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void claimBatch_whenRowPredatesTheColumns_returnsNullHandles() {
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox_alert_outbox
          (watchbox_id, config_epoch, source_id, object_id, event_id, transition, event_time)
        VALUES (?, 1, ?, 'legacy-object', ?, 'ENTER', ?)
        """,
        WATCHBOX_ID,
        SOURCE_ID,
        eventId(1),
        Timestamp.from(eventTime(1)));

    assertThat(repository.claimBatch(10))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.event().detectionId()).isNull();
              assertThat(row.event().objectDisplayName()).isNull();
            });
  }

  private static WatchboxTransition transition(
      long value, String objectId, WatchboxTransitionKind kind) {
    return transition(value, objectId, kind, null);
  }

  private static WatchboxTransition transition(
      long value, String objectId, WatchboxTransitionKind kind, String displayName) {
    return new WatchboxTransition(
        new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, objectId),
        eventId(value),
        eventTime(value),
        kind,
        displayName);
  }

  private static UUID eventId(long value) {
    return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value));
  }

  private static Instant eventTime(long value) {
    return Instant.parse("2026-08-14T12:00:00Z").plusSeconds(value);
  }
}
