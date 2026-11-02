// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.appmetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.general.technical.observability.runtimefeature.MicrometerWatchboxTrackingResetTelemetry;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.technical.gateway.db.watchbox.WatchboxJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ComponentWebIT(classes = AppMetadataWebTestApplication.class)
class RuntimeFeatureWatchboxTrackingResetIT extends AbstractWebIT {

  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443201");
  private static final UUID ACTIVE_ONE = UUID.fromString("550e8400-e29b-41d4-a716-446655443211");
  private static final UUID ACTIVE_TWO = UUID.fromString("550e8400-e29b-41d4-a716-446655443212");
  private static final UUID ARCHIVED = UUID.fromString("550e8400-e29b-41d4-a716-446655443213");
  private static final UUID PILOT_GROUP = UUID.fromString("550e8400-e29b-41d4-a716-446655443214");
  private static final Instant BASELINE_UPDATED_AT = Instant.parse("2026-08-12T00:00:00Z");
  private static final Authentication ADMIN_AUTH = TestAuthentications.oidcAdmin(ADMIN_ID);

  @Autowired private JdbcTemplate jdbc;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private WatchboxJpaRepository watchboxRepository;

  @BeforeEach
  void resetState() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            _ -> {
              deleteFixtures();
              insertAdmin();
              insertWatchbox(ACTIVE_ONE, "active-one", 1, false);
              insertWatchbox(ACTIVE_TWO, "active-two", 7, false);
              insertWatchbox(ARCHIVED, "archived", 11, true);
            });
  }

  @AfterEach
  void clearState() {
    new TransactionTemplate(transactionManager).executeWithoutResult(_ -> deleteFixtures());
  }

  @Test
  void replaceAudience_whenReenabledToAll_bumpsEveryActiveEpochOnce() throws Exception {
    double resetBaseline = resetCount();
    double bumpedBaseline = bumpedEpochCount();
    long activeWatchboxes = activeWatchboxCount();

    patchAudience("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevision\":0}", 1);
    assertEpochs(2, 8, 11);
    assertThat(updatedAt(ACTIVE_ONE)).isAfter(BASELINE_UPDATED_AT);
    assertThat(updatedAt(ACTIVE_TWO)).isAfter(BASELINE_UPDATED_AT);
    assertThat(updatedAt(ARCHIVED)).isEqualTo(BASELINE_UPDATED_AT);
    assertHistoryCount(1);

    patchAudience("{\"audience\":{\"mode\":\"ALL\"}}", 1);
    assertEpochs(2, 8, 11);
    assertHistoryCount(2);

    patchAudience("{\"audience\":{\"mode\":\"NONE\"},\"expectedRevision\":1}", 2);
    assertEpochs(2, 8, 11);
    assertHistoryCount(3);

    patchAudience("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevision\":2}", 3);
    assertEpochs(3, 9, 11);
    assertHistoryCount(4);
    assertThat(resetCount()).isEqualTo(resetBaseline + 2);
    assertThat(bumpedEpochCount()).isEqualTo(bumpedBaseline + 2 * activeWatchboxes);
  }

  @Test
  void replaceAudience_whenReenabledToUserGroups_bumpsEpochsButLiveWideningDoesNot()
      throws Exception {
    double resetBaseline = resetCount();
    double bumpedBaseline = bumpedEpochCount();
    long activeWatchboxes = activeWatchboxCount();
    insertGroup();

    patchAudience(
        "{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\""
            + PILOT_GROUP
            + "\"]},\"expectedRevision\":0}",
        1);
    assertEpochs(2, 8, 11);

    patchAudience("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevision\":1}", 2);
    assertEpochs(2, 8, 11);
    assertHistoryCount(2);
    assertThat(resetCount()).isEqualTo(resetBaseline + 1);
    assertThat(bumpedEpochCount()).isEqualTo(bumpedBaseline + activeWatchboxes);
  }

  @Test
  void
      bumpAllActiveConfigEpochs_whenTransactionStartedBeforeConcurrentPatch_keepsUpdatedAtMonotonic()
          throws Exception {
    var resetTransactionStarted = new CountDownLatch(1);
    var patchWritten = new CountDownLatch(1);
    var resetAttemptingUpdate = new CountDownLatch(1);
    var resetTransactionTime = new AtomicReference<Instant>();
    var executor = Executors.newFixedThreadPool(2);
    try {
      var resetFuture =
          executor.submit(
              () ->
                  new TransactionTemplate(transactionManager)
                      .execute(
                          _ -> {
                            jdbc.execute("SET LOCAL lock_timeout = '5s'");
                            resetTransactionTime.set(
                                jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Instant.class));
                            resetTransactionStarted.countDown();
                            await(patchWritten);
                            resetAttemptingUpdate.countDown();
                            watchboxRepository.bumpAllActiveConfigEpochs();
                            return updatedAt(ACTIVE_ONE);
                          }));
      var patchFuture =
          executor.submit(
              () -> {
                await(resetTransactionStarted);
                return new TransactionTemplate(transactionManager)
                    .execute(
                        _ -> {
                          jdbc.update(
                              "UPDATE watchbox.watchbox SET updated_at = clock_timestamp() + interval '1 day' WHERE id = ?",
                              ACTIVE_ONE);
                          var patchUpdatedAt = updatedAt(ACTIVE_ONE);
                          patchWritten.countDown();
                          await(resetAttemptingUpdate);
                          return patchUpdatedAt;
                        });
              });

      var patchUpdatedAt = patchFuture.get(10, TimeUnit.SECONDS);
      var resetUpdatedAt = resetFuture.get(10, TimeUnit.SECONDS);

      assertThat(patchUpdatedAt).isAfter(resetTransactionTime.get());
      assertThat(resetUpdatedAt).isAfterOrEqualTo(patchUpdatedAt);
      assertThat(epoch(ACTIVE_ONE)).isEqualTo(2);
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  private void patchAudience(String request, long expectedRevision) throws Exception {
    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(authentication(ADMIN_AUTH))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(expectedRevision));
  }

  private void assertEpochs(long activeOne, long activeTwo, long archived) {
    assertThat(epoch(ACTIVE_ONE)).isEqualTo(activeOne);
    assertThat(epoch(ACTIVE_TWO)).isEqualTo(activeTwo);
    assertThat(epoch(ARCHIVED)).isEqualTo(archived);
  }

  private long epoch(UUID watchboxId) {
    return jdbc.queryForObject(
        "SELECT config_epoch FROM watchbox.watchbox WHERE id = ?", Long.class, watchboxId);
  }

  private Instant updatedAt(UUID watchboxId) {
    return jdbc.queryForObject(
        "SELECT updated_at FROM watchbox.watchbox WHERE id = ?", Instant.class, watchboxId);
  }

  private void assertHistoryCount(long expected) {
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM config.runtime_feature_history", Long.class))
        .isEqualTo(expected);
  }

  private double resetCount() {
    var counter =
        meterRegistry.find(MicrometerWatchboxTrackingResetTelemetry.RESETS_TOTAL).counter();
    return counter == null ? 0 : counter.count();
  }

  private double bumpedEpochCount() {
    var counter =
        meterRegistry
            .find(MicrometerWatchboxTrackingResetTelemetry.CONFIG_EPOCHS_BUMPED_TOTAL)
            .counter();
    return counter == null ? 0 : counter.count();
  }

  private long activeWatchboxCount() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM watchbox.watchbox WHERE archived_at IS NULL", Long.class);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out coordinating Watchbox timestamp contention");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted coordinating Watchbox timestamp contention", exception);
    }
  }

  private void insertAdmin() {
    jdbc.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username,
          active, administrator, created_at, updated_at
        ) VALUES (?, 'runtime-reset-admin', 'runtime-reset-admin',
                  'runtime-reset-admin', 'runtime-reset-admin',
                  true, true, now(), now())
        """,
        ADMIN_ID);
  }

  private void insertGroup() {
    jdbc.update(
        "INSERT INTO iam.user_group (id, name, description) VALUES (?, 'runtime-reset-pilot', 'runtime reset fixture')",
        PILOT_GROUP);
  }

  private void insertWatchbox(UUID id, String name, long epoch, boolean archived) {
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox (
          id, name, enabled, direction, polygon_json, filter_json,
          config_epoch, created_by, created_at, updated_at, archived_by, archived_at
        ) VALUES (
          ?, ?, true, 'BOTH',
          '{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}'::jsonb,
          '{"predicates":[]}'::jsonb,
          ?, ?, TIMESTAMPTZ '2026-08-12 00:00:00+00',
          TIMESTAMPTZ '2026-08-12 00:00:00+00',
          ?, CASE WHEN ? THEN now() ELSE NULL END
        )
        """,
        id,
        name,
        epoch,
        ADMIN_ID,
        archived ? ADMIN_ID : null,
        archived);
  }

  private void deleteFixtures() {
    jdbc.update("DELETE FROM config.runtime_feature_history");
    jdbc.update(
        "DELETE FROM watchbox.watchbox WHERE id IN (?, ?, ?)", ACTIVE_ONE, ACTIVE_TWO, ARCHIVED);
    // V34 validates mode and target rows at commit, so reset both in this transaction.
    jdbc.update(
        "UPDATE config.runtime_feature SET audience_mode = 'NONE', revision = 0 WHERE name = 'watchbox'");
    jdbc.update(
        "DELETE FROM config.runtime_feature_audience_user_group WHERE feature_name = 'watchbox'");
    jdbc.update("DELETE FROM iam.user_group_member WHERE group_id = ?", PILOT_GROUP);
    jdbc.update("DELETE FROM iam.user_group WHERE id = ?", PILOT_GROUP);
    jdbc.update("DELETE FROM opencop_user WHERE id = ?", ADMIN_ID);
  }
}
