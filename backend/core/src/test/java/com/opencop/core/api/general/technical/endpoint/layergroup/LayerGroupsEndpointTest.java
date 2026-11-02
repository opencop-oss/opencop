// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.layergroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupContainerManagement;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupLayerManagement;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupMemberManagement;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupUserGroupManagement;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupCreateRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMemberResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupsPatchRequest;
import com.opencop.core.sdk.model.iam.group.GroupManagementSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;

class LayerGroupsEndpointTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442001");
  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442002");

  private LayerGroupContainerManagement containerUseCase;
  private LayerGroupMemberManagement membersUseCase;
  private LayerGroupLayerManagement layersUseCase;
  private LayerGroupUserGroupManagement userGroupsUseCase;
  private LayerGroupsEndpoint endpoint;

  @BeforeEach
  void setUp() {
    containerUseCase = mock(LayerGroupContainerManagement.class);
    membersUseCase = mock(LayerGroupMemberManagement.class);
    layersUseCase = mock(LayerGroupLayerManagement.class);
    userGroupsUseCase = mock(LayerGroupUserGroupManagement.class);
    endpoint =
        new LayerGroupsEndpoint(containerUseCase, membersUseCase, layersUseCase, userGroupsUseCase);
  }

  @Test
  void createLayerGroup_delegatesToUseCaseAndReturnsCreated() {
    var principal = principal(false);
    var request = new LayerGroupCreateRequest("Alpha", "desc", null);
    var response =
        new LayerGroupResponse(
            GROUP_ID, "Alpha", "desc", LayerGroupAccessScope.PRIVATE, Instant.EPOCH);
    when(containerUseCase.create(request, principal)).thenReturn(response);

    var result = endpoint.createLayerGroup(request, principal);

    verify(containerUseCase).create(request, principal);
    assertEquals(HttpStatus.CREATED, result.getStatusCode());
    assertEquals(response, result.getBody());
    assertEquals(URI.create("/v1/layer-groups/" + GROUP_ID), result.getHeaders().getLocation());
  }

  /**
   * An {@code @PreAuthorize} here would silently restore the admin-only behaviour this route
   * replaces.
   */
  @Test
  void createLayerGroup_hasNoPreAuthorizeAnnotation() throws Exception {
    var method =
        LayerGroupsEndpoint.class.getDeclaredMethod(
            "createLayerGroup", LayerGroupCreateRequest.class, OpenCopPrincipal.class);

    assertNull(
        method.getAnnotation(PreAuthorize.class),
        "createLayerGroup must stay reachable by any authenticated user");
  }

  @Test
  void patchMembers_delegatesToMembersUseCase() {
    var principal = principal(false);
    var request = new LayerGroupMembersPatchRequest(Set.of(), null);

    endpoint.patchMembers(GROUP_ID, request, principal);

    verify(membersUseCase).patchMembers(GROUP_ID, request, principal);
  }

  @Test
  void getMembers_returnsRoleAsMaintainer() {
    var principal = principal(true);
    var response =
        new LayerGroupMemberResponse(
            USER_ID,
            "alice",
            "Alice",
            "Test",
            "alice@test",
            LayerGroupRole.MAINTAINER,
            Instant.EPOCH);
    when(membersUseCase.getMembers(GROUP_ID, principal)).thenReturn(List.of(response));

    var result = endpoint.getMembers(GROUP_ID, principal);

    assertEquals(1, result.size());
    assertEquals(LayerGroupRole.MAINTAINER, result.get(0).role());
  }

  @Test
  void getUserGroups_delegatesToUseCase() {
    var principal = principal(false);
    var userGroupId = UUID.fromString("550e8400-e29b-41d4-a716-446655442010");
    var expected =
        List.of(
            new LayerGroupUserGroupResponse(
                userGroupId,
                "TeamAlpha",
                LayerGroupRole.MEMBER,
                Instant.EPOCH,
                GroupManagementSource.SCIM));
    when(userGroupsUseCase.getUserGroups(GROUP_ID, principal)).thenReturn(expected);

    var result = endpoint.getUserGroups(GROUP_ID, principal);

    verify(userGroupsUseCase).getUserGroups(GROUP_ID, principal);
    assertEquals(expected, result.getBody());
  }

  @Test
  void patchUserGroups_delegatesToUseCase() throws Exception {
    var principal = principal(false);
    var request = new LayerGroupUserGroupsPatchRequest(List.of(), List.of());

    endpoint.patchUserGroups(GROUP_ID, request, principal);

    verify(userGroupsUseCase).patchUserGroups(GROUP_ID, request, principal);
  }

  @Test
  void patchUserGroups_propagatesAccessDenied() throws Exception {
    var principal = principal(false);
    var request = new LayerGroupUserGroupsPatchRequest(List.of(), List.of());
    org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
        .when(userGroupsUseCase)
        .patchUserGroups(GROUP_ID, request, principal);

    assertThrows(
        AccessDeniedException.class, () -> endpoint.patchUserGroups(GROUP_ID, request, principal));
  }

  @Test
  void patchUserGroups_persistenceExceptionMapsToBadRequest() throws Exception {
    var principal = principal(false);
    var request = new LayerGroupUserGroupsPatchRequest(List.of(), List.of());
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

  private static OpenCopPrincipal principal(boolean admin) {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID, admin);
  }
}
