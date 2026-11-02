// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.layergroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupLayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupLayerIdEntity;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppLayerGroupLayerServiceTest {

  private static final UUID LG_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441100");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441101");
  private static final UUID LAYER_ID_2 = UUID.fromString("550e8400-e29b-41d4-a716-446655441102");

  private IamLayerGroupRepository layerGroupRepo;
  private IamLayerRepository layerRepo;
  private IamLayerGroupLayerRepository layerGroupLayerRepo;
  private AppLayerGroupLayerService service;

  @BeforeEach
  void setUp() {
    layerGroupRepo = mock(IamLayerGroupRepository.class);
    layerRepo = mock(IamLayerRepository.class);
    layerGroupLayerRepo = mock(IamLayerGroupLayerRepository.class);
    service = new AppLayerGroupLayerService(layerGroupRepo, layerRepo, layerGroupLayerRepo);
  }

  @Test
  void assignLayers_newAssignment_saveInvokedWithCorrectRole() throws PersistenceException {
    var group = layerGroup(LG_ID, "MyGroup");
    var layer = layer(LAYER_ID, "MyLayer");
    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.of(group));
    when(layerRepo.findById(LAYER_ID)).thenReturn(Optional.of(layer));
    when(layerGroupLayerRepo.findById(new LayerGroupLayerIdEntity(LG_ID, LAYER_ID)))
        .thenReturn(Optional.empty());

    var saved = new LayerGroupLayerEntity(group, layer, LayerRole.EDITOR);
    when(layerGroupLayerRepo.save(any())).thenReturn(saved);

    var result = service.assignLayersToLayerGroup(LG_ID, Set.of(LAYER_ID), LayerRole.EDITOR);

    ArgumentCaptor<LayerGroupLayerEntity> captor =
        ArgumentCaptor.forClass(LayerGroupLayerEntity.class);
    verify(layerGroupLayerRepo).save(captor.capture());
    assertEquals(LayerRole.EDITOR, captor.getValue().getRole());
    assertEquals(1, result.size());
    assertEquals(LayerRole.EDITOR, result.get(0).role());
  }

  @Test
  void assignLayers_existingAssignment_upsertInPlaceNotDeleteInsert() throws PersistenceException {
    var group = layerGroup(LG_ID, "MyGroup");
    var layer = layer(LAYER_ID, "MyLayer");
    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.of(group));
    when(layerRepo.findById(LAYER_ID)).thenReturn(Optional.of(layer));

    var existingEntity = new LayerGroupLayerEntity(group, layer, LayerRole.MEMBER);
    when(layerGroupLayerRepo.findById(new LayerGroupLayerIdEntity(LG_ID, LAYER_ID)))
        .thenReturn(Optional.of(existingEntity));

    var updated = new LayerGroupLayerEntity(group, layer, LayerRole.EDITOR);
    when(layerGroupLayerRepo.save(existingEntity)).thenReturn(updated);

    var result = service.assignLayersToLayerGroup(LG_ID, Set.of(LAYER_ID), LayerRole.EDITOR);

    ArgumentCaptor<LayerGroupLayerEntity> captor =
        ArgumentCaptor.forClass(LayerGroupLayerEntity.class);
    verify(layerGroupLayerRepo).save(captor.capture());
    assertEquals(LayerRole.EDITOR, captor.getValue().getRole());
    assertEquals(1, result.size());
    assertEquals(LayerRole.EDITOR, result.get(0).role());
  }

  @Test
  void assignLayers_multipleLayers_savesEachWithSharedRole() throws PersistenceException {
    var group = layerGroup(LG_ID, "MyGroup");
    var layerA = layer(LAYER_ID, "LayerA");
    var layerB = layer(LAYER_ID_2, "LayerB");
    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.of(group));
    when(layerRepo.findById(LAYER_ID)).thenReturn(Optional.of(layerA));
    when(layerRepo.findById(LAYER_ID_2)).thenReturn(Optional.of(layerB));
    when(layerGroupLayerRepo.findById(any())).thenReturn(Optional.empty());
    when(layerGroupLayerRepo.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0, LayerGroupLayerEntity.class));

    var result =
        service.assignLayersToLayerGroup(LG_ID, Set.of(LAYER_ID, LAYER_ID_2), LayerRole.ADMIN);

    verify(layerGroupLayerRepo, times(2)).save(any());
    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(r -> r.role() == LayerRole.ADMIN));
  }

  @Test
  void removeLayer_notAssigned_throwsPersistenceExceptionDeleteNotInvoked() {
    var id = new LayerGroupLayerIdEntity(LG_ID, LAYER_ID);
    when(layerGroupLayerRepo.existsById(id)).thenReturn(false);

    assertThrows(
        PersistenceException.class, () -> service.removeLayerFromLayerGroup(LG_ID, LAYER_ID));

    verify(layerGroupLayerRepo, never()).deleteByLayerGroupIdAndLayerId(any(), any());
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private static LayerGroupEntity layerGroup(UUID id, String name) {
    var lg = new LayerGroupEntity();
    lg.setId(id);
    lg.setName(name);
    return lg;
  }

  private static LayerEntity layer(UUID id, String name) {
    var l = new LayerEntity();
    l.setId(id);
    l.setName(name);
    return l;
  }
}
