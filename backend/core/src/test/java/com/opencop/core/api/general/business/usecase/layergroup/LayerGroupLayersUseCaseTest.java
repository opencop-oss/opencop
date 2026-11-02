// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layergroup;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.opencop.core.api.general.technical.gateway.db.layergroup.AppLayerGroupLayerService;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayerResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayersAssignRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

class LayerGroupLayersUseCaseTest {

  private static final UUID LG_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442000");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442001");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442002");
  private static final UUID LAYER_ID_2 = UUID.fromString("550e8400-e29b-41d4-a716-446655442003");

  private LayerGroupAuthorization auth;
  private AppLayerGroupLayerService layerGroupLayerService;
  private EffectiveLayerAccessService effectiveAccess;
  private LayerGroupService layerGroupService;
  private LayersRepository layersRepository;
  private LayerGroupLayersUseCase useCase;

  @BeforeEach
  void setUp() {
    auth = mock(LayerGroupAuthorization.class);
    layerGroupLayerService = mock(AppLayerGroupLayerService.class);
    effectiveAccess = mock(EffectiveLayerAccessService.class);
    layerGroupService = mock(LayerGroupService.class);
    layersRepository = mock(LayersRepository.class);
    useCase =
        new LayerGroupLayersUseCase(
            auth,
            layerGroupLayerService,
            effectiveAccess,
            layerGroupService,
            layersRepository,
            mock(AuditRecorder.class),
            new LayerGroupAuditProjection());
    when(auth.getEffectiveContainerRole(any(), any()))
        .thenReturn(Optional.of(LayerGroupRole.OWNER));
  }

  // ── SEC-001: layer scope gate (non-admins may only file PRIVATE layers) ──

  /**
   * A GLOBAL layer resolves to MEMBER for every user via the access-scope fallback, so barring
   * non-admins from filing one stops anyone redistributing global layers through a group they own.
   */
  @Test
  void assignLayers_nonAdmin_globalLayer_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = nonAdmin(USER_ID);
    privateGroup();
    stubLayer(LAYER_ID, LayerAccessScope.GLOBAL);

    var ex =
        assertThrows(
            AccessDeniedException.class,
            () ->
                useCase.assignLayers(
                    LG_ID,
                    new LayerGroupLayersAssignRequest(Set.of(LAYER_ID), LayerRole.MEMBER),
                    principal));

    assertTrue(ex.getMessage().contains("only admins can assign a GLOBAL layer"));
    verify(layerGroupLayerService, never()).assignLayersToLayerGroup(any(), any(), any());
  }

  /** The scope gate is non-admin only, so #261's admin behaviour must survive. */
  @Test
  void assignLayers_globalAdmin_globalLayerIntoPrivateGroup_succeeds() throws Exception {
    OpenCopPrincipal admin = mock(OpenCopPrincipal.class);
    when(admin.isGlobalAdmin()).thenReturn(true);
    privateGroup();
    when(layerGroupLayerService.assignLayersToLayerGroup(eq(LG_ID), any(), eq(LayerRole.MEMBER)))
        .thenReturn(List.of(layerResponse(LayerRole.MEMBER)));

    useCase.assignLayers(
        LG_ID, new LayerGroupLayersAssignRequest(Set.of(LAYER_ID), LayerRole.MEMBER), admin);

    verify(layerGroupLayerService).assignLayersToLayerGroup(eq(LG_ID), any(), eq(LayerRole.MEMBER));
  }

  // ── SEC-001: layer-role floor (EDITOR or above) ──────────────────────────

  /** EDITOR is the floor, so a MAINTAINER curating layers they can edit keeps working. */
  @Test
  void assignLayers_nonAdmin_effectiveEditor_delegatesToService() throws Exception {
    OpenCopPrincipal principal = nonAdmin(USER_ID);
    privateGroup();
    stubLayer(LAYER_ID, LayerAccessScope.PRIVATE);
    when(effectiveAccess.getEffectiveRole(LAYER_ID, USER_ID))
        .thenReturn(Optional.of(LayerRole.EDITOR));
    when(layerGroupLayerService.assignLayersToLayerGroup(eq(LG_ID), any(), eq(LayerRole.EDITOR)))
        .thenReturn(List.of(layerResponse(LayerRole.EDITOR)));

    useCase.assignLayers(
        LG_ID, new LayerGroupLayersAssignRequest(Set.of(LAYER_ID), LayerRole.EDITOR), principal);

    verify(layerGroupLayerService).assignLayersToLayerGroup(eq(LG_ID), any(), eq(LayerRole.EDITOR));
  }

  @Test
  void assignLayers_nonAdmin_effectiveMemberOnPrivateLayer_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = nonAdmin(USER_ID);
    privateGroup();
    stubLayer(LAYER_ID, LayerAccessScope.PRIVATE);
    when(effectiveAccess.getEffectiveRole(LAYER_ID, USER_ID))
        .thenReturn(Optional.of(LayerRole.MEMBER));

    var ex =
        assertThrows(
            AccessDeniedException.class,
            () ->
                useCase.assignLayers(
                    LG_ID,
                    new LayerGroupLayersAssignRequest(Set.of(LAYER_ID), LayerRole.MEMBER),
                    principal));

    assertTrue(ex.getMessage().contains("requires EDITOR or above on the layer"));
    verify(layerGroupLayerService, never()).assignLayersToLayerGroup(any(), any(), any());
  }

  @Test
  void assignLayers_nonAdmin_noEffectiveRole_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = nonAdmin(USER_ID);
    privateGroup();
    stubLayer(LAYER_ID, LayerAccessScope.PRIVATE);
    when(effectiveAccess.getEffectiveRole(LAYER_ID, USER_ID)).thenReturn(Optional.empty());

    assertThrows(
        AccessDeniedException.class,
        () ->
            useCase.assignLayers(
                LG_ID,
                new LayerGroupLayersAssignRequest(Set.of(LAYER_ID), LayerRole.MEMBER),
                principal));

    verify(layerGroupLayerService, never()).assignLayersToLayerGroup(any(), any(), any());
  }

  // ── SEC-001: privilege-escalation guard ──────────────────────────────────

  @Test
  void assignLayers_nonAdmin_effectiveAdminAssignsOwner_throwsAccessDenied() throws Exception {
    OpenCopPrincipal principal = nonAdmin(USER_ID);
    privateGroup();
    stubLayer(LAYER_ID, LayerAccessScope.PRIVATE);
    when(effectiveAccess.getEffectiveRole(LAYER_ID, USER_ID))
        .thenReturn(Optional.of(LayerRole.ADMIN));

    var ex =
        assertThrows(
            AccessDeniedException.class,
            () ->
                useCase.assignLayers(
                    LG_ID,
                    new LayerGroupLayersAssignRequest(Set.of(LAYER_ID), LayerRole.OWNER),
                    principal));

    assertTrue(ex.getMessage().contains("Cannot assign role OWNER"));
    verify(layerGroupLayerService, never()).assignLayersToLayerGroup(any(), any(), any());
  }

  @Test
  void assignLayers_nonAdmin_oneLayerBelowFloor_wholeBatchRejected() throws Exception {
    OpenCopPrincipal principal = nonAdmin(USER_ID);
    privateGroup();
    stubLayer(LAYER_ID, LayerAccessScope.PRIVATE);
    stubLayer(LAYER_ID_2, LayerAccessScope.PRIVATE);
    // Only the second layer sits below the floor, so the whole batch must be rejected rather than
    // partially applied.
    when(effectiveAccess.getEffectiveRole(LAYER_ID, USER_ID))
        .thenReturn(Optional.of(LayerRole.EDITOR));
    when(effectiveAccess.getEffectiveRole(LAYER_ID_2, USER_ID))
        .thenReturn(Optional.of(LayerRole.MEMBER));

    assertThrows(
        AccessDeniedException.class,
        () ->
            useCase.assignLayers(
                LG_ID,
                new LayerGroupLayersAssignRequest(Set.of(LAYER_ID, LAYER_ID_2), LayerRole.EDITOR),
                principal));

    verify(layerGroupLayerService, never()).assignLayersToLayerGroup(any(), any(), any());
  }

  @Test
  void assignLayers_globalAdmin_ownerRole_effectiveAccessNotChecked() throws Exception {
    OpenCopPrincipal admin = mock(OpenCopPrincipal.class);
    when(admin.isGlobalAdmin()).thenReturn(true);
    when(layerGroupLayerService.assignLayersToLayerGroup(eq(LG_ID), any(), eq(LayerRole.OWNER)))
        .thenReturn(List.of(layerResponse(LayerRole.OWNER)));
    when(layerGroupService.getGroup(LG_ID))
        .thenReturn(
            new LayerGroupResponse(
                LG_ID, "group", null, LayerGroupAccessScope.PRIVATE, Instant.EPOCH));

    useCase.assignLayers(
        LG_ID, new LayerGroupLayersAssignRequest(Set.of(LAYER_ID), LayerRole.OWNER), admin);

    verify(layerGroupLayerService).assignLayersToLayerGroup(eq(LG_ID), any(), eq(LayerRole.OWNER));
    verify(effectiveAccess, never()).getEffectiveRole(any(), any());
  }

  @Test
  void assignLayers_globalGroup_privateLayer_throwsConflict() throws Exception {
    OpenCopPrincipal admin = mock(OpenCopPrincipal.class);
    when(admin.isGlobalAdmin()).thenReturn(true);
    LayerGroupResponse globalGroup =
        new LayerGroupResponse(
            LG_ID, "GlobalGroup", null, LayerGroupAccessScope.GLOBAL, Instant.EPOCH);
    when(layerGroupService.getGroup(LG_ID)).thenReturn(globalGroup);
    Layer privateLayer =
        new Layer.Builder()
            .withId(LAYER_ID)
            .withName("PrivateLayer")
            .withAccessScope(LayerAccessScope.PRIVATE)
            .build();
    when(layersRepository.get(LAYER_ID)).thenReturn(privateLayer);

    GlobalOpenCopException ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.assignLayers(
                    LG_ID,
                    new LayerGroupLayersAssignRequest(Set.of(LAYER_ID), LayerRole.MEMBER),
                    admin));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessage())
        .contains("Cannot assign a non-global layer to a global layer group");
    verify(layerGroupLayerService, never()).assignLayersToLayerGroup(any(), any(), any());
  }

  @Test
  void assignLayers_globalGroup_globalLayer_succeeds() throws Exception {
    OpenCopPrincipal admin = mock(OpenCopPrincipal.class);
    when(admin.isGlobalAdmin()).thenReturn(true);
    LayerGroupResponse globalGroup =
        new LayerGroupResponse(
            LG_ID, "GlobalGroup", null, LayerGroupAccessScope.GLOBAL, Instant.EPOCH);
    when(layerGroupService.getGroup(LG_ID)).thenReturn(globalGroup);
    Layer globalLayer =
        new Layer.Builder()
            .withId(LAYER_ID)
            .withName("GlobalLayer")
            .withAccessScope(LayerAccessScope.GLOBAL)
            .build();
    when(layersRepository.get(LAYER_ID)).thenReturn(globalLayer);
    when(layerGroupLayerService.assignLayersToLayerGroup(eq(LG_ID), any(), eq(LayerRole.MEMBER)))
        .thenReturn(List.of(layerResponse(LayerRole.MEMBER)));

    useCase.assignLayers(
        LG_ID, new LayerGroupLayersAssignRequest(Set.of(LAYER_ID), LayerRole.MEMBER), admin);

    verify(layerGroupLayerService).assignLayersToLayerGroup(eq(LG_ID), any(), eq(LayerRole.MEMBER));
  }

  @Test
  void assignLayers_emptyLayerIds_throwsBadRequest() throws Exception {
    OpenCopPrincipal admin = mock(OpenCopPrincipal.class);
    when(admin.isGlobalAdmin()).thenReturn(true);

    GlobalOpenCopException ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.assignLayers(
                    LG_ID, new LayerGroupLayersAssignRequest(Set.of(), LayerRole.MEMBER), admin));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    verify(layerGroupLayerService, never()).assignLayersToLayerGroup(any(), any(), any());
  }

  @Test
  void assignLayers_nullLayerId_throwsBadRequest() throws Exception {
    OpenCopPrincipal admin = mock(OpenCopPrincipal.class);
    when(admin.isGlobalAdmin()).thenReturn(true);

    GlobalOpenCopException ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.assignLayers(
                    LG_ID,
                    new LayerGroupLayersAssignRequest(
                        new HashSet<>(Arrays.asList(LAYER_ID, null)), LayerRole.MEMBER),
                    admin));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    verify(layerGroupLayerService, never()).assignLayersToLayerGroup(any(), any(), any());
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  /** The common case: a PRIVATE group, which accepts a layer of any scope. */
  private void privateGroup() throws Exception {
    when(layerGroupService.getGroup(LG_ID))
        .thenReturn(
            new LayerGroupResponse(
                LG_ID, "group", null, LayerGroupAccessScope.PRIVATE, Instant.EPOCH));
  }

  /** Non-admin assignment reads each layer's own scope, so these paths need a stubbed layer. */
  private void stubLayer(UUID layerId, LayerAccessScope scope) throws Exception {
    when(layersRepository.get(layerId))
        .thenReturn(
            new Layer.Builder().withId(layerId).withName("layer").withAccessScope(scope).build());
  }

  private static OpenCopPrincipal nonAdmin(UUID userId) {
    OpenCopPrincipal p = mock(OpenCopPrincipal.class);
    when(p.isGlobalAdmin()).thenReturn(false);
    when(p.internalUserId()).thenReturn(userId);
    return p;
  }

  private static LayerGroupLayerResponse layerResponse(LayerRole role) {
    return new LayerGroupLayerResponse(LG_ID, "group", LAYER_ID, "layer", role, Instant.EPOCH);
  }
}
