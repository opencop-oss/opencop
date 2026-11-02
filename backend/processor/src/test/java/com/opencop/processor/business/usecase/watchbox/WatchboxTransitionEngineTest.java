// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.watchbox;

import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.DETECTIONS_EMITTED;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.DETECTIONS_SUPPRESSED_DUPLICATE;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.DETECTIONS_SUPPRESSED_LOST_FOLD;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.DUPLICATE_DROPPED;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.ENTERED;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.FILTER_MATCH_GAINED;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.FILTER_MATCH_LOST;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.FROZEN_DROPPED;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.INSIDE_COMPARISONS;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.LEFT;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.REFRESHED;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.RESEEDED_EXPIRED;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.SEEDED;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.STALE_DROPPED;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.STALE_DROPPED_TRANSITION;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.SUPPRESSED_BY_DIRECTION;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.TRIPPED;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.SOURCE_ID;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.WATCHBOX_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.processor.business.model.watchbox.AcceptedDetection;
import com.opencop.processor.business.model.watchbox.WatchboxAlertOutboxRow;
import com.opencop.processor.business.model.watchbox.WatchboxObservation;
import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.business.model.watchbox.WatchboxStateLockResult;
import com.opencop.processor.business.model.watchbox.WatchboxStateRow;
import com.opencop.processor.business.model.watchbox.WatchboxTransition;
import com.opencop.processor.business.policy.watchbox.PermitAllWatchboxAdmissionGate;
import com.opencop.processor.business.policy.watchbox.WatchboxAdmissionGate;
import com.opencop.processor.business.policy.watchbox.WatchboxChurnBreaker;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxAlertOutboxRepository;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxDetectionRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxAlertOutboxRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxDetectionRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxSourceStatusRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxTransitionStateRepository;
import com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class WatchboxTransitionEngineTest {

  private static final Duration RETENTION = Duration.ofHours(1);
  private static final WatchboxStateKey KEY =
      new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "object-1");
  private static final UUID OTHER_WATCHBOX_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000b12");
  private static EmbeddedPostgres postgres;
  private static DataSource dataSource;
  private static JdbcTemplate jdbc;
  private static PostgresWatchboxTransitionStateRepository stateRepository;
  private MutableClock clock;

  @BeforeAll
  static void startPostgres() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    dataSource = postgres.getPostgresDatabase(Map.of("reWriteBatchedInserts", "true"));
    WatchboxPostgresTestSupport.migrateAndSeed(dataSource);
    jdbc = new JdbcTemplate(dataSource);
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox
          (id, name, direction, polygon_json, filter_json)
        SELECT ?, 'wb-test-box-2', 'BOTH', polygon_json, filter_json
        FROM watchbox.watchbox
        WHERE id = ?
        """,
        OTHER_WATCHBOX_ID,
        WATCHBOX_ID);
    stateRepository = new PostgresWatchboxTransitionStateRepository(jdbc);
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void reset() {
    WatchboxPostgresTestSupport.clearRuntimeRows(jdbc);
    WatchboxPostgresTestSupport.resetSeededWatchboxConfigEpoch(jdbc);
    clock = new MutableClock(Instant.parse("2026-07-16T12:00:00Z"));
  }

  @Test
  void expiredRowsReseedSilentlyRegardlessOfPriorDirection() {
    insertState(true, eventId(1), "2026-07-16T10:00:00Z", "2026-07-16T10:59:59Z");
    var outsideResult =
        engine(new CapturingSink())
            .process(List.of(observation(eventId(2), "2026-07-16T11:30:00Z", false)));

    assertThat(outsideResult.count(RESEEDED_EXPIRED)).isOne();
    assertThat(outsideResult.count(LEFT)).isZero();
    assertThat(outsideResult.emissions()).isEmpty();
    assertThat(readState().lastInside()).isFalse();

    WatchboxPostgresTestSupport.clearRuntimeRows(jdbc);
    insertState(false, eventId(3), "2026-07-16T10:00:00Z", "2026-07-16T10:59:59Z");
    var insideResult =
        engine(new CapturingSink())
            .process(List.of(observation(eventId(4), "2026-07-16T11:30:00Z", true)));

    assertThat(insideResult.count(RESEEDED_EXPIRED)).isOne();
    assertThat(insideResult.count(ENTERED)).isZero();
    assertThat(insideResult.emissions()).isEmpty();
    assertThat(readState().lastInside()).isTrue();
  }

  @Test
  void foldsEverySameBatchTransitionAgainstEvolvingState() {
    var sink = new CapturingSink();
    var absentResult =
        engine(sink)
            .process(
                List.of(
                    observation(eventId(1), "2026-07-16T11:00:00Z", false),
                    observation(eventId(2), "2026-07-16T11:00:01Z", true)));

    assertThat(absentResult.count(SEEDED)).isOne();
    assertThat(absentResult.emissions())
        .extracting(WatchboxTransition::kind)
        .containsExactly(WatchboxTransitionKind.ENTER);
    assertThat(readState().lastInside()).isTrue();

    sink.emissions.clear();
    var existingResult =
        engine(sink)
            .process(
                List.of(
                    observation(eventId(3), "2026-07-16T11:00:02Z", false),
                    observation(eventId(4), "2026-07-16T11:00:03Z", true)));

    assertThat(existingResult.emissions())
        .extracting(WatchboxTransition::kind)
        .containsExactly(WatchboxTransitionKind.LEAVE, WatchboxTransitionKind.ENTER);
    assertThat(sink.emissions).containsExactlyElementsOf(existingResult.emissions());
  }

  @Test
  void acceptedStationaryObservationRefreshesWallClockLifetime() {
    insertState(false, eventId(1), "2026-07-16T10:00:00Z", "2026-07-16T10:00:00Z");
    clock.set(Instant.parse("2026-07-16T10:59:00Z"));
    var refresh =
        engine(new CapturingSink())
            .process(List.of(observation(eventId(2), "2026-07-16T10:30:00Z", false)));

    assertThat(refresh.count(REFRESHED)).isOne();
    assertThat(readState().lastObservedAt()).isEqualTo(clock.instant());

    clock.set(Instant.parse("2026-07-16T11:30:00Z"));
    var transition =
        engine(new CapturingSink())
            .process(List.of(observation(eventId(3), "2026-07-16T11:00:00Z", true)));

    assertThat(transition.count(RESEEDED_EXPIRED)).isZero();
    assertThat(transition.count(ENTERED)).isOne();
  }

  @Test
  void strictExpiryBoundaryAndDirectionGatingAdvanceState() {
    insertState(true, eventId(1), "2026-07-16T10:00:00Z", "2026-07-16T11:00:00Z");
    var result =
        engine(new CapturingSink())
            .process(
                List.of(
                    observation(
                        eventId(2), "2026-07-16T11:30:00Z", false, WatchboxDirection.ENTER)));

    assertThat(result.count(RESEEDED_EXPIRED)).isZero();
    assertThat(result.count(LEFT)).isOne();
    assertThat(result.count(SUPPRESSED_BY_DIRECTION)).isOne();
    assertThat(result.emissions()).isEmpty();
    assertThat(readState().lastInside()).isFalse();
  }

  @Test
  void monotonicGuardUsesEventTimeThenUuidAndRejectedEventsRefreshNothing() {
    var observedAt = Instant.parse("2026-07-16T11:45:00Z");
    insertState(false, eventId(2), "2026-07-16T11:00:00Z", observedAt.toString());
    var result =
        engine(new CapturingSink())
            .process(
                List.of(
                    observation(eventId(1), "2026-07-16T11:00:00Z", true),
                    observation(eventId(2), "2026-07-16T11:00:00Z", true)));

    assertThat(result.count(STALE_DROPPED_TRANSITION)).isOne();
    assertThat(result.count(DUPLICATE_DROPPED)).isOne();
    assertThat(result.emissions()).isEmpty();
    assertThat(readState().lastObservedAt()).isEqualTo(observedAt);
    assertThat(readState().lastInside()).isFalse();

    var newer = observation(eventId(3), "2026-07-16T11:01:00Z", true);
    engine(new CapturingSink()).process(List.of(newer));
    var afterNewer = readState();
    clock.set(Instant.parse("2026-07-16T12:05:00Z"));
    var older =
        engine(new CapturingSink())
            .process(List.of(observation(eventId(4), "2026-07-16T11:00:30Z", false)));

    assertThat(older.count(STALE_DROPPED_TRANSITION)).isOne();
    assertThat(readState()).isEqualTo(afterNewer);
  }

  @Test
  void staleDropOfTransitionEvidenceCountsSeparatelyFromAgreeingLateArrivals() {
    var meterRegistry = new SimpleMeterRegistry();
    var engine = engine(new CapturingSink(), new ProcessorMetrics(meterRegistry));
    engine.process(List.of(observation(eventId(2), "2026-07-16T11:00:02Z", true)));

    // Inverted delivery of a crossing pair: the outside half reaches the fold after the inside
    // half it preceded, so dropping it discards the only evidence a crossing happened.
    var lost = engine.process(List.of(observation(eventId(1), "2026-07-16T11:00:01Z", false)));
    // An agreeing late arrival is the intended late-data/redelivery policy, not a loss signal.
    var agreeing = engine.process(List.of(observation(eventId(3), "2026-07-16T11:00:00Z", true)));

    assertThat(lost.count(STALE_DROPPED_TRANSITION)).isOne();
    assertThat(lost.count(STALE_DROPPED)).isZero();
    assertThat(agreeing.count(STALE_DROPPED)).isOne();
    assertThat(agreeing.count(STALE_DROPPED_TRANSITION)).isZero();
    assertThat(foldOutcomeCount(meterRegistry, "stale_dropped_transition")).isEqualTo(1.0);
    assertThat(foldOutcomeCount(meterRegistry, "stale_dropped")).isEqualTo(1.0);
  }

  private static double foldOutcomeCount(SimpleMeterRegistry registry, String outcome) {
    var counter =
        registry.find("opencop.watchbox.state.fold.outcome").tag("outcome", outcome).counter();
    return counter == null ? 0.0 : counter.count();
  }

  @Test
  void sinkFailureRollsBackStateMutation() {
    var failingSink = failingRepository("sink failed");
    var trippingKey = new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "object-2");
    var batch =
        List.of(
            observation(eventId(1), "2026-07-16T11:00:00Z", false),
            observation(eventId(2), "2026-07-16T11:00:01Z", true),
            new WatchboxObservation(
                trippingKey,
                eventId(3),
                Instant.parse("2026-07-16T11:00:02Z"),
                true,
                true,
                WatchboxDirection.BOTH,
                null));

    assertThatThrownBy(() -> engine(failingSink, new TrippingGate()).process(batch))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("sink failed");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM watchbox.watchbox_transition_state", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_source_status", Long.class))
        .isZero();
    assertThat(detectionCount()).isZero();
  }

  @Test
  void pollShapedAdjacentDayFactsCommitStateAtomically() {
    var secondKey = new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "object-adjacent-day");
    var result =
        engine(new PostgresWatchboxDetectionRepository(jdbc))
            .process(
                List.of(
                    observation(KEY, eventId(101), "2026-07-16T23:59:58Z", false),
                    observation(KEY, eventId(102), "2026-07-16T23:59:59Z", true),
                    observation(secondKey, eventId(103), "2026-07-17T00:00:00Z", false),
                    observation(secondKey, eventId(104), "2026-07-17T00:00:01Z", true)));

    assertThat(result.count(DETECTIONS_EMITTED)).isEqualTo(2);
    assertThat(result.emissions()).hasSize(2);
    assertThat(stateCount()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM watchbox.watchbox_transition_state WHERE last_inside",
                Long.class))
        .isEqualTo(2);
    assertThat(
            jdbc
                .queryForList(
                    """
                SELECT event_time
                FROM watchbox.watchbox_detection
                ORDER BY event_time
                """,
                    java.time.OffsetDateTime.class)
                .stream()
                .map(java.time.OffsetDateTime::toInstant)
                .toList())
        .containsExactly(
            Instant.parse("2026-07-16T23:59:59Z"), Instant.parse("2026-07-17T00:00:01Z"));
  }

  @Test
  void committedTripPersistsDegradedStatusAndFreezesOnlyItsScope() {
    var breaker = new WatchboxChurnBreaker(true, 2, RETENTION);
    var object2 = new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "object-2");
    var object3 = new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "object-3");
    engine(new CapturingSink(), breaker)
        .process(
            List.of(
                observation(KEY, eventId(1), "2026-07-16T11:00:00Z", false),
                observation(object2, eventId(2), "2026-07-16T11:00:00Z", false)));

    var trip =
        engine(new CapturingSink(), breaker)
            .process(List.of(observation(object3, eventId(3), "2026-07-16T11:00:01Z", true)));

    assertThat(trip.count(TRIPPED)).isOne();
    assertThat(
            jdbc.queryForMap(
                """
                SELECT status, reason_code, observed_count, cap
                FROM watchbox.watchbox_source_status
                WHERE watchbox_id = ? AND config_epoch = 1 AND source_id = ?
                """,
                WATCHBOX_ID,
                SOURCE_ID))
        .containsEntry("status", "DEGRADED")
        .containsEntry("reason_code", "WATCHBOX_OBJECT_ID_CHURN_DETECTED")
        .containsEntry("observed_count", 3L)
        .containsEntry("cap", 2L);
    assertThat(stateCount()).isEqualTo(2);

    var frozen =
        engine(new CapturingSink(), breaker)
            .process(List.of(observation(KEY, eventId(4), "2026-07-16T11:00:02Z", true)));
    assertThat(frozen.count(FROZEN_DROPPED)).isOne();
    assertThat(stateCount()).isEqualTo(2);

    var freshEpochKey = new WatchboxStateKey(WATCHBOX_ID, 2, SOURCE_ID, "object-1");
    var unrelatedKey = new WatchboxStateKey(OTHER_WATCHBOX_ID, 1, SOURCE_ID, "object-1");
    jdbc.update("UPDATE watchbox.watchbox SET config_epoch = 2 WHERE id = ?", WATCHBOX_ID);
    var unaffected =
        engine(new CapturingSink(), breaker)
            .process(
                List.of(
                    observation(freshEpochKey, eventId(5), "2026-07-16T11:00:03Z", false),
                    observation(unrelatedKey, eventId(6), "2026-07-16T11:00:03Z", false)));
    assertThat(unaffected.count(SEEDED)).isEqualTo(2);
    assertThat(stateCount()).isEqualTo(4);
    // The trip stays scoped: the fresh epoch and the unrelated watchbox carry no status row,
    // so the tripped scope's row (asserted above) is the table's only one.
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_source_status", Long.class))
        .isEqualTo(1L);
  }

  @Test
  void capPlusOneSingleBatchPersistsRowsAndTripWithRewriteEnabled() {
    var breaker = new WatchboxChurnBreaker(true, 50, RETENTION);
    var engine = engine(new CapturingSink(), breaker);
    var batch =
        java.util.stream.IntStream.rangeClosed(1, 51)
            .mapToObj(
                index ->
                    observation(
                        new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "cap-object-" + index),
                        eventId(1_000 + index),
                        Instant.parse("2026-07-16T11:00:00Z").plusSeconds(index).toString(),
                        false))
            .toList();

    var result = engine.process(batch);

    assertThat(result.count(SEEDED)).isEqualTo(50);
    assertThat(result.count(TRIPPED)).isOne();
    assertThat(stateCount()).isEqualTo(50);
    assertThat(
            jdbc.queryForMap(
                """
                SELECT status, reason_code, observed_count, cap
                FROM watchbox.watchbox_source_status
                WHERE watchbox_id = ? AND config_epoch = 1 AND source_id = ?
                """,
                WATCHBOX_ID,
                SOURCE_ID))
        .containsEntry("status", "DEGRADED")
        .containsEntry("reason_code", "WATCHBOX_OBJECT_ID_CHURN_DETECTED")
        .containsEntry("observed_count", 51L)
        .containsEntry("cap", 50L);

    var frozenKey = new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "cap-object-frozen");
    var frozen =
        engine.process(
            List.of(observation(frozenKey, eventId(2_000), "2026-07-16T11:02:00Z", false)));

    assertThat(frozen.count(FROZEN_DROPPED)).isOne();
    assertThat(stateCount()).isEqualTo(50);
    assertThat(currentRows(stateRepository.lockCurrentAndSelectForUpdate(List.of(frozenKey))))
        .isEmpty();
  }

  @Test
  void rolledBackTripLeavesNeitherStatusNorPhantomFreeze() {
    var breaker = new WatchboxChurnBreaker(true, 1, RETENTION);
    engine(new CapturingSink(), breaker)
        .process(List.of(observation(eventId(1), "2026-07-16T11:00:00Z", false)));
    var second = new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "object-2");
    var failingSink = failingRepository("sink failed after trip");

    assertThatThrownBy(
            () ->
                engine(failingSink, breaker)
                    .process(
                        List.of(observation(second, eventId(2), "2026-07-16T11:00:01Z", true))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("sink failed after trip");
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_source_status", Long.class))
        .isZero();

    var retried =
        engine(new CapturingSink(), breaker)
            .process(List.of(observation(second, eventId(3), "2026-07-16T11:00:02Z", true)));
    assertThat(retried.count(TRIPPED)).isOne();
    assertThat(retried.count(FROZEN_DROPPED)).isZero();
  }

  @Test
  void staleGenerationIsRejectedBeforeChurnStatusOrAdmissionStateChanges() {
    var breaker = new WatchboxChurnBreaker(true, 1, RETENTION);
    var engine = engine(new CapturingSink(), breaker);
    engine.process(List.of(observation(eventId(1), "2026-07-16T11:00:00Z", false)));
    jdbc.update("UPDATE watchbox.watchbox SET config_epoch = 2 WHERE id = ?", WATCHBOX_ID);
    var staleKey = new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "object-2");

    assertThatThrownBy(
            () ->
                engine.process(
                    List.of(observation(staleKey, eventId(2), "2026-07-16T11:00:01Z", true))))
        .isInstanceOf(WatchboxGenerationChangedException.class);

    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_source_status", Long.class))
        .isZero();
    var currentKey = new WatchboxStateKey(WATCHBOX_ID, 2, SOURCE_ID, "object-2");
    var current =
        engine.process(List.of(observation(currentKey, eventId(3), "2026-07-16T11:00:02Z", true)));
    assertThat(current.count(SEEDED)).isOne();
    assertThat(current.count(TRIPPED)).isZero();
  }

  @Test
  void causeTypedFoldUsesGeometryEdgesForMotionAndInsideReclassificationForFilterChanges() {
    var result =
        engine(new PostgresWatchboxDetectionRepository(jdbc))
            .process(
                List.of(
                    observation(eventId(1), "2026-07-16T11:00:00Z", false, false, "Object One"),
                    observation(eventId(2), "2026-07-16T11:00:01Z", true, true, "Object One"),
                    observation(eventId(3), "2026-07-16T11:00:02Z", true, false, "Object One"),
                    observation(eventId(4), "2026-07-16T11:00:03Z", true, true, "Object One"),
                    observation(eventId(5), "2026-07-16T11:00:04Z", false, false, "Object One")));

    assertThat(result.emissions())
        .extracting(WatchboxTransition::kind)
        .containsExactly(
            WatchboxTransitionKind.ENTER,
            WatchboxTransitionKind.FILTER_MATCH_LOST,
            WatchboxTransitionKind.FILTER_MATCH_GAINED,
            WatchboxTransitionKind.LEAVE);
    assertThat(result.count(ENTERED)).isOne();
    assertThat(result.count(LEFT)).isOne();
    assertThat(result.count(FILTER_MATCH_GAINED)).isOne();
    assertThat(result.count(FILTER_MATCH_LOST)).isOne();
    assertThat(result.count(INSIDE_COMPARISONS)).isEqualTo(2);
    assertThat(result.count(DETECTIONS_EMITTED)).isEqualTo(4);
    assertThat(
            jdbc.queryForList(
                """
                SELECT transition || ':' || object_display_name
                FROM watchbox.watchbox_detection
                ORDER BY event_time
                """,
                String.class))
        .containsExactly(
            "ENTER:Object One",
            "FILTER_MATCH_LOST:Object One",
            "FILTER_MATCH_GAINED:Object One",
            "LEAVE:Object One");
    assertThat(readState().lastGeometryInside()).isFalse();
    assertThat(readState().lastInside()).isFalse();
  }

  @Test
  void redeliveredObservationBatchIsInertAndDoesNotAddDetectionRows() {
    var engine = engine(new PostgresWatchboxDetectionRepository(jdbc));
    var batch =
        List.of(
            observation(eventId(1), "2026-07-16T11:00:00Z", false),
            observation(eventId(2), "2026-07-16T11:00:01Z", true));

    var first = engine.process(batch);
    var detectionsAfterFirst = detectionCount();
    var redelivery = engine.process(batch);

    assertThat(first.count(DETECTIONS_EMITTED)).isOne();
    assertThat(detectionsAfterFirst).isOne();
    assertThat(redelivery.count(STALE_DROPPED_TRANSITION)).isOne();
    assertThat(redelivery.count(DUPLICATE_DROPPED)).isOne();
    assertThat(redelivery.emissions()).isEmpty();
    assertThat(redelivery.count(DETECTIONS_EMITTED)).isZero();
    assertThat(detectionCount()).isEqualTo(detectionsAfterFirst);
  }

  @Test
  void preexistingDetectionSuppressesDuplicateWithoutRollingBackState() {
    var eventTime = Instant.parse("2026-07-16T11:00:01Z");
    var repository = new PostgresWatchboxDetectionRepository(jdbc);
    assertThat(
            repository.write(
                List.of(
                    new WatchboxTransition(
                        KEY, eventId(2), eventTime, WatchboxTransitionKind.ENTER, null))))
        .hasSize(1);

    var result =
        engine(repository)
            .process(
                List.of(
                    observation(eventId(1), "2026-07-16T11:00:00Z", false),
                    observation(eventId(2), eventTime.toString(), true)));

    assertThat(result.count(ENTERED)).isOne();
    assertThat(result.count(DETECTIONS_EMITTED)).isZero();
    assertThat(result.count(DETECTIONS_SUPPRESSED_DUPLICATE)).isOne();
    assertThat(result.count(DETECTIONS_SUPPRESSED_LOST_FOLD)).isZero();
    assertThat(readState().lastInside()).isTrue();
    assertThat(readState().lastEventId()).isEqualTo(eventId(2));
    assertThat(detectionCount()).isOne();
  }

  @Test
  void committedDetectionCreatesOutboxDeliveryWorkInTheSameTransaction() {
    var result =
        engine(
                new PostgresWatchboxDetectionRepository(jdbc),
                new PostgresWatchboxAlertOutboxRepository(jdbc))
            .process(
                List.of(
                    observation(eventId(1), "2026-07-16T11:00:00Z", false),
                    observation(eventId(2), "2026-07-16T11:00:01Z", true)));

    assertThat(result.count(DETECTIONS_EMITTED)).isOne();
    assertThat(
            jdbc.queryForList(
                "SELECT event_id || ':' || transition FROM watchbox.watchbox_alert_outbox",
                String.class))
        .containsExactly(eventId(2) + ":ENTER");
  }

  @Test
  void replayAgainstLostStateCreatesNoSecondOutboxRow() {
    var repository = new PostgresWatchboxDetectionRepository(jdbc);
    var outbox = new PostgresWatchboxAlertOutboxRepository(jdbc);
    var batch =
        List.of(
            observation(eventId(1), "2026-07-16T11:00:00Z", false),
            observation(eventId(2), "2026-07-16T11:00:01Z", true));
    var first = engine(repository, outbox).process(batch);

    // Losing the fold state (e.g. a crash between commit and cleanup) forces the redelivered
    // batch to re-emit the same detection identity as a fresh candidate; the detection insert
    // deduplicates it, so no second unit of delivery work may appear.
    jdbc.update("DELETE FROM watchbox.watchbox_transition_state");
    var replay = engine(repository, outbox).process(batch);

    assertThat(first.count(DETECTIONS_EMITTED)).isOne();
    assertThat(replay.count(DETECTIONS_EMITTED)).isZero();
    assertThat(replay.count(DETECTIONS_SUPPRESSED_DUPLICATE)).isOne();
    assertThat(outboxCount()).isOne();
  }

  @Test
  void rolledBackBatchLeavesNeitherDetectionsNorDeliveryWork() {
    var engine =
        engine(new PostgresWatchboxDetectionRepository(jdbc), failingOutbox("outbox down"));

    assertThatThrownBy(
            () ->
                engine.process(
                    List.of(
                        observation(eventId(1), "2026-07-16T11:00:00Z", false),
                        observation(eventId(2), "2026-07-16T11:00:01Z", true))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("outbox down");

    assertThat(detectionCount()).isZero();
    assertThat(outboxCount()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM watchbox.watchbox_transition_state", Long.class))
        .isZero();
  }

  @Test
  void replayedSequenceAfterEpochBumpProducesDetectionUnderEachEpoch() {
    var repository = new PostgresWatchboxDetectionRepository(jdbc);
    var epochTwoKey = new WatchboxStateKey(WATCHBOX_ID, 2, SOURCE_ID, "object-1");
    var seedTime = "2026-07-16T11:00:00Z";
    var edgeTime = "2026-07-16T11:00:01Z";

    var epochOne =
        engine(repository)
            .process(
                List.of(
                    observation(KEY, eventId(1), seedTime, false),
                    observation(KEY, eventId(2), edgeTime, true)));
    jdbc.update("UPDATE watchbox.watchbox SET config_epoch = 2 WHERE id = ?", WATCHBOX_ID);
    var epochTwo =
        engine(repository)
            .process(
                List.of(
                    observation(epochTwoKey, eventId(1), seedTime, false),
                    observation(epochTwoKey, eventId(2), edgeTime, true)));

    assertThat(epochOne.count(SEEDED)).isOne();
    assertThat(epochOne.count(DETECTIONS_EMITTED)).isOne();
    assertThat(epochTwo.count(SEEDED)).isOne();
    assertThat(epochTwo.count(DETECTIONS_EMITTED)).isOne();
    assertThat(
            jdbc.queryForList(
                """
                SELECT config_epoch || ':' || transition
                FROM watchbox.watchbox_detection
                ORDER BY config_epoch
                """,
                String.class))
        .containsExactly("1:ENTER", "2:ENTER");
  }

  @Test
  void simultaneousGeometryAndFilterChangesYieldExactlyOneMotionCandidateEach() {
    var result =
        engine(new CapturingSink())
            .process(
                List.of(
                    observation(eventId(1), "2026-07-16T11:00:00Z", false, false),
                    observation(eventId(2), "2026-07-16T11:00:01Z", true, true),
                    observation(eventId(3), "2026-07-16T11:00:02Z", false, false)));

    assertThat(result.emissions())
        .extracting(WatchboxTransition::kind)
        .containsExactly(WatchboxTransitionKind.ENTER, WatchboxTransitionKind.LEAVE);
    assertThat(result.count(ENTERED)).isOne();
    assertThat(result.count(LEFT)).isOne();
    assertThat(result.count(FILTER_MATCH_GAINED)).isZero();
    assertThat(result.count(FILTER_MATCH_LOST)).isZero();
  }

  @Test
  void directionGatesMotionOnlyAndNeverFilterMatchCandidates() {
    var result =
        engine(new CapturingSink())
            .process(
                List.of(
                    observation(
                        eventId(1), "2026-07-16T11:00:00Z", false, false, WatchboxDirection.LEAVE),
                    observation(
                        eventId(2), "2026-07-16T11:00:01Z", true, true, WatchboxDirection.LEAVE),
                    observation(
                        eventId(3), "2026-07-16T11:00:02Z", true, false, WatchboxDirection.LEAVE)));

    assertThat(result.count(ENTERED)).isOne();
    assertThat(result.count(SUPPRESSED_BY_DIRECTION)).isOne();
    assertThat(result.count(FILTER_MATCH_LOST)).isOne();
    assertThat(result.emissions())
        .extracting(WatchboxTransition::kind)
        .containsExactly(WatchboxTransitionKind.FILTER_MATCH_LOST);
  }

  @Test
  void seedsAndExpiredReseedsPersistBothBooleansSilently() {
    var seed =
        engine(new CapturingSink())
            .process(List.of(observation(eventId(1), "2026-07-16T11:00:00Z", true, false)));

    assertThat(seed.count(SEEDED)).isOne();
    assertThat(seed.emissions()).isEmpty();
    assertThat(readState().lastGeometryInside()).isTrue();
    assertThat(readState().lastInside()).isFalse();

    insertState(true, true, eventId(2), "2026-07-16T10:00:00Z", "2026-07-16T10:59:59Z");
    var reseed =
        engine(new CapturingSink())
            .process(List.of(observation(eventId(3), "2026-07-16T11:30:00Z", true, false)));

    assertThat(reseed.count(RESEEDED_EXPIRED)).isOne();
    assertThat(reseed.emissions()).isEmpty();
    assertThat(readState().lastGeometryInside()).isTrue();
    assertThat(readState().lastInside()).isFalse();
  }

  @Test
  void nonmatchingInsideObservationRefreshesLifetimeAndBreakerMembership() {
    var breaker = new WatchboxChurnBreaker(true, 1, RETENTION);
    engine(new CapturingSink(), breaker)
        .process(List.of(observation(eventId(1), "2026-07-16T11:00:00Z", true, false)));
    clock.set(Instant.parse("2026-07-16T12:30:00Z"));

    var refresh =
        engine(new CapturingSink(), breaker)
            .process(List.of(observation(eventId(2), "2026-07-16T11:30:00Z", true, false)));

    assertThat(refresh.count(REFRESHED)).isOne();
    assertThat(refresh.count(RESEEDED_EXPIRED)).isZero();
    assertThat(refresh.count(TRIPPED)).isZero();
    assertThat(readState().lastObservedAt()).isEqualTo(clock.instant());
  }

  @Test
  void insideComparisonsCountsOnlyOrderedNonSeedInsideToInsideComparisons() {
    insertState(false, false, eventId(1), "2026-07-16T11:00:00Z", "2026-07-16T11:45:00Z");
    var result =
        engine(new CapturingSink())
            .process(
                List.of(
                    observation(eventId(2), "2026-07-16T11:00:01Z", true, false),
                    observation(eventId(3), "2026-07-16T11:00:02Z", true, false),
                    observation(eventId(4), "2026-07-16T11:00:03Z", true, true),
                    observation(eventId(4), "2026-07-16T11:00:03Z", true, true),
                    observation(eventId(5), "2026-07-16T10:59:59Z", true, false)));

    assertThat(result.count(INSIDE_COMPARISONS)).isEqualTo(2);
    assertThat(result.count(REFRESHED)).isEqualTo(2);
    assertThat(result.count(FILTER_MATCH_GAINED)).isOne();
    assertThat(result.count(DUPLICATE_DROPPED)).isOne();
    // Sorted first within the batch, eventId(5) is compared against the stored row it agrees
    // with, so it is a plain stale drop rather than discarded transition evidence.
    assertThat(result.count(STALE_DROPPED)).isOne();
  }

  @Test
  void stateShapesRejectQualifyingPresenceOutsideGeometry() {
    assertThatThrownBy(
            () ->
                new WatchboxObservation(
                    KEY,
                    eventId(1),
                    Instant.parse("2026-07-16T11:00:00Z"),
                    false,
                    true,
                    WatchboxDirection.BOTH,
                    null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new WatchboxStateRow(
                    KEY,
                    true,
                    false,
                    eventId(1),
                    Instant.parse("2026-07-16T11:00:00Z"),
                    Instant.parse("2026-07-16T11:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private WatchboxTransitionEngine engine(WatchboxDetectionRepository sink) {
    return engine(sink, new PermitAllWatchboxAdmissionGate());
  }

  private WatchboxTransitionEngine engine(
      WatchboxDetectionRepository sink, WatchboxAlertOutboxRepository outbox) {
    return engine(sink, outbox, new PermitAllWatchboxAdmissionGate(), ProcessorMetrics.noop());
  }

  private WatchboxTransitionEngine engine(
      WatchboxDetectionRepository sink, WatchboxAdmissionGate admissionGate) {
    return engine(sink, admissionGate, ProcessorMetrics.noop());
  }

  private WatchboxTransitionEngine engine(
      WatchboxDetectionRepository sink, ProcessorMetrics metrics) {
    return engine(sink, new PermitAllWatchboxAdmissionGate(), metrics);
  }

  private WatchboxTransitionEngine engine(
      WatchboxDetectionRepository sink,
      WatchboxAdmissionGate admissionGate,
      ProcessorMetrics metrics) {
    return engine(sink, new CapturingOutbox(), admissionGate, metrics);
  }

  private WatchboxTransitionEngine engine(
      WatchboxDetectionRepository sink,
      WatchboxAlertOutboxRepository outbox,
      WatchboxAdmissionGate admissionGate,
      ProcessorMetrics metrics) {
    return new WatchboxTransitionEngine(
        stateRepository,
        new PostgresWatchboxSourceStatusRepository(jdbc),
        sink,
        outbox,
        admissionGate,
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
        clock,
        RETENTION,
        metrics);
  }

  private void insertState(
      boolean inside, UUID eventId, String effectiveEventTime, String observedAt) {
    insertState(inside, inside, eventId, effectiveEventTime, observedAt);
  }

  private void insertState(
      boolean geometryInside,
      boolean inside,
      UUID eventId,
      String effectiveEventTime,
      String observedAt) {
    stateRepository.upsertAll(
        List.of(
            new WatchboxStateRow(
                KEY,
                inside,
                geometryInside,
                eventId,
                Instant.parse(effectiveEventTime),
                Instant.parse(observedAt))));
  }

  private WatchboxStateRow readState() {
    return currentRows(stateRepository.lockCurrentAndSelectForUpdate(List.of(KEY))).get(KEY);
  }

  private static Map<WatchboxStateKey, WatchboxStateRow> currentRows(
      WatchboxStateLockResult result) {
    return switch (result) {
      case WatchboxStateLockResult.Current(var rows) -> rows;
      case WatchboxStateLockResult.GenerationChanged _ ->
          throw new AssertionError("expected current Watchbox generation");
    };
  }

  private long stateCount() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM watchbox.watchbox_transition_state", Long.class);
  }

  private long detectionCount() {
    return jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_detection", Long.class);
  }

  private long outboxCount() {
    return jdbc.queryForObject("SELECT count(*) FROM watchbox.watchbox_alert_outbox", Long.class);
  }

  private static WatchboxObservation observation(UUID eventId, String eventTime, boolean inside) {
    return observation(eventId, eventTime, inside, WatchboxDirection.BOTH);
  }

  private static WatchboxObservation observation(
      UUID eventId, String eventTime, boolean geometryInside, boolean inside) {
    return observation(eventId, eventTime, geometryInside, inside, WatchboxDirection.BOTH);
  }

  private static WatchboxObservation observation(
      UUID eventId,
      String eventTime,
      boolean geometryInside,
      boolean inside,
      String objectDisplayName) {
    return new WatchboxObservation(
        KEY,
        eventId,
        Instant.parse(eventTime),
        geometryInside,
        inside,
        WatchboxDirection.BOTH,
        objectDisplayName);
  }

  private static WatchboxObservation observation(
      UUID eventId,
      String eventTime,
      boolean geometryInside,
      boolean inside,
      WatchboxDirection direction) {
    return new WatchboxObservation(
        KEY, eventId, Instant.parse(eventTime), geometryInside, inside, direction, null);
  }

  private static WatchboxObservation observation(
      UUID eventId, String eventTime, boolean inside, WatchboxDirection direction) {
    return observation(KEY, eventId, eventTime, inside, direction);
  }

  private static WatchboxObservation observation(
      WatchboxStateKey key, UUID eventId, String eventTime, boolean inside) {
    return observation(key, eventId, eventTime, inside, WatchboxDirection.BOTH);
  }

  private static WatchboxObservation observation(
      WatchboxStateKey key,
      UUID eventId,
      String eventTime,
      boolean inside,
      WatchboxDirection direction) {
    return new WatchboxObservation(
        key, eventId, Instant.parse(eventTime), inside, inside, direction, null);
  }

  private static UUID eventId(long value) {
    return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value));
  }

  private static final class CapturingSink implements WatchboxDetectionRepository {
    private final List<WatchboxTransition> emissions = new ArrayList<>();

    @Override
    public List<AcceptedDetection> write(List<WatchboxTransition> batch) {
      emissions.addAll(batch);
      return batch.stream().map(e -> new AcceptedDetection(UUID.randomUUID(), e)).toList();
    }

    @Override
    public int deleteExpiredBatch(Instant cutoff, int batchSize) {
      return 0;
    }
  }

  private static final class CapturingOutbox implements WatchboxAlertOutboxRepository {
    private final List<WatchboxTransition> inserted = new ArrayList<>();

    @Override
    public void insertAll(List<AcceptedDetection> acceptedDetections) {
      inserted.addAll(acceptedDetections.stream().map(AcceptedDetection::transition).toList());
    }

    @Override
    public List<WatchboxAlertOutboxRow> claimBatch(int batchSize) {
      return List.of();
    }

    @Override
    public void deleteAll(List<Long> outboxIds) {}

    @Override
    public Optional<Instant> oldestCreatedAt() {
      return Optional.empty();
    }
  }

  private static WatchboxAlertOutboxRepository failingOutbox(String message) {
    return new WatchboxAlertOutboxRepository() {
      @Override
      public void insertAll(List<AcceptedDetection> acceptedDetections) {
        throw new IllegalStateException(message);
      }

      @Override
      public List<WatchboxAlertOutboxRow> claimBatch(int batchSize) {
        return List.of();
      }

      @Override
      public void deleteAll(List<Long> outboxIds) {}

      @Override
      public Optional<Instant> oldestCreatedAt() {
        return Optional.empty();
      }
    };
  }

  private static WatchboxDetectionRepository failingRepository(String message) {
    return new WatchboxDetectionRepository() {
      @Override
      public List<AcceptedDetection> write(List<WatchboxTransition> emissions) {
        throw new IllegalStateException(message);
      }

      @Override
      public int deleteExpiredBatch(Instant cutoff, int batchSize) {
        return 0;
      }
    };
  }

  private static final class TrippingGate implements WatchboxAdmissionGate {
    private static final AdmissionBatch BATCH = new AdmissionBatch() {};

    @Override
    public AdmissionBatch begin() {
      return BATCH;
    }

    @Override
    public Admission admit(
        AdmissionBatch batch,
        com.opencop.processor.business.model.watchbox.WatchboxScopeKey scope,
        String objectId,
        Instant observedAt) {
      return objectId.equals("object-2") ? Admission.TRIPPED : Admission.ADMITTED;
    }

    @Override
    public void confirm(AdmissionBatch batch) {}

    @Override
    public void release(AdmissionBatch batch) {}

    @Override
    public long cap() {
      return 10;
    }
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void set(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
