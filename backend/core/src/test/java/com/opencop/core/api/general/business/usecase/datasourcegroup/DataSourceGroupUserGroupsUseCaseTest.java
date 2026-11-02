// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.datasourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupUserGroupPatchGuard;
import com.opencop.core.api.general.technical.gateway.db.datasourcegroup.AppDataSourceGroupUserGroupService;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupUserGroupsPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupUserGroupsPatchRequest.UserGroupAdd;
import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DataSourceGroupUserGroupsUseCaseTest {

  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-556655445010");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-556655445011");
  private static final UUID USER_GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-556655445012");
  private static final UUID USER_GROUP_B_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-556655445013");

  private DataSourceGroupAuthorization auth;
  private AppDataSourceGroupUserGroupService service;
  private DataSourceGroupUserGroupsUseCase useCase;

  @BeforeEach
  void setUp() {
    auth = mock(DataSourceGroupAuthorization.class);
    service = mock(AppDataSourceGroupUserGroupService.class);
    useCase = new DataSourceGroupUserGroupsUseCase(auth, service);
  }

  @Test
  void patchUserGroups_adminPromotesUserGroupToOwner_requiresOwner() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.ADMIN));
    simulateServerSideAssignments(
        Map.of(USER_ID, DataSourceGroupRole.OWNER),
        Map.of(USER_GROUP_ID, DataSourceGroupRole.MEMBER));

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () ->
                useCase.patchUserGroups(
                    GROUP_ID,
                    new DataSourceGroupUserGroupsPatchRequest(
                        List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.OWNER)),
                        List.of()),
                    principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    assertEquals(
        "You are not authorized to assign the requested data source group role", ex.getMessage());
  }

  @Test
  void patchUserGroups_adminAssignsUserGroupAtAdminRole_succeeds() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.ADMIN));
    simulateServerSideAssignments(Map.of(USER_ID, DataSourceGroupRole.OWNER), Map.of());

    useCase.patchUserGroups(
        GROUP_ID,
        new DataSourceGroupUserGroupsPatchRequest(
            List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.ADMIN)), List.of()),
        principal);

    verify(service).applyUserGroupPatch(eq(GROUP_ID), any(), any(), any());
  }

  @Test
  void patchUserGroups_adminAssignsUserGroupAtMaintainerRole_succeeds() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.ADMIN));
    simulateServerSideAssignments(Map.of(USER_ID, DataSourceGroupRole.OWNER), Map.of());

    useCase.patchUserGroups(
        GROUP_ID,
        new DataSourceGroupUserGroupsPatchRequest(
            List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MAINTAINER)), List.of()),
        principal);

    verify(service).applyUserGroupPatch(eq(GROUP_ID), any(), any(), any());
  }

  @Test
  void patchUserGroups_effectiveTransitiveAdminRole_addsMaintainerUserGroup() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.ADMIN));
    simulateServerSideAssignments(Map.of(USER_ID, DataSourceGroupRole.OWNER), Map.of());

    useCase.patchUserGroups(
        GROUP_ID,
        new DataSourceGroupUserGroupsPatchRequest(
            List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MAINTAINER)), List.of()),
        principal);

    verify(service).applyUserGroupPatch(eq(GROUP_ID), any(), any(), any());
  }

  @Test
  void patchUserGroups_adminAssignsUserGroupAtMemberRole_succeeds() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.ADMIN));
    simulateServerSideAssignments(Map.of(USER_ID, DataSourceGroupRole.OWNER), Map.of());

    useCase.patchUserGroups(
        GROUP_ID,
        new DataSourceGroupUserGroupsPatchRequest(
            List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MEMBER)), List.of()),
        principal);

    verify(service).applyUserGroupPatch(eq(GROUP_ID), any(), any(), any());
  }

  @Test
  void patchUserGroups_nullRequest_throwsPersistenceException() {
    OpenCopPrincipal principal = nonAdmin();

    var ex =
        assertThrows(
            PersistenceException.class, () -> useCase.patchUserGroups(GROUP_ID, null, principal));

    assertEquals("Data source group user groups patch request is required", ex.getMessage());
  }

  @Test
  void patchUserGroups_nullAddAndRemoveLists_defaultToEmptyAndSucceed() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.OWNER));
    simulateServerSideAssignments(Map.of(USER_ID, DataSourceGroupRole.OWNER), Map.of());

    useCase.patchUserGroups(
        GROUP_ID, new DataSourceGroupUserGroupsPatchRequest(null, null), principal);

    verify(service).applyUserGroupPatch(eq(GROUP_ID), eq(List.of()), eq(List.of()), any());
  }

  @Test
  void patchUserGroups_nullUserGroupId_throwsPersistenceException() {
    OpenCopPrincipal principal = nonAdmin();

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                useCase.patchUserGroups(
                    GROUP_ID,
                    new DataSourceGroupUserGroupsPatchRequest(
                        List.of(new UserGroupAdd(null, DataSourceGroupRole.MEMBER)), List.of()),
                    principal));

    assertEquals("User group id is required", ex.getMessage());
  }

  @Test
  void patchUserGroups_nullRole_throwsPersistenceException() {
    OpenCopPrincipal principal = nonAdmin();

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                useCase.patchUserGroups(
                    GROUP_ID,
                    new DataSourceGroupUserGroupsPatchRequest(
                        List.of(new UserGroupAdd(USER_GROUP_ID, null)), List.of()),
                    principal));

    assertEquals("User group role is required", ex.getMessage());
  }

  @Test
  void patchUserGroups_nullAdditionEntry_throwsPersistenceException() {
    OpenCopPrincipal principal = nonAdmin();

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                useCase.patchUserGroups(
                    GROUP_ID,
                    new DataSourceGroupUserGroupsPatchRequest(
                        java.util.Collections.singletonList(null), List.of()),
                    principal));

    assertEquals("User group addition is required", ex.getMessage());
  }

  @Test
  void patchUserGroups_nullRemovalId_throwsPersistenceException() {
    OpenCopPrincipal principal = nonAdmin();

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                useCase.patchUserGroups(
                    GROUP_ID,
                    new DataSourceGroupUserGroupsPatchRequest(
                        List.of(), java.util.Collections.singletonList(null)),
                    principal));

    assertEquals("User group removal id is required", ex.getMessage());
  }

  @Test
  void patchUserGroups_maintainerRole_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MAINTAINER));

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () ->
                useCase.patchUserGroups(
                    GROUP_ID,
                    new DataSourceGroupUserGroupsPatchRequest(
                        List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MEMBER)),
                        List.of()),
                    principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(service, never()).applyUserGroupPatch(any(), any(), any(), any());
  }

  @Test
  void patchUserGroups_effectiveTransitiveMaintainerRole_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MAINTAINER));

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () ->
                useCase.patchUserGroups(
                    GROUP_ID,
                    new DataSourceGroupUserGroupsPatchRequest(
                        List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MEMBER)),
                        List.of()),
                    principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(service, never()).applyUserGroupPatch(any(), any(), any(), any());
  }

  @Test
  void patchUserGroups_memberRole_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MEMBER));

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () ->
                useCase.patchUserGroups(
                    GROUP_ID,
                    new DataSourceGroupUserGroupsPatchRequest(
                        List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MEMBER)),
                        List.of()),
                    principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(service, never()).applyUserGroupPatch(any(), any(), any(), any());
  }

  @Test
  void patchUserGroups_missingRole_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal)).thenReturn(Optional.empty());

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () ->
                useCase.patchUserGroups(
                    GROUP_ID,
                    new DataSourceGroupUserGroupsPatchRequest(
                        List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MEMBER)),
                        List.of()),
                    principal));

    assertEquals(AuthzReasonCode.MISSING_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(service, never()).applyUserGroupPatch(any(), any(), any(), any());
  }

  @Test
  void patchUserGroups_ownerAssignsMaintainerUserGroup_succeeds() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.OWNER));
    simulateServerSideAssignments(Map.of(USER_ID, DataSourceGroupRole.OWNER), Map.of());

    useCase.patchUserGroups(
        GROUP_ID,
        new DataSourceGroupUserGroupsPatchRequest(
            List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.MAINTAINER)), List.of()),
        principal);

    verify(service).applyUserGroupPatch(eq(GROUP_ID), any(), any(), any());
  }

  @Test
  void patchUserGroups_ownerAssignsOwnerUserGroup_succeeds() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.OWNER));
    simulateServerSideAssignments(Map.of(USER_ID, DataSourceGroupRole.OWNER), Map.of());

    useCase.patchUserGroups(
        GROUP_ID,
        new DataSourceGroupUserGroupsPatchRequest(
            List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.OWNER)), List.of()),
        principal);

    verify(service).applyUserGroupPatch(eq(GROUP_ID), any(), any(), any());
  }

  @Test
  void patchUserGroups_globalAdminAssignsOwnerUserGroup_succeeds() throws Exception {
    OpenCopPrincipal principal = admin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal)).thenReturn(Optional.empty());
    simulateServerSideAssignments(Map.of(USER_ID, DataSourceGroupRole.OWNER), Map.of());

    useCase.patchUserGroups(
        GROUP_ID,
        new DataSourceGroupUserGroupsPatchRequest(
            List.of(new UserGroupAdd(USER_GROUP_ID, DataSourceGroupRole.OWNER)), List.of()),
        principal);

    verify(service).applyUserGroupPatch(eq(GROUP_ID), any(), any(), any());
  }

  @Test
  void patchUserGroups_adminRemovesOwnerUserGroup_requiresOwner() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.ADMIN));
    simulateServerSideAssignments(
        Map.of(USER_ID, DataSourceGroupRole.OWNER),
        Map.of(USER_GROUP_ID, DataSourceGroupRole.OWNER));

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () ->
                useCase.patchUserGroups(
                    GROUP_ID,
                    new DataSourceGroupUserGroupsPatchRequest(List.of(), List.of(USER_GROUP_ID)),
                    principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
  }

  @Test
  void patchUserGroups_ownerRemovesOwnerUserGroupWhenDirectOwnerRemains_succeeds()
      throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.OWNER));
    simulateServerSideAssignments(
        Map.of(USER_ID, DataSourceGroupRole.OWNER),
        Map.of(USER_GROUP_ID, DataSourceGroupRole.OWNER));

    useCase.patchUserGroups(
        GROUP_ID,
        new DataSourceGroupUserGroupsPatchRequest(List.of(), List.of(USER_GROUP_ID)),
        principal);

    verify(service).applyUserGroupPatch(eq(GROUP_ID), any(), any(), any());
  }

  @Test
  void patchUserGroups_removingLastOwnerUserGroup_throwsPersistenceException() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.OWNER));
    simulateServerSideAssignments(Map.of(), Map.of(USER_GROUP_ID, DataSourceGroupRole.OWNER));

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                useCase.patchUserGroups(
                    GROUP_ID,
                    new DataSourceGroupUserGroupsPatchRequest(List.of(), List.of(USER_GROUP_ID)),
                    principal));

    assertEquals("Data source group must have at least one OWNER", ex.getMessage());
  }

  @Test
  void patchUserGroups_removeNonOwnerUserGroupWhenOnlyUserGroupOwnerRemains_succeeds()
      throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.OWNER));
    simulateServerSideAssignments(
        Map.of(),
        Map.of(
            USER_GROUP_ID, DataSourceGroupRole.OWNER,
            USER_GROUP_B_ID, DataSourceGroupRole.MEMBER));

    useCase.patchUserGroups(
        GROUP_ID,
        new DataSourceGroupUserGroupsPatchRequest(List.of(), List.of(USER_GROUP_B_ID)),
        principal);

    verify(service).applyUserGroupPatch(eq(GROUP_ID), any(), any(), any());
  }

  @Test
  void getUserGroups_missingGroup_mapsPersistenceExceptionToNotFound() throws Exception {
    OpenCopPrincipal principal = admin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal)).thenReturn(Optional.empty());
    doThrow(new PersistenceException("Could not find data source group: " + GROUP_ID))
        .when(service)
        .listUserGroupsForDataSourceGroup(GROUP_ID);

    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> useCase.getUserGroups(GROUP_ID, principal));

    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  private void simulateServerSideAssignments(
      Map<UUID, DataSourceGroupRole> currentUsers, Map<UUID, DataSourceGroupRole> currentUserGroups)
      throws PersistenceException {
    doAnswer(
            invocation -> {
              List<UserGroupAdd> additions = invocation.getArgument(1);
              List<UUID> removals = invocation.getArgument(2);
              DataSourceGroupUserGroupPatchGuard guard = invocation.getArgument(3);
              var projected = new java.util.HashMap<>(currentUserGroups);
              removals.forEach(projected::remove);
              additions.forEach(a -> projected.put(a.userGroupId(), a.role()));
              guard.check(currentUsers, currentUserGroups, projected);
              return null;
            })
        .when(service)
        .applyUserGroupPatch(eq(GROUP_ID), any(), any(), any());
  }

  private static OpenCopPrincipal nonAdmin() {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.isGlobalAdmin()).thenReturn(false);
    return principal;
  }

  private static OpenCopPrincipal admin() {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.isGlobalAdmin()).thenReturn(true);
    return principal;
  }
}
