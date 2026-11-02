// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.group;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.model.group.GroupCreateRequest;
import com.opencop.core.api.general.technical.model.group.GroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.group.GroupPatchRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppGroupServiceTest {

  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final UUID USER_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
  private static final UUID USER_B = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
  private static final UUID UNKNOWN_USER = UUID.fromString("550e8400-e29b-41d4-a716-446655440004");

  private IamGroupRepository groupRepo;
  private IamGroupMembersRepository membersRepo;
  private InternalUserRepository userRepo;
  private AppGroupService service;

  @BeforeEach
  void setUp() {
    groupRepo = mock(IamGroupRepository.class);
    membersRepo = mock(IamGroupMembersRepository.class);
    userRepo = mock(InternalUserRepository.class);
    service = new AppGroupService(groupRepo, membersRepo, userRepo);
  }

  // ── createGroup ──────────────────────────────────────────────────────────

  @Test
  void createGroup_throwsWhenNameIsBlank() {
    assertThrows(
        PersistenceException.class, () -> service.createGroup(new GroupCreateRequest("  ", null)));
  }

  @Test
  void createGroup_throwsWhenNameAlreadyExists() {
    when(groupRepo.findByIdpIssuerIsNullAndName("Alpha"))
        .thenReturn(Optional.of(group(GROUP_ID, "Alpha")));
    assertThrows(
        PersistenceException.class,
        () -> service.createGroup(new GroupCreateRequest("Alpha", null)));
  }

  @Test
  void createGroup_savesAndReturnsResponse() throws PersistenceException {
    when(groupRepo.findByIdpIssuerIsNullAndName("Alpha")).thenReturn(Optional.empty());
    var entity = group(GROUP_ID, "Alpha");
    when(groupRepo.save(any())).thenReturn(entity);

    var result = service.createGroup(new GroupCreateRequest("Alpha", null));

    verify(groupRepo).save(any());
    assertEquals(GROUP_ID, result.id());
    assertEquals("Alpha", result.name());
  }

  // ── deleteGroup ──────────────────────────────────────────────────────────

  @Test
  void deleteGroup_throwsWhenGroupNotFound() {
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.empty());
    assertThrows(PersistenceException.class, () -> service.deleteGroup(GROUP_ID));
    verify(groupRepo, never()).deleteById(any());
  }

  @Test
  void deleteGroup_deletesWhenFound() throws PersistenceException {
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group(GROUP_ID, "Alpha")));
    assertDoesNotThrow(() -> service.deleteGroup(GROUP_ID));
    verify(groupRepo).deleteById(GROUP_ID);
  }

  @Test
  void deleteGroup_rejectsScimManagedGroup() {
    var scimGroup = group(GROUP_ID, "Alpha");
    scimGroup.setIdpIssuer("https://idp.example.com");
    scimGroup.setIdpGroupId("ext-scim-group");
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(scimGroup));

    assertThrows(GlobalOpenCopException.class, () -> service.deleteGroup(GROUP_ID));
    verify(groupRepo, never()).deleteById(any());
  }

  // ── patchGroup ───────────────────────────────────────────────────────────

  @Test
  void patchGroup_rejectsScimManagedGroup() {
    var scimGroup = group(GROUP_ID, "Alpha");
    scimGroup.setIdpIssuer("https://idp.example.com");
    scimGroup.setIdpGroupId("ext-scim-group");
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(scimGroup));

    assertThrows(
        GlobalOpenCopException.class,
        () -> service.patchGroup(GROUP_ID, new GroupPatchRequest("Renamed", null)));
    verify(groupRepo, never()).save(any());
  }

  @Test
  void patchGroup_rejectsScimManagedGroupEvenForDescriptionOnly() {
    var scimGroup = group(GROUP_ID, "Alpha");
    scimGroup.setIdpIssuer("https://idp.example.com");
    scimGroup.setIdpGroupId("ext-scim-group");
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(scimGroup));

    assertThrows(
        GlobalOpenCopException.class,
        () -> service.patchGroup(GROUP_ID, new GroupPatchRequest(null, "new desc")));
    verify(groupRepo, never()).save(any());
  }

  // ── patchMembers — batch existence checks ────────────────────────────────

  @Test
  void patchMembers_rejectsScimManagedGroup() {
    var scimGroup = group(GROUP_ID, "Alpha");
    scimGroup.setIdpIssuer("https://idp.example.com");
    scimGroup.setIdpGroupId("ext-scim-group");
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(scimGroup));

    assertThrows(
        GlobalOpenCopException.class,
        () ->
            service.patchMembers(
                GROUP_ID, new GroupMembersPatchRequest(Set.of(USER_A), Set.of(USER_B))));
    verify(membersRepo, never()).saveAll(any());
    verify(membersRepo, never()).deleteAllByGroupIdAndUserIds(any(), any());
  }

  @Test
  void patchMembers_throwsWhenGroupNotFound() {
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.empty());
    var request = new GroupMembersPatchRequest(Set.of(USER_A), null);
    assertThrows(PersistenceException.class, () -> service.patchMembers(GROUP_ID, request));
  }

  @Test
  void patchMembers_throwsWhenAnyAddedUserDoesNotExist() {
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group(GROUP_ID, "Alpha")));
    when(userRepo.findAllById(Set.of(USER_A, UNKNOWN_USER))).thenReturn(List.of(user(USER_A)));

    assertThrows(
        PersistenceException.class,
        () ->
            service.patchMembers(
                GROUP_ID, new GroupMembersPatchRequest(Set.of(USER_A, UNKNOWN_USER), null)));
    verify(membersRepo, never()).saveAll(any());
  }

  @Test
  void patchMembers_skipsAlreadyExistingMembersAndSavesOnlyNewOnes() throws PersistenceException {
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group(GROUP_ID, "Alpha")));
    when(userRepo.findAllById(Set.of(USER_A, USER_B)))
        .thenReturn(List.of(user(USER_A), user(USER_B)));
    // USER_A already a member; USER_B is new
    when(membersRepo.findExistingUserIds(GROUP_ID, Set.of(USER_A, USER_B)))
        .thenReturn(Set.of(USER_A));

    service.patchMembers(GROUP_ID, new GroupMembersPatchRequest(Set.of(USER_A, USER_B), null));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<GroupMemberEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
    verify(membersRepo).saveAll(captor.capture());
    List<GroupMemberEntity> saved = new java.util.ArrayList<>();
    captor.getValue().forEach(saved::add);
    assertEquals(1, saved.size());
    assertEquals(USER_B, saved.getFirst().getUserId());
  }

  @Test
  void patchMembers_removesUsersInBatch() throws PersistenceException {
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group(GROUP_ID, "Alpha")));

    service.patchMembers(GROUP_ID, new GroupMembersPatchRequest(null, Set.of(USER_A, USER_B)));

    verify(membersRepo).deleteAllByGroupIdAndUserIds(GROUP_ID, Set.of(USER_A, USER_B));
    verify(userRepo, never()).findAllById(any());
  }

  @Test
  void patchMembers_doesNothingWhenBothSetsAreEmpty() throws PersistenceException {
    when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group(GROUP_ID, "Alpha")));

    service.patchMembers(GROUP_ID, new GroupMembersPatchRequest(null, null));

    verify(membersRepo, never()).saveAll(any());
    verify(membersRepo, never()).deleteAllByGroupIdAndUserIds(any(), any());
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private static GroupEntity group(UUID id, String name) {
    var g = new GroupEntity();
    g.setId(id);
    g.setName(name);
    return g;
  }

  private static InternalUserEntity user(UUID id) {
    var u = new InternalUserEntity();
    u.setId(id);
    return u;
  }
}
