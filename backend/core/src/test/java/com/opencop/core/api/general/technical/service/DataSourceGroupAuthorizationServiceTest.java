// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupMemberIdEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupUserGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataSourceGroupAuthorizationServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-686655442001");
  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-686655442002");
  private static final UUID USER_GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-686655442003");
  private static final UUID SECOND_USER_GROUP_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-686655442004");

  private IamDataSourceGroupMembersRepository membersRepository;
  private IamGroupMembersRepository groupMembersRepository;
  private IamDataSourceGroupUserGroupRepository dataSourceGroupUserGroupRepository;
  private DataSourceGroupAuthorizationService service;

  @BeforeEach
  void setUp() {
    membersRepository = mock(IamDataSourceGroupMembersRepository.class);
    groupMembersRepository = mock(IamGroupMembersRepository.class);
    dataSourceGroupUserGroupRepository = mock(IamDataSourceGroupUserGroupRepository.class);
    service =
        new DataSourceGroupAuthorizationService(
            membersRepository, groupMembersRepository, dataSourceGroupUserGroupRepository);
  }

  @Test
  void getEffectiveContainerRole_directMaintainer_returnsMaintainer() {
    when(membersRepository.findById(memberId()))
        .thenReturn(Optional.of(member(DataSourceGroupRole.MAINTAINER)));
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());

    var result = service.getEffectiveContainerRole(GROUP_ID, principal());

    assertEquals(Optional.of(DataSourceGroupRole.MAINTAINER), result);
  }

  @Test
  void getEffectiveContainerRole_noDirectOrUserGroupRole_returnsEmpty() {
    when(membersRepository.findById(memberId())).thenReturn(Optional.empty());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());

    var result = service.getEffectiveContainerRole(GROUP_ID, principal());

    assertEquals(Optional.empty(), result);
    verify(dataSourceGroupUserGroupRepository, org.mockito.Mockito.never())
        .findAllByIdDataSourceGroupIdAndIdUserGroupIdIn(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void getEffectiveContainerRole_userViaUserGroupAdmin_returnsAdmin() {
    when(membersRepository.findById(memberId())).thenReturn(Optional.empty());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of(USER_GROUP_ID));
    when(dataSourceGroupUserGroupRepository.findAllByIdDataSourceGroupIdAndIdUserGroupIdIn(
            GROUP_ID, Set.of(USER_GROUP_ID)))
        .thenReturn(List.of(userGroupAssignment(USER_GROUP_ID, DataSourceGroupRole.ADMIN)));

    var result = service.getEffectiveContainerRole(GROUP_ID, principal());

    assertEquals(Optional.of(DataSourceGroupRole.ADMIN), result);
  }

  @Test
  void getEffectiveContainerRole_userViaUserGroupMaintainer_returnsMaintainer() {
    when(membersRepository.findById(memberId())).thenReturn(Optional.empty());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of(USER_GROUP_ID));
    when(dataSourceGroupUserGroupRepository.findAllByIdDataSourceGroupIdAndIdUserGroupIdIn(
            GROUP_ID, Set.of(USER_GROUP_ID)))
        .thenReturn(List.of(userGroupAssignment(USER_GROUP_ID, DataSourceGroupRole.MAINTAINER)));

    var result = service.getEffectiveContainerRole(GROUP_ID, principal());

    assertEquals(Optional.of(DataSourceGroupRole.MAINTAINER), result);
  }

  @Test
  void getEffectiveContainerRole_directMaintainerAndUserGroupAdmin_returnsAdmin() {
    when(membersRepository.findById(memberId()))
        .thenReturn(Optional.of(member(DataSourceGroupRole.MAINTAINER)));
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of(USER_GROUP_ID));
    when(dataSourceGroupUserGroupRepository.findAllByIdDataSourceGroupIdAndIdUserGroupIdIn(
            GROUP_ID, Set.of(USER_GROUP_ID)))
        .thenReturn(List.of(userGroupAssignment(USER_GROUP_ID, DataSourceGroupRole.ADMIN)));

    var result = service.getEffectiveContainerRole(GROUP_ID, principal());

    assertEquals(Optional.of(DataSourceGroupRole.ADMIN), result);
  }

  @Test
  void getEffectiveContainerRole_directOwnerAndUserGroupMember_returnsOwner() {
    when(membersRepository.findById(memberId()))
        .thenReturn(Optional.of(member(DataSourceGroupRole.OWNER)));
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of(USER_GROUP_ID));
    when(dataSourceGroupUserGroupRepository.findAllByIdDataSourceGroupIdAndIdUserGroupIdIn(
            GROUP_ID, Set.of(USER_GROUP_ID)))
        .thenReturn(List.of(userGroupAssignment(USER_GROUP_ID, DataSourceGroupRole.MEMBER)));

    var result = service.getEffectiveContainerRole(GROUP_ID, principal());

    assertEquals(Optional.of(DataSourceGroupRole.OWNER), result);
  }

  @Test
  void getEffectiveContainerRole_multipleUserGroups_returnsHighestRole() {
    when(membersRepository.findById(memberId())).thenReturn(Optional.empty());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID))
        .thenReturn(Set.of(USER_GROUP_ID, SECOND_USER_GROUP_ID));
    when(dataSourceGroupUserGroupRepository.findAllByIdDataSourceGroupIdAndIdUserGroupIdIn(
            GROUP_ID, Set.of(USER_GROUP_ID, SECOND_USER_GROUP_ID)))
        .thenReturn(
            List.of(
                userGroupAssignment(USER_GROUP_ID, DataSourceGroupRole.MEMBER),
                userGroupAssignment(SECOND_USER_GROUP_ID, DataSourceGroupRole.OWNER)));

    var result = service.getEffectiveContainerRole(GROUP_ID, principal());

    assertEquals(Optional.of(DataSourceGroupRole.OWNER), result);
  }

  private static DataSourceGroupMemberIdEntity memberId() {
    return new DataSourceGroupMemberIdEntity(GROUP_ID, USER_ID);
  }

  private static DataSourceGroupMemberEntity member(DataSourceGroupRole role) {
    var group = new DataSourceGroupEntity();
    group.setId(GROUP_ID);
    return new DataSourceGroupMemberEntity(group, USER_ID, role);
  }

  private static DataSourceGroupUserGroupEntity userGroupAssignment(
      UUID userGroupId, DataSourceGroupRole role) {
    var dataSourceGroup = new DataSourceGroupEntity();
    dataSourceGroup.setId(GROUP_ID);
    var userGroup = new GroupEntity();
    userGroup.setId(userGroupId);
    return new DataSourceGroupUserGroupEntity(dataSourceGroup, userGroup, role);
  }

  private static OpenCopPrincipal principal() {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.internalUserId()).thenReturn(USER_ID);
    return principal;
  }
}
