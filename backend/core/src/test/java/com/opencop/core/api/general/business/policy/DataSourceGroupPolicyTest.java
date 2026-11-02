// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataSourceGroupPolicyTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-010000000001");
  private static final UUID USER_GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-010000000002");

  @Test
  void assertContainerRole_missingRoleThrowsBoundedReasonCode() {
    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () ->
                DataSourceGroupPolicy.assertContainerRole(
                    Optional.empty(), false, DataSourceGroupRole.ADMIN));

    assertEquals(AuthzReasonCode.MISSING_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    assertEquals(
        "You are not authorized for the specified change to this data source group",
        ex.getMessage());
  }

  @Test
  void assertContainerRole_insufficientRoleThrowsBoundedReasonCode() {
    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () ->
                DataSourceGroupPolicy.assertContainerRole(
                    Optional.of(DataSourceGroupRole.MEMBER), false, DataSourceGroupRole.ADMIN));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
  }

  @Test
  void assertCanAssignSourceRole_throwsStableMessageWithoutSourceId() {
    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () ->
                DataSourceGroupPolicy.assertCanAssignSourceRole(
                    Optional.of(ServerRole.VIEWER), ServerRole.ADMIN));

    assertEquals(
        AuthzReasonCode.INSUFFICIENT_SOURCE_ROLE_FOR_DATA_SOURCE_GROUP_ASSIGNMENT, ex.reasonCode());
    assertEquals(
        "You are not authorized to assign the requested data source role to this data source group",
        ex.getMessage());
  }

  @Test
  void memberPatchAffectsOwnerList_usesCurrentUserGroupsAsUnchangedSide() {
    var currentUsers = Map.of(USER_ID, DataSourceGroupRole.OWNER);
    var projectedUsers = Map.of(USER_ID, DataSourceGroupRole.MEMBER);
    var currentUserGroups = Map.of(USER_GROUP_ID, DataSourceGroupRole.MEMBER);

    assertEquals(
        DataSourceGroupPolicy.affectsOwnerList(
            currentUsers, projectedUsers, currentUserGroups, currentUserGroups),
        DataSourceGroupPolicy.memberPatchAffectsOwnerList(
            currentUsers, projectedUsers, currentUserGroups));
  }

  @Test
  void userGroupPatchAffectsOwnerList_usesCurrentUsersAsUnchangedSide() {
    var currentUsers = Map.of(USER_ID, DataSourceGroupRole.MEMBER);
    var currentUserGroups = Map.of(USER_GROUP_ID, DataSourceGroupRole.MEMBER);
    var projectedUserGroups = Map.of(USER_GROUP_ID, DataSourceGroupRole.OWNER);

    assertEquals(
        DataSourceGroupPolicy.affectsOwnerList(
            currentUsers, currentUsers, currentUserGroups, projectedUserGroups),
        DataSourceGroupPolicy.userGroupPatchAffectsOwnerList(
            currentUsers, currentUserGroups, projectedUserGroups));
  }
}
