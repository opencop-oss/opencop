// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupMemberIdEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupUserGroupEntity;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LayerGroupAuthorizationServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442001");
  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442002");
  private static final UUID USER_GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442003");

  private IamLayerGroupMembersRepository membersRepository;
  private IamGroupMembersRepository groupMembersRepository;
  private IamLayerGroupUserGroupRepository layerGroupUserGroupRepository;
  private IamLayerGroupRepository layerGroupRepository;
  private LayerGroupAuthorizationService service;

  @BeforeEach
  void setUp() {
    membersRepository = mock(IamLayerGroupMembersRepository.class);
    groupMembersRepository = mock(IamGroupMembersRepository.class);
    layerGroupUserGroupRepository = mock(IamLayerGroupUserGroupRepository.class);
    layerGroupRepository = mock(IamLayerGroupRepository.class);
    service =
        new LayerGroupAuthorizationService(
            membersRepository,
            groupMembersRepository,
            layerGroupUserGroupRepository,
            layerGroupRepository);
  }

  @Test
  void getEffectiveContainerRole_directMaintainer_returnsMaintainer() {
    var member = mock(LayerGroupMemberEntity.class);
    when(member.getRole()).thenReturn(LayerGroupRole.MAINTAINER);
    var memberId = new LayerGroupMemberIdEntity(GROUP_ID, USER_ID);
    when(membersRepository.findById(memberId)).thenReturn(Optional.of(member));

    var result = service.getEffectiveContainerRole(GROUP_ID, principal(false));

    assertEquals(Optional.of(LayerGroupRole.MAINTAINER), result);
  }

  @Test
  void getEffectiveContainerRole_directAdmin_returnsAdmin() {
    var member = mock(LayerGroupMemberEntity.class);
    when(member.getRole()).thenReturn(LayerGroupRole.ADMIN);
    var memberId = new LayerGroupMemberIdEntity(GROUP_ID, USER_ID);
    when(membersRepository.findById(memberId)).thenReturn(Optional.of(member));

    var result = service.getEffectiveContainerRole(GROUP_ID, principal(false));

    assertEquals(Optional.of(LayerGroupRole.ADMIN), result);
  }

  @Test
  void getEffectiveContainerRole_userViaUserGroupAdmin_returnsAdmin() {
    var memberId = new LayerGroupMemberIdEntity(GROUP_ID, USER_ID);
    when(membersRepository.findById(memberId)).thenReturn(Optional.empty());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of(USER_GROUP_ID));
    when(layerGroupUserGroupRepository.findAllByIdLayerGroupIdAndIdUserGroupIdIn(
            GROUP_ID, Set.of(USER_GROUP_ID)))
        .thenReturn(List.of(userGroupAssignment(GROUP_ID, USER_GROUP_ID, LayerGroupRole.ADMIN)));

    var result = service.getEffectiveContainerRole(GROUP_ID, principal(false));

    assertEquals(Optional.of(LayerGroupRole.ADMIN), result);
  }

  @Test
  void getEffectiveContainerRole_userViaUserGroupMember_returnsMember() {
    var memberId = new LayerGroupMemberIdEntity(GROUP_ID, USER_ID);
    when(membersRepository.findById(memberId)).thenReturn(Optional.empty());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of(USER_GROUP_ID));
    when(layerGroupUserGroupRepository.findAllByIdLayerGroupIdAndIdUserGroupIdIn(
            GROUP_ID, Set.of(USER_GROUP_ID)))
        .thenReturn(List.of(userGroupAssignment(GROUP_ID, USER_GROUP_ID, LayerGroupRole.MEMBER)));

    var result = service.getEffectiveContainerRole(GROUP_ID, principal(false));

    assertEquals(Optional.of(LayerGroupRole.MEMBER), result);
  }

  @Test
  void getEffectiveContainerRole_directMaintainerAndTransitiveAdmin_returnsAdmin() {
    var member = mock(LayerGroupMemberEntity.class);
    when(member.getRole()).thenReturn(LayerGroupRole.MAINTAINER);
    var memberId = new LayerGroupMemberIdEntity(GROUP_ID, USER_ID);
    when(membersRepository.findById(memberId)).thenReturn(Optional.of(member));
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of(USER_GROUP_ID));
    when(layerGroupUserGroupRepository.findAllByIdLayerGroupIdAndIdUserGroupIdIn(
            GROUP_ID, Set.of(USER_GROUP_ID)))
        .thenReturn(List.of(userGroupAssignment(GROUP_ID, USER_GROUP_ID, LayerGroupRole.ADMIN)));

    var result = service.getEffectiveContainerRole(GROUP_ID, principal(false));

    assertEquals(Optional.of(LayerGroupRole.ADMIN), result);
  }

  @Test
  void getEffectiveContainerRole_globalGroupNoExplicitGrant_returnsMember() {
    var memberId = new LayerGroupMemberIdEntity(GROUP_ID, USER_ID);
    when(membersRepository.findById(memberId)).thenReturn(Optional.empty());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    var globalGroup = new LayerGroupEntity();
    globalGroup.setId(GROUP_ID);
    globalGroup.setAccessScope(LayerGroupAccessScope.GLOBAL);
    when(layerGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(globalGroup));

    var result = service.getEffectiveContainerRole(GROUP_ID, principal(false));

    assertEquals(Optional.of(LayerGroupRole.MEMBER), result);
  }

  @Test
  void getEffectiveContainerRole_globalGroupExplicitOwner_returnsOwner() {
    var member = mock(LayerGroupMemberEntity.class);
    when(member.getRole()).thenReturn(LayerGroupRole.OWNER);
    var memberId = new LayerGroupMemberIdEntity(GROUP_ID, USER_ID);
    when(membersRepository.findById(memberId)).thenReturn(Optional.of(member));

    var result = service.getEffectiveContainerRole(GROUP_ID, principal(false));

    assertEquals(Optional.of(LayerGroupRole.OWNER), result);
  }

  private static LayerGroupUserGroupEntity userGroupAssignment(
      UUID layerGroupId, UUID userGroupId, LayerGroupRole role) {
    var lg = new LayerGroupEntity();
    lg.setId(layerGroupId);
    var ug = new GroupEntity();
    ug.setId(userGroupId);
    return new LayerGroupUserGroupEntity(lg, ug, role);
  }

  private static OpenCopPrincipal principal(boolean admin) {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID, admin);
  }
}
