// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.layergroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.policy.LayerGroupPolicy;
import com.opencop.core.api.general.ports.gateway.layergroup.UserGroupPatchGuard;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupsPatchRequest.UserGroupAdd;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupUserGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupUserGroupIdEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppLayerGroupUserGroupServiceTest {

  private static final UUID LG_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440030");
  private static final UUID UG_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440031");

  private IamLayerGroupUserGroupRepository userGroupRepo;
  private IamLayerGroupRepository layerGroupRepo;
  private IamGroupRepository groupRepo;
  private IamLayerGroupMembersRepository membersRepo;
  private AppLayerGroupUserGroupService service;

  @BeforeEach
  void setUp() {
    userGroupRepo = mock(IamLayerGroupUserGroupRepository.class);
    layerGroupRepo = mock(IamLayerGroupRepository.class);
    groupRepo = mock(IamGroupRepository.class);
    membersRepo = mock(IamLayerGroupMembersRepository.class);
    service =
        new AppLayerGroupUserGroupService(userGroupRepo, layerGroupRepo, groupRepo, membersRepo);
  }

  @Test
  void listUserGroupsForLayerGroup_delegatesToRepo() {
    var entity = mock(LayerGroupUserGroupEntity.class);
    when(userGroupRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of(entity));

    var result = service.listUserGroupsForLayerGroup(LG_ID);

    assertEquals(1, result.size());
    verify(userGroupRepo).findAllByLayerGroupId(LG_ID);
  }

  @Test
  void applyUserGroupPatch_newAssignment_savesEntity() throws PersistenceException {
    var layerGroup = layerGroupEntity(LG_ID);
    var userGroup = groupEntity(UG_ID);
    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.of(layerGroup));
    when(userGroupRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of());
    when(groupRepo.findById(UG_ID)).thenReturn(Optional.of(userGroup));
    when(userGroupRepo.findById(new LayerGroupUserGroupIdEntity(LG_ID, UG_ID)))
        .thenReturn(Optional.empty());
    when(userGroupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.applyUserGroupPatch(
        LG_ID, List.of(new UserGroupAdd(UG_ID, LayerGroupRole.MAINTAINER)), List.of(), null);

    ArgumentCaptor<LayerGroupUserGroupEntity> captor =
        ArgumentCaptor.forClass(LayerGroupUserGroupEntity.class);
    verify(userGroupRepo).save(captor.capture());
    assertEquals(LayerGroupRole.MAINTAINER, captor.getValue().getRole());
  }

  @Test
  void applyUserGroupPatch_layerGroupNotFound_throwsPersistenceException() {
    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.empty());

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                service.applyUserGroupPatch(
                    LG_ID,
                    List.of(new UserGroupAdd(UG_ID, LayerGroupRole.MEMBER)),
                    List.of(),
                    null));

    assertTrue(ex.getMessage().contains(LG_ID.toString()));
  }

  @Test
  void applyUserGroupPatch_userGroupNotFound_throwsPersistenceException() {
    var layerGroup = layerGroupEntity(LG_ID);
    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.of(layerGroup));
    when(userGroupRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of());
    when(groupRepo.findById(UG_ID)).thenReturn(Optional.empty());

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                service.applyUserGroupPatch(
                    LG_ID,
                    List.of(new UserGroupAdd(UG_ID, LayerGroupRole.MEMBER)),
                    List.of(),
                    null));

    assertTrue(ex.getMessage().contains(UG_ID.toString()));
  }

  @Test
  void applyUserGroupPatch_removeOnly_invokesDeleteOnRepo() throws PersistenceException {
    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.of(layerGroupEntity(LG_ID)));
    when(userGroupRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of());

    service.applyUserGroupPatch(LG_ID, List.of(), List.of(UG_ID), null);

    verify(userGroupRepo).deleteAllByIdLayerGroupIdAndIdUserGroupIdIn(LG_ID, Set.of(UG_ID));
    verify(userGroupRepo, never()).save(any());
  }

  @Test
  void applyUserGroupPatch_addAndRemoveSameUserGroup_keepsAddedRole() throws PersistenceException {
    var existing = userGroupEntity(UG_ID, LayerGroupRole.MEMBER);
    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.of(layerGroupEntity(LG_ID)));
    when(userGroupRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of(existing));
    when(groupRepo.findById(UG_ID)).thenReturn(Optional.of(groupEntity(UG_ID)));
    when(userGroupRepo.findById(new LayerGroupUserGroupIdEntity(LG_ID, UG_ID)))
        .thenReturn(Optional.of(existing));

    service.applyUserGroupPatch(
        LG_ID, List.of(new UserGroupAdd(UG_ID, LayerGroupRole.OWNER)), List.of(UG_ID), null);

    ArgumentCaptor<LayerGroupUserGroupEntity> captor =
        ArgumentCaptor.forClass(LayerGroupUserGroupEntity.class);
    verify(userGroupRepo).save(captor.capture());
    assertEquals(LayerGroupRole.OWNER, captor.getValue().getRole());
    verify(userGroupRepo, never()).deleteAllByIdLayerGroupIdAndIdUserGroupIdIn(any(), any());
  }

  @Test
  void applyUserGroupPatch_guardInvokedBeforeDelete_enforcesOwnerRetention() {
    UserGroupPatchGuard guard =
        (currentUsers, currentUserGroups, projectedUserGroups) -> {
          LayerGroupPolicy.assertRetainsOwner(currentUsers, projectedUserGroups);
        };

    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.of(layerGroupEntity(LG_ID)));
    when(userGroupRepo.findAllByLayerGroupId(LG_ID))
        .thenReturn(List.of(userGroupEntity(UG_ID, LayerGroupRole.OWNER)));
    when(membersRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of());

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.applyUserGroupPatch(LG_ID, List.of(), List.of(UG_ID), guard));

    assertTrue(ex.getMessage().contains("OWNER"));
    verify(userGroupRepo, never()).deleteAllByIdLayerGroupIdAndIdUserGroupIdIn(any(), any());
  }

  @Test
  void applyUserGroupPatch_guardReceivesProjectedUserGroupsForOverlappedEntries()
      throws PersistenceException {
    AtomicReference<Map<UUID, LayerGroupRole>> seenProjected = new AtomicReference<>();
    UserGroupPatchGuard guard =
        (currentUsers, currentUserGroups, projectedUserGroups) -> {
          seenProjected.set(new LinkedHashMap<>(projectedUserGroups));
        };

    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.of(layerGroupEntity(LG_ID)));
    when(userGroupRepo.findAllByLayerGroupId(LG_ID))
        .thenReturn(List.of(userGroupEntity(UG_ID, LayerGroupRole.MEMBER)));
    when(groupRepo.findById(UG_ID)).thenReturn(Optional.of(groupEntity(UG_ID)));
    when(userGroupRepo.findById(new LayerGroupUserGroupIdEntity(LG_ID, UG_ID)))
        .thenReturn(Optional.of(userGroupEntity(UG_ID, LayerGroupRole.MEMBER)));

    service.applyUserGroupPatch(
        LG_ID, List.of(new UserGroupAdd(UG_ID, LayerGroupRole.OWNER)), List.of(UG_ID), guard);

    assertEquals(Map.of(UG_ID, LayerGroupRole.OWNER), seenProjected.get());

    verify(userGroupRepo).save(any());
    verify(userGroupRepo, never()).deleteAllByIdLayerGroupIdAndIdUserGroupIdIn(any(), any());
  }

  private static LayerGroupEntity layerGroupEntity(UUID id) {
    LayerGroupEntity e = new LayerGroupEntity();
    e.setId(id);
    return e;
  }

  private static GroupEntity groupEntity(UUID id) {
    GroupEntity e = new GroupEntity();
    e.setId(id);
    return e;
  }

  private static LayerGroupUserGroupEntity userGroupEntity(UUID id, LayerGroupRole role) {
    LayerGroupEntity layerGroup = layerGroupEntity(LG_ID);
    GroupEntity userGroup = groupEntity(id);
    return new LayerGroupUserGroupEntity(layerGroup, userGroup, role);
  }
}
