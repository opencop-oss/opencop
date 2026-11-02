// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.technical.gateway.notification.NotificationWorker;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationReader;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ComponentWebIT(classes = LayerWebTestApplication.class)
class LayerArchiveRepositoryIT extends AbstractDatabaseIT {

  private static final UUID ARCHIVER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440801");
  private static final UUID SECOND_ARCHIVER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440802");

  @Autowired private LayersRepository layersRepository;
  @Autowired private IamLayerRepository iamLayerRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoBean private UserService userService;
  @MockitoBean private NotificationWorker notificationWorker;
  @MockitoBean private ConnectInstanceResolver connectInstanceResolver;
  @MockitoBean private SourceRuntimeObservationReader sourceRuntimeObservationReader;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM iam.server_layer_access");
    jdbcTemplate.update("DELETE FROM iam.group_layer_member");
    jdbcTemplate.update("DELETE FROM iam.layer_member");
    jdbcTemplate.update("DELETE FROM iam.layer");
    seedUser(ARCHIVER_ID);
    seedUser(SECOND_ARCHIVER_ID);
  }

  @Test
  void listActive_excludesArchivedLayers() throws Exception {
    var activeId = persistLayer("Active Layer");
    var archivedId = persistLayer("Archived Layer");
    archiveRow(archivedId, ARCHIVER_ID);

    var activeLayers =
        layersRepository.list(new LayerReadFilter(RecordVisibility.ACTIVE, null, null, null, null));

    assertTrue(activeLayers.stream().map(Layer::getId).anyMatch(activeId::equals));
    assertTrue(activeLayers.stream().map(Layer::getId).noneMatch(archivedId::equals));
  }

  @Test
  void listActive_excludesLayersThatAreNotReadyForOperationalUse() {
    var activeId = persistLayer("Active Layer");
    var lockedId = persistLayer("Locked Layer", LayerLifecycleState.MERGE_SOURCE_LOCKED);
    var pendingId = persistLayer("Pending Layer", LayerLifecycleState.PENDING_MERGE);
    var failedId = persistLayer("Failed Layer", LayerLifecycleState.MERGE_FAILED);

    var activeLayers =
        layersRepository.list(new LayerReadFilter(RecordVisibility.ACTIVE, null, null, null, null));

    assertTrue(activeLayers.stream().map(Layer::getId).anyMatch(activeId::equals));
    assertTrue(activeLayers.stream().map(Layer::getId).noneMatch(lockedId::equals));
    assertTrue(activeLayers.stream().map(Layer::getId).noneMatch(pendingId::equals));
    assertTrue(activeLayers.stream().map(Layer::getId).noneMatch(failedId::equals));
  }

  @Test
  void listArchived_returnsOnlyArchivedLifecycleRows() {
    var activeId = persistLayer("Active Layer");
    var pendingId = persistLayer("Pending Layer", LayerLifecycleState.PENDING_MERGE);
    var archivedId = persistLayer("Archived Layer");
    archiveRow(archivedId, ARCHIVER_ID);

    var archivedLayers =
        layersRepository.list(
            new LayerReadFilter(RecordVisibility.ARCHIVED, null, null, null, null));

    assertTrue(archivedLayers.stream().map(Layer::getId).anyMatch(archivedId::equals));
    assertTrue(archivedLayers.stream().map(Layer::getId).noneMatch(activeId::equals));
    assertTrue(archivedLayers.stream().map(Layer::getId).noneMatch(pendingId::equals));
  }

  @Test
  void archive_setsArchiveMetadataAndHidesLayerFromActiveLookup() throws Exception {
    var layerId = persistLayer("Ops");

    layersRepository.archive(layerId, ARCHIVER_ID);

    var archivedLayer = layersRepository.get(layerId, RecordVisibility.ALL);
    assertEquals(layerId, archivedLayer.getId());
    assertNotNull(archivedLayer.getArchivedAt());
    assertEquals(ARCHIVER_ID, archivedLayer.getArchivedBy());
    var ex =
        assertThrows(
            RepositoryException.class,
            () -> layersRepository.get(layerId, RecordVisibility.ACTIVE));
    assertEquals(RepositoryExceptionCodes.DOES_NOT_EXIST.code(), ex.getCode());
  }

  @Test
  void archive_whenLayerIsAlreadyArchived_throwsDoesNotExistAndKeepsTheFirstArchiverMetadata()
      throws Exception {
    var layerId = persistLayer("Ops");
    layersRepository.archive(layerId, ARCHIVER_ID);
    var firstArchivedAt = loadArchivedAt(layerId);

    var exception =
        assertThrows(
            RepositoryException.class, () -> layersRepository.archive(layerId, SECOND_ARCHIVER_ID));

    assertEquals(RepositoryExceptionCodes.DOES_NOT_EXIST.code(), exception.getCode());
    assertEquals(ARCHIVER_ID, loadArchivedBy(layerId));
    assertEquals(firstArchivedAt, loadArchivedAt(layerId));
    assertEquals(LayerLifecycleState.ARCHIVED, loadLifecycleState(layerId));
  }

  /**
   * The guard that makes overlapping archives safe is evaluated when the row is written, not when
   * it was read. A caller holding a stale ACTIVE read therefore affects no rows instead of
   * overwriting the archive the winner already acted on. A read-then-save pair evaluates the state
   * at read time and applies both writes.
   */
  @Test
  void archiveIfInState_whenAnotherCallerAlreadyArchived_affectsNoRowsAndKeepsTheWinnersMetadata() {
    var layerId = persistLayer("Ops");
    var transactions = new TransactionTemplate(transactionManager);

    var winner =
        transactions.execute(
            _ ->
                iamLayerRepository.archiveIfInState(
                    layerId,
                    LayerLifecycleState.ACTIVE,
                    LayerLifecycleState.ARCHIVED,
                    Instant.now(),
                    ARCHIVER_ID));
    var loser =
        transactions.execute(
            _ ->
                iamLayerRepository.archiveIfInState(
                    layerId,
                    LayerLifecycleState.ACTIVE,
                    LayerLifecycleState.ARCHIVED,
                    Instant.now(),
                    SECOND_ARCHIVER_ID));

    assertEquals(1, winner);
    assertEquals(0, loser);
    assertEquals(ARCHIVER_ID, loadArchivedBy(layerId));
    assertEquals(LayerLifecycleState.ARCHIVED, loadLifecycleState(layerId));
  }

  @Test
  void unarchiveIfInState_whenLayerIsAlreadyActive_affectsNoRows() {
    var layerId = persistLayer("Ops");
    var transactions = new TransactionTemplate(transactionManager);

    var affected =
        transactions.execute(
            _ ->
                iamLayerRepository.unarchiveIfInState(
                    layerId, LayerLifecycleState.ARCHIVED, LayerLifecycleState.ACTIVE));

    assertEquals(0, affected);
    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(layerId));
  }

  @Test
  void archive_whenLayerIsNotActive_throwsDoesNotExistAndLeavesLifecycleUntouched() {
    var lockedId = persistLayer("Locked", LayerLifecycleState.MERGE_SOURCE_LOCKED);

    var exception =
        assertThrows(
            RepositoryException.class, () -> layersRepository.archive(lockedId, ARCHIVER_ID));

    assertEquals(RepositoryExceptionCodes.DOES_NOT_EXIST.code(), exception.getCode());
    assertEquals(LayerLifecycleState.MERGE_SOURCE_LOCKED, loadLifecycleState(lockedId));
    assertNull(loadArchivedAt(lockedId));
    assertNull(loadArchivedBy(lockedId));
  }

  @Test
  void unarchive_clearsArchiveMetadataAndRestoresActiveLookup() throws Exception {
    var layerId = persistLayer("Ops");
    layersRepository.archive(layerId, ARCHIVER_ID);

    layersRepository.unarchive(layerId);

    var restoredLayer = layersRepository.get(layerId, RecordVisibility.ACTIVE);
    assertEquals(layerId, restoredLayer.getId());
    assertNull(loadArchivedAt(layerId));
    assertNull(loadArchivedBy(layerId));
    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(layerId));
  }

  @Test
  void unarchive_whenLayerIsNotArchived_throwsDoesNotExist() {
    var layerId = persistLayer("Ops");

    var exception =
        assertThrows(RepositoryException.class, () -> layersRepository.unarchive(layerId));

    assertEquals(RepositoryExceptionCodes.DOES_NOT_EXIST.code(), exception.getCode());
    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(layerId));
  }

  @Test
  void finalizeMergeSuccess_archivesLockedSourcesAndActivatesPendingTarget() throws Exception {
    var firstSourceId = persistLayer("Locked Source A", LayerLifecycleState.MERGE_SOURCE_LOCKED);
    var secondSourceId = persistLayer("Locked Source B", LayerLifecycleState.MERGE_SOURCE_LOCKED);
    var targetId = persistLayer("Pending Target", LayerLifecycleState.PENDING_MERGE);

    layersRepository.finalizeMergeSuccess(
        targetId, List.of(firstSourceId, secondSourceId), ARCHIVER_ID);

    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(targetId));
    assertEquals(LayerLifecycleState.ARCHIVED, loadLifecycleState(firstSourceId));
    assertEquals(LayerLifecycleState.ARCHIVED, loadLifecycleState(secondSourceId));
    assertNotNull(loadArchivedAt(firstSourceId));
    assertNotNull(loadArchivedAt(secondSourceId));
    assertEquals(ARCHIVER_ID, loadArchivedBy(firstSourceId));
    assertEquals(ARCHIVER_ID, loadArchivedBy(secondSourceId));
  }

  @Test
  void finalizeMergeFailure_unlocksSourcesAndMarksPendingTargetFailed() throws Exception {
    var firstSourceId = persistLayer("Failed Source A", LayerLifecycleState.MERGE_SOURCE_LOCKED);
    var secondSourceId = persistLayer("Failed Source B", LayerLifecycleState.MERGE_SOURCE_LOCKED);
    var targetId = persistLayer("Failed Target", LayerLifecycleState.PENDING_MERGE);

    layersRepository.finalizeMergeFailure(targetId, List.of(firstSourceId, secondSourceId));

    assertEquals(LayerLifecycleState.MERGE_FAILED, loadLifecycleState(targetId));
    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(firstSourceId));
    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(secondSourceId));
    assertNull(loadArchivedAt(firstSourceId));
    assertNull(loadArchivedAt(secondSourceId));
  }

  @Test
  void create_allowsReusingArchivedLayerName() throws Exception {
    var archivedId = persistLayer("Ops");
    archiveRow(archivedId, ARCHIVER_ID);

    var created = layersRepository.create("Ops", LayerAccessScope.PRIVATE, null);

    var activeLayersByName =
        layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, null, "Ops", null, null));
    assertEquals(1, activeLayersByName.size());
    assertEquals(created.getId(), activeLayersByName.getFirst().getId());
    assertEquals("Ops", activeLayersByName.getFirst().getName());
  }

  @Test
  void create_allowsReusingFailedMergeTargetName() throws Exception {
    var failedTargetId = persistLayer("Ops", LayerLifecycleState.MERGE_FAILED);

    var created = layersRepository.create("Ops", LayerAccessScope.PRIVATE, null);

    var allLayersByName =
        layersRepository.list(new LayerReadFilter(RecordVisibility.ALL, null, "Ops", null, null));
    assertEquals(2, allLayersByName.size());
    assertTrue(allLayersByName.stream().map(Layer::getId).anyMatch(failedTargetId::equals));
    assertTrue(allLayersByName.stream().map(Layer::getId).anyMatch(created.getId()::equals));
    assertEquals(LayerLifecycleState.MERGE_FAILED, loadLifecycleState(failedTargetId));
    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(created.getId()));
  }

  @Test
  void createPendingMerge_allowsReusingFailedMergeTargetName() throws Exception {
    var failedTargetId = persistLayer("Ops", LayerLifecycleState.MERGE_FAILED);

    var created = layersRepository.createPendingMerge("Ops", null);

    var allLayersByName =
        layersRepository.list(new LayerReadFilter(RecordVisibility.ALL, null, "Ops", null, null));
    assertEquals(2, allLayersByName.size());
    assertTrue(allLayersByName.stream().map(Layer::getId).anyMatch(failedTargetId::equals));
    assertTrue(allLayersByName.stream().map(Layer::getId).anyMatch(created.getId()::equals));
    assertEquals(LayerLifecycleState.MERGE_FAILED, loadLifecycleState(failedTargetId));
    assertEquals(LayerLifecycleState.PENDING_MERGE, loadLifecycleState(created.getId()));
  }

  @Test
  void create_whenPendingMergeTargetHasSameName_throwsAlreadyExist() {
    persistLayer("Ops", LayerLifecycleState.PENDING_MERGE);

    var exception =
        assertThrows(
            RepositoryException.class,
            () -> layersRepository.create("Ops", LayerAccessScope.PRIVATE, null));

    assertEquals(RepositoryExceptionCodes.ALREADY_EXIST.code(), exception.getCode());
  }

  @Test
  void createPendingMerge_whenActiveLayerHasSameName_throwsAlreadyExist() {
    persistLayer("Ops", LayerLifecycleState.ACTIVE);

    var exception =
        assertThrows(
            RepositoryException.class, () -> layersRepository.createPendingMerge("Ops", null));

    assertEquals(RepositoryExceptionCodes.ALREADY_EXIST.code(), exception.getCode());
  }

  @Test
  void uniqueIndex_whenMergeSourceLockedLayerHasSameName_rejectsDuplicateActiveRow() {
    persistLayer("Ops", LayerLifecycleState.MERGE_SOURCE_LOCKED);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> persistLayer("Ops", LayerLifecycleState.ACTIVE));
  }

  @Test
  void list_withTypedFilter_returnsOnlyActiveGlobalLayerByName() {
    var archivedGlobalOps = persistLayer("Ops", LayerAccessScope.GLOBAL);
    archiveRow(archivedGlobalOps, ARCHIVER_ID);
    var activeGlobalOps = persistLayer("Ops", LayerAccessScope.GLOBAL);
    persistLayer("Other", LayerAccessScope.GLOBAL);

    var layers =
        layersRepository.list(
            new LayerReadFilter(
                RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, "Ops", null, null));

    assertEquals(1, layers.size());
    assertEquals(activeGlobalOps, layers.getFirst().getId());
    assertEquals("Ops", layers.getFirst().getName());
    assertEquals(LayerAccessScope.GLOBAL, layers.getFirst().getAccessScope());
  }

  @Test
  void create_leavesUpdateMetadataNull() throws Exception {
    var layer = layersRepository.create("Fresh Layer", LayerAccessScope.PRIVATE, ARCHIVER_ID);

    // A create is not an edit. The timestamp generator is scoped to UPDATE, so the row starts with
    // no update metadata at all rather than a timestamp beside a null editor.
    assertNull(rawUpdatedAt(layer.getId()), "a newly created layer has never been updated");
    assertNull(rawUpdatedBy(layer.getId()));
    assertEquals(ARCHIVER_ID, rawCreatedBy(layer.getId()));
  }

  @Test
  void update_whenSomethingChanges_populatesBothUpdateFields() throws Exception {
    var layer = layersRepository.create("Fresh Layer", LayerAccessScope.PRIVATE, ARCHIVER_ID);

    layersRepository.update(layer.getId(), "Renamed Layer", null, null, SECOND_ARCHIVER_ID);

    assertNotNull(rawUpdatedAt(layer.getId()));
    assertEquals(SECOND_ARCHIVER_ID, rawUpdatedBy(layer.getId()));
    assertEquals(ARCHIVER_ID, rawCreatedBy(layer.getId()), "an edit must not rewrite the creator");
  }

  @Test
  void update_whenNothingChanges_leavesUpdateMetadataUntouched() throws Exception {
    var layer = layersRepository.create("Fresh Layer", LayerAccessScope.PRIVATE, ARCHIVER_ID);

    // Every value matches the row as created, so the patch changes nothing and must not claim an
    // edit happened — neither the timestamp nor the editor may move.
    layersRepository.update(
        layer.getId(), "Fresh Layer", LayerAccessScope.PRIVATE, null, SECOND_ARCHIVER_ID);

    assertNull(rawUpdatedAt(layer.getId()));
    assertNull(rawUpdatedBy(layer.getId()));
  }

  @Test
  void lockMergeSources_attributesTheLockToTheRequester() throws Exception {
    var sourceId = persistLayer("Lockable Source");
    stampPreviousEditor(sourceId, SECOND_ARCHIVER_ID);
    var before = rawUpdatedAt(sourceId);

    layersRepository.lockMergeSources(List.of(sourceId), ARCHIVER_ID);

    assertEquals(LayerLifecycleState.MERGE_SOURCE_LOCKED, loadLifecycleState(sourceId));
    assertEquals(ARCHIVER_ID, rawUpdatedBy(sourceId), "the lock belongs to whoever merged");
    assertNotEquals(before, rawUpdatedAt(sourceId));
  }

  @Test
  void finalizeMergeSuccess_attributesTargetAndSourcesToTheMerger() throws Exception {
    var sourceId = persistLayer("Locked Source", LayerLifecycleState.MERGE_SOURCE_LOCKED);
    var targetId = persistLayer("Pending Target", LayerLifecycleState.PENDING_MERGE);
    // A different, earlier editor on every row, so a stale value is distinguishable from a fresh
    // one rather than coincidentally matching the merger.
    stampPreviousEditor(sourceId, SECOND_ARCHIVER_ID);
    stampPreviousEditor(targetId, SECOND_ARCHIVER_ID);

    layersRepository.finalizeMergeSuccess(targetId, List.of(sourceId), ARCHIVER_ID);

    assertEquals(ARCHIVER_ID, rawUpdatedBy(targetId));
    assertEquals(ARCHIVER_ID, rawUpdatedBy(sourceId), "the archived source is part of the merge");
    assertEquals(ARCHIVER_ID, loadArchivedBy(sourceId));
  }

  @Test
  void finalizeMergeFailure_clearsTheEditorOnTargetAndSources() throws Exception {
    var sourceId = persistLayer("Locked Source", LayerLifecycleState.MERGE_SOURCE_LOCKED);
    var targetId = persistLayer("Pending Target", LayerLifecycleState.PENDING_MERGE);
    stampPreviousEditor(sourceId, SECOND_ARCHIVER_ID);
    stampPreviousEditor(targetId, SECOND_ARCHIVER_ID);
    var sourceBefore = rawUpdatedAt(sourceId);

    layersRepository.finalizeMergeFailure(targetId, List.of(sourceId));

    // Compensation is the system undoing itself: the transition is recorded, but crediting the
    // previous human for a rollback they did not perform would be worse than crediting nobody.
    assertNull(rawUpdatedBy(targetId));
    assertNull(rawUpdatedBy(sourceId));
    assertNotEquals(sourceBefore, rawUpdatedAt(sourceId), "the unlock is still a real transition");
  }

  /** Backdates a layer's audit fields to a different editor, so a stale value is detectable. */
  private void stampPreviousEditor(UUID layerId, UUID editorId) {
    jdbcTemplate.update(
        "UPDATE iam.layer SET updated_at = now() - interval '1 day', updated_by = ? WHERE id = ?",
        editorId,
        layerId);
  }

  private Object rawUpdatedAt(UUID layerId) {
    return jdbcTemplate.queryForObject(
        "SELECT updated_at FROM iam.layer WHERE id = ?", Object.class, layerId);
  }

  private UUID rawUpdatedBy(UUID layerId) {
    return jdbcTemplate.queryForObject(
        "SELECT updated_by FROM iam.layer WHERE id = ?", UUID.class, layerId);
  }

  private UUID rawCreatedBy(UUID layerId) {
    return jdbcTemplate.queryForObject(
        "SELECT created_by FROM iam.layer WHERE id = ?", UUID.class, layerId);
  }

  private UUID persistLayer(String name) {
    var entity = new LayerEntity();
    entity.setName(name);
    entity.setAccessScope(LayerAccessScope.PRIVATE);
    return iamLayerRepository.saveAndFlush(entity).getId();
  }

  private UUID persistLayer(String name, LayerAccessScope accessScope) {
    var entity = new LayerEntity();
    entity.setName(name);
    entity.setAccessScope(accessScope);
    return iamLayerRepository.saveAndFlush(entity).getId();
  }

  private UUID persistLayer(String name, LayerLifecycleState lifecycleState) {
    var entity = new LayerEntity();
    entity.setName(name);
    entity.setAccessScope(LayerAccessScope.PRIVATE);
    entity.setLifecycleState(lifecycleState);
    return iamLayerRepository.saveAndFlush(entity).getId();
  }

  private void archiveRow(UUID layerId, UUID archivedBy) {
    jdbcTemplate.update(
        """
        UPDATE iam.layer
        SET archived_at = now(),
            archived_by = ?,
            lifecycle_state = 'ARCHIVED'::iam.layer_lifecycle_state
        WHERE id = ?
        """,
        archivedBy,
        layerId);
  }

  private LayerLifecycleState loadLifecycleState(UUID layerId) {
    return jdbcTemplate.queryForObject(
        "SELECT lifecycle_state FROM iam.layer WHERE id = ?", LayerLifecycleState.class, layerId);
  }

  private Instant loadArchivedAt(UUID layerId) {
    return jdbcTemplate.queryForObject(
        "SELECT archived_at FROM iam.layer WHERE id = ?", Instant.class, layerId);
  }

  private UUID loadArchivedBy(UUID layerId) {
    return jdbcTemplate.queryForObject(
        "SELECT archived_by FROM iam.layer WHERE id = ?", UUID.class, layerId);
  }

  private void seedUser(UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "https://issuer.test",
        "user-" + userId,
        "user@example.test",
        "Test",
        "User",
        "Test User");
  }
}
