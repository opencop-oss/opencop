// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.datasourcegroup;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupUserGroupsPatchRequest.UserGroupAdd;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupUserGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupUserGroupIdEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppDataSourceGroupUserGroupServiceTest {

  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443200");
  private static final UUID USER_GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443201");
  private static final UUID REMOVED_USER_GROUP_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655443202");

  private IamDataSourceGroupUserGroupRepository userGroupRepo;
  private IamDataSourceGroupRepository dataSourceGroupRepo;
  private IamGroupRepository groupRepo;
  private AppDataSourceGroupUserGroupService service;

  @BeforeEach
  void setUp() {
    userGroupRepo = mock(IamDataSourceGroupUserGroupRepository.class);
    dataSourceGroupRepo = mock(IamDataSourceGroupRepository.class);
    groupRepo = mock(IamGroupRepository.class);
    IamDataSourceGroupMembersRepository membersRepo =
        mock(IamDataSourceGroupMembersRepository.class);
    service =
        new AppDataSourceGroupUserGroupService(
            userGroupRepo, dataSourceGroupRepo, groupRepo, membersRepo);
  }

  @Test
  void listUserGroupsForDataSourceGroup_existingGroup_returnsAssignments()
      throws PersistenceException {
    var assignment =
        new DataSourceGroupUserGroupEntity(
            group(GROUP_ID), userGroup(USER_GROUP_ID), DataSourceGroupRole.MEMBER);
    when(dataSourceGroupRepo.existsById(GROUP_ID)).thenReturn(true);
    when(userGroupRepo.findAllByDataSourceGroupId(GROUP_ID)).thenReturn(List.of(assignment));

    var result = service.listUserGroupsForDataSourceGroup(GROUP_ID);

    assertTrue(result.getFirst() == assignment);
  }

  @Test
  void listUserGroupsForDataSourceGroup_missingGroup_throwsAndDoesNotQueryAssignments() {
    when(dataSourceGroupRepo.existsById(GROUP_ID)).thenReturn(false);

    assertThrows(
        PersistenceException.class, () -> service.listUserGroupsForDataSourceGroup(GROUP_ID));

    verify(userGroupRepo, never()).findAllByDataSourceGroupId(GROUP_ID);
  }

  @Test
  void applyUserGroupPatch_newAssignment_savesEntity() throws PersistenceException {
    var group = group(GROUP_ID);
    var userGroup = userGroup(USER_GROUP_ID);
    when(dataSourceGroupRepo.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
    when(userGroupRepo.findAllByDataSourceGroupId(GROUP_ID)).thenReturn(List.of());
    when(groupRepo.findById(USER_GROUP_ID)).thenReturn(Optional.of(userGroup));
    when(userGroupRepo.findById(new DataSourceGroupUserGroupIdEntity(GROUP_ID, USER_GROUP_ID)))
        .thenReturn(Optional.empty());
    when(userGroupRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.applyUserGroupPatch(
        GROUP_ID,
        List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MAINTAINER)),
        List.of(),
        null);

    ArgumentCaptor<DataSourceGroupUserGroupEntity> captor =
        ArgumentCaptor.forClass(DataSourceGroupUserGroupEntity.class);
    verify(userGroupRepo).save(captor.capture());
    assertTrue(captor.getValue().getDataSourceGroup() == group);
    assertTrue(captor.getValue().getUserGroup() == userGroup);
    assertTrue(captor.getValue().getRole() == DataSourceGroupRole.MAINTAINER);
  }

  @Test
  void applyUserGroupPatch_missingDataSourceGroup_throwsAndDoesNotQueryAssignments() {
    when(dataSourceGroupRepo.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.empty());

    assertThrows(
        PersistenceException.class,
        () ->
            service.applyUserGroupPatch(
                GROUP_ID,
                List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MEMBER)),
                List.of(),
                null));

    verify(userGroupRepo, never()).findAllByDataSourceGroupId(any());
    verify(userGroupRepo, never()).save(any());
  }

  @Test
  void applyUserGroupPatch_guardRunsBeforeResolvingAdditions() {
    var group = group(GROUP_ID);
    when(dataSourceGroupRepo.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
    when(userGroupRepo.findAllByDataSourceGroupId(GROUP_ID)).thenReturn(List.of());

    assertThrows(
        PersistenceException.class,
        () ->
            service.applyUserGroupPatch(
                GROUP_ID,
                List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MEMBER)),
                List.of(),
                (currentUsers, currentUserGroups, projectedUserGroups) -> {
                  throw new PersistenceException("guard failed");
                }));

    verify(groupRepo, never()).findById(any());
    verify(userGroupRepo, never()).save(any());
  }

  @Test
  void applyUserGroupPatch_missingAddedUserGroup_doesNotDeleteRemovedAssignments() {
    var group = group(GROUP_ID);
    when(dataSourceGroupRepo.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
    when(userGroupRepo.findAllByDataSourceGroupId(GROUP_ID)).thenReturn(List.of());
    when(groupRepo.findById(USER_GROUP_ID)).thenReturn(Optional.empty());

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                service.applyUserGroupPatch(
                    GROUP_ID,
                    List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MEMBER)),
                    List.of(REMOVED_USER_GROUP_ID),
                    null));

    assertTrue(ex.getMessage().contains(USER_GROUP_ID.toString()));
    verify(userGroupRepo, never()).deleteAllByIdDataSourceGroupIdAndIdUserGroupIdIn(any(), any());
    verify(userGroupRepo, never()).save(any());
  }

  @Test
  void applyUserGroupPatch_resolvesAdditionsBeforeDeletingRemovals() throws PersistenceException {
    var group = group(GROUP_ID);
    when(dataSourceGroupRepo.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
    when(userGroupRepo.findAllByDataSourceGroupId(GROUP_ID)).thenReturn(List.of());
    when(groupRepo.findById(USER_GROUP_ID)).thenReturn(Optional.of(userGroup(USER_GROUP_ID)));
    when(userGroupRepo.findById(new DataSourceGroupUserGroupIdEntity(GROUP_ID, USER_GROUP_ID)))
        .thenReturn(Optional.empty());
    when(userGroupRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.applyUserGroupPatch(
        GROUP_ID,
        List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MEMBER)),
        List.of(REMOVED_USER_GROUP_ID),
        null);

    var inOrder = inOrder(groupRepo, userGroupRepo);
    inOrder.verify(groupRepo).findById(USER_GROUP_ID);
    inOrder
        .verify(userGroupRepo)
        .deleteAllByIdDataSourceGroupIdAndIdUserGroupIdIn(GROUP_ID, Set.of(REMOVED_USER_GROUP_ID));
  }

  @Test
  void applyUserGroupPatch_existingAssignment_updatesRoleAndSavesExisting()
      throws PersistenceException {
    var group = group(GROUP_ID);
    var userGroup = userGroup(USER_GROUP_ID);
    var existing = new DataSourceGroupUserGroupEntity(group, userGroup, DataSourceGroupRole.MEMBER);
    when(dataSourceGroupRepo.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
    when(userGroupRepo.findAllByDataSourceGroupId(GROUP_ID)).thenReturn(List.of(existing));
    when(groupRepo.findById(USER_GROUP_ID)).thenReturn(Optional.of(userGroup));
    when(userGroupRepo.findById(new DataSourceGroupUserGroupIdEntity(GROUP_ID, USER_GROUP_ID)))
        .thenReturn(Optional.of(existing));

    service.applyUserGroupPatch(
        GROUP_ID,
        List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.ADMIN)),
        List.of(),
        null);

    assertTrue(existing.getRole() == DataSourceGroupRole.ADMIN);
    verify(userGroupRepo).save(existing);
  }

  @Test
  void applyUserGroupPatch_removeOnly_deletesRemovedAssignments() throws PersistenceException {
    var group = group(GROUP_ID);
    when(dataSourceGroupRepo.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
    when(userGroupRepo.findAllByDataSourceGroupId(GROUP_ID)).thenReturn(List.of());

    service.applyUserGroupPatch(GROUP_ID, List.of(), List.of(REMOVED_USER_GROUP_ID), null);

    verify(userGroupRepo)
        .deleteAllByIdDataSourceGroupIdAndIdUserGroupIdIn(GROUP_ID, Set.of(REMOVED_USER_GROUP_ID));
    verify(userGroupRepo, never()).save(any());
  }

  @Test
  void applyUserGroupPatch_addAndRemoveSameUserGroup_addWinsAndDoesNotDelete()
      throws PersistenceException {
    var group = group(GROUP_ID);
    var userGroup = userGroup(USER_GROUP_ID);
    when(dataSourceGroupRepo.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
    when(userGroupRepo.findAllByDataSourceGroupId(GROUP_ID)).thenReturn(List.of());
    when(groupRepo.findById(USER_GROUP_ID)).thenReturn(Optional.of(userGroup));
    when(userGroupRepo.findById(new DataSourceGroupUserGroupIdEntity(GROUP_ID, USER_GROUP_ID)))
        .thenReturn(Optional.empty());

    service.applyUserGroupPatch(
        GROUP_ID,
        List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MEMBER)),
        List.of(USER_GROUP_ID),
        null);

    verify(userGroupRepo, never()).deleteAllByIdDataSourceGroupIdAndIdUserGroupIdIn(any(), any());
    verify(userGroupRepo).save(any(DataSourceGroupUserGroupEntity.class));
  }

  private static DataSourceGroupEntity group(UUID id) {
    var group = new DataSourceGroupEntity();
    group.setId(id);
    return group;
  }

  private static GroupEntity userGroup(UUID id) {
    var group = new GroupEntity();
    group.setId(id);
    return group;
  }
}
