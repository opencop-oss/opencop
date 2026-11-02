// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.datasourcegroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupService;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupCreateRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupResponse;
import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.NotFoundException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DataSourceGroupContainerUseCaseTest {

  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-556655443001");

  private DataSourceGroupAuthorization auth;
  private DataSourceGroupService dataSourceGroupService;
  private DataSourceGroupContainerUseCase useCase;

  @BeforeEach
  void setUp() {
    auth = mock(DataSourceGroupAuthorization.class);
    dataSourceGroupService = mock(DataSourceGroupService.class);
    var transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
    useCase =
        new DataSourceGroupContainerUseCase(auth, dataSourceGroupService, transactionTemplate);
  }

  @Test
  void create_assignsCallerAsOwner() throws Exception {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000099");
    when(principal.internalUserId()).thenReturn(userId);
    DataSourceGroupCreateRequest request = new DataSourceGroupCreateRequest("New Group", null);
    DataSourceGroupResponse response =
        new DataSourceGroupResponse(GROUP_ID, "New Group", null, Instant.now());
    when(dataSourceGroupService.createGroup(request)).thenReturn(response);

    DataSourceGroupResponse result = useCase.create(request, principal);

    assertThat(result).isEqualTo(response);
    verify(dataSourceGroupService).createGroup(request);
    verify(dataSourceGroupService)
        .patchMembers(
            eq(GROUP_ID),
            argThat(
                patch ->
                    patch
                            .add()
                            .equals(
                                Set.of(
                                    new DataSourceGroupMembersPatchRequest.MemberAdd(
                                        userId, DataSourceGroupRole.OWNER)))
                        && patch.remove().isEmpty()),
            isNull());
  }

  @Test
  void create_nullRequest_throwsBadRequest() throws Exception {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);

    var ex = assertThrows(GlobalOpenCopException.class, () -> useCase.create(null, principal));

    assertEquals("Data source group request is required", ex.getMessage());
    verify(dataSourceGroupService, never()).createGroup(null);
  }

  @Test
  void create_ownerAssignmentFailure_throwsBadRequest() throws Exception {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000099");
    when(principal.internalUserId()).thenReturn(userId);
    DataSourceGroupCreateRequest request = new DataSourceGroupCreateRequest("New Group", null);
    DataSourceGroupResponse response =
        new DataSourceGroupResponse(GROUP_ID, "New Group", null, Instant.now());
    when(dataSourceGroupService.createGroup(request)).thenReturn(response);
    doThrow(new PersistenceException("Could not find user: " + userId))
        .when(dataSourceGroupService)
        .patchMembers(eq(GROUP_ID), any(), isNull());

    var ex = assertThrows(GlobalOpenCopException.class, () -> useCase.create(request, principal));

    assertEquals("Could not find user: " + userId, ex.getMessage());
    verify(dataSourceGroupService).createGroup(request);
  }

  @Test
  void patch_nullRequest_throwsBadRequest() throws Exception {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.isGlobalAdmin()).thenReturn(false);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.OWNER));

    var ex =
        assertThrows(GlobalOpenCopException.class, () -> useCase.patch(GROUP_ID, null, principal));

    assertEquals("Data source group patch request is required", ex.getMessage());
    verify(dataSourceGroupService, never())
        .patchGroup(eq(GROUP_ID), (DataSourceGroupPatchRequest) isNull());
  }

  @Test
  void patch_ownerRole_patchesGroup() throws Exception {
    OpenCopPrincipal principal = principal(false);
    var request = new DataSourceGroupPatchRequest("Renamed", "desc");
    var response = new DataSourceGroupResponse(GROUP_ID, "Renamed", "desc", Instant.now());
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.OWNER));
    when(dataSourceGroupService.patchGroup(GROUP_ID, request)).thenReturn(response);

    var result = useCase.patch(GROUP_ID, request, principal);

    assertThat(result).isEqualTo(response);
    verify(dataSourceGroupService).patchGroup(GROUP_ID, request);
  }

  @Test
  void patch_adminRole_patchesGroup() throws Exception {
    OpenCopPrincipal principal = principal(false);
    var request = new DataSourceGroupPatchRequest("Renamed", "desc");
    var response = new DataSourceGroupResponse(GROUP_ID, "Renamed", "desc", Instant.now());
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.ADMIN));
    when(dataSourceGroupService.patchGroup(GROUP_ID, request)).thenReturn(response);

    var result = useCase.patch(GROUP_ID, request, principal);

    assertThat(result).isEqualTo(response);
    verify(dataSourceGroupService).patchGroup(GROUP_ID, request);
  }

  @Test
  void patch_effectiveTransitiveAdminRole_patchesGroup() throws Exception {
    OpenCopPrincipal principal = principal(false);
    var request = new DataSourceGroupPatchRequest("Renamed", "desc");
    var response = new DataSourceGroupResponse(GROUP_ID, "Renamed", "desc", Instant.now());
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.ADMIN));
    when(dataSourceGroupService.patchGroup(GROUP_ID, request)).thenReturn(response);

    var result = useCase.patch(GROUP_ID, request, principal);

    assertThat(result).isEqualTo(response);
    verify(dataSourceGroupService).patchGroup(GROUP_ID, request);
  }

  @Test
  void patch_globalAdminWithoutGroupRole_patchesGroup() throws Exception {
    OpenCopPrincipal principal = principal(true);
    var request = new DataSourceGroupPatchRequest("Renamed", "desc");
    var response = new DataSourceGroupResponse(GROUP_ID, "Renamed", "desc", Instant.now());
    when(auth.getEffectiveContainerRole(GROUP_ID, principal)).thenReturn(Optional.empty());
    when(dataSourceGroupService.patchGroup(GROUP_ID, request)).thenReturn(response);

    var result = useCase.patch(GROUP_ID, request, principal);

    assertThat(result).isEqualTo(response);
    verify(dataSourceGroupService).patchGroup(GROUP_ID, request);
  }

  @Test
  void patch_maintainerRole_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = principal(false);
    var request = new DataSourceGroupPatchRequest("Renamed", null);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MAINTAINER));

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class, () -> useCase.patch(GROUP_ID, request, principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(dataSourceGroupService, never()).patchGroup(any(), any());
  }

  @Test
  void patch_memberRole_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = principal(false);
    var request = new DataSourceGroupPatchRequest("Renamed", null);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MEMBER));

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class, () -> useCase.patch(GROUP_ID, request, principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(dataSourceGroupService, never()).patchGroup(any(), any());
  }

  @Test
  void patch_missingRole_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = principal(false);
    var request = new DataSourceGroupPatchRequest("Renamed", null);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal)).thenReturn(Optional.empty());

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class, () -> useCase.patch(GROUP_ID, request, principal));

    assertEquals(AuthzReasonCode.MISSING_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(dataSourceGroupService, never()).patchGroup(any(), any());
  }

  @Test
  void patch_serviceValidationFailure_throwsBadRequest() throws Exception {
    OpenCopPrincipal principal = principal(false);
    var request = new DataSourceGroupPatchRequest("  ", null);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.OWNER));
    when(dataSourceGroupService.patchGroup(GROUP_ID, request))
        .thenThrow(new PersistenceException("Data source group name cannot be blank"));

    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> useCase.patch(GROUP_ID, request, principal));

    assertEquals("Data source group name cannot be blank", ex.getMessage());
    assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getHttpStatus());
  }

  @Test
  void patch_missingGroupFromService_throwsNotFound() throws Exception {
    OpenCopPrincipal principal = principal(false);
    var request = new DataSourceGroupPatchRequest("Renamed", null);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.OWNER));
    when(dataSourceGroupService.patchGroup(GROUP_ID, request))
        .thenThrow(new NotFoundException("Could not find data source group: " + GROUP_ID));

    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> useCase.patch(GROUP_ID, request, principal));

    assertEquals("Could not find data source group: " + GROUP_ID, ex.getMessage());
    assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  @Test
  void delete_ownerRole_deletesGroup() throws Exception {
    OpenCopPrincipal principal = principal(false);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.OWNER));

    useCase.delete(GROUP_ID, principal);

    verify(dataSourceGroupService).deleteGroup(GROUP_ID);
  }

  @Test
  void delete_globalAdminWithoutGroupRole_deletesGroup() throws Exception {
    OpenCopPrincipal principal = principal(true);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal)).thenReturn(Optional.empty());

    useCase.delete(GROUP_ID, principal);

    verify(dataSourceGroupService).deleteGroup(GROUP_ID);
  }

  @Test
  void delete_adminRole_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = principal(false);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.ADMIN));

    var ex =
        assertThrows(AuthzAccessDeniedException.class, () -> useCase.delete(GROUP_ID, principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(dataSourceGroupService, never()).deleteGroup(any());
  }

  @Test
  void delete_effectiveTransitiveAdminRole_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = principal(false);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.ADMIN));

    var ex =
        assertThrows(AuthzAccessDeniedException.class, () -> useCase.delete(GROUP_ID, principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(dataSourceGroupService, never()).deleteGroup(any());
  }

  @Test
  void delete_maintainerRole_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = principal(false);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MAINTAINER));

    var ex =
        assertThrows(AuthzAccessDeniedException.class, () -> useCase.delete(GROUP_ID, principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(dataSourceGroupService, never()).deleteGroup(any());
  }

  @Test
  void delete_memberRole_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = principal(false);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MEMBER));

    var ex =
        assertThrows(AuthzAccessDeniedException.class, () -> useCase.delete(GROUP_ID, principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(dataSourceGroupService, never()).deleteGroup(any());
  }

  @Test
  void delete_missingRole_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = principal(false);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal)).thenReturn(Optional.empty());

    var ex =
        assertThrows(AuthzAccessDeniedException.class, () -> useCase.delete(GROUP_ID, principal));

    assertEquals(AuthzReasonCode.MISSING_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(dataSourceGroupService, never()).deleteGroup(any());
  }

  @Test
  void delete_missingGroupFromService_throwsNotFound() throws Exception {
    OpenCopPrincipal principal = principal(false);
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.OWNER));
    doThrow(new PersistenceException("Could not find data source group: " + GROUP_ID))
        .when(dataSourceGroupService)
        .deleteGroup(GROUP_ID);

    var ex = assertThrows(GlobalOpenCopException.class, () -> useCase.delete(GROUP_ID, principal));

    assertEquals("Could not find data source group: " + GROUP_ID, ex.getMessage());
    assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  private static OpenCopPrincipal principal(boolean globalAdmin) {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.isGlobalAdmin()).thenReturn(globalAdmin);
    return principal;
  }
}
