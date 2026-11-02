// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.server.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.entity.ServerLayerAccessEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerLayerAccessServiceTest {

  private static final UUID SERVER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448001");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448002");
  private static final UUID MISSING_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655448003");
  private static final UUID SECOND_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655448004");

  private IamServerLayerRepository repository;
  private IamLayerRepository layerRepository;
  private ServerLayerAccessService service;

  @BeforeEach
  void setUp() {
    repository = org.mockito.Mockito.mock(IamServerLayerRepository.class);
    layerRepository = org.mockito.Mockito.mock(IamLayerRepository.class);
    service = new ServerLayerAccessService(repository, layerRepository);
  }

  @Test
  void add_whenLayerMissing_throwsRepositoryException() {
    when(layerRepository.findById(LAYER_ID)).thenReturn(Optional.empty());

    var exception =
        assertThrows(
            RepositoryException.class, () -> service.add(SERVER_ID, LAYER_ID, ServerRole.ADMIN));

    assertEquals(RepositoryExceptionCodes.DOES_NOT_EXIST.code(), exception.getCode());
  }

  @Test
  void add_whenLayerExists_savesAndMapsResult() throws Exception {
    var layer = layerEntity(LAYER_ID, "Ops");
    var entity = new ServerLayerAccessEntity(SERVER_ID, layer, ServerRole.ADMIN);
    when(layerRepository.findById(LAYER_ID)).thenReturn(Optional.of(layer));
    when(repository.save(any(ServerLayerAccessEntity.class))).thenReturn(entity);

    var result = service.add(SERVER_ID, LAYER_ID, ServerRole.ADMIN);

    assertEquals(SERVER_ID, result.getServerId());
    assertEquals(ServerRole.ADMIN, result.getRole());
    assertEquals(LAYER_ID, result.getLayer().getId());
  }

  @Test
  void addLayers_skipsMissingLayerIdsAndInsertsTheOnesWithNoGrantYet() throws Exception {
    var layer = layerEntity(LAYER_ID, "Ops");
    when(layerRepository.existsById(LAYER_ID)).thenReturn(true);
    when(layerRepository.existsById(MISSING_LAYER_ID)).thenReturn(false);
    when(layerRepository.findById(LAYER_ID)).thenReturn(Optional.of(layer));
    when(repository.findAllByServerAndLayer(SERVER_ID, LAYER_ID)).thenReturn(List.of());
    when(repository.save(any(ServerLayerAccessEntity.class)))
        .thenReturn(new ServerLayerAccessEntity(SERVER_ID, layer, ServerRole.ADMIN));

    service.addLayers(SERVER_ID, Set.of(LAYER_ID, MISSING_LAYER_ID), ServerRole.ADMIN);

    verify(repository).save(any(ServerLayerAccessEntity.class));
    verify(repository, never()).findAllByServerAndLayer(SERVER_ID, MISSING_LAYER_ID);
  }

  /**
   * A layer that already holds a grant keeps its row and has its role moved in place.
   *
   * <p>It used to be deleted and re-inserted. The pair is unique, so inside a caller's transaction
   * Hibernate ordered the insert before the delete and the grant was silently revoked.
   */
  @Test
  void addLayers_whenTheLayerAlreadyHasAGrant_movesTheRoleWithoutDeletingTheRow() throws Exception {
    var layer = layerEntity(LAYER_ID, "Ops");
    var existing = new ServerLayerAccessEntity(SERVER_ID, layer, ServerRole.VIEWER);
    when(layerRepository.findById(LAYER_ID)).thenReturn(Optional.of(layer));
    when(repository.findAllByServerAndLayer(SERVER_ID, LAYER_ID)).thenReturn(List.of(existing));

    service.addLayers(SERVER_ID, Set.of(LAYER_ID), ServerRole.OWNER);

    assertEquals(ServerRole.OWNER, existing.getRole());
    verify(repository, never()).save(any());
    verify(repository, never()).deleteOne(any(), any(), any());
  }

  @Test
  void addLayers_skipsNonActiveLayerIds() throws Exception {
    var lockedLayer = layerEntity(LAYER_ID, "Locked", LayerLifecycleState.MERGE_SOURCE_LOCKED);
    when(layerRepository.findById(LAYER_ID)).thenReturn(Optional.of(lockedLayer));

    service.addLayers(SERVER_ID, Set.of(LAYER_ID), ServerRole.ADMIN);

    verify(repository, never()).findAllByServerAndLayer(SERVER_ID, LAYER_ID);
    verify(repository, never()).save(any());
  }

  @Test
  void removeLayers_whenEmpty_doesNothing() {
    service.removeLayers(SERVER_ID, Set.of());

    verify(repository, never()).deleteMany(any(), any());
  }

  @Test
  void getLayers_excludesNonActiveLayers() {
    var activeLayer = layerEntity(LAYER_ID, "Active", LayerLifecycleState.ACTIVE);
    var pendingLayer = layerEntity(SECOND_LAYER_ID, "Pending", LayerLifecycleState.PENDING_MERGE);
    when(repository.findAllByServerId(SERVER_ID))
        .thenReturn(
            List.of(
                new ServerLayerAccessEntity(SERVER_ID, activeLayer, ServerRole.VIEWER),
                new ServerLayerAccessEntity(SERVER_ID, pendingLayer, ServerRole.ADMIN)));

    var result = service.getLayers(SERVER_ID);

    assertEquals(1, result.size());
    assertTrue(
        result.stream().anyMatch(sourceLayer -> LAYER_ID.equals(sourceLayer.getLayer().getId())));
  }

  @Test
  void getLayerServers_mapsEntities() {
    var layer = layerEntity(LAYER_ID, "Ops");
    when(repository.findServerIdsByLayer(LAYER_ID))
        .thenReturn(List.of(new ServerLayerAccessEntity(SERVER_ID, layer, ServerRole.VIEWER)));

    var result = service.getLayerServers(LAYER_ID);

    assertEquals(1, result.size());
    var sourceLayer = result.iterator().next();
    assertEquals(SERVER_ID, sourceLayer.getServerId());
    assertEquals("Ops", sourceLayer.getLayer().getName());
    assertEquals(LayerAccessScope.PRIVATE, sourceLayer.getLayer().getAccessScope());
  }

  @Test
  void getLayerServers_excludesNonActiveLayers() {
    var failedLayer = layerEntity(LAYER_ID, "Failed", LayerLifecycleState.MERGE_FAILED);
    when(repository.findServerIdsByLayer(LAYER_ID))
        .thenReturn(
            List.of(new ServerLayerAccessEntity(SERVER_ID, failedLayer, ServerRole.VIEWER)));

    var result = service.getLayerServers(LAYER_ID);

    assertTrue(result.isEmpty());
  }

  private static LayerEntity layerEntity(UUID id, String name) {
    return layerEntity(id, name, LayerLifecycleState.ACTIVE);
  }

  private static LayerEntity layerEntity(UUID id, String name, LayerLifecycleState lifecycleState) {
    var layer = new LayerEntity();
    layer.setId(id);
    layer.setName(name);
    layer.setAccessScope(LayerAccessScope.PRIVATE);
    layer.setLifecycleState(lifecycleState);
    return layer;
  }
}
