// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.watchbox;

import static com.opencop.core.sdk.model.watchbox.WatchboxFilterOperation.EQUALS;
import static com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome.TRIPPED;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.SOURCE_ID;
import static com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport.WATCHBOX_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.model.watchbox.WatchboxFilterPredicate;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusReason;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.Timestampz;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.processor.business.model.watchbox.AcceptedDetection;
import com.opencop.processor.business.model.watchbox.WatchboxBatchOutcome;
import com.opencop.processor.business.model.watchbox.WatchboxBatchResult;
import com.opencop.processor.business.model.watchbox.WatchboxObservation;
import com.opencop.processor.business.model.watchbox.WatchboxRuntimeSnapshot;
import com.opencop.processor.business.model.watchbox.WatchboxRuntimeTarget;
import com.opencop.processor.business.model.watchbox.WatchboxStateKey;
import com.opencop.processor.business.model.watchbox.WatchboxTransition;
import com.opencop.processor.business.policy.watchbox.PermitAllWatchboxAdmissionGate;
import com.opencop.processor.business.policy.watchbox.WatchboxChurnBreaker;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxAlertDrainSignal;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxDetectionRepository;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxRuntimeConfigGateway;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxAlertOutboxRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxDetectionRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxRuntimeConfigRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxSourceStatusRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxTransitionStateRepository;
import com.opencop.processor.technical.gateway.db.watchbox.WatchboxPostgresTestSupport;
import com.opencop.processor.technical.gateway.db.watchbox.WatchboxRuntimeConfigCache;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.geojson.Feature;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class WatchboxEventHandlerTest {

  private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");
  private static final Duration LIVE_WINDOW = Duration.ofMinutes(10);
  private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);
  private static final UUID OTHER_SOURCE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000005099");
  private static final UUID OTHER_WATCHBOX_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000b99");

  private static EmbeddedPostgres postgres;
  private static DataSource dataSource;
  private static JdbcTemplate jdbc;

  @BeforeAll
  static void startPostgres() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    dataSource = postgres.getPostgresDatabase();
    WatchboxPostgresTestSupport.migrateAndSeed(dataSource);
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterAll
  static void stopPostgres() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void clearState() {
    WatchboxPostgresTestSupport.clearRuntimeRows(jdbc);
    WatchboxPostgresTestSupport.resetSeededWatchboxConfigEpoch(jdbc);
    jdbc.update(
        "UPDATE watchbox.watchbox SET direction = 'BOTH', enabled = true WHERE id = ?",
        WATCHBOX_ID);
    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "NONE");
  }

  @Test
  void emptySnapshotCountsWholeBatchWithoutParsing() {
    var fixture = fixture(WatchboxRuntimeSnapshot.empty());

    fixture.handler().process(List.of(new Feature(), new Feature()));

    assertThat(fixture.guard("unwatched_source")).isEqualTo(2.0);
    assertThat(fixture.guard("unparseable")).isZero();
    verifyNoInteractions(fixture.engine());
  }

  @Test
  void unwatchedSourceWinsBeforeImportGuard() {
    var fixture =
        fixture(snapshot(target(WATCHBOX_ID, WatchboxDirection.BOTH, null, emptyFilter())));
    var feature = feature(OTHER_SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5);
    properties(feature).setImportId(UUID.randomUUID());

    fixture.handler().process(List.of(feature));

    assertThat(fixture.guard("unwatched_source")).isOne();
    assertThat(fixture.guard("import")).isZero();
    verifyNoInteractions(fixture.engine());
  }

  @Test
  void unparseableSourcePeekDoesNotReachFullParsingOrEngine() {
    var fixture =
        fixture(snapshot(target(WATCHBOX_ID, WatchboxDirection.BOTH, null, emptyFilter())));
    var feature = new Feature();
    feature.setProperty(OpenCopPropertiesParser.OPENCOP_NODE, Map.of("source", "not-a-uuid"));

    fixture.handler().process(List.of(feature));

    assertThat(fixture.guard("unparseable")).isOne();
    verifyNoInteractions(fixture.engine());
  }

  @Test
  void appliesGuardsInOrderAndAcceptsBothWindowBoundaries() {
    var fixture =
        fixture(snapshot(target(WATCHBOX_ID, WatchboxDirection.BOTH, null, emptyFilter())));
    var imported = feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5);
    properties(imported).setImportId(UUID.randomUUID());
    var nonPoint = feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5);
    nonPoint.setGeometry(null);
    var missingIdentity = feature(SOURCE_ID, NOW, false, true, "friendly", 0.5, 0.5);
    var stale =
        feature(SOURCE_ID, NOW.minus(LIVE_WINDOW).minusMillis(1), true, true, "friendly", 0.5, 0.5);
    var staleBoundary =
        feature(SOURCE_ID, NOW.minus(LIVE_WINDOW), true, true, "friendly", 0.5, 0.5);
    var future =
        feature(
            SOURCE_ID, NOW.plus(FUTURE_TOLERANCE).plusMillis(1), true, true, "friendly", 0.5, 0.5);
    var futureBoundary =
        feature(SOURCE_ID, NOW.plus(FUTURE_TOLERANCE), true, true, "friendly", 0.5, 0.5);
    var missingTimestamp = feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5);
    properties(missingTimestamp).setTimestampz(null);
    var normalAfterFuture = feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5);

    fixture
        .handler()
        .process(
            List.of(
                imported,
                nonPoint,
                missingIdentity,
                stale,
                staleBoundary,
                future,
                futureBoundary,
                missingTimestamp,
                normalAfterFuture));

    assertThat(fixture.guard("import")).isOne();
    assertThat(fixture.guard("non_point")).isOne();
    assertThat(fixture.guard("missing_identity")).isOne();
    assertThat(fixture.guard("stale")).isOne();
    assertThat(fixture.guard("future_skew")).isOne();
    assertThat(fixture.guard("unparseable")).isOne();
    assertThat(fixture.observations()).hasSize(3);
    assertThat(fixture.observations())
        .extracting(WatchboxObservation::effectiveEventTime)
        .containsExactly(NOW.minus(LIVE_WINDOW), NOW.plus(FUTURE_TOLERANCE), NOW);
  }

  @Test
  void oversizedObjectIdIsRejectedWithoutFailingTheBatch() {
    // The 255 bound matches V37's varchar columns. Screening here keeps one oversized producer
    // value from rolling back the whole batch at the database, where the resulting SQLSTATE
    // class-54/22 error would at best tombstone the batch and at worst read as retryable
    // infrastructure. A boundary-length id must still pass.
    var fixture =
        fixture(snapshot(target(WATCHBOX_ID, WatchboxDirection.BOTH, null, emptyFilter())));
    var oversized = feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5);
    properties(oversized).setObjectId("x".repeat(256));
    var atBound = feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5);
    properties(atBound).setObjectId("y".repeat(255));

    fixture.handler().process(List.of(oversized, atBound));

    assertThat(fixture.guard("oversized_object_id")).isOne();
    assertThat(fixture.observations()).hasSize(1);
    assertThat(fixture.observations().getFirst().key().objectId()).isEqualTo("y".repeat(255));
  }

  @Test
  void frozenTargetExitsBeforeEvaluationWhileOtherTargetProcesses() {
    var frozen =
        target(
            WATCHBOX_ID,
            WatchboxDirection.BOTH,
            WatchboxStatusReason.WATCHBOX_OBJECT_ID_CHURN_DETECTED,
            emptyFilter());
    var active = target(OTHER_WATCHBOX_ID, WatchboxDirection.LEAVE, null, emptyFilter());
    var fixture = fixture(snapshot(frozen, active));

    fixture.handler().process(List.of(feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5)));

    assertThat(fixture.guard("frozen_scope")).isOne();
    assertThat(fixture.observations())
        .singleElement()
        .satisfies(
            observation -> {
              assertThat(observation.key().watchboxId()).isEqualTo(OTHER_WATCHBOX_ID);
              assertThat(observation.direction()).isEqualTo(WatchboxDirection.LEAVE);
            });
  }

  @Test
  void activeMultiTargetEventProducesOneObservationPerTarget() {
    var first = target(WATCHBOX_ID, WatchboxDirection.ENTER, null, emptyFilter());
    var second = target(OTHER_WATCHBOX_ID, WatchboxDirection.LEAVE, null, emptyFilter());
    var fixture = fixture(snapshot(first, second));

    fixture.handler().process(List.of(feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5)));

    assertThat(fixture.observations())
        .extracting(observation -> observation.key().watchboxId())
        .containsExactly(WATCHBOX_ID, OTHER_WATCHBOX_ID);
  }

  @Test
  void asymmetricBoxPinsLongitudeLatitudeAxisOrder() {
    // The box spans lon 10..20 x lat 0..5 and the point sits at (lon 15, lat 2), so a
    // transposed Coordinate(lat, lon) would land at (2, 15) — outside on both axes. The
    // symmetric unit square used elsewhere cannot detect an axis swap.
    var lonLatBox =
        new GeometryFactory()
            .createPolygon(
                new Coordinate[] {
                  new Coordinate(10, 0),
                  new Coordinate(20, 0),
                  new Coordinate(20, 5),
                  new Coordinate(10, 5),
                  new Coordinate(10, 0)
                });
    var asymmetricTarget =
        new WatchboxRuntimeTarget(
            WATCHBOX_ID,
            1,
            WatchboxDirection.ENTER,
            PreparedGeometryFactory.prepare(lonLatBox),
            emptyFilter(),
            null);
    var fixture = fixture(snapshot(asymmetricTarget));

    fixture.handler().process(List.of(feature(SOURCE_ID, NOW, true, true, "friendly", 15.0, 2.0)));

    assertThat(fixture.observations())
        .singleElement()
        .satisfies(observation -> assertThat(observation.geometryInside()).isTrue());
  }

  @Test
  void inBoxFilterNonMatchProducesGeometryInsideButNotQualifyingPresence() {
    var filter =
        new WatchboxFilter(
            List.of(
                new WatchboxFilterPredicate(
                    "properties.opencop.attributes.status", EQUALS, List.of("friendly"))));
    var fixture = fixture(snapshot(target(WATCHBOX_ID, WatchboxDirection.ENTER, null, filter)));
    var feature = feature(SOURCE_ID, NOW, true, true, "hostile", 0.5, 0.5);
    properties(feature).setDisplayName("Visible Object");

    fixture.handler().process(List.of(feature));

    assertThat(fixture.observations())
        .singleElement()
        .satisfies(
            observation -> {
              assertThat(observation.geometryInside()).isTrue();
              assertThat(observation.inside()).isFalse();
              assertThat(observation.direction()).isEqualTo(WatchboxDirection.ENTER);
              assertThat(observation.objectDisplayName()).isEqualTo("Visible Object");
            });
  }

  @Test
  void inBoxFilterMatchProducesQualifyingPresenceThroughRealEvaluation() {
    // Pins the filterMatches=true outcome via actual predicate evaluation over the feature's
    // JSON tree; every other filter test either matches nothing or takes the empty-predicates
    // shortcut, which never exercises the featureNode path for a positive match.
    var filter =
        new WatchboxFilter(
            List.of(
                new WatchboxFilterPredicate(
                    "properties.opencop.attributes.status", EQUALS, List.of("friendly"))));
    var fixture = fixture(snapshot(target(WATCHBOX_ID, WatchboxDirection.ENTER, null, filter)));
    var feature = feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5);

    fixture.handler().process(List.of(feature));

    assertThat(fixture.observations())
        .singleElement()
        .satisfies(
            observation -> {
              assertThat(observation.geometryInside()).isTrue();
              assertThat(observation.inside()).isTrue();
            });
  }

  @Test
  void blankDisplayNameIsNormalizedToNull() {
    var fixture =
        fixture(snapshot(target(WATCHBOX_ID, WatchboxDirection.BOTH, null, emptyFilter())));
    var feature = feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5);
    properties(feature).setDisplayName("  ");

    fixture.handler().process(List.of(feature));

    assertThat(fixture.observations())
        .singleElement()
        .extracting(WatchboxObservation::objectDisplayName)
        .isNull();
  }

  @Test
  void overlongDisplayNameIsCappedAtTheRetainedBound() {
    var fixture =
        fixture(snapshot(target(WATCHBOX_ID, WatchboxDirection.BOTH, null, emptyFilter())));
    var feature = feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5);
    // 254 single-unit characters then two astral-plane ones: the cap keeps 255 code points and
    // never splits the surrogate pair of the 255th.
    var truck = "\uD83D\uDE9A";
    properties(feature).setDisplayName("n".repeat(254) + truck + truck);

    fixture.handler().process(List.of(feature));

    assertThat(fixture.observations())
        .singleElement()
        .extracting(WatchboxObservation::objectDisplayName)
        .isEqualTo("n".repeat(254) + truck);
  }

  @Test
  void multiFeatureBatchHandsOffToEngineExactlyOnce() {
    // The engine folds per batch, and the churn breaker's admission maths assume one call per
    // consumed batch; per-feature or per-observation handoff would pass the accumulating mock
    // in every other test.
    var fixture =
        fixture(snapshot(target(WATCHBOX_ID, WatchboxDirection.BOTH, null, emptyFilter())));

    fixture
        .handler()
        .process(
            List.of(
                feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5),
                feature(SOURCE_ID, NOW, true, true, "friendly", 0.6, 0.6)));

    verify(fixture.engine(), times(1)).process(anyList());
    assertThat(fixture.observations()).hasSize(2);
  }

  @Test
  void generationChangeReloadsConfigAndReevaluatesBatchOnce() {
    var stale = snapshot(target(WATCHBOX_ID, 1, WatchboxDirection.BOTH, null, emptyFilter()));
    var current = snapshot(target(WATCHBOX_ID, 2, WatchboxDirection.BOTH, null, emptyFilter()));
    var reloads = new AtomicInteger();
    WatchboxRuntimeConfigGateway runtimeConfig =
        new WatchboxRuntimeConfigGateway() {
          @Override
          public WatchboxRuntimeSnapshot load() {
            return stale;
          }

          @Override
          public WatchboxRuntimeSnapshot reloadAfterGenerationChange(
              WatchboxRuntimeSnapshot rejectedSnapshot) {
            assertThat(rejectedSnapshot).isSameAs(stale);
            reloads.incrementAndGet();
            return current;
          }
        };
    var registry = new SimpleMeterRegistry();
    var metrics = new ProcessorMetrics(registry);
    var engine = mock(WatchboxTransitionEngine.class);
    var attemptedEpochs = new ArrayList<Long>();
    when(engine.process(anyList()))
        .thenAnswer(
            invocation -> {
              List<WatchboxObservation> observations = invocation.getArgument(0);
              attemptedEpochs.add(observations.getFirst().key().configEpoch());
              if (attemptedEpochs.size() == 1) {
                throw new WatchboxGenerationChangedException();
              }
              return new WatchboxBatchResult(List.of(), Map.of());
            });
    var handler = handler(runtimeConfig, engine, metrics);

    handler.process(List.of(feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5)));

    assertThat(attemptedEpochs).containsExactly(1L, 2L);
    assertThat(reloads).hasValue(1);
    assertThat(registry.counter("opencop.watchbox.pipeline.config.generation_retries").count())
        .isOne();
  }

  @Test
  void generationChangeAfterReloadPropagatesWithoutLooping() {
    var stale = snapshot(target(WATCHBOX_ID, 1, WatchboxDirection.BOTH, null, emptyFilter()));
    var current = snapshot(target(WATCHBOX_ID, 2, WatchboxDirection.BOTH, null, emptyFilter()));
    var reloads = new AtomicInteger();
    var invalidations = new AtomicInteger();
    WatchboxRuntimeConfigGateway runtimeConfig =
        new WatchboxRuntimeConfigGateway() {
          @Override
          public WatchboxRuntimeSnapshot load() {
            return stale;
          }

          @Override
          public WatchboxRuntimeSnapshot reloadAfterGenerationChange(
              WatchboxRuntimeSnapshot rejectedSnapshot) {
            assertThat(rejectedSnapshot).isSameAs(stale);
            reloads.incrementAndGet();
            return current;
          }

          @Override
          public void invalidateAfterGenerationChange(WatchboxRuntimeSnapshot rejectedSnapshot) {
            assertThat(rejectedSnapshot).isSameAs(current);
            invalidations.incrementAndGet();
          }
        };
    var registry = new SimpleMeterRegistry();
    var metrics = new ProcessorMetrics(registry);
    var engine = mock(WatchboxTransitionEngine.class);
    when(engine.process(anyList())).thenThrow(new WatchboxGenerationChangedException());
    var handler = handler(runtimeConfig, engine, metrics);

    assertThatThrownBy(
            () ->
                handler.process(List.of(feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5))))
        .isInstanceOf(WatchboxGenerationChangedException.class);

    verify(engine, times(2)).process(anyList());
    assertThat(reloads).hasValue(1);
    assertThat(invalidations).hasValue(1);
    assertThat(registry.counter("opencop.watchbox.pipeline.config.generation_retries").count())
        .isOne();
  }

  @Test
  void filterNonMatchSeedsBothBooleansThroughRealEngine() {
    var filter =
        new WatchboxFilter(
            List.of(
                new WatchboxFilterPredicate(
                    "properties.opencop.attributes.status", EQUALS, List.of("friendly"))));
    var registry = new SimpleMeterRegistry();
    var metrics = new ProcessorMetrics(registry);
    var engine = realEngine(metrics);
    var cache =
        new WatchboxRuntimeConfigCache(
            () -> snapshot(target(WATCHBOX_ID, WatchboxDirection.BOTH, null, filter)),
            Duration.ofMinutes(1),
            metrics);
    var handler = handler(cache, engine, metrics);

    handler.process(List.of(feature(SOURCE_ID, NOW, true, true, "hostile", 0.5, 0.5)));

    var row =
        jdbc.queryForMap(
            """
            SELECT last_inside, last_geometry_inside
            FROM watchbox.watchbox_transition_state
            WHERE watchbox_id = ? AND source_id = ?
            """,
            WATCHBOX_ID,
            SOURCE_ID);
    assertThat(row.get("last_inside")).isEqualTo(false);
    assertThat(row.get("last_geometry_inside")).isEqualTo(true);
  }

  @Test
  void sameEventIdAcrossObjectsPersistsBothDetections() {
    // Producer-supplied event ids are only trusted to be unique per (source, object): a
    // duplicated source replaying the same payloads, or a low-entropy producer, must not
    // suppress another object's detection. The widened unique index scopes dedupe accordingly;
    // a true replay still carries the same source and object, so idempotency is unchanged.
    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "ALL");
    var metrics = new ProcessorMetrics(new SimpleMeterRegistry());
    var engine = realEngine(metrics);
    var handler = handler(runtimeCache(metrics), engine, metrics);
    var seedA = feature(SOURCE_ID, NOW.minusSeconds(2), true, true, "friendly", 2.0, 2.0);
    properties(seedA).setObjectId("shared-event-object-a");
    var seedB = feature(SOURCE_ID, NOW.minusSeconds(2), true, true, "friendly", 2.0, 2.0);
    properties(seedB).setObjectId("shared-event-object-b");
    handler.process(List.of(seedA, seedB));

    var sharedEventId = UUID.randomUUID();
    var enterA = feature(SOURCE_ID, NOW.minusSeconds(1), true, true, "friendly", 0.5, 0.5);
    properties(enterA).setObjectId("shared-event-object-a");
    properties(enterA).setId(sharedEventId);
    var enterB = feature(SOURCE_ID, NOW.minusSeconds(1), true, true, "friendly", 0.5, 0.5);
    properties(enterB).setObjectId("shared-event-object-b");
    properties(enterB).setId(sharedEventId);
    handler.process(List.of(enterA, enterB));

    assertThat(
            jdbc.queryForList(
                "SELECT object_id FROM watchbox.watchbox_detection WHERE event_id = ? AND transition = 'ENTER' ORDER BY object_id",
                String.class,
                sharedEventId))
        .containsExactly("shared-event-object-a", "shared-event-object-b");
  }

  @Test
  void oversizedObjectIdBypassingTheGuardFailsAsDataIntegrityNotInfrastructure() {
    // Backstop for the screen() guard: should an oversized id ever reach the database, the V37
    // varchar bound must fail it as a data error (finite retry, then DLT) rather than an
    // unbounded-index SQLSTATE class-54 error, which the listener's infrastructure
    // classification would retry in place forever. Pins the Spring/PostgreSQL translation.
    var metrics = new ProcessorMetrics(new SimpleMeterRegistry());
    var engine = realEngine(metrics);
    var oversized =
        new WatchboxObservation(
            new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "z".repeat(256)),
            UUID.randomUUID(),
            NOW.minusSeconds(1),
            true,
            true,
            WatchboxDirection.BOTH,
            null);

    assertThatThrownBy(() -> engine.process(List.of(oversized)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void directionEditAppliesProspectivelyWithoutResettingState() {
    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "ALL");
    jdbc.update("UPDATE watchbox.watchbox SET direction = 'ENTER' WHERE id = ?", WATCHBOX_ID);
    var registry = new SimpleMeterRegistry();
    var metrics = new ProcessorMetrics(registry);
    var sink = new CapturingSink();
    var engine = realEngine(metrics, sink);
    var firstHandler = handler(runtimeCache(metrics), engine, metrics);
    var inside = feature(SOURCE_ID, NOW.minusSeconds(1), true, true, "friendly", 0.5, 0.5);
    properties(inside).setObjectId("direction-object");

    firstHandler.process(List.of(inside));
    assertThat(sink.emissions).isEmpty();

    jdbc.update("UPDATE watchbox.watchbox SET direction = 'BOTH' WHERE id = ?", WATCHBOX_ID);
    var secondHandler = handler(runtimeCache(metrics), engine, metrics);
    var outside = feature(SOURCE_ID, NOW, true, true, "friendly", 2.0, 2.0);
    properties(outside).setObjectId("direction-object");
    secondHandler.process(List.of(outside));

    assertThat(sink.emissions)
        .extracting(WatchboxTransition::kind)
        .containsExactly(WatchboxTransitionKind.LEAVE);
    assertThat(
            registry
                .counter("opencop.watchbox.detection.outcome", Tags.of("outcome", "emitted"))
                .count())
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM watchbox.watchbox_transition_state WHERE object_id = 'direction-object'",
                Long.class))
        .isOne();
  }

  @Test
  void freshRuntimeAfterRestartRefreezesDurableChurnScopeBeforeEvaluation() {
    var metrics = new ProcessorMetrics(new SimpleMeterRegistry());
    var breakerEngine =
        new WatchboxTransitionEngine(
            new PostgresWatchboxTransitionStateRepository(jdbc),
            new PostgresWatchboxSourceStatusRepository(jdbc),
            new PostgresWatchboxDetectionRepository(jdbc),
            new PostgresWatchboxAlertOutboxRepository(jdbc),
            new WatchboxChurnBreaker(true, 1, Duration.ofHours(1)),
            new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofHours(1),
            metrics);
    breakerEngine.process(
        List.of(
            new WatchboxObservation(
                new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "first"),
                UUID.randomUUID(),
                NOW.minusSeconds(2),
                false,
                false,
                WatchboxDirection.BOTH,
                null)));
    assertThat(
            breakerEngine
                .process(
                    List.of(
                        new WatchboxObservation(
                            new WatchboxStateKey(WATCHBOX_ID, 1, SOURCE_ID, "second"),
                            UUID.randomUUID(),
                            NOW.minusSeconds(1),
                            false,
                            false,
                            WatchboxDirection.BOTH,
                            null)))
                .count(TRIPPED))
        .isOne();

    WatchboxPostgresTestSupport.setWatchboxFeatureAudience(jdbc, "ALL");
    var freshRegistry = new SimpleMeterRegistry();
    var freshMetrics = new ProcessorMetrics(freshRegistry);
    var freshEngine = mock(WatchboxTransitionEngine.class);
    var freshHandler = handler(runtimeCache(freshMetrics), freshEngine, freshMetrics);

    freshHandler.process(List.of(feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5)));

    assertThat(
            freshRegistry
                .counter("opencop.watchbox.pipeline.guard", Tags.of("reason", "frozen_scope"))
                .count())
        .isOne();
    verifyNoInteractions(freshEngine);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM watchbox.watchbox_transition_state", Long.class))
        .isOne();
  }

  @Test
  void nudgesTheAlertDrainerOnlyWhenTheBatchCommittedNewDetections() {
    var snapshot = snapshot(target(WATCHBOX_ID, 1, WatchboxDirection.BOTH, null, emptyFilter()));
    var registry = new SimpleMeterRegistry();
    var metrics = new ProcessorMetrics(registry);
    var engine = mock(WatchboxTransitionEngine.class);
    when(engine.process(anyList()))
        .thenReturn(
            new WatchboxBatchResult(List.of(), Map.of(WatchboxBatchOutcome.DETECTIONS_EMITTED, 1L)))
        .thenReturn(new WatchboxBatchResult(List.of(), Map.of()));
    var nudges = new AtomicInteger();
    var handler =
        handler(
            new WatchboxRuntimeConfigCache(() -> snapshot, Duration.ofMinutes(1), metrics),
            engine,
            metrics,
            nudges::incrementAndGet);

    handler.process(List.of(feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5)));
    assertThat(nudges).hasValue(1);

    // A batch whose candidates were all deduplicated commits no new detections: no nudge.
    handler.process(List.of(feature(SOURCE_ID, NOW, true, true, "friendly", 0.5, 0.5)));
    assertThat(nudges).hasValue(1);
  }

  private Fixture fixture(WatchboxRuntimeSnapshot snapshot) {
    var registry = new SimpleMeterRegistry();
    var metrics = new ProcessorMetrics(registry);
    var engine = mock(WatchboxTransitionEngine.class);
    var observations = new ArrayList<WatchboxObservation>();
    when(engine.process(anyList()))
        .thenAnswer(
            invocation -> {
              observations.addAll(invocation.getArgument(0));
              return new WatchboxBatchResult(List.of(), Map.of());
            });
    var cache = new WatchboxRuntimeConfigCache(() -> snapshot, Duration.ofMinutes(1), metrics);
    return new Fixture(handler(cache, engine, metrics), engine, observations, registry);
  }

  private WatchboxEventHandler handler(
      WatchboxRuntimeConfigGateway cache,
      WatchboxTransitionEngine engine,
      ProcessorMetrics metrics) {
    return handler(cache, engine, metrics, () -> {});
  }

  private WatchboxEventHandler handler(
      WatchboxRuntimeConfigGateway cache,
      WatchboxTransitionEngine engine,
      ProcessorMetrics metrics,
      WatchboxAlertDrainSignal alertDrainSignal) {
    return new WatchboxEventHandler(
        cache,
        engine,
        alertDrainSignal,
        LIVE_WINDOW,
        FUTURE_TOLERANCE,
        new ObjectMapper(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        metrics);
  }

  private WatchboxTransitionEngine realEngine(ProcessorMetrics metrics) {
    return realEngine(metrics, new PostgresWatchboxDetectionRepository(jdbc));
  }

  private WatchboxTransitionEngine realEngine(
      ProcessorMetrics metrics, WatchboxDetectionRepository detectionSink) {
    return new WatchboxTransitionEngine(
        new PostgresWatchboxTransitionStateRepository(jdbc),
        new PostgresWatchboxSourceStatusRepository(jdbc),
        detectionSink,
        new PostgresWatchboxAlertOutboxRepository(jdbc),
        new PermitAllWatchboxAdmissionGate(),
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
        Clock.fixed(NOW, ZoneOffset.UTC),
        Duration.ofHours(1),
        metrics);
  }

  private WatchboxRuntimeConfigCache runtimeCache(ProcessorMetrics metrics) {
    return new WatchboxRuntimeConfigCache(
        new PostgresWatchboxRuntimeConfigRepository(jdbc, new ObjectMapper(), metrics),
        Duration.ofMinutes(1),
        metrics);
  }

  private static WatchboxRuntimeSnapshot snapshot(WatchboxRuntimeTarget... targets) {
    return new WatchboxRuntimeSnapshot(Map.of(SOURCE_ID, List.of(targets)));
  }

  private static WatchboxRuntimeTarget target(
      UUID watchboxId,
      WatchboxDirection direction,
      WatchboxStatusReason frozenReason,
      WatchboxFilter filter) {
    return target(watchboxId, 1, direction, frozenReason, filter);
  }

  private static WatchboxRuntimeTarget target(
      UUID watchboxId,
      long configEpoch,
      WatchboxDirection direction,
      WatchboxStatusReason frozenReason,
      WatchboxFilter filter) {
    var geometryFactory = new GeometryFactory();
    var polygon =
        geometryFactory.createPolygon(
            new Coordinate[] {
              new Coordinate(0, 0),
              new Coordinate(1, 0),
              new Coordinate(1, 1),
              new Coordinate(0, 1),
              new Coordinate(0, 0)
            });
    return new WatchboxRuntimeTarget(
        watchboxId,
        configEpoch,
        direction,
        PreparedGeometryFactory.prepare(polygon),
        filter,
        frozenReason);
  }

  private static WatchboxFilter emptyFilter() {
    return WatchboxFilter.empty();
  }

  private static Feature feature(
      UUID sourceId,
      Instant eventTime,
      boolean includeEventId,
      boolean includeTimestamp,
      String status,
      double longitude,
      double latitude) {
    var builder =
        new OpenCopProperties.Builder()
            .objectId("object-" + UUID.randomUUID())
            .source(sourceId)
            .putAttribute("status", status);
    if (includeEventId) {
      builder.id(UUID.randomUUID());
    }
    if (includeTimestamp) {
      builder.timestampz(
          new Timestampz(
              eventTime.toEpochMilli(),
              eventTime.toEpochMilli(),
              eventTime.plusSeconds(600).toEpochMilli()));
    }
    return OpenCopPropertiesParser.writeToFeature(
        builder.build(), new Point(new LngLatAlt(longitude, latitude)));
  }

  private static OpenCopProperties properties(Feature feature) {
    return (OpenCopProperties) feature.getProperty(OpenCopPropertiesParser.OPENCOP_NODE);
  }

  private record Fixture(
      WatchboxEventHandler handler,
      WatchboxTransitionEngine engine,
      List<WatchboxObservation> observations,
      SimpleMeterRegistry registry) {

    double guard(String reason) {
      return registry.counter("opencop.watchbox.pipeline.guard", Tags.of("reason", reason)).count();
    }
  }

  private static final class CapturingSink implements WatchboxDetectionRepository {
    private final List<WatchboxTransition> emissions = new ArrayList<>();

    @Override
    public List<AcceptedDetection> write(List<WatchboxTransition> transitions) {
      emissions.addAll(transitions);
      return transitions.stream().map(e -> new AcceptedDetection(UUID.randomUUID(), e)).toList();
    }

    @Override
    public int deleteExpiredBatch(Instant cutoff, int batchSize) {
      return 0;
    }
  }
}
