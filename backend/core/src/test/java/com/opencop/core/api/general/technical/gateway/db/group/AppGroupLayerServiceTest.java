// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.technical.model.group.GroupLayerAssignRequest;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupLayerMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupLayerMemberIdEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupLayerMemberRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppGroupLayerServiceTest {

  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441001");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441002");

  private IamGroupRepository groupRepo;
  private IamLayerRepository layerRepo;
  private IamGroupLayerMemberRepository groupLayerRepo;
  private AppGroupLayerService service;

  @BeforeEach
  void setUp() {
    groupRepo = mock(IamGroupRepository.class);
    layerRepo = mock(IamLayerRepository.class);
    groupLayerRepo = mock(IamGroupLayerMemberRepository.class);
    service = new AppGroupLayerService(groupRepo, layerRepo, groupLayerRepo);
  }

  // ── getLayersForGroup ────────────────────────────────────────────────────

  @Test
  void getLayersForGroup_throwsWhenGroupNotFound() {
    when(groupRepo.existsById(GROUP_ID)).thenReturn(false);
    assertThrows(PersistenceException.class, () -> service.getLayersForGroup(GROUP_ID));
  }

  // ── getGroupsForLayer ────────────────────────────────────────────────────

  @Test
  void getGroupsForLayer_throwsWhenLayerNotFound() {
    when(layerRepo.existsById(LAYER_ID)).thenReturn(false);
    assertThrows(PersistenceException.class, () -> service.getGroupsForLayer(LAYER_ID));
  }

  // ── assignGroupToLayer ───────────────────────────────────────────────────

  @Test
  void assignGroupToLayer_throwsWhenGroupNotFound() {
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.empty());
    var request = new GroupLayerAssignRequest(LAYER_ID, LayerRole.MEMBER);
    assertThrows(PersistenceException.class, () -> service.assignGroupToLayer(GROUP_ID, request));
  }

  @Test
  void assignGroupToLayer_throwsWhenLayerNotFound() {
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group(GROUP_ID, "Alpha")));
    when(layerRepo.findById(LAYER_ID)).thenReturn(Optional.empty());
    var request = new GroupLayerAssignRequest(LAYER_ID, LayerRole.MEMBER);
    assertThrows(PersistenceException.class, () -> service.assignGroupToLayer(GROUP_ID, request));
  }

  @Test
  void assignGroupToLayer_createsNewAssignmentWhenNoneExists() throws PersistenceException {
    var group = group(GROUP_ID, "Alpha");
    var layer = layer(LAYER_ID, "Ops");
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group));
    when(layerRepo.findById(LAYER_ID)).thenReturn(Optional.of(layer));
    var id = new GroupLayerMemberIdEntity(GROUP_ID, LAYER_ID);
    when(groupLayerRepo.findById(id)).thenReturn(Optional.empty());
    var saved = new GroupLayerMemberEntity(group, layer, LayerRole.MEMBER);
    when(groupLayerRepo.save(any())).thenReturn(saved);

    var result =
        service.assignGroupToLayer(
            GROUP_ID, new GroupLayerAssignRequest(LAYER_ID, LayerRole.MEMBER));

    verify(groupLayerRepo).save(any());
    assertEquals(LayerRole.MEMBER, result.role());
    assertEquals(GROUP_ID, result.groupId());
    assertEquals(LAYER_ID, result.layerId());
  }

  @Test
  void assignGroupToLayer_updatesRoleWhenAssignmentAlreadyExists() throws PersistenceException {
    var group = group(GROUP_ID, "Alpha");
    var layer = layer(LAYER_ID, "Ops");
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group));
    when(layerRepo.findById(LAYER_ID)).thenReturn(Optional.of(layer));
    var id = new GroupLayerMemberIdEntity(GROUP_ID, LAYER_ID);
    var existing = new GroupLayerMemberEntity(group, layer, LayerRole.MEMBER);
    when(groupLayerRepo.findById(id)).thenReturn(Optional.of(existing));
    // save returns the updated entity
    var updated = new GroupLayerMemberEntity(group, layer, LayerRole.ADMIN);
    when(groupLayerRepo.save(existing)).thenReturn(updated);

    var result =
        service.assignGroupToLayer(
            GROUP_ID, new GroupLayerAssignRequest(LAYER_ID, LayerRole.ADMIN));

    assertEquals(LayerRole.ADMIN, result.role());
    verify(groupLayerRepo).save(existing);
  }

  @Test
  void assignGroupToLayer_defaultsToMemberWhenRoleIsNull() throws PersistenceException {
    var group = group(GROUP_ID, "Alpha");
    var layer = layer(LAYER_ID, "Ops");
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group));
    when(layerRepo.findById(LAYER_ID)).thenReturn(Optional.of(layer));
    when(groupLayerRepo.findById(any())).thenReturn(Optional.empty());
    var saved = new GroupLayerMemberEntity(group, layer, LayerRole.MEMBER);
    when(groupLayerRepo.save(any())).thenReturn(saved);

    var result = service.assignGroupToLayer(GROUP_ID, new GroupLayerAssignRequest(LAYER_ID, null));

    assertEquals(LayerRole.MEMBER, result.role());
  }

  // ── removeGroupFromLayer ─────────────────────────────────────────────────

  @Test
  void removeGroupFromLayer_throwsWhenAssignmentNotFound() {
    var id = new GroupLayerMemberIdEntity(GROUP_ID, LAYER_ID);
    when(groupLayerRepo.existsById(id)).thenReturn(false);
    assertThrows(
        PersistenceException.class, () -> service.removeGroupFromLayer(GROUP_ID, LAYER_ID));
    verify(groupLayerRepo, never()).deleteByGroupIdAndLayerId(any(), any());
  }

  @Test
  void removeGroupFromLayer_deletesAssignmentWhenFound() throws PersistenceException {
    var id = new GroupLayerMemberIdEntity(GROUP_ID, LAYER_ID);
    when(groupLayerRepo.existsById(id)).thenReturn(true);

    service.removeGroupFromLayer(GROUP_ID, LAYER_ID);

    verify(groupLayerRepo).deleteByGroupIdAndLayerId(GROUP_ID, LAYER_ID);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private static GroupEntity group(UUID id, String name) {
    var g = new GroupEntity();
    g.setId(id);
    g.setName(name);
    return g;
  }

  private static LayerEntity layer(UUID id, String name) {
    var l = new LayerEntity();
    l.setId(id);
    l.setName(name);
    return l;
  }
}
