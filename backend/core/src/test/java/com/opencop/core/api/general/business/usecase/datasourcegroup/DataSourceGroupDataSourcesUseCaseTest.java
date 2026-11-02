// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.datasourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupDataSourceService;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceAssignRequest;
import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

class DataSourceGroupDataSourcesUseCaseTest {

  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-656655443001");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-656655443002");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-656655443003");

  private DataSourceGroupAuthorization auth;
  private DataSourceGroupDataSourceService dataSourceService;
  private SourceAccessRepository sourceAccessRepository;
  private ResourceLifecycleGuard lifecycleGuard;
  private DataSourceGroupDataSourcesUseCase useCase;

  @BeforeEach
  void setUp() {
    auth = mock(DataSourceGroupAuthorization.class);
    dataSourceService = mock(DataSourceGroupDataSourceService.class);
    sourceAccessRepository = mock(SourceAccessRepository.class);
    lifecycleGuard = mock(ResourceLifecycleGuard.class);
    useCase =
        new DataSourceGroupDataSourcesUseCase(
            auth, dataSourceService, sourceAccessRepository, lifecycleGuard);
  }

  @Test
  void assignDataSources_defaultsOmittedRoleToViewer() throws Exception {
    var principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MAINTAINER));
    when(sourceAccessRepository.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.VIEWER);
    var request = new DataSourceGroupDataSourceAssignRequest(Set.of(SOURCE_ID), null);

    useCase.assignDataSources(GROUP_ID, request, principal);

    assertEquals(ServerRole.VIEWER, request.role());
    verify(lifecycleGuard)
        .assertDataSourceActive(SOURCE_ID, "assign data source to data source group");
    verify(dataSourceService).assignDataSources(GROUP_ID, request);
  }

  @Test
  void assignDataSources_effectiveTransitiveMaintainerRole_assignsDataSources() throws Exception {
    var principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MAINTAINER));
    when(sourceAccessRepository.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.ADMIN);
    var request = new DataSourceGroupDataSourceAssignRequest(Set.of(SOURCE_ID), ServerRole.VIEWER);

    useCase.assignDataSources(GROUP_ID, request, principal);

    verify(dataSourceService).assignDataSources(GROUP_ID, request);
  }

  @Test
  void assignDataSources_rejectsRoleAboveActorsEffectiveSourceRole() throws Exception {
    var principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MAINTAINER));
    when(sourceAccessRepository.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.VIEWER);
    var request = new DataSourceGroupDataSourceAssignRequest(Set.of(SOURCE_ID), ServerRole.ADMIN);

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () -> useCase.assignDataSources(GROUP_ID, request, principal));

    assertEquals(
        AuthzReasonCode.INSUFFICIENT_SOURCE_ROLE_FOR_DATA_SOURCE_GROUP_ASSIGNMENT, ex.reasonCode());
    assertEquals(
        "You are not authorized to assign the requested data source role to this data source group",
        ex.getMessage());
    verify(dataSourceService, never()).assignDataSources(GROUP_ID, request);
  }

  @Test
  void assignDataSources_globalAdminBypassesSourceRoleGuardButValidatesLifecycle()
      throws Exception {
    var principal = globalAdmin();
    var request = new DataSourceGroupDataSourceAssignRequest(Set.of(SOURCE_ID), ServerRole.OWNER);

    useCase.assignDataSources(GROUP_ID, request, principal);

    verify(lifecycleGuard)
        .assertDataSourceActive(SOURCE_ID, "assign data source to data source group");
    verify(sourceAccessRepository, never()).userRole(USER_ID, SOURCE_ID);
    verify(dataSourceService).assignDataSources(GROUP_ID, request);
  }

  @Test
  void assignDataSources_emptyRequestThrowsBadRequest() {
    var principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MAINTAINER));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.assignDataSources(
                    GROUP_ID,
                    new DataSourceGroupDataSourceAssignRequest(Set.of(), null),
                    principal));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
  }

  @Test
  void getDataSources_allowsContainerMembers() throws Exception {
    var principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MEMBER));

    useCase.getDataSources(GROUP_ID, principal);

    verify(dataSourceService).getDataSources(GROUP_ID);
  }

  @Test
  void removeDataSource_requiresMaintainerRole() throws Exception {
    var principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MEMBER));

    assertThrows(
        AccessDeniedException.class,
        () -> useCase.removeDataSource(GROUP_ID, SOURCE_ID, principal));

    verify(dataSourceService, never()).removeDataSource(GROUP_ID, SOURCE_ID);
  }

  @Test
  void removeDataSource_allowsMaintainerRole() throws Exception {
    var principal = nonAdmin();
    when(auth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.of(DataSourceGroupRole.MAINTAINER));

    useCase.removeDataSource(GROUP_ID, SOURCE_ID, principal);

    verify(dataSourceService).removeDataSource(GROUP_ID, SOURCE_ID);
  }

  private static OpenCopPrincipal nonAdmin() {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.isGlobalAdmin()).thenReturn(false);
    when(principal.internalUserId()).thenReturn(USER_ID);
    return principal;
  }

  private static OpenCopPrincipal globalAdmin() {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.isGlobalAdmin()).thenReturn(true);
    when(principal.internalUserId()).thenReturn(USER_ID);
    return principal;
  }
}
