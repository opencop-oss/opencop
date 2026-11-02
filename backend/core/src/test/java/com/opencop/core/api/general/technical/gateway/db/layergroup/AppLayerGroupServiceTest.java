// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.layergroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.policy.LayerGroupPolicy;
import com.opencop.core.api.general.ports.gateway.layergroup.MemberPatchGuard;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupCreateRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest.MemberAdd;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerSummary;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupLayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupMemberIdEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupUserGroupEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

class AppLayerGroupServiceTest {

  private static final UUID LG_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final UUID GROUP_ID = LG_ID;
  private static final UUID USER_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
  private static final UUID USER_B = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
  private static final UUID USER_GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440006");

  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440004");

  private IamLayerGroupRepository layerGroupRepo;
  private IamLayerGroupMembersRepository membersRepo;
  private InternalUserRepository userRepo;
  private IamLayerGroupLayerRepository layerGroupLayerRepo;
  private IamGroupMembersRepository groupMembersRepo;
  private IamLayerGroupUserGroupRepository layerGroupUserGroupRepo;
  private AppLayerGroupService service;

  @BeforeEach
  void setUp() {
    layerGroupRepo = mock(IamLayerGroupRepository.class);
    membersRepo = mock(IamLayerGroupMembersRepository.class);
    userRepo = mock(InternalUserRepository.class);
    layerGroupLayerRepo = mock(IamLayerGroupLayerRepository.class);
    groupMembersRepo = mock(IamGroupMembersRepository.class);
    layerGroupUserGroupRepo = mock(IamLayerGroupUserGroupRepository.class);
    when(layerGroupLayerRepo.findAllByLayerGroupIdIn(any())).thenReturn(List.of());
    when(groupMembersRepo.findGroupIdsByUserId(USER_A)).thenReturn(Set.of());
    when(layerGroupUserGroupRepo.findAllByLayerGroupId(any())).thenReturn(List.of());
    service =
        new AppLayerGroupService(
            layerGroupRepo,
            membersRepo,
            userRepo,
            layerGroupLayerRepo,
            groupMembersRepo,
            layerGroupUserGroupRepo);
  }

  @Test
  void createGroup_withValidName_returnsResponseAndSavesOnce() throws PersistenceException {
    when(layerGroupRepo.existsByName("Alpha")).thenReturn(false);
    var entity = layerGroup(GROUP_ID, "Alpha", "desc");
    when(layerGroupRepo.save(any())).thenReturn(entity);

    var result = service.createGroup(new LayerGroupCreateRequest("Alpha", "desc", null));

    verify(layerGroupRepo).save(any());
    assertEquals(GROUP_ID, result.id());
    assertEquals("Alpha", result.name());
    assertEquals("desc", result.description());
  }

  @Test
  void createGroup_withDuplicateName_throwsPersistenceExceptionAndDoesNotSave() {
    when(layerGroupRepo.existsByName("Alpha")).thenReturn(true);

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.createGroup(new LayerGroupCreateRequest("Alpha", null, null)));

