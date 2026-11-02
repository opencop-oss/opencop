// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.runtimefeature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureActor;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureConsumerClass;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureHistoryEntry;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureHistoryQuery;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureRegistrationResult;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureWriteResult;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureBusyException;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = JpaRuntimeFeatureStoreTest.TestApplication.class)
@Import(ComponentTestDatabaseConfig.class)
class JpaRuntimeFeatureStoreTest extends AbstractDatabaseIT {

  private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-4000-8000-000000000101");
  private static final Instant NOW = Instant.parse("2026-07-11T20:00:00Z");
  // config.runtime_feature_audience_user_group deliberately carries no FK to iam.user_group,
  // so an arbitrary UUID is a legal target here.
  private static final UUID GROUP_A = UUID.fromString("11111111-1111-4111-8111-111111111111");

  @Autowired private JpaRuntimeFeatureStore store;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @DynamicPropertySource
  static void registerFlywayPlaceholders(DynamicPropertyRegistry registry) {
    FlywayGeoFeaturesPlaceholders.defaultPlaceholders()
        .forEach((name, value) -> registry.add("spring.flyway.placeholders." + name, () -> value));
  }

  @BeforeEach
  void resetState() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            _ -> {
              jdbc.update("DELETE FROM config.runtime_feature_history");
              jdbc.update("DELETE FROM config.runtime_feature_audience_user_group");
              // Registration tests create rows; restore the migration-seeded catalog.
              jdbc.update(
                  "DELETE FROM config.runtime_feature WHERE name NOT IN ('watchbox', 'rtsp')");
              jdbc.update(
                  "UPDATE config.runtime_feature SET audience_mode = 'NONE', revision = 0, updated_at = created_at WHERE name = 'watchbox'");
              jdbc.update(
                  "UPDATE config.runtime_feature SET audience_mode = 'ALL', revision = 0, updated_at = created_at WHERE name = 'rtsp'");
              jdbc.update("DELETE FROM opencop_user WHERE id = ?", ACTOR_ID);
              jdbc.update(
                  "INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer, username) VALUES (?, 'runtime-feature-test', 'runtime-feature-test', 'runtime-feature-test', 'runtime-feature-test')",
                  ACTOR_ID);
            });
  }

  // Fresh-migration seed values are proven by V33FlywayMigrationTest; resetState()
  // rewrites these rows, so this test covers deterministic reads only.
  @Test
  void deterministicReadsAndUnknownLookups() {
    assertThat(store.findAll())
        .extracting(state -> state.name() + ":" + state.audience().getClass().getSimpleName())
        .containsExactly("rtsp:All", "watchbox:None");
    assertThat(store.findByName("watchbox").orElseThrow().consumerClass())
        .isEqualTo(RuntimeFeatureConsumerClass.BACKEND_ENFORCED);
    assertThat(store.findByName("byod")).isEmpty();
    assertThat(history()).isEmpty();
  }

  @Test
  void replaceAudienceSupportsAppliedIdempotencyUnknownAndStale() {
    var audience = new RuntimeFeatureAudience.All();
    var actor =
        new RuntimeFeatureActor(
            ACTOR_ID,
            Optional.of("Runtime Feature Administrator"),
            Optional.of("admin@example.test"),
            true);

    var applied = store.replaceAudience("watchbox", audience, OptionalLong.of(0), actor);
    assertThat(applied).isInstanceOf(RuntimeFeatureWriteResult.Applied.class);
    assertThat(((RuntimeFeatureWriteResult.Applied) applied).state().revision()).isEqualTo(1);
    assertThat(store.findByName("watchbox").orElseThrow().audience()).isEqualTo(audience);

    var idempotent = store.replaceAudience("watchbox", audience, OptionalLong.of(1), actor);
    assertThat(idempotent).isInstanceOf(RuntimeFeatureWriteResult.Idempotent.class);
    assertThat(history()).hasSize(2);

    var stateBeforeStaleSameAudience = store.findByName("watchbox").orElseThrow();
    var historyBeforeStaleSameAudience = history();
    var staleSameAudience = store.replaceAudience("watchbox", audience, OptionalLong.of(0), actor);
    assertThat(staleSameAudience).isInstanceOf(RuntimeFeatureWriteResult.Stale.class);
    assertThat(store.findByName("watchbox").orElseThrow()).isEqualTo(stateBeforeStaleSameAudience);
    assertThat(history()).isEqualTo(historyBeforeStaleSameAudience);

    var stale =
        store.replaceAudience(
            "watchbox", new RuntimeFeatureAudience.None(), OptionalLong.of(0), actor);
    assertThat(stale).isInstanceOf(RuntimeFeatureWriteResult.Stale.class);
    assertThat(history()).hasSize(2);

    var unknown =
        store.replaceAudience(
            "unknown", new RuntimeFeatureAudience.All(), OptionalLong.empty(), actor);
    assertThat(unknown).isEqualTo(new RuntimeFeatureWriteResult.Unknown("unknown"));

    var history = history();
    assertThat(history)
        .extracting(RuntimeFeatureHistoryEntry::operation)
        .containsOnly(RuntimeFeatureHistoryEntry.Operation.PATCH);
    assertThat(history.getFirst().beforeAudience()).isEqualTo(audience);
    assertThat(history.getFirst().afterAudience()).isEqualTo(audience);
    assertThat(history.getFirst().revisionBefore()).isEqualTo(1L);
    assertThat(history.getFirst().revisionAfter()).isEqualTo(1L);
    assertThat(history.getLast().beforeAudience()).isEqualTo(new RuntimeFeatureAudience.None());
    assertThat(history.getLast().afterAudience()).isEqualTo(audience);
    assertThat(history.getLast().revisionBefore()).isZero();
    assertThat(history.getLast().revisionAfter()).isEqualTo(1L);
    assertThat(history.getFirst().actor().userId()).isEqualTo(actor.userId());
    assertThat(history.getFirst().actor().fullName()).isEqualTo(actor.fullName().orElseThrow());
    assertThat(history.getFirst().actor().email()).isEqualTo(actor.email().orElseThrow());
    assertThat(history.getFirst().actor().globalAdmin()).isTrue();
  }

  @Test
  void history_whenActorFactsAreNullable_preservesIndependentFacts() {
    var requestActor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);
    store.replaceAudience(
        "watchbox", new RuntimeFeatureAudience.All(), OptionalLong.of(0), requestActor);

    var actor = history().getFirst().actor();
    assertThat(actor.userId()).isEqualTo(ACTOR_ID);
    assertThat(actor.fullName()).isNull();
    assertThat(actor.email()).isNull();
    assertThat(actor.globalAdmin()).isFalse();
  }

  @Test
  void replaceAudience_whenApplied_carriesPreviousAudienceInBothDirections() {
    var actor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);

    var enabled =
        (RuntimeFeatureWriteResult.Applied)
            store.replaceAudience(
                "watchbox", new RuntimeFeatureAudience.All(), OptionalLong.empty(), actor);
    assertThat(enabled.previousAudience()).isEqualTo(new RuntimeFeatureAudience.None());

    var disabled =
        (RuntimeFeatureWriteResult.Applied)
            store.replaceAudience(
                "watchbox", new RuntimeFeatureAudience.None(), OptionalLong.empty(), actor);
    assertThat(disabled.previousAudience()).isEqualTo(new RuntimeFeatureAudience.All());
  }

  @Test
  void userGroupAudienceRoundTripsReplacesOverlappingTargetsAndClearsThem() {
    var groupA = UUID.fromString("00000000-0000-4000-8000-000000000201");
    var groupB = UUID.fromString("00000000-0000-4000-8000-000000000202");
    var groupC = UUID.fromString("00000000-0000-4000-8000-000000000203");
    var actor =
        new RuntimeFeatureActor(
            ACTOR_ID, Optional.empty(), Optional.of("admin@example.test"), true);
    var groupsAB = userGroups(groupB, groupA);
    var groupsAC = userGroups(groupC, groupA);

    var applied = store.replaceAudience("watchbox", groupsAB, OptionalLong.of(0), actor);
    assertThat(applied).isInstanceOf(RuntimeFeatureWriteResult.Applied.class);
    assertThat(store.findByName("watchbox").orElseThrow().audience()).isEqualTo(groupsAB);
    assertThat(targetIds("watchbox")).containsExactly(groupA, groupB);

    var stale = store.replaceAudience("watchbox", groupsAC, OptionalLong.of(0), actor);
    assertThat(stale).isInstanceOf(RuntimeFeatureWriteResult.Stale.class);
    assertThat(targetIds("watchbox")).containsExactly(groupA, groupB);

    var idempotent = store.replaceAudience("watchbox", groupsAB, OptionalLong.of(1), actor);
    assertThat(idempotent).isInstanceOf(RuntimeFeatureWriteResult.Idempotent.class);
    assertThat(store.findByName("watchbox").orElseThrow().revision()).isEqualTo(1);
    assertThat(history()).hasSize(2);

    var overlappingReplacement =
        store.replaceAudience("watchbox", groupsAC, OptionalLong.of(1), actor);
    assertThat(overlappingReplacement).isInstanceOf(RuntimeFeatureWriteResult.Applied.class);
    assertThat(targetIds("watchbox")).containsExactly(groupA, groupC);

    var cleared =
        store.replaceAudience(
            "watchbox", new RuntimeFeatureAudience.All(), OptionalLong.of(2), actor);
    assertThat(cleared).isInstanceOf(RuntimeFeatureWriteResult.Applied.class);
    assertThat(targetIds("watchbox")).isEmpty();
    assertThat(store.findByName("watchbox").orElseThrow().audience())
        .isEqualTo(new RuntimeFeatureAudience.All());

    assertThat(
            jdbc.queryForObject(
                "SELECT after_audience::text FROM config.runtime_feature_history"
                    + " WHERE feature_name = 'watchbox' AND revision_after = 1"
                    + " AND before_audience <> after_audience",
                String.class))
        .isEqualTo(
            "{\"mode\": \"USER_GROUPS\", \"userGroupIds\": [\""
                + groupA
                + "\", \""
                + groupB
                + "\"]}");
    assertThat(history())
        .extracting(entry -> entry.beforeAudience() + " -> " + entry.afterAudience())
        .containsExactly(
            groupsAC + " -> " + new RuntimeFeatureAudience.All(),
            groupsAB + " -> " + groupsAC,
            groupsAB + " -> " + groupsAB,
            new RuntimeFeatureAudience.None() + " -> " + groupsAB);
  }

  @Test
  void userGroupAudienceReadsUseOneStatementSnapshot() {
    var groupA = UUID.fromString("00000000-0000-4000-8000-000000000201");
    var groupB = UUID.fromString("00000000-0000-4000-8000-000000000202");
    var audience = userGroups(groupB, groupA);
    var actor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);
    store.replaceAudience("watchbox", audience, OptionalLong.of(0), actor);

    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    var statisticsWereEnabled = statistics.isStatisticsEnabled();
    statistics.setStatisticsEnabled(true);
    try {
      statistics.clear();
      assertThat(store.findByName("watchbox").orElseThrow().audience()).isEqualTo(audience);
      assertThat(statistics.getPrepareStatementCount()).isOne();

      statistics.clear();
      var states = store.findAll();
      assertThat(states).extracting(state -> state.name()).containsExactly("rtsp", "watchbox");
      assertThat(states.getFirst().audience()).isEqualTo(new RuntimeFeatureAudience.All());
      assertThat(states.get(1).audience()).isEqualTo(audience);
      assertThat(statistics.getPrepareStatementCount()).isOne();
    } finally {
      statistics.clear();
      statistics.setStatisticsEnabled(statisticsWereEnabled);
    }
  }

  @Test
  void scalarAudienceProjectionDoesNotReuseStaleManagedFeatureState() throws Exception {
    var groupId = UUID.fromString("00000000-0000-4000-8000-000000000204");
    var audience = userGroups(groupId);
    var actor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);
    var transactions = new TransactionTemplate(transactionManager);

    transactions.executeWithoutResult(
        _ -> {
          var before = store.findByName("watchbox").orElseThrow();
          assertThat(before.audience()).isEqualTo(new RuntimeFeatureAudience.None());
          assertThat(before.revision()).isZero();

          try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var writer =
                executor.submit(
                    () -> store.replaceAudience("watchbox", audience, OptionalLong.of(0), actor));
            assertThat(writer.get(10, TimeUnit.SECONDS))
                .isInstanceOf(RuntimeFeatureWriteResult.Applied.class);
          } catch (Exception exception) {
            throw new IllegalStateException("Concurrent writer failed", exception);
          }

          var after = store.findByName("watchbox").orElseThrow();
          assertThat(after.audience()).isEqualTo(audience);
          assertThat(after.revision()).isEqualTo(1);
        });
  }

  @Test
  void heldFeatureRowFailsFastInsteadOfQueueingWriters() throws Exception {
    var actor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);
    var writerACompletedStoreCall = new CountDownLatch(1);
    var releaseWriterACommit = new CountDownLatch(1);
    var transactions = new TransactionTemplate(transactionManager);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var writerA =
          executor.submit(
              () ->
                  transactions.execute(
                      _ -> {
                        var result =
                            store.replaceAudience(
                                "watchbox",
                                new RuntimeFeatureAudience.All(),
                                OptionalLong.of(0),
                                actor);
                        writerACompletedStoreCall.countDown();
                        await(releaseWriterACommit);
                        return result;
                      }));
      assertThat(writerACompletedStoreCall.await(10, TimeUnit.SECONDS)).isTrue();

      // NOWAIT: the held row fails a second writer immediately instead of queueing it.
      assertThatThrownBy(
              () ->
                  store.replaceAudience(
                      "watchbox", new RuntimeFeatureAudience.None(), OptionalLong.of(0), actor))
          .isInstanceOf(RuntimeFeatureBusyException.class)
          .hasCauseInstanceOf(PessimisticLockingFailureException.class);

      releaseWriterACommit.countDown();

      assertThat(writerA.get(10, TimeUnit.SECONDS))
          .isInstanceOf(RuntimeFeatureWriteResult.Applied.class);
    }

    var retryAfterRelease =
        store.replaceAudience(
            "watchbox", new RuntimeFeatureAudience.None(), OptionalLong.of(0), actor);
    assertThat(retryAfterRelease).isInstanceOf(RuntimeFeatureWriteResult.Stale.class);
    assertThat(store.findByName("watchbox").orElseThrow().revision()).isEqualTo(1);
    assertThat(history()).hasSize(1);
  }

  @Test
  void identicalRacingWritersProduceExactlyOneChange() throws Exception {
    var actor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);
    var barrier = new CyclicBarrier(2);
    List<Object> outcomes;

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> {
                barrier.await();
                return store.replaceAudience(
                    "watchbox", new RuntimeFeatureAudience.All(), OptionalLong.empty(), actor);
              });
      var second =
          executor.submit(
              () -> {
                barrier.await();
                return store.replaceAudience(
                    "watchbox", new RuntimeFeatureAudience.All(), OptionalLong.empty(), actor);
              });

      // Under NOWAIT the loser of a genuine race fails fast; when the writers happen to
      // serialize, the second one observes the applied state as Idempotent.
      outcomes = List.of(outcome(first), outcome(second));
      assertThat(outcomes).anyMatch(RuntimeFeatureWriteResult.Applied.class::isInstance);
      assertThat(outcomes)
          .allMatch(
              writeOutcome ->
                  writeOutcome instanceof RuntimeFeatureWriteResult.Applied
                      || writeOutcome instanceof RuntimeFeatureWriteResult.Idempotent
                      || writeOutcome instanceof RuntimeFeatureBusyException);
    }

    assertThat(store.findByName("watchbox").orElseThrow().revision()).isEqualTo(1);
    var acceptedCount =
        outcomes.stream()
            .filter(
                result ->
                    result instanceof RuntimeFeatureWriteResult.Applied
                        || result instanceof RuntimeFeatureWriteResult.Idempotent)
            .count();
    assertThat(history()).hasSize((int) acceptedCount);
  }

  @Test
  void opposingUnguardedWritersProduceAValidRevisionChain() throws Exception {
    var actor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);
    var barrier = new CyclicBarrier(2);
    List<Object> outcomes;

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var toAll =
          executor.submit(
              () -> {
                barrier.await();
                return store.replaceAudience(
                    "watchbox", new RuntimeFeatureAudience.All(), OptionalLong.empty(), actor);
              });
      var toNone =
          executor.submit(
              () -> {
                barrier.await();
                return store.replaceAudience(
                    "watchbox", new RuntimeFeatureAudience.None(), OptionalLong.empty(), actor);
              });

      // Watchbox starts at NONE. Whoever acquires the row first completes (the NONE writer as a
      // no-op Idempotent); under NOWAIT the genuinely simultaneous loser fails fast instead of
      // queueing, so at most one writer can fail and every completed write stays consistent.
      outcomes = List.of(outcome(toAll), outcome(toNone));
      assertThat(outcomes)
          .allMatch(
              writeOutcome ->
                  writeOutcome instanceof RuntimeFeatureWriteResult.Applied
                      || writeOutcome instanceof RuntimeFeatureWriteResult.Idempotent
                      || writeOutcome instanceof RuntimeFeatureBusyException);
      assertThat(outcomes.stream().filter(RuntimeFeatureBusyException.class::isInstance).count())
          .isLessThanOrEqualTo(1);
    }

    var finalState = store.findByName("watchbox").orElseThrow();
    var history = history();
    var acceptedCount =
        outcomes.stream()
            .filter(
                result ->
                    result instanceof RuntimeFeatureWriteResult.Applied
                        || result instanceof RuntimeFeatureWriteResult.Idempotent)
            .count();
    assertThat(history).hasSize((int) acceptedCount);
    assertThat(history)
        .extracting(RuntimeFeatureHistoryEntry::operation)
        .containsOnly(RuntimeFeatureHistoryEntry.Operation.PATCH);
    if (!history.isEmpty()) {
      assertThat(history.getFirst().afterAudience()).isEqualTo(finalState.audience());
      assertThat(history.getFirst().revisionAfter()).isEqualTo(finalState.revision());
    }
  }

  private static Object outcome(Future<RuntimeFeatureWriteResult> future) throws Exception {
    try {
      return future.get(10, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeFeatureBusyException busy) {
        return busy;
      }
      throw e;
    }
  }

  private List<UUID> targetIds(String featureName) {
    return jdbc.queryForList(
        "SELECT user_group_id FROM config.runtime_feature_audience_user_group"
            + " WHERE feature_name = ? ORDER BY user_group_id",
        UUID.class,
        featureName);
  }

  private List<RuntimeFeatureHistoryEntry> history() {
    return store
        .findPage(new RuntimeFeatureHistoryQuery(null, null, null, null, null, 0, 100))
        .content();
  }

  private static RuntimeFeatureAudience.UserGroups userGroups(UUID... ids) {
    return new RuntimeFeatureAudience.UserGroups(new TreeSet<>(List.of(ids)));
  }

  @Test
  void concurrentReadersObserveCompleteUserGroupsOrAllState() throws Exception {
    assertConcurrentReadersObserveOnlyBeforeOrAfter(
        userGroups(UUID.fromString("00000000-0000-4000-8000-000000000201")),
        new RuntimeFeatureAudience.All());
  }

  @Test
  void concurrentReadersObserveCompleteOldOrNewUserGroupsState() throws Exception {
    assertConcurrentReadersObserveOnlyBeforeOrAfter(
        userGroups(UUID.fromString("00000000-0000-4000-8000-000000000201")),
        userGroups(UUID.fromString("00000000-0000-4000-8000-000000000202")));
  }

  private void assertConcurrentReadersObserveOnlyBeforeOrAfter(
      RuntimeFeatureAudience before, RuntimeFeatureAudience after) throws Exception {
    var actor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);
    assertThat(store.replaceAudience("watchbox", before, OptionalLong.of(0), actor))
        .isInstanceOf(RuntimeFeatureWriteResult.Applied.class);
    var writerCompletedStoreCall = new CountDownLatch(1);
    var releaseCommit = new CountDownLatch(1);
    var transactions = new TransactionTemplate(transactionManager);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var writer =
          executor.submit(
              () ->
                  transactions.execute(
                      _ -> {
                        var result =
                            store.replaceAudience("watchbox", after, OptionalLong.of(1), actor);
                        writerCompletedStoreCall.countDown();
                        await(releaseCommit);
                        return result;
                      }));
      assertThat(writerCompletedStoreCall.await(10, TimeUnit.SECONDS)).isTrue();

      for (var read = 0; read < 25; read++) {
        var state = store.findByName("watchbox").orElseThrow();
        assertThat(state.audience()).isEqualTo(before);
        assertThat(state.revision()).isEqualTo(1);
      }

      releaseCommit.countDown();
      assertThat(writer.get(10, TimeUnit.SECONDS))
          .isInstanceOf(RuntimeFeatureWriteResult.Applied.class);
    }

    for (var read = 0; read < 25; read++) {
      var state = store.findByName("watchbox").orElseThrow();
      assertThat(state.audience()).isEqualTo(after);
      assertThat(state.revision()).isEqualTo(2);
    }
  }

  @Test
  void historyFailureRollsBackTargetReplacementStateAndRevision() {
    var groupA = UUID.fromString("00000000-0000-4000-8000-000000000205");
    var groupB = UUID.fromString("00000000-0000-4000-8000-000000000206");
    var actor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);
    var nonexistentActor =
        new RuntimeFeatureActor(UUID.randomUUID(), Optional.empty(), Optional.empty(), false);
    var before = userGroups(groupA);
    var after = userGroups(groupB);

    assertThat(store.replaceAudience("watchbox", before, OptionalLong.of(0), actor))
        .isInstanceOf(RuntimeFeatureWriteResult.Applied.class);
    var historyBeforeFailure = history();

    assertThatThrownBy(
            () -> store.replaceAudience("watchbox", after, OptionalLong.of(1), nonexistentActor))
        .isInstanceOf(DataIntegrityViolationException.class);

    var state = store.findByName("watchbox").orElseThrow();
    assertThat(state.audience()).isEqualTo(before);
    assertThat(state.revision()).isEqualTo(1);
    assertThat(targetIds("watchbox")).containsExactly(groupA);
    assertThat(history()).isEqualTo(historyBeforeFailure);
  }

  @Test
  void acceptedNoOp_whenHistoryWriteFails_leavesLiveStateUnchanged() {
    var nonexistentActor =
        new RuntimeFeatureActor(UUID.randomUUID(), Optional.empty(), Optional.empty(), false);
    var before = store.findByName("watchbox").orElseThrow();

    assertThatThrownBy(
            () ->
                store.replaceAudience(
                    "watchbox", before.audience(), OptionalLong.of(0), nonexistentActor))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(store.findByName("watchbox").orElseThrow()).isEqualTo(before);
    assertThat(history()).isEmpty();
  }

  @Test
  void audienceModeCheckRejectsUnknownMode() {
    var transactions = new TransactionTemplate(transactionManager);

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    _ ->
                        jdbc.update(
                            "UPDATE config.runtime_feature SET audience_mode = 'LAYERS' WHERE name = 'watchbox'")))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(store.findByName("watchbox").orElseThrow().audience())
        .isEqualTo(new RuntimeFeatureAudience.None());
  }

  @Test
  void registerCreatesExactlyFrontendOnlyNoneAtRevisionZero() {
    var actor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);

    var result = store.register("themes-page", actor);

    assertThat(result).isInstanceOf(RuntimeFeatureRegistrationResult.Created.class);
    var state = result.state();
    assertThat(state.name()).isEqualTo("themes-page");
    assertThat(state.consumerClass()).isEqualTo(RuntimeFeatureConsumerClass.FRONTEND_ONLY);
    assertThat(state.audience()).isEqualTo(new RuntimeFeatureAudience.None());
    assertThat(state.revision()).isZero();

    assertThat(history())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.operation()).isEqualTo(RuntimeFeatureHistoryEntry.Operation.CREATE);
              assertThat(entry.featureName()).isEqualTo("themes-page");
              assertThat(entry.consumerClass())
                  .isEqualTo(RuntimeFeatureConsumerClass.FRONTEND_ONLY);
              assertThat(entry.beforeAudience()).isNull();
              assertThat(entry.afterAudience()).isEqualTo(new RuntimeFeatureAudience.None());
              assertThat(entry.revisionBefore()).isNull();
              assertThat(entry.revisionAfter()).isZero();
            });
  }

  @Test
  void create_whenHistoryWriteFails_rollsBackDefinition() {
    var nonexistentActor =
        new RuntimeFeatureActor(UUID.randomUUID(), Optional.empty(), Optional.empty(), false);

    assertThatThrownBy(() -> store.register("failing-registration", nonexistentActor))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(store.findByName("failing-registration")).isEmpty();
    assertThat(history()).isEmpty();
  }

  @Test
  void repeatedRegisterNeverOverwritesAudienceRevisionTargetsOrHistory() {
    var actor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);
    store.register("plugin-loader", actor);
    store.replaceAudience(
        "plugin-loader",
        new RuntimeFeatureAudience.UserGroups(new TreeSet<>(List.of(GROUP_A))),
        OptionalLong.empty(),
        actor);
    var enabled = store.findByName("plugin-loader").orElseThrow();

    var result = store.register("plugin-loader", actor);

    assertThat(result).isInstanceOf(RuntimeFeatureRegistrationResult.AlreadyExists.class);
    var after = store.findByName("plugin-loader").orElseThrow();
    assertThat(after.audience()).isEqualTo(enabled.audience());
    assertThat(after.revision()).isEqualTo(enabled.revision());
    assertThat(after.consumerClass()).isEqualTo(enabled.consumerClass());
    assertThat(after.updatedAt()).isEqualTo(enabled.updatedAt());
    assertThat(history())
        .extracting(RuntimeFeatureHistoryEntry::operation)
        .containsExactly(
            RuntimeFeatureHistoryEntry.Operation.PATCH,
            RuntimeFeatureHistoryEntry.Operation.CREATE);
  }

  @Test
  void registerReportsAnExistingBackendEnforcedRowWithoutReclassifyingIt() {
    var actor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);

    var result = store.register("watchbox", actor);

    assertThat(result).isInstanceOf(RuntimeFeatureRegistrationResult.AlreadyExists.class);
    // The store reports the stored truth; rejecting the collision is the use case's job.
    assertThat(result.state().consumerClass())
        .isEqualTo(RuntimeFeatureConsumerClass.BACKEND_ENFORCED);
    assertThat(store.findByName("watchbox").orElseThrow().consumerClass())
        .isEqualTo(RuntimeFeatureConsumerClass.BACKEND_ENFORCED);
    assertThat(history()).isEmpty();
  }

  @Test
  void concurrentDuplicateRegistrationsCreateOneRowAndNeverFailTheTransaction() throws Exception {
    var actor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);
    var barrier = new CyclicBarrier(2);

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> {
                barrier.await();
                return store.register("racing-flag", actor);
              });
      var second =
          executor.submit(
              () -> {
                barrier.await();
                return store.register("racing-flag", actor);
              });

      // ON CONFLICT DO NOTHING means neither caller can poison its transaction, so both settle:
      // exactly one Created, one AlreadyExists, and no exception on either side.
      //
      // Order-independent on purpose. Which thread wins the insert is exactly what this test does
      // not control, so asserting the types positionally (hasExactlyElementsOfTypes compares them
      // in order) failed whenever `second` won — a flake in the assertion, not in the store.
      var outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
      assertThat(outcomes)
          .satisfiesExactlyInAnyOrder(
              outcome ->
                  assertThat(outcome).isInstanceOf(RuntimeFeatureRegistrationResult.Created.class),
              outcome ->
                  assertThat(outcome)
                      .isInstanceOf(RuntimeFeatureRegistrationResult.AlreadyExists.class));
      assertThat(outcomes)
          .allSatisfy(
              outcome -> {
                assertThat(outcome.state().consumerClass())
                    .isEqualTo(RuntimeFeatureConsumerClass.FRONTEND_ONLY);
                assertThat(outcome.state().audience()).isEqualTo(new RuntimeFeatureAudience.None());
                assertThat(outcome.state().revision()).isZero();
              });
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM config.runtime_feature WHERE name = 'racing-flag'",
                Long.class))
        .isEqualTo(1L);
    assertThat(history())
        .singleElement()
        .extracting(RuntimeFeatureHistoryEntry::operation)
        .isEqualTo(RuntimeFeatureHistoryEntry.Operation.CREATE);
  }

  @Test
  void registerRejectsBlankNames() {
    var actor = new RuntimeFeatureActor(ACTOR_ID, Optional.empty(), Optional.empty(), false);

    assertThatThrownBy(() -> store.register("  ", actor))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store.register(null, actor))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting to release transaction");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting to release transaction", e);
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = RuntimeFeatureEntity.class)
  @EnableJpaRepositories(basePackageClasses = RuntimeFeatureJpaRepository.class)
  static class TestApplication {

    @Bean
    Clock runtimeFeatureClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    JpaRuntimeFeatureStore runtimeFeatureStore(
        RuntimeFeatureJpaRepository featureRepository,
        RuntimeFeatureAudienceUserGroupJpaRepository audienceUserGroupRepository,
        RuntimeFeatureHistoryJpaRepository historyRepository,
        ObjectMapper objectMapper,
        Clock runtimeFeatureClock) {
      return new JpaRuntimeFeatureStore(
          featureRepository,
          audienceUserGroupRepository,
          historyRepository,
          objectMapper,
          runtimeFeatureClock);
    }
  }
}
