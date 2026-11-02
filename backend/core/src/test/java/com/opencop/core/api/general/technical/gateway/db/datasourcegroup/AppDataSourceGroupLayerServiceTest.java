// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.datasourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamLayerDataSourceGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.LayerDataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.LayerDataSourceGroupIdEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppDataSourceGroupLayerServiceTest {

  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-676655442001");
  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-676655442002");
  private static final UUID SECOND_GROUP_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-676655442003");

  private IamLayerRepository layerRepo;
  private IamDataSourceGroupRepository dataSourceGroupRepo;
  private IamLayerDataSourceGroupRepository layerDataSourceGroupRepo;
  private AppDataSourceGroupLayerService service;

  @BeforeEach
  void setUp() {
    layerRepo = mock(IamLayerRepository.class);
    dataSourceGroupRepo = mock(IamDataSourceGroupRepository.class);
    layerDataSourceGroupRepo = mock(IamLayerDataSourceGroupRepository.class);
    service =
        new AppDataSourceGroupLayerService(
            layerRepo, dataSourceGroupRepo, layerDataSourceGroupRepo);
  }

  @Test
  void getDataSourceGroupsForLayer_whenLayerExists_returnsEmbeddings() throws PersistenceException {
    var embedding = embedding(layer(LAYER_ID, "Ops"), group(GROUP_ID, "Sources"));
    when(layerRepo.existsById(LAYER_ID)).thenReturn(true);
    when(layerDataSourceGroupRepo.findAllByLayerId(LAYER_ID)).thenReturn(List.of(embedding));

    var result = service.getDataSourceGroupsForLayer(LAYER_ID);

    assertEquals(1, result.size());
    assertEquals(GROUP_ID, result.getFirst().dataSourceGroupId());
    assertEquals("Sources", result.getFirst().dataSourceGroupName());
    assertEquals(LAYER_ID, result.getFirst().layerId());
    assertEquals("Ops", result.getFirst().layerName());
  }

  @Test
  void getDataSourceGroupsForLayer_missingLayer_throwsPersistenceException() {
    when(layerRepo.existsById(LAYER_ID)).thenReturn(false);

    var ex =
        assertThrows(
            PersistenceException.class, () -> service.getDataSourceGroupsForLayer(LAYER_ID));

    assertEquals("Could not find layer: " + LAYER_ID, ex.getMessage());
    verify(layerDataSourceGroupRepo, never()).findAllByLayerId(any());
  }

  @Test
  void assignDataSourceGroupsToLayer_newEmbedding_savesAndReturnsResponse()
      throws PersistenceException {
    var layer = layer(LAYER_ID, "Ops");
    var group = group(GROUP_ID, "Sources");
    var id = new LayerDataSourceGroupIdEntity(LAYER_ID, GROUP_ID);
    when(layerRepo.findById(LAYER_ID)).thenReturn(Optional.of(layer));
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group));
    when(layerDataSourceGroupRepo.findById(id)).thenReturn(Optional.empty());
    when(layerDataSourceGroupRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.assignDataSourceGroupsToLayer(LAYER_ID, Set.of(GROUP_ID));

    verify(layerDataSourceGroupRepo).save(any(LayerDataSourceGroupEntity.class));
    assertEquals(1, result.size());
    assertEquals(GROUP_ID, result.getFirst().dataSourceGroupId());
    assertEquals(LAYER_ID, result.getFirst().layerId());
  }

  @Test
  void assignDataSourceGroupsToLayer_existingEmbedding_reusesAndSavesExisting()
      throws PersistenceException {
    var layer = layer(LAYER_ID, "Ops");
    var group = group(GROUP_ID, "Sources");
    var id = new LayerDataSourceGroupIdEntity(LAYER_ID, GROUP_ID);
    var existing = embedding(layer, group);
    when(layerRepo.findById(LAYER_ID)).thenReturn(Optional.of(layer));
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group));
    when(layerDataSourceGroupRepo.findById(id)).thenReturn(Optional.of(existing));
    when(layerDataSourceGroupRepo.save(existing)).thenReturn(existing);

    service.assignDataSourceGroupsToLayer(LAYER_ID, Set.of(GROUP_ID));

    verify(layerDataSourceGroupRepo).save(existing);
  }

  @Test
  void assignDataSourceGroupsToLayer_missingLayer_throwsPersistenceExceptionAndDoesNotSave() {
    when(layerRepo.findById(LAYER_ID)).thenReturn(Optional.empty());

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.assignDataSourceGroupsToLayer(LAYER_ID, Set.of(GROUP_ID)));

    assertEquals("Could not find layer: " + LAYER_ID, ex.getMessage());
    verify(layerDataSourceGroupRepo, never()).save(any());
  }

  @Test
  void
      assignDataSourceGroupsToLayer_missingDataSourceGroup_throwsPersistenceExceptionAndDoesNotSave() {
    when(layerRepo.findById(LAYER_ID)).thenReturn(Optional.of(layer(LAYER_ID, "Ops")));
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.empty());

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.assignDataSourceGroupsToLayer(LAYER_ID, Set.of(GROUP_ID)));

    assertEquals("Could not find data source group: " + GROUP_ID, ex.getMessage());
    verify(layerDataSourceGroupRepo, never()).save(any());
  }

  @Test
  void assignDataSourceGroupsToLayer_secondMissingDataSourceGroup_throwsBeforeSavingAnyEmbedding() {
    when(layerRepo.findById(LAYER_ID)).thenReturn(Optional.of(layer(LAYER_ID, "Ops")));
    when(dataSourceGroupRepo.findById(GROUP_ID))
        .thenReturn(Optional.of(group(GROUP_ID, "Sources")));
    when(dataSourceGroupRepo.findById(SECOND_GROUP_ID)).thenReturn(Optional.empty());

    assertThrows(
        PersistenceException.class,
        () -> service.assignDataSourceGroupsToLayer(LAYER_ID, Set.of(GROUP_ID, SECOND_GROUP_ID)));

    verify(layerDataSourceGroupRepo, never()).save(any());
  }

  @Test
  void removeDataSourceGroupFromLayer_existingEmbedding_deletesByLayerAndGroup()
      throws PersistenceException {
    var id = new LayerDataSourceGroupIdEntity(LAYER_ID, GROUP_ID);
    when(layerDataSourceGroupRepo.existsById(id)).thenReturn(true);

    service.removeDataSourceGroupFromLayer(LAYER_ID, GROUP_ID);

    verify(layerDataSourceGroupRepo).deleteByLayerIdAndDataSourceGroupId(LAYER_ID, GROUP_ID);
  }

  @Test
  void
      removeDataSourceGroupFromLayer_missingEmbedding_throwsPersistenceExceptionAndDoesNotDelete() {
    var id = new LayerDataSourceGroupIdEntity(LAYER_ID, GROUP_ID);
    when(layerDataSourceGroupRepo.existsById(id)).thenReturn(false);

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.removeDataSourceGroupFromLayer(LAYER_ID, GROUP_ID));

    assertEquals(
        "Data source group " + GROUP_ID + " is not embedded in layer " + LAYER_ID, ex.getMessage());
    verify(layerDataSourceGroupRepo, never()).deleteByLayerIdAndDataSourceGroupId(any(), any());
  }

  @Test
  void getLayersForDataSourceGroup_whenGroupExists_returnsEmbeddingLayers()
      throws PersistenceException {
    var embedding = embedding(layer(LAYER_ID, "Ops"), group(GROUP_ID, "Sources"));
    when(dataSourceGroupRepo.existsById(GROUP_ID)).thenReturn(true);
    when(layerDataSourceGroupRepo.findAllByDataSourceGroupId(GROUP_ID))
        .thenReturn(List.of(embedding));

    var result = service.getLayersForDataSourceGroup(GROUP_ID);

    assertEquals(1, result.size());
    assertEquals(LAYER_ID, result.getFirst().layerId());
  }

  @Test
  void getLayersForDataSourceGroup_missingGroup_throwsPersistenceException() {
    when(dataSourceGroupRepo.existsById(GROUP_ID)).thenReturn(false);

    var ex =
        assertThrows(
            PersistenceException.class, () -> service.getLayersForDataSourceGroup(GROUP_ID));

    assertEquals("Could not find data source group: " + GROUP_ID, ex.getMessage());
    verify(layerDataSourceGroupRepo, never()).findAllByDataSourceGroupId(any());
  }

  private static LayerDataSourceGroupEntity embedding(
      LayerEntity layer, DataSourceGroupEntity group) {
    return new LayerDataSourceGroupEntity(layer, group);
  }

  private static LayerEntity layer(UUID id, String name) {
    var layer = new LayerEntity();
    layer.setId(id);
    layer.setName(name);
    return layer;
  }

  private static DataSourceGroupEntity group(UUID id, String name) {
    var group = new DataSourceGroupEntity();
    group.setId(id);
    group.setName(name);
    return group;
  }
}