    assertEquals("A layer group with that name already exists", ex.getMessage());
    verify(layerGroupRepo, never()).save(any());
  }

  @Test
  void createGroup_withBlankName_throwsPersistenceExceptionAndDoesNotSave() {
    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.createGroup(new LayerGroupCreateRequest("  ", null, null)));

    assertEquals("Layer group name is required", ex.getMessage());
    verify(layerGroupRepo, never()).save(any());
  }

  @Test
  void getGroup_returnsResponseWithCorrectIdAndName() throws PersistenceException {
    var entity = layerGroup(GROUP_ID, "Alpha", null);
    when(layerGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));

    var result = service.getGroup(GROUP_ID);

    assertEquals(GROUP_ID, result.id());
    assertEquals("Alpha", result.name());
  }

  @Test
  void patchGroup_updatesNameAndSavesOnceReturnsUpdatedResponse() throws PersistenceException {
    var entity = layerGroup(GROUP_ID, "OldName", null);
    when(layerGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    var updated = layerGroup(GROUP_ID, "NewName", null);
    when(layerGroupRepo.save(entity)).thenReturn(updated);

    var result = service.patchGroup(GROUP_ID, new LayerGroupPatchRequest("NewName", null, null));

    verify(layerGroupRepo).save(entity);
    assertEquals("NewName", result.name());
    assertEquals(GROUP_ID, result.id());
  }

  @Test
  void patchGroup_renameToExistingName_throwsPersistenceExceptionAndDoesNotSave() {
    var entity = layerGroup(GROUP_ID, "OldName", null);
    when(layerGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(layerGroupRepo.existsByName("NewName")).thenReturn(true);

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.patchGroup(GROUP_ID, new LayerGroupPatchRequest("NewName", null, null)));

    assertEquals("A layer group with that name already exists", ex.getMessage());
    verify(layerGroupRepo, never()).save(any());
  }

  @Test
  void patchGroup_sameNameRoundtrip_skipsExistsCheckAndSaves() throws PersistenceException {
    var entity = layerGroup(GROUP_ID, "SameName", null);
    when(layerGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(layerGroupRepo.save(entity)).thenReturn(entity);

    service.patchGroup(GROUP_ID, new LayerGroupPatchRequest("SameName", null, null));

    verify(layerGroupRepo, never()).existsByName(any());
    verify(layerGroupRepo).save(entity);
  }

  @Test
  void patchGroup_dataIntegrityViolationOnSave_translatesToPersistenceException() {
    var entity = layerGroup(GROUP_ID, "OldName", null);
    when(layerGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(layerGroupRepo.existsByName("NewName")).thenReturn(false);
    when(layerGroupRepo.save(entity)).thenThrow(new DataIntegrityViolationException("uk"));

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.patchGroup(GROUP_ID, new LayerGroupPatchRequest("NewName", null, null)));

    assertEquals("A layer group with that name already exists", ex.getMessage());
  }

  @Test
  void deleteGroup_whenNotFound_throwsPersistenceExceptionAndDoesNotDelete() {
    when(layerGroupRepo.existsById(GROUP_ID)).thenReturn(false);

    var ex = assertThrows(PersistenceException.class, () -> service.deleteGroup(GROUP_ID));

    assertEquals("Could not find layer group: " + GROUP_ID, ex.getMessage());
    verify(layerGroupRepo, never()).deleteById(any());
  }

  // ── patchMembers ─────────────────────────────────────────────────────────

  @Test
  void patchMembers_newOwner_savesEntityWithCorrectUserAndRole() throws PersistenceException {
    when(layerGroupRepo.findByIdForUpdate(LG_ID))
        .thenReturn(Optional.of(layerGroup(LG_ID, "G", null)));
    when(membersRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of());
    when(userRepo.findAllById(Set.of(USER_A))).thenReturn(List.of(user(USER_A)));

    service.patchMembers(
        LG_ID,
        new LayerGroupMembersPatchRequest(
            Set.of(new MemberAdd(USER_A, LayerGroupRole.OWNER)), null),
        null);

    ArgumentCaptor<LayerGroupMemberEntity> captor =
        ArgumentCaptor.forClass(LayerGroupMemberEntity.class);
    verify(membersRepo).save(captor.capture());
    assertEquals(USER_A, captor.getValue().getUserId());
    assertEquals(LayerGroupRole.OWNER, captor.getValue().getRole());
  }

  @Test
  void patchMembers_existingMember_upsertUpdatesRoleInPlace() throws PersistenceException {
    LayerGroupMemberEntity existingMember = memberEntity(LG_ID, USER_A, LayerGroupRole.MAINTAINER);
    when(layerGroupRepo.findByIdForUpdate(LG_ID))
        .thenReturn(Optional.of(layerGroup(LG_ID, "G", null)));
    when(membersRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of(existingMember));
    when(userRepo.findAllById(Set.of(USER_A))).thenReturn(List.of(user(USER_A)));

    service.patchMembers(
        LG_ID,
        new LayerGroupMembersPatchRequest(
            Set.of(new MemberAdd(USER_A, LayerGroupRole.ADMIN)), null),
        null);

    verify(membersRepo).save(existingMember);
    assertEquals(LayerGroupRole.ADMIN, existingMember.getRole());
  }

  @Test
  void patchMembers_nullRole_throwsAndDoesNotSave() {
    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                service.patchMembers(
                    LG_ID,
                    new LayerGroupMembersPatchRequest(Set.of(new MemberAdd(USER_A, null)), null),
                    null));

    assertEquals("Member role is required", ex.getMessage());
    verify(membersRepo, never()).save(any());
  }

  @Test
  void patchMembers_missingGroup_throwsPersistenceException() {
    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.empty());

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                service.patchMembers(
                    LG_ID,
                    new LayerGroupMembersPatchRequest(
                        Set.of(new MemberAdd(USER_A, LayerGroupRole.MAINTAINER)), null),
                    null));

    assertEquals("Could not find layer group: " + LG_ID, ex.getMessage());
    verify(membersRepo, never()).save(any());
  }

  @Test
  void patchMembers_guardInvokedWithCurrentAndProjectedBeforeWrites() throws PersistenceException {
    LayerGroupMemberEntity existingMember = memberEntity(LG_ID, USER_A, LayerGroupRole.OWNER);
    when(layerGroupRepo.findByIdForUpdate(LG_ID))
        .thenReturn(Optional.of(layerGroup(LG_ID, "G", null)));
    when(membersRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of(existingMember));
    when(userRepo.findAllById(Set.of(USER_B))).thenReturn(List.of(user(USER_B)));

    AtomicReference<Map<UUID, LayerGroupRole>> capturedCurrent = new AtomicReference<>();
    AtomicReference<Map<UUID, LayerGroupRole>> capturedProjected = new AtomicReference<>();
    AtomicReference<Map<UUID, LayerGroupRole>> capturedUserGroups = new AtomicReference<>();
    MemberPatchGuard guard =
        (current, projected, currentUserGroups) -> {
          capturedCurrent.set(current);
          capturedProjected.set(projected);
          capturedUserGroups.set(currentUserGroups);
        };

    service.patchMembers(
        LG_ID,
        new LayerGroupMembersPatchRequest(
            Set.of(new MemberAdd(USER_B, LayerGroupRole.OWNER)), Set.of(USER_A)),
        guard);

    assertEquals(Map.of(USER_A, LayerGroupRole.OWNER), capturedCurrent.get());
    assertEquals(Map.of(USER_B, LayerGroupRole.OWNER), capturedProjected.get());
    assertEquals(Map.of(), capturedUserGroups.get());
  }

  @Test
  void patchMembers_guardThrows_writesAreSkipped() {
    LayerGroupMemberEntity existingMember = memberEntity(LG_ID, USER_A, LayerGroupRole.OWNER);
    when(layerGroupRepo.findByIdForUpdate(LG_ID))
        .thenReturn(Optional.of(layerGroup(LG_ID, "G", null)));
    when(membersRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of(existingMember));
    when(userRepo.findAllById(Set.of(USER_B))).thenReturn(List.of(user(USER_B)));

    MemberPatchGuard guard =
        (current, projected, currentUserGroups) -> {
          throw new PersistenceException("invariant violated");
        };

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                service.patchMembers(
                    LG_ID,
                    new LayerGroupMembersPatchRequest(
                        Set.of(new MemberAdd(USER_B, LayerGroupRole.MAINTAINER)), null),
                    guard));

    assertEquals("invariant violated", ex.getMessage());
    verify(membersRepo, never()).save(any());
    verify(membersRepo, never()).deleteAllByLayerGroupIdAndUserIds(any(), any());
  }

  @Test
  void patchMembers_guardReceivesUserGroupsReadUnderParentLock() throws PersistenceException {
    LayerGroupMemberEntity existingMember = memberEntity(LG_ID, USER_A, LayerGroupRole.OWNER);
    LayerGroupEntity layerGroup = layerGroup(LG_ID, "G", null);
    GroupEntity userGroup = userGroup(USER_GROUP_ID, "Owners");
    LayerGroupUserGroupEntity userGroupOwner =
        new LayerGroupUserGroupEntity(layerGroup, userGroup, LayerGroupRole.OWNER);
    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.of(layerGroup));
    when(membersRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of(existingMember));
    when(layerGroupUserGroupRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of(userGroupOwner));

    AtomicReference<Map<UUID, LayerGroupRole>> capturedUserGroups = new AtomicReference<>();
    MemberPatchGuard guard =
        (currentUsers, projectedUsers, currentUserGroups) -> {
          capturedUserGroups.set(Map.copyOf(currentUserGroups));
          LayerGroupPolicy.assertRetainsOwner(projectedUsers, currentUserGroups);
        };

    service.patchMembers(LG_ID, new LayerGroupMembersPatchRequest(null, Set.of(USER_A)), guard);

    assertEquals(Map.of(USER_GROUP_ID, LayerGroupRole.OWNER), capturedUserGroups.get());
    InOrder inOrder = inOrder(layerGroupRepo, membersRepo, layerGroupUserGroupRepo);
    inOrder.verify(layerGroupRepo).findByIdForUpdate(LG_ID);
    inOrder.verify(membersRepo).findAllByLayerGroupId(LG_ID);
    inOrder.verify(layerGroupUserGroupRepo).findAllByLayerGroupId(LG_ID);
    verify(membersRepo).deleteAllByLayerGroupIdAndUserIds(LG_ID, Set.of(USER_A));
  }

  @Test
  void patchMembers_lastDirectOwnerRemovalUsesLockedUserGroupSnapshotBeforeDelete() {
    LayerGroupMemberEntity existingMember = memberEntity(LG_ID, USER_A, LayerGroupRole.OWNER);
    when(layerGroupRepo.findByIdForUpdate(LG_ID))
        .thenReturn(Optional.of(layerGroup(LG_ID, "G", null)));
    when(membersRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of(existingMember));
    when(layerGroupUserGroupRepo.findAllByLayerGroupId(LG_ID)).thenReturn(List.of());

    MemberPatchGuard guard =
        (currentUsers, projectedUsers, currentUserGroups) ->
            LayerGroupPolicy.assertRetainsOwner(projectedUsers, currentUserGroups);

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                service.patchMembers(
                    LG_ID, new LayerGroupMembersPatchRequest(null, Set.of(USER_A)), guard));

    assertEquals("Layer group must have at least one OWNER", ex.getMessage());
    verify(membersRepo, never()).deleteAllByLayerGroupIdAndUserIds(any(), any());
  }

  @Test
  void getMembers_missingGroup_throwsPersistenceException() {
    when(layerGroupRepo.existsById(LG_ID)).thenReturn(false);

    var ex = assertThrows(PersistenceException.class, () -> service.getMembers(LG_ID));

    assertEquals("Could not find layer group: " + LG_ID, ex.getMessage());
  }

  @Test
  void patchMembers_emptyPatchAgainstMissingGroup_throwsNotFound() {
    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.empty());

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.patchMembers(LG_ID, new LayerGroupMembersPatchRequest(null, null), null));

    assertEquals("Could not find layer group: " + LG_ID, ex.getMessage());
  }

  @Test
  void patchMembers_removeOnlyAgainstMissingGroup_throwsNotFound() {
    when(layerGroupRepo.findByIdForUpdate(LG_ID)).thenReturn(Optional.empty());

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                service.patchMembers(
                    LG_ID, new LayerGroupMembersPatchRequest(null, Set.of(USER_A)), null));

    assertEquals("Could not find layer group: " + LG_ID, ex.getMessage());
    verify(membersRepo, never()).deleteAllByLayerGroupIdAndUserIds(any(), any());
  }

  @Test
  void patchMembers_nullUserId_throwsBeforeWrites() {
    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                service.patchMembers(
                    LG_ID,
                    new LayerGroupMembersPatchRequest(
                        Set.of(new MemberAdd(null, LayerGroupRole.MAINTAINER)), null),
                    null));

    assertEquals("Member user id is required", ex.getMessage());
    verify(layerGroupRepo, never()).findByIdForUpdate(any());
    verify(membersRepo, never()).save(any());
  }

  @Test
  void patchMembers_unknownAddedUser_throwsCouldNotFindUser() {
    when(layerGroupRepo.findByIdForUpdate(LG_ID))
        .thenReturn(Optional.of(layerGroup(LG_ID, "G", null)));
    when(userRepo.findAllById(Set.of(USER_A))).thenReturn(List.of());

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                service.patchMembers(
                    LG_ID,
                    new LayerGroupMembersPatchRequest(
                        Set.of(new MemberAdd(USER_A, LayerGroupRole.MAINTAINER)), null),
                    null));

    assertEquals("Could not find user: " + USER_A, ex.getMessage());
    verify(membersRepo, never()).save(any());
  }

  // ── listMyLayerGroups ────────────────────────────────────────────────────

  @Test
  void listMyLayerGroups_includesGlobalGroupsAsMember() {
    LayerGroupEntity globalGroup = layerGroup(LG_ID, "Global", "desc");
    globalGroup.setAccessScope(LayerGroupAccessScope.GLOBAL);
    when(membersRepo.findAllByUserIdWithGroup(USER_A)).thenReturn(List.of());
    when(layerGroupRepo.findAllByAccessScope(LayerGroupAccessScope.GLOBAL))
        .thenReturn(List.of(globalGroup));

    var results = service.listMyLayerGroups(USER_A);

    assertEquals(1, results.size());
    assertEquals(LG_ID, results.getFirst().id());
    assertEquals(LayerGroupRole.MEMBER, results.getFirst().containerRole());
    assertEquals(LayerGroupAccessScope.GLOBAL, results.getFirst().accessScope());
  }

  @Test
  void listMyLayerGroups_globalGroupWithExplicitOwnerRole_deduplicatesAndKeepsOwner() {
    LayerGroupEntity globalGroup = layerGroup(LG_ID, "Global", "desc");
    globalGroup.setAccessScope(LayerGroupAccessScope.GLOBAL);
    LayerGroupMemberEntity member = memberEntity(LG_ID, USER_A, LayerGroupRole.OWNER);
    member.setGroup(globalGroup);
    when(membersRepo.findAllByUserIdWithGroup(USER_A)).thenReturn(List.of(member));
    when(layerGroupRepo.findAllByAccessScope(LayerGroupAccessScope.GLOBAL))
        .thenReturn(List.of(globalGroup));

    var results = service.listMyLayerGroups(USER_A);

    assertEquals(1, results.size());
    assertEquals(LG_ID, results.getFirst().id());
    assertEquals(LayerGroupRole.OWNER, results.getFirst().containerRole());
  }

  @Test
  void listMyLayerGroups_includesGroupsAssignedThroughUserGroups() {
    UUID userGroupId = UUID.fromString("550e8400-e29b-41d4-a716-446655440006");
    LayerGroupEntity group = layerGroup(LG_ID, "UG Group", "desc");
    GroupEntity userGroup = userGroup(userGroupId, "Analysts");
    LayerGroupUserGroupEntity assignment =
        new LayerGroupUserGroupEntity(group, userGroup, LayerGroupRole.MAINTAINER);
    when(membersRepo.findAllByUserIdWithGroup(USER_A)).thenReturn(List.of());
    when(groupMembersRepo.findGroupIdsByUserId(USER_A)).thenReturn(Set.of(userGroupId));
    when(layerGroupUserGroupRepo.findAllByUserGroupIdInWithLayerGroup(Set.of(userGroupId)))
        .thenReturn(List.of(assignment));

    var results = service.listMyLayerGroups(USER_A);

    assertEquals(1, results.size());
    assertEquals(LG_ID, results.getFirst().id());
    assertEquals(LayerGroupRole.MAINTAINER, results.getFirst().containerRole());
  }

  @Test
  void listMyLayerGroups_directHigherRoleBeatsUserGroupRole() {
    UUID userGroupId = UUID.fromString("550e8400-e29b-41d4-a716-446655440006");
    LayerGroupEntity group = layerGroup(LG_ID, "UG Group", "desc");
    LayerGroupMemberEntity member = memberEntity(LG_ID, USER_A, LayerGroupRole.OWNER);
    member.setGroup(group);
    GroupEntity userGroup = userGroup(userGroupId, "Analysts");
    LayerGroupUserGroupEntity assignment =
        new LayerGroupUserGroupEntity(group, userGroup, LayerGroupRole.MEMBER);
    when(membersRepo.findAllByUserIdWithGroup(USER_A)).thenReturn(List.of(member));
    when(groupMembersRepo.findGroupIdsByUserId(USER_A)).thenReturn(Set.of(userGroupId));
    when(layerGroupUserGroupRepo.findAllByUserGroupIdInWithLayerGroup(Set.of(userGroupId)))
        .thenReturn(List.of(assignment));

    var results = service.listMyLayerGroups(USER_A);

    assertEquals(1, results.size());
    assertEquals(LayerGroupRole.OWNER, results.getFirst().containerRole());
  }

  @Test
  void listMyLayerGroups_returnsMappedResponses() {
    LayerGroupEntity group = layerGroup(LG_ID, "G", "desc");
    LayerGroupMemberEntity member = memberEntity(LG_ID, USER_A, LayerGroupRole.MAINTAINER);
    member.setGroup(group);
    when(membersRepo.findAllByUserIdWithGroup(USER_A)).thenReturn(List.of(member));

    var results = service.listMyLayerGroups(USER_A);

    assertEquals(1, results.size());
    assertEquals(LG_ID, results.getFirst().id());
    assertEquals(LayerGroupRole.MAINTAINER, results.getFirst().containerRole());
  }

  @Test
  void listMyLayerGroups_layersInGroupAreEmbeddedInResponse() {
    LayerGroupEntity group = layerGroup(LG_ID, "G", "desc");
    LayerGroupMemberEntity member = memberEntity(LG_ID, USER_A, LayerGroupRole.MAINTAINER);
    member.setGroup(group);
    when(membersRepo.findAllByUserIdWithGroup(USER_A)).thenReturn(List.of(member));
    when(layerGroupLayerRepo.findAllByLayerGroupIdIn(Set.of(LG_ID)))
        .thenReturn(List.of(layerGroupLayerEntity(LG_ID, LAYER_ID, "Ops", LayerRole.EDITOR)));

    var results = service.listMyLayerGroups(USER_A);

    assertEquals(1, results.size());
    List<LayerSummary> layers = results.getFirst().layers();
    assertEquals(1, layers.size());
    assertEquals(LAYER_ID, layers.getFirst().id());
    assertEquals("Ops", layers.getFirst().name());
    assertEquals(LayerRole.EDITOR, layers.getFirst().role());
  }

  @Test
  void listMyLayerGroups_layerInTwoGroups_appearsInBothFolders() {
    UUID lgId2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440005");
    LayerGroupEntity g1 = layerGroup(LG_ID, "G1", null);
    LayerGroupEntity g2 = layerGroup(lgId2, "G2", null);
    LayerGroupMemberEntity m1 = memberEntity(LG_ID, USER_A, LayerGroupRole.MEMBER);
    m1.setGroup(g1);
    LayerGroupMemberEntity m2 = memberEntity(lgId2, USER_A, LayerGroupRole.MEMBER);
    m2.setGroup(g2);
    when(membersRepo.findAllByUserIdWithGroup(USER_A)).thenReturn(List.of(m1, m2));
    when(layerGroupLayerRepo.findAllByLayerGroupIdIn(Set.of(LG_ID, lgId2)))
        .thenReturn(
            List.of(
                layerGroupLayerEntity(LG_ID, LAYER_ID, "Shared", LayerRole.MEMBER),
                layerGroupLayerEntity(lgId2, LAYER_ID, "Shared", LayerRole.EDITOR)));

    var results = service.listMyLayerGroups(USER_A);

    assertEquals(2, results.size());
    var r1 = results.stream().filter(r -> r.id().equals(LG_ID)).findFirst().orElseThrow();
    var r2 = results.stream().filter(r -> r.id().equals(lgId2)).findFirst().orElseThrow();
    assertEquals(1, r1.layers().size());
    assertEquals(LAYER_ID, r1.layers().getFirst().id());
    assertEquals(LayerRole.MEMBER, r1.layers().getFirst().role());
    assertEquals(1, r2.layers().size());
    assertEquals(LAYER_ID, r2.layers().getFirst().id());
    assertEquals(LayerRole.EDITOR, r2.layers().getFirst().role());
  }

  // ── accessScope persistence ───────────────────────────────────────────────

  @Test
  void createGroup_persistsAccessScope() throws PersistenceException {
    when(layerGroupRepo.existsByName("g")).thenReturn(false);
    var entity = layerGroup(GROUP_ID, "g", "d");
    entity.setAccessScope(LayerGroupAccessScope.GLOBAL);
    ArgumentCaptor<LayerGroupEntity> captor = ArgumentCaptor.forClass(LayerGroupEntity.class);
    when(layerGroupRepo.save(captor.capture())).thenReturn(entity);

    var result =
        service.createGroup(new LayerGroupCreateRequest("g", "d", LayerGroupAccessScope.GLOBAL));

    assertEquals(LayerGroupAccessScope.GLOBAL, captor.getValue().getAccessScope());
    assertEquals(LayerGroupAccessScope.GLOBAL, result.accessScope());
  }

  @Test
  void createGroup_withNullScope_defaultsToPrivate() throws PersistenceException {
    when(layerGroupRepo.existsByName("g")).thenReturn(false);
    var entity = layerGroup(GROUP_ID, "g", "d");
    entity.setAccessScope(LayerGroupAccessScope.PRIVATE);
    ArgumentCaptor<LayerGroupEntity> captor = ArgumentCaptor.forClass(LayerGroupEntity.class);
    when(layerGroupRepo.save(captor.capture())).thenReturn(entity);

    service.createGroup(new LayerGroupCreateRequest("g", "d", null));

    assertEquals(LayerGroupAccessScope.PRIVATE, captor.getValue().getAccessScope());
  }

  @Test
  void patchGroup_withGlobalScope_updatesFromPrivateToGlobal() throws PersistenceException {
    var entity = layerGroup(GROUP_ID, "g", "d");
    entity.setAccessScope(LayerGroupAccessScope.PRIVATE);
    when(layerGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(layerGroupRepo.save(entity))
        .thenAnswer(
            inv -> {
              LayerGroupEntity saved = inv.getArgument(0);
              var reRead = layerGroup(GROUP_ID, saved.getName(), saved.getDescription());
              reRead.setAccessScope(saved.getAccessScope());
              return reRead;
            });

    var result =
        service.patchGroup(
            GROUP_ID, new LayerGroupPatchRequest(null, null, LayerGroupAccessScope.GLOBAL));

    assertEquals(LayerGroupAccessScope.GLOBAL, result.accessScope());
    assertEquals(LayerGroupAccessScope.GLOBAL, entity.getAccessScope());
  }

  @Test
  void patchGroup_withNullScope_leavesExistingAccessScopeUnchanged() throws PersistenceException {
    var entity = layerGroup(GROUP_ID, "g", "d");
    entity.setAccessScope(LayerGroupAccessScope.GLOBAL);
    when(layerGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(layerGroupRepo.save(entity)).thenReturn(entity);

    service.patchGroup(GROUP_ID, new LayerGroupPatchRequest("new-name", null, null));

    assertEquals(LayerGroupAccessScope.GLOBAL, entity.getAccessScope());
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private static LayerGroupEntity layerGroup(UUID id, String name, String description) {
    var e = new LayerGroupEntity();
    e.setId(id);
    e.setName(name);
    e.setDescription(description);
    return e;
  }

  private static LayerGroupMemberEntity memberEntity(
      UUID groupId, UUID userId, LayerGroupRole role) {
    LayerGroupMemberEntity e = new LayerGroupMemberEntity();
    e.setId(new LayerGroupMemberIdEntity(groupId, userId));
    e.setRole(role);
    return e;
  }

  private static InternalUserEntity user(UUID id) {
    InternalUserEntity u = new InternalUserEntity();
    u.setId(id);
    return u;
  }

  private static GroupEntity userGroup(UUID id, String name) {
    GroupEntity g = new GroupEntity();
    g.setId(id);
    g.setName(name);
    return g;
  }

  private static LayerGroupLayerEntity layerGroupLayerEntity(
      UUID groupId, UUID layerId, String layerName, LayerRole role) {
    var group = new LayerGroupEntity();
    group.setId(groupId);
    var layer = new LayerEntity();
    layer.setId(layerId);
    layer.setName(layerName);
    layer.setAccessScope(LayerAccessScope.PRIVATE);
    return new LayerGroupLayerEntity(group, layer, role);
  }
}
