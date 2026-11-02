// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.datasourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupContainerManagement;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupDataSourceManagement;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupLayerEmbeddingManagement;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupMemberManagement;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupUserGroupManagement;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceAssignRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMemberResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupUserGroupResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupUserGroupsPatchRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.NotFoundException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class DataSourceGroupsEndpointTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-556655442001");
  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-556655442002");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-556655442003");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-556655442004");

  private DataSourceGroupContainerManagement containerUseCase;
  private DataSourceGroupMemberManagement membersUseCase;
  private DataSourceGroupUserGroupManagement userGroupsUseCase;
  private DataSourceGroupLayerEmbeddingManagement layerEmbeddingUseCase;
  private DataSourceGroupDataSourceManagement dataSourcesUseCase;
  private DataSourceGroupsEndpoint endpoint;

  @BeforeEach
  void setUp() {
    containerUseCase = mock(DataSourceGroupContainerManagement.class);
    membersUseCase = mock(DataSourceGroupMemberManagement.class);
    userGroupsUseCase = mock(DataSourceGroupUserGroupManagement.class);
    layerEmbeddingUseCase = mock(DataSourceGroupLayerEmbeddingManagement.class);
    dataSourcesUseCase = mock(DataSourceGroupDataSourceManagement.class);
    endpoint =
        new DataSourceGroupsEndpoint(
            containerUseCase,
            membersUseCase,
            userGroupsUseCase,
            layerEmbeddingUseCase,
            dataSourcesUseCase);
  }

  @Test
  void patchMembers_delegatesToMembersUseCase() {
    var principal = principal(false);
    var request = new DataSourceGroupMembersPatchRequest(Set.of(), null);

    endpoint.patchMembers(GROUP_ID, request, principal);

    verify(membersUseCase).patchMembers(GROUP_ID, request, principal);
  }

  @Test
  void getMembers_returnsRoleAsMaintainer() {
    var principal = principal(true);
    var response =
        new DataSourceGroupMemberResponse(
            USER_ID,
            "alice",
            "Alice",
            "Test",
            "alice@test",
            DataSourceGroupRole.MAINTAINER,
            Instant.EPOCH);
    when(membersUseCase.getMembers(GROUP_ID, principal)).thenReturn(List.of(response));

    var result = endpoint.getMembers(GROUP_ID, principal);

    assertEquals(1, result.size());
    assertEquals(DataSourceGroupRole.MAINTAINER, result.get(0).role());
  }

  @Test
  void getUserGroups_delegatesToUseCase() {
    var principal = principal(false);
    var userGroupId = UUID.fromString("550e8400-e29b-41d4-a716-556655442010");
    var expected =
        List.of(
            new DataSourceGroupUserGroupResponse(
                userGroupId, "TeamAlpha", DataSourceGroupRole.MEMBER, Instant.EPOCH));
    when(userGroupsUseCase.getUserGroups(GROUP_ID, principal)).thenReturn(expected);

    var result = endpoint.getUserGroups(GROUP_ID, principal);

    verify(userGroupsUseCase).getUserGroups(GROUP_ID, principal);
    assertEquals(expected, result.getBody());
  }

  @Test
  void patchUserGroups_persistenceExceptionMapsToBadRequest() throws Exception {
    var principal = principal(false);
    var request = new DataSourceGroupUserGroupsPatchRequest(List.of(), List.of());
    org.mockito.Mockito.doThrow(new PersistenceException("User group id is required"))
        .when(userGroupsUseCase)
        .patchUserGroups(GROUP_ID, request, principal);

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> endpoint.patchUserGroups(GROUP_ID, request, principal));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    assertEquals("User group id is required", ex.getMessage());
  }

  @Test
  void patchUserGroups_notFoundExceptionMapsToNotFound() throws Exception {
    var principal = principal(false);
    var request = new DataSourceGroupUserGroupsPatchRequest(List.of(), List.of());
    org.mockito.Mockito.doThrow(
            new NotFoundException("Could not find data source group: " + GROUP_ID))
        .when(userGroupsUseCase)
        .patchUserGroups(GROUP_ID, request, principal);

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> endpoint.patchUserGroups(GROUP_ID, request, principal));

    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    assertEquals("Could not find data source group: " + GROUP_ID, ex.getMessage());
  }

  @Test
  void getLayersForDataSourceGroup_delegatesToUseCase() {
    var principal = principal(false);
    var expected =
        List.of(
            new DataSourceGroupLayerResponse(
                GROUP_ID, "Sources", LAYER_ID, "Ops Layer", Instant.EPOCH));
    when(layerEmbeddingUseCase.getLayersForDataSourceGroup(GROUP_ID, principal))
        .thenReturn(expected);

    var result = endpoint.getLayersForDataSourceGroup(GROUP_ID, principal);

    verify(layerEmbeddingUseCase).getLayersForDataSourceGroup(GROUP_ID, principal);
    assertEquals(expected, result.getBody());
  }

  @Test
  void getDataSources_delegatesToUseCase() {
    var principal = principal(false);
    var expected =
        List.of(
            new DataSourceGroupDataSourceResponse(
                GROUP_ID, "Sources", SOURCE_ID, "camera", ServerRole.VIEWER, Instant.EPOCH));
    when(dataSourcesUseCase.getDataSources(GROUP_ID, principal)).thenReturn(expected);

    var result = endpoint.getDataSources(GROUP_ID, principal);

    verify(dataSourcesUseCase).getDataSources(GROUP_ID, principal);
    assertEquals(expected, result.getBody());
  }

  @Test
  void assignDataSources_delegatesToUseCaseAndReturnsCreated() {
    var principal = principal(false);
    var request = new DataSourceGroupDataSourceAssignRequest(Set.of(SOURCE_ID), ServerRole.ADMIN);
    var expected =
        List.of(
            new DataSourceGroupDataSourceResponse(
                GROUP_ID, "Sources", SOURCE_ID, "camera", ServerRole.ADMIN, Instant.EPOCH));
    when(dataSourcesUseCase.assignDataSources(GROUP_ID, request, principal)).thenReturn(expected);

    var result = endpoint.assignDataSources(GROUP_ID, request, principal);

    verify(dataSourcesUseCase).assignDataSources(GROUP_ID, request, principal);
    assertEquals(HttpStatus.CREATED, result.getStatusCode());
    assertEquals(expected, result.getBody());
  }

  @Test
  void removeDataSource_delegatesToUseCaseAndReturnsNoContent() {
    var principal = principal(false);

    var result = endpoint.removeDataSource(GROUP_ID, SOURCE_ID, principal);

    verify(dataSourcesUseCase).removeDataSource(GROUP_ID, SOURCE_ID, principal);
    assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
  }

  @Test
  void deleteDataSourceGroup_delegatesToUseCaseAndReturnsNoContent() {
    var principal = principal(false);

    var result = endpoint.deleteDataSourceGroup(GROUP_ID, principal);

    verify(containerUseCase).delete(GROUP_ID, principal);
    assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
  }

  private static OpenCopPrincipal principal(boolean admin) {
    var oidcUser = mock(OidcUser.class);
    doReturn(List.of()).when(oidcUser).getAuthorities();
    return new OpenCopPrincipal(USER_ID, oidcUser, admin);
  }
}
