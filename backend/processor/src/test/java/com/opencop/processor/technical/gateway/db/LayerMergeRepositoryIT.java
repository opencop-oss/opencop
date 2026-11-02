// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.LayerMergeJobSubmission;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperationPhase;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.processor.business.usecase.event.LayerMergeHandler;
import com.opencop.processor.ports.technical.gateway.cache.GeoCacheGateway;
import com.opencop.processor.ports.technical.gateway.db.GeoCacheHydrationGateway;
import com.opencop.processor.ports.technical.gateway.db.LayerMergeGateway;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.geojson.Feature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class LayerMergeRepositoryIT {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();
  private static final Instant BASE_TIME = Instant.parse("2026-05-01T10:00:00Z");
  private static final UUID TARGET_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655449000");
  private static final UUID FIRST_SOURCE_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655449001");
  private static final UUID SECOND_SOURCE_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655449002");
  private static final UUID THIRD_SOURCE_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655449003");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449004");
  private static final UUID JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449005");
  private static final List<UUID> SOURCE_LAYER_IDS =
      List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID, THIRD_SOURCE_LAYER_ID);

  private static EmbeddedPostgres postgres;
  private static DataSource dataSource;
  private static JdbcTemplate jdbc;

  private LayerMergeRepository repository;

  @BeforeAll
  static void startDatabase() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    dataSource = postgres.getPostgresDatabase();
    migrateAll(dataSource);
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterAll
  static void stopDatabase() throws Exception {
    if (postgres != null) {
      postgres.close();
    }
  }

  @BeforeEach
  void setUp() {
    repository = new LayerMergeRepository(jdbc, new DataSourceTransactionManager(dataSource));
    cleanDatabase();
    seedUser();
    seedLayer(TARGET_LAYER_ID, "target");
    seedLayer(FIRST_SOURCE_LAYER_ID, "source-one");
    seedLayer(SECOND_SOURCE_LAYER_ID, "source-two");
    seedLayer(THIRD_SOURCE_LAYER_ID, "source-three");
  }

  @Test
  void mergeLayersKeepsFirstSourceWinnerForCollidingActiveObjects() {
    seedActiveFeature(FIRST_SOURCE_LAYER_ID, "shared", "first-winner", BASE_TIME.plusSeconds(10));
    seedActiveFeature(SECOND_SOURCE_LAYER_ID, "shared", "second-loser", BASE_TIME.plusSeconds(20));
    seedActiveFeature(
        THIRD_SOURCE_LAYER_ID, "unique-third", "third-only", BASE_TIME.plusSeconds(30));

    repository.mergeLayers(SOURCE_LAYER_IDS, TARGET_LAYER_ID);

    assertThat(targetObjectPayloadLabels()).containsExactlyInAnyOrder("first-winner", "third-only");
    assertThat(targetObjectIds()).containsExactlyInAnyOrder("shared", "unique-third");
  }

  @Test
  void mergeLayersUsesHelperSelectedRowsInsteadOfHistoricalRows() {
    seedHistoricalFeature(
        FIRST_SOURCE_LAYER_ID, "tracked", "stale-history", BASE_TIME.minusSeconds(60), BASE_TIME);
    seedActiveFeature(FIRST_SOURCE_LAYER_ID, "tracked", "latest-helper", BASE_TIME.plusSeconds(60));

    repository.mergeLayers(List.of(FIRST_SOURCE_LAYER_ID), TARGET_LAYER_ID);

    assertThat(targetObjectPayloadLabels()).containsExactly("latest-helper");
    assertThat(helperObjectIds(TARGET_LAYER_ID)).containsExactly("tracked");
  }

  @Test
  void mergeLayersTreatsFutureStaleHelperRowsAsActive() {
    seedActiveFeature(
        FIRST_SOURCE_LAYER_ID,
        "future-stale",
        "active-until-future",
        BASE_TIME.plusSeconds(10),
        Instant.parse("3000-01-01T12:00:00Z"));

    repository.mergeLayers(List.of(FIRST_SOURCE_LAYER_ID), TARGET_LAYER_ID);

    assertThat(targetObjectPayloadLabels()).containsExactly("active-until-future");
    assertThat(helperObjectIds(TARGET_LAYER_ID)).containsExactly("future-stale");
  }

  @Test
  void mergeLayersCopiesRemarksOnlyForRetainedObjects() {
    seedActiveFeature(FIRST_SOURCE_LAYER_ID, "shared", "first-winner", BASE_TIME.plusSeconds(10));
    seedActiveFeature(SECOND_SOURCE_LAYER_ID, "shared", "second-loser", BASE_TIME.plusSeconds(20));
    seedRemark(FIRST_SOURCE_LAYER_ID, "shared", "copied remark");
    seedRemark(SECOND_SOURCE_LAYER_ID, "shared", "skipped remark");

    repository.mergeLayers(List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID), TARGET_LAYER_ID);

    assertThat(targetRemarkContents()).containsExactly("copied remark");
  }

  @Test
  void mergeLayersCopiesFileAttachmentsWithNewIdsAndThumbnailLinks() {
    seedActiveFeature(FIRST_SOURCE_LAYER_ID, "attached", "with-file", BASE_TIME.plusSeconds(10));
    UUID fileId = UUID.fromString("550e8400-e29b-41d4-a716-446655449101");
    UUID thumbnailId = UUID.fromString("550e8400-e29b-41d4-a716-446655449102");
    seedFileAttachment(fileId, null, "source-file.bin");
    seedFileAttachment(thumbnailId, fileId, "source-thumb.bin");
    seedEventAttachment(FIRST_SOURCE_LAYER_ID, "attached", fileId);

    repository.mergeLayers(List.of(FIRST_SOURCE_LAYER_ID), TARGET_LAYER_ID);

    List<UUID> copiedIds = targetAttachmentIds("attached");
    assertThat(copiedIds).hasSize(1).doesNotContain(fileId);
    UUID copiedFileId = copiedIds.getFirst();
    UUID copiedThumbnailParent =
        jdbc.queryForObject(
            "SELECT thumbnail_of FROM media_file WHERE original_filename = ? AND id <> ?",
            UUID.class,
            "source-thumb.bin",
            thumbnailId);
    assertThat(copiedThumbnailParent).isEqualTo(copiedFileId);
  }

  @Test
  void mergeLayersCopiesUrlAttachmentsWithNewIdsAndTargetAssociations() {
    seedActiveFeature(FIRST_SOURCE_LAYER_ID, "linked", "with-url", BASE_TIME.plusSeconds(10));
    UUID urlId = UUID.fromString("550e8400-e29b-41d4-a716-446655449111");
    seedUrlAttachment(urlId, "https://example.test/a");
    seedEventAttachment(FIRST_SOURCE_LAYER_ID, "linked", urlId);

    repository.mergeLayers(List.of(FIRST_SOURCE_LAYER_ID), TARGET_LAYER_ID);

    List<UUID> copiedIds = targetAttachmentIds("linked");
    assertThat(copiedIds).hasSize(1).doesNotContain(urlId);
    assertThat(
            jdbc.queryForObject(
                "SELECT url FROM media_url WHERE id = ?", String.class, copiedIds.getFirst()))
        .isEqualTo("https://example.test/a");
    // Guards against a future default-supplying fix silently reverting the copy to RTSP.
    assertThat(
            jdbc.queryForObject(
                "SELECT stream_kind FROM media_url WHERE id = ?",
                String.class,
                copiedIds.getFirst()))
        .isEqualTo("MJPEG");
  }

  @Test
  void mergeLayersCopiesLayerMediaWithNewIds() {
    seedActiveFeature(
        FIRST_SOURCE_LAYER_ID, "object", "with-layer-media", BASE_TIME.plusSeconds(10));
    UUID mediaId = UUID.fromString("550e8400-e29b-41d4-a716-446655449121");
    seedFileAttachment(mediaId, null, "layer-icon.bin");
    seedLayerMedia(FIRST_SOURCE_LAYER_ID, mediaId);

    repository.mergeLayers(List.of(FIRST_SOURCE_LAYER_ID), TARGET_LAYER_ID);

    List<UUID> copiedIds =
        jdbc.queryForList(
            "SELECT media_id FROM layer_media WHERE layer_id = ?", UUID.class, TARGET_LAYER_ID);
    assertThat(copiedIds).hasSize(1).doesNotContain(mediaId);
  }

  @Test
  void mergeLayersInheritsHighestServerRolePerServer() {
    seedActiveFeature(FIRST_SOURCE_LAYER_ID, "object", "source", BASE_TIME.plusSeconds(10));
    UUID sharedServerId = UUID.fromString("550e8400-e29b-41d4-a716-446655449131");
    UUID ownerServerId = UUID.fromString("550e8400-e29b-41d4-a716-446655449132");
    seedServerLayerAccess(sharedServerId, FIRST_SOURCE_LAYER_ID, "VIEWER");
    seedServerLayerAccess(sharedServerId, SECOND_SOURCE_LAYER_ID, "ADMIN");
    seedServerLayerAccess(ownerServerId, THIRD_SOURCE_LAYER_ID, "OWNER");

    repository.mergeLayers(SOURCE_LAYER_IDS, TARGET_LAYER_ID);

    assertThat(targetServerRoles())
        .containsEntry(sharedServerId, "ADMIN")
        .containsEntry(ownerServerId, "OWNER");
  }

  @Test
  void mergeLayersRejectsEmptySourceOrder() {
    assertThatThrownBy(() -> repository.mergeLayers(List.of(), TARGET_LAYER_ID))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sourceLayerIds");
  }

  @Test
  void mergeLayersAndAdvanceOperationCommitsMaterializationAndPhaseTogether() {
    seedActiveFeature(FIRST_SOURCE_LAYER_ID, "object", "source", BASE_TIME.plusSeconds(10));
    seedMergeOperation(
        JOB_ID, List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID), "PENDING", null);

    repository.mergeLayersAndAdvanceOperation(
        List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID),
        TARGET_LAYER_ID,
        LayerMergeOperationPhase.MATERIALIZED);

    assertThat(targetObjectIds()).containsExactly("object");
    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("MATERIALIZED");
  }

  @Test
  void mergeLayersAndAdvanceOperationRollsBackMaterializationWhenPhaseCannotAdvance() {
    seedActiveFeature(FIRST_SOURCE_LAYER_ID, "object", "source", BASE_TIME.plusSeconds(10));
    seedMergeOperation(
        JOB_ID, List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID), "HYDRATED", null);

    assertThatThrownBy(
            () ->
                repository.mergeLayersAndAdvanceOperation(
                    List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID),
                    TARGET_LAYER_ID,
                    LayerMergeOperationPhase.MATERIALIZED))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("to advance from PENDING to MATERIALIZED");

    assertThat(targetObjectIds()).isEmpty();
    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("HYDRATED");
  }

  @Test
  void
      finalizeMergeSuccess_whenTargetPendingAndSourcesLocked_marksTargetActiveAndArchivesSources() {
    setLayerLifecycle(TARGET_LAYER_ID, "PENDING_MERGE");
    setLayerLifecycle(FIRST_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    setLayerLifecycle(SECOND_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");

    repository.finalizeMergeSuccess(
        TARGET_LAYER_ID, List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID), USER_ID);

    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("ACTIVE");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("ARCHIVED");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("ARCHIVED");
  }

  @Test
  void finalizeMergeSuccess_whenTargetAlreadyActive_throwsAndDoesNotArchiveSources() {
    setLayerLifecycle(TARGET_LAYER_ID, "ACTIVE");
    setLayerLifecycle(FIRST_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    setLayerLifecycle(SECOND_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");

    assertThatThrownBy(
            () ->
                repository.finalizeMergeSuccess(
                    TARGET_LAYER_ID,
                    List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID),
                    USER_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transition 1 target and 2 sources")
        .hasMessageContaining("0 target and 0 sources");

    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("ACTIVE");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
  }

  @Test
  void finalizeMergeSuccess_whenOneSourceNotLocked_throwsAndRollsBackTransitions() {
    setLayerLifecycle(TARGET_LAYER_ID, "PENDING_MERGE");
    setLayerLifecycle(FIRST_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    setLayerLifecycle(SECOND_SOURCE_LAYER_ID, "ACTIVE");

    assertThatThrownBy(
            () ->
                repository.finalizeMergeSuccess(
                    TARGET_LAYER_ID,
                    List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID),
                    USER_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transition 1 target and 2 sources")
        .hasMessageContaining("1 target and 1 sources");

    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("PENDING_MERGE");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("ACTIVE");
  }

  @Test
  void finalizeMergeSuccessAndAdvanceOperationCommitsLifecycleAndPhaseTogether() {
    setLayerLifecycle(TARGET_LAYER_ID, "PENDING_MERGE");
    setLayerLifecycle(FIRST_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    setLayerLifecycle(SECOND_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    seedMergeOperation(
        JOB_ID, List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID), "HYDRATED", null);

    repository.finalizeMergeSuccessAndAdvanceOperation(
        TARGET_LAYER_ID,
        List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID),
        USER_ID,
        LayerMergeOperationPhase.FINALIZED);

    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("ACTIVE");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("ARCHIVED");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("ARCHIVED");
    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("FINALIZED");
  }

  @Test
  void finalizeMergeSuccessAndAdvanceOperationRollsBackLifecycleWhenPhaseCannotAdvance() {
    setLayerLifecycle(TARGET_LAYER_ID, "PENDING_MERGE");
    setLayerLifecycle(FIRST_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    setLayerLifecycle(SECOND_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    seedMergeOperation(
        JOB_ID, List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID), "MATERIALIZED", null);

    assertThatThrownBy(
            () ->
                repository.finalizeMergeSuccessAndAdvanceOperation(
                    TARGET_LAYER_ID,
                    List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID),
                    USER_ID,
                    LayerMergeOperationPhase.FINALIZED))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("to advance from HYDRATED to FINALIZED");

    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("PENDING_MERGE");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("MATERIALIZED");
  }

  @Test
  void finalizeMergeFailure_whenTargetAlreadyActive_throwsAndDoesNotUnlockSources() {
    setLayerLifecycle(TARGET_LAYER_ID, "ACTIVE");
    setLayerLifecycle(FIRST_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    setLayerLifecycle(SECOND_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");

    assertThatThrownBy(
            () ->
                repository.finalizeMergeFailure(
                    TARGET_LAYER_ID, List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transition 1 target and 2 sources")
        .hasMessageContaining("0 target and 0 sources");

    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("ACTIVE");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
  }

  @Test
  void finalizeMergeFailure_whenTargetPending_marksTargetFailedAndUnlocksSources() {
    setLayerLifecycle(TARGET_LAYER_ID, "PENDING_MERGE");
    setLayerLifecycle(FIRST_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    setLayerLifecycle(SECOND_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");

    repository.finalizeMergeFailure(
        TARGET_LAYER_ID, List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID));

    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("MERGE_FAILED");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("ACTIVE");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("ACTIVE");
  }

  @Test
  void finalizeMergeFailure_whenOneSourceNotLocked_throwsAndRollsBackTransitions() {
    setLayerLifecycle(TARGET_LAYER_ID, "PENDING_MERGE");
    setLayerLifecycle(FIRST_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    setLayerLifecycle(SECOND_SOURCE_LAYER_ID, "ACTIVE");

    assertThatThrownBy(
            () ->
                repository.finalizeMergeFailure(
                    TARGET_LAYER_ID, List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transition 1 target and 2 sources")
        .hasMessageContaining("1 target and 1 sources");

    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("PENDING_MERGE");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("ACTIVE");
  }

  @Test
  void finalizeMergeFailureAndSetOperationPhaseCommitsLifecycleAndPhaseTogether() {
    setLayerLifecycle(TARGET_LAYER_ID, "PENDING_MERGE");
    setLayerLifecycle(FIRST_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    setLayerLifecycle(SECOND_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    seedMergeOperation(
        JOB_ID, List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID), "PENDING", null);

    repository.finalizeMergeFailureAndSetOperationPhase(
        TARGET_LAYER_ID,
        List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID),
        LayerMergeOperationPhase.FAILED,
        "MATERIALIZATION_FAILED");

    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("MERGE_FAILED");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("ACTIVE");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("ACTIVE");
    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("FAILED");
    assertThat(operationFailureReason(TARGET_LAYER_ID)).isEqualTo("MATERIALIZATION_FAILED");
  }

  @Test
  void
      finalizeMergeFailureAndSetOperationPhaseRollsBackOperationFailureWhenLifecycleCannotFinalize() {
    setLayerLifecycle(TARGET_LAYER_ID, "ACTIVE");
    setLayerLifecycle(FIRST_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    setLayerLifecycle(SECOND_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    seedMergeOperation(
        JOB_ID, List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID), "PENDING", null);

    assertThatThrownBy(
            () ->
                repository.finalizeMergeFailureAndSetOperationPhase(
                    TARGET_LAYER_ID,
                    List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID),
                    LayerMergeOperationPhase.FAILED,
                    "MATERIALIZATION_FAILED"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transition 1 target and 2 sources");

    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("ACTIVE");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("PENDING");
    assertThat(operationFailureReason(TARGET_LAYER_ID)).isNull();
  }

  @Test
  void isMergeTargetActiveReturnsTrueOnlyForActiveTarget() {
    setLayerLifecycle(TARGET_LAYER_ID, "PENDING_MERGE");
    assertThat(repository.isMergeTargetActive(TARGET_LAYER_ID)).isFalse();

    setLayerLifecycle(TARGET_LAYER_ID, "ACTIVE");
    assertThat(repository.isMergeTargetActive(TARGET_LAYER_ID)).isTrue();
  }

  @Test
  void isMergeTargetPendingReturnsTrueOnlyForPendingTarget() {
    setLayerLifecycle(TARGET_LAYER_ID, "ACTIVE");
    assertThat(repository.isMergeTargetPending(TARGET_LAYER_ID)).isFalse();

    setLayerLifecycle(TARGET_LAYER_ID, "PENDING_MERGE");
    assertThat(repository.isMergeTargetPending(TARGET_LAYER_ID)).isTrue();
  }

  @Test
  void findMergeOperationReturnsAcceptedOperationState() {
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655449201");
    seedMergeOperation(jobId, SOURCE_LAYER_IDS, "PENDING", null);

    var operation = repository.findMergeOperation(TARGET_LAYER_ID);

    assertThat(operation).isPresent();
    assertThat(operation.get().targetLayerId()).isEqualTo(TARGET_LAYER_ID);
    assertThat(operation.get().jobId()).isEqualTo(jobId);
    assertThat(operation.get().sourceLayerIds()).containsExactlyElementsOf(SOURCE_LAYER_IDS);
    assertThat(operation.get().phase()).isEqualTo(LayerMergeOperationPhase.PENDING);
    assertThat(operation.get().failureReason()).isNull();
  }

  @Test
  void advanceMergeOperationUpdatesOnlyExpectedPhase() {
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655449202");
    seedMergeOperation(jobId, SOURCE_LAYER_IDS, "PENDING", null);

    repository.advanceMergeOperation(
        TARGET_LAYER_ID, LayerMergeOperationPhase.PENDING, LayerMergeOperationPhase.MATERIALIZED);

    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("MATERIALIZED");
  }

  @Test
  void advanceMergeOperationThrowsWhenCurrentPhaseDiffers() {
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655449203");
    seedMergeOperation(jobId, SOURCE_LAYER_IDS, "MATERIALIZED", null);

    assertThatThrownBy(
            () ->
                repository.advanceMergeOperation(
                    TARGET_LAYER_ID,
                    LayerMergeOperationPhase.PENDING,
                    LayerMergeOperationPhase.MATERIALIZED))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("to advance from PENDING to MATERIALIZED");

    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("MATERIALIZED");
  }

  @Test
  void markMergeOperationFailedPersistsTerminalReason() {
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655449204");
    seedMergeOperation(jobId, SOURCE_LAYER_IDS, "PENDING", null);

    repository.markMergeOperationFailed(
        TARGET_LAYER_ID, LayerMergeOperationPhase.PENDING, "OPERATION_IDENTITY_CONFLICT");

    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("FAILED");
    assertThat(operationFailureReason(TARGET_LAYER_ID)).isEqualTo("OPERATION_IDENTITY_CONFLICT");
  }

  @Test
  void markMergeOperationFailedThrowsWhenCurrentPhaseDiffers() {
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655449205");
    seedMergeOperation(jobId, SOURCE_LAYER_IDS, "MATERIALIZED", null);

    assertThatThrownBy(
            () ->
                repository.markMergeOperationFailed(
                    TARGET_LAYER_ID,
                    LayerMergeOperationPhase.PENDING,
                    "OPERATION_IDENTITY_CONFLICT"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("to fail from PENDING");

    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("MATERIALIZED");
    assertThat(operationFailureReason(TARGET_LAYER_ID)).isNull();
  }

  @Test
  void layerMergeHandler_whenHydrationFailsAfterMaterialization_replaySkipsMaterialization() {
    seedAcceptedMergeWithActiveSourceFeatures();
    var hydrationGateway = new FailsFirstHydrationGateway();
    var cacheGateway = new RecordingGeoCacheGateway();
    var handler = new LayerMergeHandler(repository, hydrationGateway, cacheGateway);
    var job = layerMergeJob();

    assertThatThrownBy(() -> handler.handle(job))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("target hydration failed");

    assertThat(targetObjectIds()).containsExactlyInAnyOrder("first-only", "second-only");
    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("MATERIALIZED");
    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("PENDING_MERGE");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
    assertThat(cacheGateway.clearedSourceIds()).isEmpty();

    handler.handle(job);

    assertThat(targetObjectIds()).containsExactlyInAnyOrder("first-only", "second-only");
    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("FINALIZED");
    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("ACTIVE");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("ARCHIVED");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("ARCHIVED");
    assertThat(hydrationGateway.callCount()).isEqualTo(2);
    assertThat(cacheGateway.writeCount()).isEqualTo(1);
    assertThat(cacheGateway.clearedSourceIds())
        .containsExactly(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID);
  }

  @Test
  void layerMergeHandler_whenLifecycleFinalizationFailsAfterHydration_replayFinalizesOnly() {
    seedAcceptedMergeWithActiveSourceFeatures();
    var failingGateway = new FailsFirstFinalizeSuccessGateway(repository);
    var hydrationGateway = new RecordingHydrationGateway();
    var cacheGateway = new RecordingGeoCacheGateway();
    var handler = new LayerMergeHandler(failingGateway, hydrationGateway, cacheGateway);
    var job = layerMergeJob();

    assertThatThrownBy(() -> handler.handle(job))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("lifecycle finalization failed");

    assertThat(targetObjectIds()).containsExactlyInAnyOrder("first-only", "second-only");
    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("HYDRATED");
    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("PENDING_MERGE");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("MERGE_SOURCE_LOCKED");
    assertThat(hydrationGateway.callCount()).isEqualTo(1);
    assertThat(cacheGateway.clearedSourceIds()).isEmpty();

    handler.handle(job);

    assertThat(targetObjectIds()).containsExactlyInAnyOrder("first-only", "second-only");
    assertThat(operationPhase(TARGET_LAYER_ID)).isEqualTo("FINALIZED");
    assertThat(layerLifecycle(TARGET_LAYER_ID)).isEqualTo("ACTIVE");
    assertThat(layerLifecycle(FIRST_SOURCE_LAYER_ID)).isEqualTo("ARCHIVED");
    assertThat(layerLifecycle(SECOND_SOURCE_LAYER_ID)).isEqualTo("ARCHIVED");
    assertThat(hydrationGateway.callCount()).isEqualTo(1);
    assertThat(cacheGateway.clearedSourceIds())
        .containsExactly(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID);
  }

  private static void migrateAll(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration", sdkMigrationLocation())
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();
  }

  private static String sdkMigrationLocation() {
    return "filesystem:"
        + Path.of("..", "sdk", "src", "main", "resources", "db", "migration").toAbsolutePath();
  }

  private static void cleanDatabase() {
    jdbc.execute("DELETE FROM processor.layer_merge_operation");
    jdbc.execute("DELETE FROM event_attachment");
    jdbc.execute("DELETE FROM layer_media");
    jdbc.execute("DELETE FROM media_url");
    jdbc.execute("DELETE FROM media_file");
    jdbc.execute("DELETE FROM media_attachment");
    jdbc.execute("DELETE FROM geo.remarks");
    jdbc.execute("DELETE FROM geo.feature_latest_active_helper");
    jdbc.execute("DELETE FROM geo.features");
    jdbc.execute("DELETE FROM iam.server_layer_access");
    jdbc.execute("DELETE FROM iam.layer");
    jdbc.execute("DELETE FROM opencop_user");
  }

  private static void seedUser() {
    jdbc.update(
        """
        INSERT INTO opencop_user(id, idp_user_id, idp_username, idp_issuer, username)
        VALUES (?, ?, ?, ?, ?)
        """,
        USER_ID,
        "user",
        "user",
        "issuer",
        "user");
  }

  private static void seedLayer(UUID layerId, String name) {
    jdbc.update("INSERT INTO iam.layer(id, name) VALUES (?, ?)", layerId, name);
  }

  private void seedAcceptedMergeWithActiveSourceFeatures() {
    setLayerLifecycle(TARGET_LAYER_ID, "PENDING_MERGE");
    setLayerLifecycle(FIRST_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    setLayerLifecycle(SECOND_SOURCE_LAYER_ID, "MERGE_SOURCE_LOCKED");
    seedActiveFeature(FIRST_SOURCE_LAYER_ID, "first-only", "first", BASE_TIME.plusSeconds(10));
    seedActiveFeature(SECOND_SOURCE_LAYER_ID, "second-only", "second", BASE_TIME.plusSeconds(20));
    seedMergeOperation(
        JOB_ID, List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID), "PENDING", null);
  }

  private static LayerMergeJobSubmission layerMergeJob() {
    return new LayerMergeJobSubmission(
        JOB_ID,
        JobIssuer.user(USER_ID),
        TARGET_LAYER_ID,
        List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID));
  }

  private static void setLayerLifecycle(UUID layerId, String lifecycleState) {
    jdbc.update(
        "UPDATE iam.layer SET lifecycle_state = ?::iam.layer_lifecycle_state WHERE id = ?",
        lifecycleState,
        layerId);
  }

  private static void seedMergeOperation(
      UUID jobId, List<UUID> sourceLayerIds, String phase, String failureReason) {
    jdbc.update(
        """
        INSERT INTO processor.layer_merge_operation(
          target_layer_id, job_id, source_layer_ids, phase, failure_reason
        )
        VALUES (?, ?, ?::uuid[], ?::processor.layer_merge_operation_phase, ?)
        """,
        TARGET_LAYER_ID,
        jobId,
        sourceLayerIds.stream().map(UUID::toString).toArray(String[]::new),
        phase,
        failureReason);
  }

  private static String operationPhase(UUID targetLayerId) {
    return jdbc.queryForObject(
        "SELECT phase::text FROM processor.layer_merge_operation WHERE target_layer_id = ?",
        String.class,
        targetLayerId);
  }

  private static String operationFailureReason(UUID targetLayerId) {
    return jdbc.queryForObject(
        "SELECT failure_reason FROM processor.layer_merge_operation WHERE target_layer_id = ?",
        String.class,
        targetLayerId);
  }

  private static String layerLifecycle(UUID layerId) {
    return jdbc.queryForObject(
        "SELECT lifecycle_state::text FROM iam.layer WHERE id = ?", String.class, layerId);
  }

  private static void seedHistoricalFeature(
      UUID sourceId, String objectId, String label, Instant createdAt, Instant staleAt) {
    UUID eventId = UUID.randomUUID();
    seedFeatureRow(eventId, sourceId, objectId, label, createdAt, staleAt);
  }

  private static void seedActiveFeature(
      UUID sourceId, String objectId, String label, Instant createdAt) {
    seedActiveFeature(sourceId, objectId, label, createdAt, null);
  }

  private static void seedActiveFeature(
      UUID sourceId, String objectId, String label, Instant createdAt, Instant staleAt) {
    UUID eventId = UUID.randomUUID();
    seedFeatureRow(eventId, sourceId, objectId, label, createdAt, staleAt);
    jdbc.update(
        """
        INSERT INTO geo.feature_latest_active_helper(
          source, object_id, event_id, playback_at, created_at, stale_at, first_emission_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        sourceId,
        objectId,
        eventId,
        Timestamp.from(createdAt),
        Timestamp.from(createdAt),
        staleAt == null ? null : Timestamp.from(staleAt),
        Timestamp.from(createdAt));
  }

  private static void seedFeatureRow(
      UUID eventId,
      UUID sourceId,
      String objectId,
      String label,
      Instant createdAt,
      Instant staleAt) {
    jdbc.update(
        """
        INSERT INTO geo.features(
          id, feature_kind, source, object_id, created_at, start_at, stale_at, geom, payload
        )
        VALUES (?, 'event', ?, ?, ?, ?, ?, ?, ?::jsonb)
        """,
        eventId,
        sourceId,
        objectId,
        Timestamp.from(createdAt),
        Timestamp.from(createdAt),
        staleAt == null ? null : Timestamp.from(staleAt),
        "{\"type\":\"Point\",\"coordinates\":[-73.5,45.5]}",
        """
        {"type":"Feature","id":"%s","source":"%s","properties":{"label":"%s","opencop":{"id":"%s","source":"%s"}}}
        """
            .formatted(eventId, sourceId, label, eventId, sourceId));
  }

  private static void seedRemark(UUID sourceId, String objectId, String content) {
    jdbc.update(
        """
        INSERT INTO geo.remarks(id, source_id, object_id, content, user_id)
        VALUES (?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        sourceId,
        objectId,
        content,
        USER_ID);
  }

  private static void seedFileAttachment(UUID mediaId, UUID thumbnailOf, String filename) {
    jdbc.update(
        """
        INSERT INTO media_attachment(id, creator_id, owner_id, access, usage)
        VALUES (?, ?, ?, 'LOGGED_IN', 'EVENT_DETAIL')
        """,
        mediaId,
        USER_ID,
        USER_ID);
    jdbc.update(
        """
        INSERT INTO media_file(
          id, blob_key, original_filename, content_type, size_bytes, mime_type, attributes, thumbnail_of
        )
        VALUES (?, ?, ?, 'IMAGE', 20, 'image/png', '{}'::jsonb, ?)
        """,
        mediaId,
        "blob/" + mediaId,
        filename,
        thumbnailOf);
  }

  private static void seedUrlAttachment(UUID mediaId, String url) {
    jdbc.update(
        """
        INSERT INTO media_attachment(id, creator_id, owner_id, access, usage)
        VALUES (?, ?, ?, 'LOGGED_IN', 'EVENT_DETAIL')
        """,
        mediaId,
        USER_ID,
        USER_ID);
    jdbc.update("INSERT INTO media_url(id, url, stream_kind) VALUES (?, ?, 'MJPEG')", mediaId, url);
  }

  private static void seedEventAttachment(UUID sourceId, String objectId, UUID mediaId) {
    jdbc.update(
        "INSERT INTO event_attachment(source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        sourceId,
        objectId,
        mediaId);
  }

  private static void seedLayerMedia(UUID layerId, UUID mediaId) {
    jdbc.update("INSERT INTO layer_media(layer_id, media_id) VALUES (?, ?)", layerId, mediaId);
  }

  private static void seedServerLayerAccess(UUID serverId, UUID layerId, String role) {
    jdbc.update(
        "INSERT INTO iam.server_layer_access(server_id, layer_id, role) VALUES (?, ?, ?::iam.server_role)",
        serverId,
        layerId,
        role);
  }

  private static List<String> targetObjectPayloadLabels() {
    return jdbc.queryForList(
        "SELECT payload #>> '{properties,label}' FROM geo.features WHERE source = ? ORDER BY object_id",
        String.class,
        TARGET_LAYER_ID);
  }

  private static List<String> targetObjectIds() {
    return jdbc.queryForList(
        "SELECT object_id FROM geo.features WHERE source = ? ORDER BY object_id",
        String.class,
        TARGET_LAYER_ID);
  }

  private static List<String> helperObjectIds(UUID sourceId) {
    return jdbc.queryForList(
        "SELECT object_id FROM geo.feature_latest_active_helper WHERE source = ? ORDER BY object_id",
        String.class,
        sourceId);
  }

  private static List<String> targetRemarkContents() {
    return jdbc.queryForList(
        "SELECT content FROM geo.remarks WHERE source_id = ? ORDER BY content",
        String.class,
        TARGET_LAYER_ID);
  }

  private static List<UUID> targetAttachmentIds(String objectId) {
    return jdbc.queryForList(
        """
        SELECT attachment_id
        FROM event_attachment
        WHERE source_id = ? AND object_id = ?
        ORDER BY attachment_id
        """,
        UUID.class,
        TARGET_LAYER_ID,
        objectId);
  }

  private static Map<UUID, String> targetServerRoles() {
    return jdbc.query(
        "SELECT server_id, role::text FROM iam.server_layer_access WHERE layer_id = ?",
        rs -> {
          Map<UUID, String> roles = new LinkedHashMap<>();
          while (rs.next()) {
            roles.put(rs.getObject("server_id", UUID.class), rs.getString("role"));
          }
          return roles;
        },
        TARGET_LAYER_ID);
  }

  private static class RecordingHydrationGateway implements GeoCacheHydrationGateway {

    private int callCount;

    @Override
    public HydrationSnapshotStats hydrateSnapshot(
        UUID sourceId, int pageSize, java.util.function.Consumer<List<Feature>> pageConsumer) {
      callCount++;
      pageConsumer.accept(List.of(new Feature()));
      return new HydrationSnapshotStats(1, 1, "object");
    }

    int callCount() {
      return callCount;
    }
  }

  private static final class FailsFirstHydrationGateway extends RecordingHydrationGateway {

    @Override
    public HydrationSnapshotStats hydrateSnapshot(
        UUID sourceId, int pageSize, java.util.function.Consumer<List<Feature>> pageConsumer) {
      if (callCount() == 0) {
        super.hydrateSnapshot(sourceId, pageSize, _ -> {});
        throw new IllegalStateException("target hydration failed");
      }
      return super.hydrateSnapshot(sourceId, pageSize, pageConsumer);
    }
  }

  private static final class RecordingGeoCacheGateway implements GeoCacheGateway {

    private int writeCount;
    private final List<UUID> clearedSourceIds = new ArrayList<>();

    @Override
    public void write(List<Feature> inputs, boolean publishUpdates) {
      writeCount++;
    }

    @Override
    public void clearSource(UUID sourceId) {
      clearedSourceIds.add(sourceId);
    }

    @Override
    public void evictObject(UUID sourceId, String objectId, long ifNotNewerThanMs) {}

    int writeCount() {
      return writeCount;
    }

    List<UUID> clearedSourceIds() {
      return Collections.unmodifiableList(clearedSourceIds);
    }
  }

  private static final class FailsFirstFinalizeSuccessGateway implements LayerMergeGateway {

    private final LayerMergeGateway delegate;
    private boolean shouldFail = true;

    private FailsFirstFinalizeSuccessGateway(LayerMergeGateway delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<com.opencop.core.sdk.model.layermerge.LayerMergeOperation> findMergeOperation(
        UUID targetLayerId) {
      return delegate.findMergeOperation(targetLayerId);
    }

    @Override
    public void advanceMergeOperation(
        UUID targetLayerId,
        LayerMergeOperationPhase expectedPhase,
        LayerMergeOperationPhase nextPhase) {
      delegate.advanceMergeOperation(targetLayerId, expectedPhase, nextPhase);
    }

    @Override
    public void markMergeOperationFailed(
        UUID targetLayerId, LayerMergeOperationPhase expectedPhase, String failureReason) {
      delegate.markMergeOperationFailed(targetLayerId, expectedPhase, failureReason);
    }

    @Override
    public boolean isMergeTargetActive(UUID targetLayerId) {
      return delegate.isMergeTargetActive(targetLayerId);
    }

    @Override
    public boolean isMergeTargetPending(UUID targetLayerId) {
      return delegate.isMergeTargetPending(targetLayerId);
    }

    @Override
    public void mergeLayers(List<UUID> sourceLayerIds, UUID targetLayerId) {
      delegate.mergeLayers(sourceLayerIds, targetLayerId);
    }

    @Override
    public void mergeLayersAndAdvanceOperation(
        List<UUID> sourceLayerIds, UUID targetLayerId, LayerMergeOperationPhase nextPhase) {
      delegate.mergeLayersAndAdvanceOperation(sourceLayerIds, targetLayerId, nextPhase);
    }

    @Override
    public void finalizeMergeSuccess(
        UUID targetLayerId, List<UUID> sourceLayerIds, UUID archivedBy) {
      delegate.finalizeMergeSuccess(targetLayerId, sourceLayerIds, archivedBy);
    }

    @Override
    public void finalizeMergeSuccessAndAdvanceOperation(
        UUID targetLayerId,
        List<UUID> sourceLayerIds,
        UUID archivedBy,
        LayerMergeOperationPhase nextPhase) {
      if (shouldFail) {
        shouldFail = false;
        throw new IllegalStateException("lifecycle finalization failed");
      }
      delegate.finalizeMergeSuccessAndAdvanceOperation(
          targetLayerId, sourceLayerIds, archivedBy, nextPhase);
    }

    @Override
    public void finalizeMergeFailure(UUID targetLayerId, List<UUID> sourceLayerIds) {
      delegate.finalizeMergeFailure(targetLayerId, sourceLayerIds);
    }

    @Override
    public void finalizeMergeFailureAndSetOperationPhase(
        UUID targetLayerId,
        List<UUID> sourceLayerIds,
        LayerMergeOperationPhase phase,
        String failureReason) {
      delegate.finalizeMergeFailureAndSetOperationPhase(
          targetLayerId, sourceLayerIds, phase, failureReason);
    }
  }
}
