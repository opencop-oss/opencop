// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layergroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupService;
import com.opencop.core.api.general.ports.gateway.layergroup.UserGroupPatchGuard;
import com.opencop.core.api.general.technical.gateway.db.layergroup.AppLayerGroupUserGroupService;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupsPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupsPatchRequest.UserGroupAdd;
import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LayerGroupUserGroupsUseCaseTest {

  private static final UUID LG_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440020");
  private static final UUID UG_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440021");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440022");

  private LayerGroupAuthorization auth;
  private AppLayerGroupUserGroupService service;
  private LayerGroupUserGroupsUseCase useCase;

  @BeforeEach
  void setUp() {
    auth = mock(LayerGroupAuthorization.class);
    service = mock(AppLayerGroupUserGroupService.class);
    useCase =
        new LayerGroupUserGroupsUseCase(
            auth,
            service,
            mock(LayerGroupService.class),
            mock(AuditRecorder.class),
            new LayerGroupAuditProjection());

    when(auth.getEffectiveContainerRole(any(), any()))
        .thenReturn(Optional.of(LayerGroupRole.OWNER));
  }

  @Test
  void patchUserGroups_globalAdmin_addMaintainer_invokesServiceOnce() throws Exception {
    OpenCopPrincipal principal = globalAdmin();
    var guardCaptor = ArgumentCaptor.forClass(UserGroupPatchGuard.class);

    var request =
        new LayerGroupUserGroupsPatchRequest(
            List.of(new UserGroupAdd(UG_ID, LayerGroupRole.MAINTAINER)), List.of());

    useCase.patchUserGroups(LG_ID, request, principal);

    verify(service)
        .applyUserGroupPatch(
            eq(LG_ID), eq(request.add()), eq(request.remove()), guardCaptor.capture());

    assertEquals(
        1, guardCaptor.getAllValues().size(), "Expected owner-retention guard for patching path");
    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                guardCaptor
                    .getValue()
                    .check(
                        Map.of(),
                        Map.of(UG_ID, LayerGroupRole.OWNER),
                        Map.of(UG_ID, LayerGroupRole.MEMBER)));
    assertEquals("Layer group must have at least one OWNER", ex.getMessage());
  }

  @Test
  void patchUserGroups_actorWithoutRequiredRole_serviceNeverInvoked() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(LG_ID, principal))
        .thenReturn(Optional.of(LayerGroupRole.MEMBER));

    var request =
        new LayerGroupUserGroupsPatchRequest(
            List.of(new UserGroupAdd(UG_ID, LayerGroupRole.MAINTAINER)), List.of());

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () -> useCase.patchUserGroups(LG_ID, request, principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_LAYER_GROUP_ROLE, ex.reasonCode());
    verify(service, never()).applyUserGroupPatch(any(), any(), any(), any());
  }

  @Test
  void patchUserGroups_actorWithoutLayerGroupRole_reportsMissingReasonCode() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(LG_ID, principal)).thenReturn(Optional.empty());

    var request =
        new LayerGroupUserGroupsPatchRequest(
            List.of(new UserGroupAdd(UG_ID, LayerGroupRole.MEMBER)), List.of());

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () -> useCase.patchUserGroups(LG_ID, request, principal));

    assertEquals(AuthzReasonCode.MISSING_LAYER_GROUP_ROLE, ex.reasonCode());
    verify(service, never()).applyUserGroupPatch(any(), any(), any(), any());
  }

  @Test
  void patchUserGroups_escalationGuard_maintainerAssignsAdmin_denied() throws Exception {
    OpenCopPrincipal principal = nonAdmin();
    when(auth.getEffectiveContainerRole(LG_ID, principal))
        .thenReturn(Optional.of(LayerGroupRole.MAINTAINER));

    var request =
        new LayerGroupUserGroupsPatchRequest(
            List.of(new UserGroupAdd(UG_ID, LayerGroupRole.ADMIN)), List.of());

    var ex =
        assertThrows(
            AuthzAccessDeniedException.class,
            () -> useCase.patchUserGroups(LG_ID, request, principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_LAYER_GROUP_ROLE, ex.reasonCode());
    verify(service, never()).applyUserGroupPatch(any(), any(), any(), any());
  }

  @Test
  void patchUserGroups_addNullUserGroupId_throwsPersistenceException() throws Exception {
    OpenCopPrincipal principal = globalAdmin();

    var request =
        new LayerGroupUserGroupsPatchRequest(
            List.of(new UserGroupAdd(null, LayerGroupRole.MEMBER)), List.of());

    var ex =
        assertThrows(
            PersistenceException.class, () -> useCase.patchUserGroups(LG_ID, request, principal));

    assertEquals("User group id is required", ex.getMessage());
    verify(service, never()).applyUserGroupPatch(any(), any(), any(), any());
  }

  @Test
  void patchUserGroups_addNullRole_throwsPersistenceException() throws Exception {
    OpenCopPrincipal principal = globalAdmin();

    var request =
        new LayerGroupUserGroupsPatchRequest(List.of(new UserGroupAdd(UG_ID, null)), List.of());

    var ex =
        assertThrows(
            PersistenceException.class, () -> useCase.patchUserGroups(LG_ID, request, principal));

    assertEquals("User group role is required", ex.getMessage());
    verify(service, never()).applyUserGroupPatch(any(), any(), any(), any());
  }

  @Test
  void patchUserGroups_globalAdmin_keepsOwnerRetentionGuard() throws Exception {
    OpenCopPrincipal principal = globalAdmin();
    var guardCaptor = ArgumentCaptor.forClass(UserGroupPatchGuard.class);

    var request = new LayerGroupUserGroupsPatchRequest(List.of(), List.of(UG_ID));

    useCase.patchUserGroups(LG_ID, request, principal);

    verify(service)
        .applyUserGroupPatch(
            eq(LG_ID), eq(request.add()), eq(request.remove()), guardCaptor.capture());

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                guardCaptor
                    .getValue()
                    .check(Map.of(), Map.of(UG_ID, LayerGroupRole.OWNER), Map.of()));
    assertEquals("Layer group must have at least one OWNER", ex.getMessage());
  }

  @Test
  void patchUserGroups_nonGlobalAdmin_delegatesOwnerPatchGuardToService() throws Exception {
    OpenCopPrincipal principal = nonAdmin();

    var request = new LayerGroupUserGroupsPatchRequest(List.of(), List.of(UG_ID));
    var guardCaptor = ArgumentCaptor.forClass(UserGroupPatchGuard.class);

    useCase.patchUserGroups(LG_ID, request, principal);

    verify(service)
        .applyUserGroupPatch(
            eq(LG_ID), eq(request.add()), eq(request.remove()), guardCaptor.capture());

    var ex =
        assertThrows(
            PersistenceException.class,
            () ->
                guardCaptor
                    .getValue()
                    .check(
                        Map.of(),
                        Map.of(UG_ID, LayerGroupRole.OWNER),
                        Map.of(UG_ID, LayerGroupRole.MEMBER)));

    assertTrue(ex.getMessage().contains("OWNER"));
  }

  private static OpenCopPrincipal nonAdmin() {
    OpenCopPrincipal p = mock(OpenCopPrincipal.class);
    when(p.isGlobalAdmin()).thenReturn(false);
    when(p.internalUserId()).thenReturn(USER_ID);
    return p;
  }

  private static OpenCopPrincipal globalAdmin() {
    OpenCopPrincipal p = mock(OpenCopPrincipal.class);
    when(p.isGlobalAdmin()).thenReturn(true);
    return p;
  }
}
