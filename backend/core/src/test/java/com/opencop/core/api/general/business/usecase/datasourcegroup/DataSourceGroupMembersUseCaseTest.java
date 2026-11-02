// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.datasourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupMemberPatchGuard;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupService;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMembersPatchRequest.MemberAdd;
import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DataSourceGroupMembersUseCaseTest {

  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-556655444010");
  private static final UUID USER_A = UUID.fromString("550e8400-e29b-41d4-a716-556655444011");
  private static final UUID USER_B = UUID.fromString("550e8400-e29b-41d4-a716-556655444012");

  private DataSourceGroupAuthorization auth;
  private DataSourceGroupService dataSourceGroupService;
  private DataSourceGroupMembersUseCase useCase;

  @BeforeEach
  void setUp() {
    auth = mock(DataSourceGroupAuthorization.class);
    dataSourceGroupService = mock(DataSourceGroupService.class);
    useCase = new DataSourceGroupMembersUseCase(auth, dataSourceGroupService);
    when(auth.getEffectiveContainerRole(any(), any()))
        .thenReturn(Optional.of(DataSourceGroupRole.OWNER));
  }

  @Test
  void patchMembers_soleOwnerRemoved_throwsBadRequest() throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    simulateServerSideMembers(Map.of(USER_A, DataSourceGroupRole.OWNER));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.patchMembers(
                    GROUP_ID,
                    new DataSourceGroupMembersPatchRequest(null, Set.of(USER_A)),
                    principal));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
  }

  @Test
  void patchMembers_nullRequest_throwsBadRequest() {
    OpenCopPrincipal principal = nonAdmin();

    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> useCase.patchMembers(GROUP_ID, null, principal));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals("Data source group members patch request is required", ex.getMessage());
  }

  @Test
  void patchMembers_nullAddition_throwsBadRequestAndDoesNotCallService()
      throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.patchMembers(
                    GROUP_ID,
                    new DataSourceGroupMembersPatchRequest(Collections.singleton(null), null),
                    principal));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals("Member addition is required", ex.getMessage());
    verify(dataSourceGroupService, never()).patchMembers(any(), any(), any());
  }

  @Test
  void patchMembers_nullRemoval_throwsBadRequestAndDoesNotCallService()
      throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.patchMembers(
                    GROUP_ID,
                    new DataSourceGroupMembersPatchRequest(null, Collections.singleton(null)),
                    principal));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals("Member removal id is required", ex.getMessage());
    verify(dataSourceGroupService, never()).patchMembers(any(), any(), any());
  }

  @Test
  void patchMembers_maintainerRole_throwsAccessDenied() throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MAINTAINER));

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () ->
                useCase.patchMembers(
                    GROUP_ID,
                    new DataSourceGroupMembersPatchRequest(
                        Set.of(new MemberAdd(USER_B, DataSourceGroupRole.MEMBER)), null),
                    principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(dataSourceGroupService, never()).patchMembers(any(), any(), any());
  }

  @Test
  void patchMembers_effectiveTransitiveAdminRole_addsMember() throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.ADMIN));
    simulateServerSideMembers(Map.of(USER_A, DataSourceGroupRole.OWNER));

    useCase.patchMembers(
        GROUP_ID,
        new DataSourceGroupMembersPatchRequest(
            Set.of(new MemberAdd(USER_B, DataSourceGroupRole.MEMBER)), null),
        principal);

    verify(dataSourceGroupService).patchMembers(eq(GROUP_ID), any(), any());
  }

  @Test
  void patchMembers_twoOwners_demoteOneSucceeds() throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    simulateServerSideMembers(
        Map.of(USER_A, DataSourceGroupRole.OWNER, USER_B, DataSourceGroupRole.OWNER));

    useCase.patchMembers(
        GROUP_ID,
        new DataSourceGroupMembersPatchRequest(
            Set.of(new MemberAdd(USER_A, DataSourceGroupRole.MAINTAINER)), null),
        principal);

    verify(dataSourceGroupService).patchMembers(eq(GROUP_ID), any(), any());
  }

  @Test
  void patchMembers_globalAdmin_removesLastOwnerStillThrowsBadRequest()
      throws PersistenceException {
    OpenCopPrincipal admin = mock(OpenCopPrincipal.class);
    when(admin.isGlobalAdmin()).thenReturn(true);
    simulateServerSideMembers(Map.of(USER_A, DataSourceGroupRole.OWNER));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.patchMembers(
                    GROUP_ID, new DataSourceGroupMembersPatchRequest(null, Set.of(USER_A)), admin));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
  }

  private void simulateServerSideMembers(Map<UUID, DataSourceGroupRole> currentUsers)
      throws PersistenceException {
    doAnswer(
            invocation -> {
              DataSourceGroupMembersPatchRequest request = invocation.getArgument(1);
              @SuppressWarnings("unchecked")
              DataSourceGroupMemberPatchGuard guard = invocation.getArgument(2);
              var projected = new java.util.HashMap<>(currentUsers);
              request.add().forEach(a -> projected.put(a.userId(), a.role()));
              request.remove().forEach(projected::remove);
              guard.check(currentUsers, projected, Map.of());
              return null;
            })
        .when(dataSourceGroupService)
        .patchMembers(eq(GROUP_ID), any(), any());
  }

  private static OpenCopPrincipal nonAdmin() {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.isGlobalAdmin()).thenReturn(false);
    return principal;
  }
}
