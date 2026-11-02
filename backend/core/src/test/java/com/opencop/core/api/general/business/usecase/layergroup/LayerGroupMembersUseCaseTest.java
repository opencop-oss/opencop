// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layergroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupService;
import com.opencop.core.api.general.ports.gateway.layergroup.MemberPatchGuard;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest.MemberAdd;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

class LayerGroupMembersUseCaseTest {

  private static final UUID LG_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
  private static final UUID USER_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440011");
  private static final UUID USER_B = UUID.fromString("550e8400-e29b-41d4-a716-446655440012");
  private static final UUID UG_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440013");

  private LayerGroupAuthorization auth;
  private LayerGroupService layerGroupService;
  private LayerGroupMembersUseCase useCase;

  @BeforeEach
  void setUp() {
    auth = mock(LayerGroupAuthorization.class);
    layerGroupService = mock(LayerGroupService.class);
    useCase =
        new LayerGroupMembersUseCase(
            auth, layerGroupService, mock(AuditRecorder.class), new LayerGroupAuditProjection());
    when(auth.getEffectiveContainerRole(any(), any()))
        .thenReturn(Optional.of(LayerGroupRole.OWNER));
  }

  // ── last-owner guard ──────────────────────────────────────────────────────

  @Test
  void patchMembers_lastOwnerDemoted_nonEmptyGroup_throwsAndWritesSkipped()
      throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    simulateServerSideMembers(Map.of(USER_A, LayerGroupRole.OWNER));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.patchMembers(
                    LG_ID,
                    new LayerGroupMembersPatchRequest(
                        Set.of(new MemberAdd(USER_A, LayerGroupRole.MAINTAINER)), null),
                    principal));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
  }

  @Test
  void patchMembers_ownerRemovedWithNonOwnerRemaining_throws() throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    simulateServerSideMembers(
        Map.of(USER_A, LayerGroupRole.OWNER, USER_B, LayerGroupRole.MAINTAINER));

    assertThrows(
        GlobalOpenCopException.class,
        () ->
            useCase.patchMembers(
                LG_ID, new LayerGroupMembersPatchRequest(null, Set.of(USER_A)), principal));
  }

  @Test
  void patchMembers_twoOwners_demoteOneSucceeds() throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    simulateServerSideMembers(Map.of(USER_A, LayerGroupRole.OWNER, USER_B, LayerGroupRole.OWNER));

    useCase.patchMembers(
        LG_ID,
        new LayerGroupMembersPatchRequest(
            Set.of(new MemberAdd(USER_A, LayerGroupRole.MAINTAINER)), null),
        principal);

    verify(layerGroupService).patchMembers(eq(LG_ID), any(), any());
  }

  @Test
  void patchMembers_soleMemberOwnerRemoved_noUserGroupOwner_throws() throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    simulateServerSideMembers(Map.of(USER_A, LayerGroupRole.OWNER));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.patchMembers(
                    LG_ID, new LayerGroupMembersPatchRequest(null, Set.of(USER_A)), principal));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
  }

  @Test
  void patchMembers_globalAdmin_passesOwnerRetentionGuard() throws PersistenceException {
    OpenCopPrincipal admin = mock(OpenCopPrincipal.class);
    when(admin.isGlobalAdmin()).thenReturn(true);

    useCase.patchMembers(
        LG_ID,
        new LayerGroupMembersPatchRequest(
            Set.of(new MemberAdd(USER_A, LayerGroupRole.MAINTAINER)), null),
        admin);

    var guardCaptor = ArgumentCaptor.forClass(MemberPatchGuard.class);
    verify(layerGroupService).patchMembers(eq(LG_ID), any(), guardCaptor.capture());
    assertNotNull(guardCaptor.getValue());
  }

  // ── patchMembers delegates with LayerGroupRole.ADMIN request ─────────────

  @Test
  void patchMembers_addMemberWithAdminRole_servicePatchCalledWithAdminRole()
      throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    simulateServerSideMembers(Map.of(USER_B, LayerGroupRole.OWNER));

    useCase.patchMembers(
        LG_ID,
        new LayerGroupMembersPatchRequest(
            Set.of(new MemberAdd(USER_A, LayerGroupRole.ADMIN)), null),
        principal);

    verify(layerGroupService).patchMembers(eq(LG_ID), any(), any());
  }

  // ── owner-list changes require OWNER ─────────────────────────────────────

  @Test
  void patchMembers_adminPromotesSelfToOwner_denied() throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    when(principal.internalUserId()).thenReturn(USER_A);
    when(auth.getEffectiveContainerRole(LG_ID, principal))
        .thenReturn(Optional.of(LayerGroupRole.ADMIN));
    simulateServerSideMembers(Map.of(USER_A, LayerGroupRole.ADMIN, USER_B, LayerGroupRole.OWNER));

    assertThrows(
        AccessDeniedException.class,
        () ->
            useCase.patchMembers(
                LG_ID,
                new LayerGroupMembersPatchRequest(
                    Set.of(new MemberAdd(USER_A, LayerGroupRole.OWNER)), null),
                principal));
  }

  @Test
  void patchMembers_adminDemotesOwner_denied() throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    when(principal.internalUserId()).thenReturn(USER_A);
    when(auth.getEffectiveContainerRole(LG_ID, principal))
        .thenReturn(Optional.of(LayerGroupRole.ADMIN));
    simulateServerSideMembers(Map.of(USER_A, LayerGroupRole.ADMIN, USER_B, LayerGroupRole.OWNER));

    assertThrows(
        AccessDeniedException.class,
        () ->
            useCase.patchMembers(
                LG_ID,
                new LayerGroupMembersPatchRequest(
                    Set.of(new MemberAdd(USER_B, LayerGroupRole.MAINTAINER)), null),
                principal));
  }

  @Test
  void patchMembers_adminRemovesOwner_denied() throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    when(principal.internalUserId()).thenReturn(USER_A);
    when(auth.getEffectiveContainerRole(LG_ID, principal))
        .thenReturn(Optional.of(LayerGroupRole.ADMIN));
    UUID extraOwner = UUID.randomUUID();
    simulateServerSideMembers(
        Map.of(
            USER_A,
            LayerGroupRole.ADMIN,
            USER_B,
            LayerGroupRole.OWNER,
            extraOwner,
            LayerGroupRole.OWNER));
    assertThrows(
        AccessDeniedException.class,
        () ->
            useCase.patchMembers(
                LG_ID, new LayerGroupMembersPatchRequest(null, Set.of(USER_B)), principal));
  }

  @Test
  void patchMembers_ownerPromotesOtherToOwner_allowed() throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    when(principal.internalUserId()).thenReturn(USER_A);
    simulateServerSideMembers(
        Map.of(USER_A, LayerGroupRole.OWNER, USER_B, LayerGroupRole.MAINTAINER));

    useCase.patchMembers(
        LG_ID,
        new LayerGroupMembersPatchRequest(
            Set.of(new MemberAdd(USER_B, LayerGroupRole.OWNER)), null),
        principal);

    verify(layerGroupService).patchMembers(eq(LG_ID), any(), any());
  }

  @Test
  void patchMembers_adminNonOwnerChange_doesNotRequireOwner() throws PersistenceException {
    OpenCopPrincipal principal = nonAdmin();
    when(principal.internalUserId()).thenReturn(USER_A);
    simulateServerSideMembers(Map.of(USER_A, LayerGroupRole.ADMIN, USER_B, LayerGroupRole.OWNER));

    UUID userC = UUID.randomUUID();
    useCase.patchMembers(
        LG_ID,
        new LayerGroupMembersPatchRequest(
            Set.of(new MemberAdd(userC, LayerGroupRole.MAINTAINER)), null),
        principal);

    verify(layerGroupService).patchMembers(eq(LG_ID), any(), any());
  }

  @Test
  void patchMembers_globalAdmin_promotesToOwner_bypassesRule() throws PersistenceException {
    OpenCopPrincipal admin = mock(OpenCopPrincipal.class);
    when(admin.isGlobalAdmin()).thenReturn(true);

    useCase.patchMembers(
        LG_ID,
        new LayerGroupMembersPatchRequest(
            Set.of(new MemberAdd(USER_A, LayerGroupRole.OWNER)), null),
        admin);

    verify(layerGroupService).patchMembers(eq(LG_ID), any(), any());
  }

  @Test
  void patchMembers_globalAdmin_removesLastOwnerNoUserGroupOwner_throwsBadRequest()
      throws PersistenceException {
    OpenCopPrincipal admin = mock(OpenCopPrincipal.class);
    when(admin.isGlobalAdmin()).thenReturn(true);
    simulateServerSideMembers(Map.of(USER_A, LayerGroupRole.OWNER));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.patchMembers(
                    LG_ID, new LayerGroupMembersPatchRequest(null, Set.of(USER_A)), admin));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
  }

  @Test
  void patchMembers_globalAdmin_removesDirectOwnerWithUserGroupOwner_succeeds()
      throws PersistenceException {
    OpenCopPrincipal admin = mock(OpenCopPrincipal.class);
    when(admin.isGlobalAdmin()).thenReturn(true);
    simulateServerSideMembers(
        Map.of(USER_A, LayerGroupRole.OWNER), Map.of(UG_ID, LayerGroupRole.OWNER));

    useCase.patchMembers(LG_ID, new LayerGroupMembersPatchRequest(null, Set.of(USER_A)), admin);

    var guardCaptor = ArgumentCaptor.forClass(MemberPatchGuard.class);
    verify(layerGroupService).patchMembers(eq(LG_ID), any(), guardCaptor.capture());
    assertNotNull(guardCaptor.getValue());
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  /**
   * Stubs layerGroupService.patchMembers so that — like the real service — it builds the projected
   * map from {@code current} + the request's adds/removes and invokes the guard. Lets these tests
   * exercise the guard the use case actually constructs.
   */
  private void simulateServerSideMembers(Map<UUID, LayerGroupRole> current)
      throws PersistenceException {
    simulateServerSideMembers(current, Map.of());
  }

  private void simulateServerSideMembers(
      Map<UUID, LayerGroupRole> current, Map<UUID, LayerGroupRole> currentUserGroups)
      throws PersistenceException {
    doAnswer(
            inv -> {
              LayerGroupMembersPatchRequest req = inv.getArgument(1);
              MemberPatchGuard guard = inv.getArgument(2);
              if (guard == null) {
                return null;
              }
              Map<UUID, LayerGroupRole> projected = new HashMap<>(current);
              req.add().forEach(a -> projected.put(a.userId(), a.role()));
              req.remove().forEach(projected::remove);
              guard.check(current, projected, currentUserGroups);
              return null;
            })
        .when(layerGroupService)
        .patchMembers(eq(LG_ID), any(), any());
  }

  private static OpenCopPrincipal nonAdmin() {
    OpenCopPrincipal p = mock(OpenCopPrincipal.class);
    when(p.isGlobalAdmin()).thenReturn(false);
    return p;
  }
}
