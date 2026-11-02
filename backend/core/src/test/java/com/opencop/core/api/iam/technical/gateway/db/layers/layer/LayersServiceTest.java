// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.iam.ports.gateway.mapper.RepositoryMapper;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.member.LayerMemberEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;

class LayersServiceTest {

  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655446001");
  private static final UUID ARCHIVED_BY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final UUID UPDATED_BY = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
  private static final Instant ARCHIVED_AT = Instant.parse("2026-05-25T12:00:00Z");

  private IamLayerRepository repository;
  private LayersService service;

  @BeforeEach
  void setUp() {
    repository = org.mockito.Mockito.mock(IamLayerRepository.class);
    service =
        new LayersService(repository, _ -> Map.of(), org.mockito.Mockito.mock(EntityManager.class));
  }

  @Test
  void create_whenScopeIsNull_defaultsToPrivate() throws Exception {
    when(repository.count(any(Specification.class))).thenReturn(0L);
    when(repository.saveAndFlush(any(LayerEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Layer created = service.create("Ops", null, null);

    var captor = ArgumentCaptor.forClass(LayerEntity.class);
    verify(repository).saveAndFlush(captor.capture());
    assertEquals(LayerAccessScope.PRIVATE, captor.getValue().getAccessScope());
    assertEquals(LayerAccessScope.PRIVATE, created.getAccessScope());
  }

  @Test
  void get_whenLayerIsArchived_returnsIdentityRow() throws Exception {
    var archived = layerEntity(LAYER_ID, "Archived", LayerAccessScope.PRIVATE);
    archived.setArchivedAt(ARCHIVED_AT);
    when(repository.findById(LAYER_ID)).thenReturn(Optional.of(archived));

    var layer = service.get(LAYER_ID);

    assertEquals(LAYER_ID, layer.getId());
    assertEquals(ARCHIVED_AT, layer.getArchivedAt());
  }

  @Test
  void get_whenRecordVisibilityAllAndLayerArchived_returnsLayer() throws Exception {
    var archived = layerEntity(LAYER_ID, "Archived", LayerAccessScope.PRIVATE);
    archived.setArchivedAt(ARCHIVED_AT);
    when(repository.findAll(any(Specification.class))).thenReturn(List.of(archived));

    var layer = service.get(LAYER_ID, RecordVisibility.ALL);

    assertEquals(LAYER_ID, layer.getId());
    verify(repository).findAll(any(Specification.class));
  }

  @Test
  void create_whenNameExists_throwsAlreadyExist() {
    when(repository.count(any(Specification.class))).thenReturn(1L);

    var exception =
        assertThrows(RepositoryException.class, () -> service.create("Ops", null, null));

    assertEquals(RepositoryExceptionCodes.ALREADY_EXIST.code(), exception.getCode());
  }

  @Test
  void list_withGlobalFilter_mapsRepositoryResults() {
    when(repository.findAll(any(Specification.class)))
        .thenReturn(List.of(layerEntity(LAYER_ID, "Shared", LayerAccessScope.GLOBAL)));

    var layers =
        service.list(new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null));

    assertEquals(1, layers.size());
    assertEquals(LayerAccessScope.GLOBAL, layers.getFirst().getAccessScope());
    assertEquals("Shared", layers.getFirst().getName());
  }

  @Test
  void update_whenNameAndScopeChange_persistsBothAndRecordsEditor() throws Exception {
    var entity = layerEntity(LAYER_ID, "Old", LayerAccessScope.PRIVATE);
    when(repository.findById(LAYER_ID)).thenReturn(Optional.of(entity));
    when(repository.count(any(Specification.class))).thenReturn(0L);

    service.update(LAYER_ID, "New", LayerAccessScope.GLOBAL, null, UPDATED_BY);

    assertEquals("New", entity.getName());
    assertEquals(LayerAccessScope.GLOBAL, entity.getAccessScope());
    assertEquals(UPDATED_BY, entity.getUpdatedBy());
    verify(repository).save(entity);
  }

  @Test
  void update_whenNothingChanges_doesNotSaveOrRecordEditor() throws Exception {
    var entity = layerEntity(LAYER_ID, "Same", LayerAccessScope.PRIVATE);
    when(repository.findById(LAYER_ID)).thenReturn(Optional.of(entity));

    service.update(LAYER_ID, "Same", LayerAccessScope.PRIVATE, null, UPDATED_BY);

    assertNull(entity.getUpdatedBy());
    verify(repository, never()).save(any());
  }

  @Test
  void update_whenNewNameAlreadyTaken_throwsAndDoesNotSave() {
    var entity = layerEntity(LAYER_ID, "Old", LayerAccessScope.PRIVATE);
    when(repository.findById(LAYER_ID)).thenReturn(Optional.of(entity));
    when(repository.count(any(Specification.class))).thenReturn(1L);

    var exception =
        assertThrows(
            RepositoryException.class,
            () -> service.update(LAYER_ID, "New", LayerAccessScope.GLOBAL, null, null));

    assertEquals(RepositoryExceptionCodes.NAME_ALREADY_TAKEN.code(), exception.getCode());
    verify(repository, never()).save(any());
  }

  @Test
  void toModel_whenLayerIsArchived_mapsArchiveMetadata() {
    var entity = layerEntity(LAYER_ID, "Shared", LayerAccessScope.GLOBAL);
    entity.setArchivedAt(ARCHIVED_AT);
    entity.setArchivedBy(ARCHIVED_BY);
    RepositoryMapper<LayerMemberEntity, LayerMember> layerMemberMapper =
        mock(RepositoryMapper.class);
    var mapper = new LayerMapper(layerMemberMapper);

    Layer mapped = mapper.toModel(entity);

    assertEquals(ARCHIVED_AT, mapped.getArchivedAt());
    assertEquals(ARCHIVED_BY, mapped.getArchivedBy());
  }

  @Test
  void layerReadFilter_whenRecordVisibilityIsNull_defaultsToActiveAndPreservesOtherFields() {
    var filter = new LayerReadFilter(null, LayerAccessScope.GLOBAL, "Ops", null, Set.of());

    assertEquals(RecordVisibility.ACTIVE, filter.recordVisibilityOrDefault());
    assertEquals(LayerAccessScope.GLOBAL, filter.accessScope());
    assertEquals("Ops", filter.name());
  }

  @Test
  void list_whenTypedFilterRecordVisibilityIsNull_usesActiveSpecification() {
    when(repository.findAll(any(Specification.class))).thenReturn(List.of());

    service.list(new LayerReadFilter(null, LayerAccessScope.GLOBAL, null, null, Set.of()));

    var specificationCaptor = ArgumentCaptor.forClass(Specification.class);
    verify(repository).findAll(specificationCaptor.capture());
    verifyActiveAndGlobalPredicates(specificationCaptor.getValue());
  }

  @Test
  void
      list_whenRecordVisibilityAllAndIdsProvided_returnsActiveAndArchivedFromOneSpecificationCall() {
    var firstId = UUID.fromString("550e8400-e29b-41d4-a716-446655446010");
    var secondId = UUID.fromString("550e8400-e29b-41d4-a716-446655446011");
    var activeLayer = layerEntity(firstId, "One", LayerAccessScope.PRIVATE);
    var archivedLayer = layerEntity(secondId, "Two", LayerAccessScope.PRIVATE);
    archivedLayer.setArchivedAt(ARCHIVED_AT);
    when(repository.findAll(any(Specification.class)))
        .thenReturn(List.of(activeLayer, archivedLayer));

    var layers =
        service.list(
            new LayerReadFilter(RecordVisibility.ALL, null, null, null, Set.of(firstId, secondId)));

    assertEquals(2, layers.size());
    var specificationCaptor = ArgumentCaptor.forClass(Specification.class);
    verify(repository).findAll(specificationCaptor.capture());
    verifyAllRecordVisibilityOnlyUsesIdsPredicate(
        specificationCaptor.getValue(), Set.of(firstId, secondId));
  }

  @Test
  void list_whenRecordVisibilityArchivedAndSingleIdProvided_usesArchivedAndSingleIdPredicates() {
    var expectedId = UUID.fromString("550e8400-e29b-41d4-a716-446655446099");
    var archivedLayer = layerEntity(expectedId, "Archived", LayerAccessScope.PRIVATE);
    archivedLayer.setArchivedAt(ARCHIVED_AT);
    when(repository.findAll(any(Specification.class))).thenReturn(List.of(archivedLayer));

    var layers =
        service.list(
            new LayerReadFilter(RecordVisibility.ARCHIVED, null, null, expectedId, Set.of()));

    assertEquals(1, layers.size());
    var specificationCaptor = ArgumentCaptor.forClass(Specification.class);
    verify(repository).findAll(specificationCaptor.capture());
    verifyArchivedAndSingleIdPredicates(specificationCaptor.getValue(), expectedId);
  }

  @Test
  void create_whenArchivedLayerHasSameName_duplicateNameDetectionIgnoresArchivedLayer()
      throws Exception {
    when(repository.count(any(Specification.class))).thenReturn(0L);
    when(repository.saveAndFlush(any(LayerEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var created = service.create("Ops", LayerAccessScope.PRIVATE, null);

    assertEquals("Ops", created.getName());
    var specificationCaptor = ArgumentCaptor.forClass(Specification.class);
    verify(repository).count(specificationCaptor.capture());
    verifyReservedNameSpecification(specificationCaptor.getValue(), "Ops");
    verify(repository).saveAndFlush(any(LayerEntity.class));
  }

  @Test
  void create_whenArchivedAtPredicateFindsNoActiveName_createsPrivateLayer() throws Exception {
    when(repository.count(any(Specification.class))).thenReturn(0L);
    when(repository.saveAndFlush(any(LayerEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Layer created = service.create("Ops", LayerAccessScope.PRIVATE, null);

    assertEquals("Ops", created.getName());
    assertEquals(LayerAccessScope.PRIVATE, created.getAccessScope());
  }

  @Test
  void createPendingMerge_whenUniqueNameConstraintFails_throwsAlreadyExist() {
    when(repository.count(any(Specification.class))).thenReturn(0L);
    when(repository.saveAndFlush(any(LayerEntity.class)))
        .thenThrow(new DataIntegrityViolationException("uk_layer_name_active"));

    var exception =
        assertThrows(RepositoryException.class, () -> service.createPendingMerge("Ops", null));

    assertEquals(RepositoryExceptionCodes.ALREADY_EXIST.code(), exception.getCode());
  }

  @Test
  void createPendingMerge_whenDifferentIntegrityConstraintFails_rethrowsIntegrityViolation() {
    when(repository.count(any(Specification.class))).thenReturn(0L);
    when(repository.saveAndFlush(any(LayerEntity.class)))
        .thenThrow(new DataIntegrityViolationException("ck_layer_archive_actor_consistency"));

    assertThrows(
        DataIntegrityViolationException.class, () -> service.createPendingMerge("Ops", null));
  }

  @Test
  void layersServiceSource_doesNotContainMockitoBranchMarkers() throws Exception {
    String source =
        Files.readString(
            java.nio.file.Path.of(
                "src/main/java/com/opencop/core/api/iam/technical/gateway/db/layers/layer/LayersService.java"));

    org.junit.jupiter.api.Assertions.assertFalse(source.contains("repository.getClass().getName"));
    org.junit.jupiter.api.Assertions.assertFalse(source.contains("isMockitoRepositoryProxy"));
    org.junit.jupiter.api.Assertions.assertFalse(source.contains("MockitoMock"));
  }

  private static LayerEntity layerEntity(UUID id, String name, LayerAccessScope accessScope) {
    var entity = new LayerEntity();
    entity.setId(id);
    entity.setName(name);
    entity.setAccessScope(accessScope);
    return entity;
  }

  private static void verifyActiveAndGlobalPredicates(Specification<LayerEntity> specification) {
    Root<LayerEntity> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
    @SuppressWarnings("unchecked")
    Path<Object> lifecycleStatePath = mock(Path.class);
    @SuppressWarnings("unchecked")
    Path<Object> accessScopePath = mock(Path.class);
    Predicate lifecycleStatePredicate = mock(Predicate.class);
    Predicate accessScopePredicate = mock(Predicate.class);
    Predicate combinedPredicate = mock(Predicate.class);

    when(root.get(eq("lifecycleState"))).thenReturn(lifecycleStatePath);
    when(root.get(eq("accessScope"))).thenReturn(accessScopePath);
    when(criteriaBuilder.equal(lifecycleStatePath, LayerLifecycleState.ACTIVE))
        .thenReturn(lifecycleStatePredicate);
    when(criteriaBuilder.equal(accessScopePath, LayerAccessScope.GLOBAL))
        .thenReturn(accessScopePredicate);
    when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(combinedPredicate);

    specification.toPredicate(root, query, criteriaBuilder);

    verify(criteriaBuilder).equal(lifecycleStatePath, LayerLifecycleState.ACTIVE);
    verify(criteriaBuilder).equal(accessScopePath, LayerAccessScope.GLOBAL);
  }

  private static void verifyAllRecordVisibilityOnlyUsesIdsPredicate(
      Specification<LayerEntity> specification, Collection<UUID> ids) {
    Root<LayerEntity> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
    @SuppressWarnings("unchecked")
    Path<Object> lifecycleStatePath = mock(Path.class);
    @SuppressWarnings("unchecked")
    Path<Object> idPath = mock(Path.class);
    Predicate idsPredicate = mock(Predicate.class);
    Predicate combinedPredicate = mock(Predicate.class);

    when(root.get(eq("lifecycleState"))).thenReturn(lifecycleStatePath);
    when(root.get(eq("id"))).thenReturn(idPath);
    when(idPath.in(ids)).thenReturn(idsPredicate);
    when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(combinedPredicate);

    specification.toPredicate(root, query, criteriaBuilder);

    verify(criteriaBuilder, never()).equal(lifecycleStatePath, LayerLifecycleState.ACTIVE);
    verify(criteriaBuilder, never()).equal(lifecycleStatePath, LayerLifecycleState.ARCHIVED);
    verify(idPath).in(ids);
  }

  private static void verifyReservedNameSpecification(
      Specification<LayerEntity> specification, String expectedName) {
    Root<LayerEntity> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
    @SuppressWarnings("unchecked")
    Path<Object> namePath = mock(Path.class);
    @SuppressWarnings("unchecked")
    Path<Object> lifecycleStatePath = mock(Path.class);
    Predicate namePredicate = mock(Predicate.class);
    Predicate lifecycleStatePredicate = mock(Predicate.class);
    Predicate combinedPredicate = mock(Predicate.class);
    var reservedStates =
        Set.of(
            LayerLifecycleState.ACTIVE,
            LayerLifecycleState.MERGE_SOURCE_LOCKED,
            LayerLifecycleState.PENDING_MERGE);

    when(root.get(eq("name"))).thenReturn(namePath);
    when(root.get(eq("lifecycleState"))).thenReturn(lifecycleStatePath);
    when(criteriaBuilder.equal(namePath, expectedName)).thenReturn(namePredicate);
    when(lifecycleStatePath.in(reservedStates)).thenReturn(lifecycleStatePredicate);
    when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(combinedPredicate);

    specification.toPredicate(root, query, criteriaBuilder);

    verify(criteriaBuilder).equal(namePath, expectedName);
    verify(lifecycleStatePath).in(reservedStates);
  }

  private static void verifyArchivedAndSingleIdPredicates(
      Specification<LayerEntity> specification, UUID expectedId) {
    Root<LayerEntity> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
    @SuppressWarnings("unchecked")
    Path<Object> lifecycleStatePath = mock(Path.class);
    @SuppressWarnings("unchecked")
    Path<Object> idPath = mock(Path.class);
    Predicate lifecycleStatePredicate = mock(Predicate.class);
    Predicate idPredicate = mock(Predicate.class);
    Predicate combinedPredicate = mock(Predicate.class);

    when(root.get(eq("lifecycleState"))).thenReturn(lifecycleStatePath);
    when(root.get(eq("id"))).thenReturn(idPath);
    when(criteriaBuilder.equal(lifecycleStatePath, LayerLifecycleState.ARCHIVED))
        .thenReturn(lifecycleStatePredicate);
    when(criteriaBuilder.equal(idPath, expectedId)).thenReturn(idPredicate);
    when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(combinedPredicate);

    specification.toPredicate(root, query, criteriaBuilder);

    verify(criteriaBuilder).equal(lifecycleStatePath, LayerLifecycleState.ARCHIVED);
    verify(criteriaBuilder).equal(idPath, expectedId);
  }
}
