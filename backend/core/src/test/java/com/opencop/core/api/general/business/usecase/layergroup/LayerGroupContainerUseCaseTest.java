// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layergroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupLayerService;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupService;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupUserGroupService;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupCreateRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

class LayerGroupContainerUseCaseTest {

  private static final UUID LG_ID = UUID.fromString("550e8400-e29b-41d4-a716-000000000020");

  private LayerGroupAuthorization auth;
  private LayerGroupService layerGroupService;
  private LayerGroupLayerService layerGroupLayerService;
  private LayerGroupContainerUseCase useCase;

  @BeforeEach
  void setUp() {
    auth = mock(LayerGroupAuthorization.class);
    layerGroupService = mock(LayerGroupService.class);
    layerGroupLayerService = mock(LayerGroupLayerService.class);
    useCase =
        new LayerGroupContainerUseCase(
            auth,
            layerGroupService,
            layerGroupLayerService,
            mock(LayerGroupUserGroupService.class),
            mock(AuditRecorder.class),
            new LayerGroupAuditProjection());
    when(auth.getEffectiveContainerRole(any(), any()))
        .thenReturn(Optional.of(LayerGroupRole.OWNER));
  }

  @Test
  void create_assignsCallerAsOwner() throws Exception {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.internalUserId()).thenReturn(UUID.randomUUID());
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000099");
    when(principal.internalUserId()).thenReturn(userId);
    when(principal.isGlobalAdmin()).thenReturn(false);
    LayerGroupCreateRequest request = new LayerGroupCreateRequest("New Group", null, null);
    LayerGroupResponse response =
        new LayerGroupResponse(LG_ID, "New Group", null, null, Instant.now());
    when(layerGroupService.createGroup(request)).thenReturn(response);

    LayerGroupResponse result = useCase.create(request, principal);

    assertThat(result).isEqualTo(response);
    verify(layerGroupService).createGroup(request);
    verify(layerGroupService)
        .patchMembers(
            eq(LG_ID),
            argThat(
                patch ->
                    patch
                            .add()
                            .equals(
                                Set.of(
                                    new LayerGroupMembersPatchRequest.MemberAdd(
                                        userId, LayerGroupRole.OWNER)))
                        && patch.remove().isEmpty()),
            isNull());
  }

  @Test
  void create_globalAdmin_globalScope_succeeds() throws Exception {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.internalUserId()).thenReturn(UUID.randomUUID());
    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    when(principal.internalUserId()).thenReturn(userId);
    when(principal.isGlobalAdmin()).thenReturn(true);
    LayerGroupCreateRequest request =
        new LayerGroupCreateRequest("GlobalGroup", null, LayerGroupAccessScope.GLOBAL);
    LayerGroupResponse response =
        new LayerGroupResponse(
            LG_ID, "GlobalGroup", null, LayerGroupAccessScope.GLOBAL, Instant.now());
    when(layerGroupService.createGroup(request)).thenReturn(response);

    LayerGroupResponse result = useCase.create(request, principal);

    assertThat(result.accessScope()).isEqualTo(LayerGroupAccessScope.GLOBAL);
    verify(layerGroupService).createGroup(request);
  }

  @Test
  void create_nonAdmin_globalScope_throwsForbidden() throws Exception {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.internalUserId()).thenReturn(UUID.randomUUID());
    when(principal.isGlobalAdmin()).thenReturn(false);
    LayerGroupCreateRequest request =
        new LayerGroupCreateRequest("GlobalGroup", null, LayerGroupAccessScope.GLOBAL);

    GlobalOpenCopException ex =
        assertThrows(GlobalOpenCopException.class, () -> useCase.create(request, principal));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(ex.getMessage()).contains("Only admin users can change layer group access scope");
    verify(layerGroupService, never()).createGroup(any());
  }

  @Test
  void patch_adminFlipsPrivateToGlobal_withPrivateLayers_throwsConflict() throws Exception {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.internalUserId()).thenReturn(UUID.randomUUID());
    when(principal.isGlobalAdmin()).thenReturn(true);
    LayerGroupPatchRequest request =
        new LayerGroupPatchRequest(null, null, LayerGroupAccessScope.GLOBAL);
    LayerGroupResponse existing =
        new LayerGroupResponse(LG_ID, "Group", null, LayerGroupAccessScope.PRIVATE, Instant.now());
    when(layerGroupService.getGroup(LG_ID)).thenReturn(existing);
    when(layerGroupLayerService.countNonGlobalLayers(LG_ID)).thenReturn(1L);

    GlobalOpenCopException ex =
        assertThrows(GlobalOpenCopException.class, () -> useCase.patch(LG_ID, request, principal));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessage()).contains("non-global layers");
    verify(layerGroupService, never()).patchGroup(any(), any());
  }

  @Test
  void patch_adminFlipsPrivateToGlobal_withNoPrivateLayers_succeeds() throws Exception {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.internalUserId()).thenReturn(UUID.randomUUID());
    when(principal.isGlobalAdmin()).thenReturn(true);
    LayerGroupPatchRequest request =
        new LayerGroupPatchRequest(null, null, LayerGroupAccessScope.GLOBAL);
    LayerGroupResponse existing =
        new LayerGroupResponse(LG_ID, "Group", null, LayerGroupAccessScope.PRIVATE, Instant.now());
    LayerGroupResponse updated =
        new LayerGroupResponse(LG_ID, "Group", null, LayerGroupAccessScope.GLOBAL, Instant.now());
    when(layerGroupService.getGroup(LG_ID)).thenReturn(existing);
    when(layerGroupLayerService.countNonGlobalLayers(LG_ID)).thenReturn(0L);
    when(layerGroupService.patchGroup(LG_ID, request)).thenReturn(updated);

    LayerGroupResponse result = useCase.patch(LG_ID, request, principal);

    assertThat(result.accessScope()).isEqualTo(LayerGroupAccessScope.GLOBAL);
    verify(layerGroupService).patchGroup(LG_ID, request);
  }

  @Test
  void patch_nonAdminWithUnchangedAccessScope_succeeds() throws Exception {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.internalUserId()).thenReturn(UUID.randomUUID());
    when(principal.isGlobalAdmin()).thenReturn(false);
    LayerGroupPatchRequest request =
        new LayerGroupPatchRequest("Renamed", null, LayerGroupAccessScope.PRIVATE);
    LayerGroupResponse existing =
        new LayerGroupResponse(LG_ID, "Group", null, LayerGroupAccessScope.PRIVATE, Instant.now());
    LayerGroupResponse updated =
        new LayerGroupResponse(
            LG_ID, "Renamed", null, LayerGroupAccessScope.PRIVATE, Instant.now());
    when(layerGroupService.getGroup(LG_ID)).thenReturn(existing);
    when(layerGroupService.patchGroup(LG_ID, request)).thenReturn(updated);

    LayerGroupResponse result = useCase.patch(LG_ID, request, principal);

    assertThat(result.name()).isEqualTo("Renamed");
    assertThat(result.accessScope()).isEqualTo(LayerGroupAccessScope.PRIVATE);
    verify(layerGroupService).patchGroup(LG_ID, request);
    verify(layerGroupLayerService, never()).countNonGlobalLayers(any());
  }

  @Test
  void patch_nonAdminWithChangedAccessScope_throwsForbidden() throws Exception {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.internalUserId()).thenReturn(UUID.randomUUID());
    when(principal.isGlobalAdmin()).thenReturn(false);
    LayerGroupPatchRequest request =
        new LayerGroupPatchRequest(null, null, LayerGroupAccessScope.GLOBAL);
    LayerGroupResponse existing =
        new LayerGroupResponse(LG_ID, "Group", null, LayerGroupAccessScope.PRIVATE, Instant.now());
    when(layerGroupService.getGroup(LG_ID)).thenReturn(existing);

    GlobalOpenCopException ex =
        assertThrows(GlobalOpenCopException.class, () -> useCase.patch(LG_ID, request, principal));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(ex.getMessage()).contains("Only admin users can change layer group access scope");
    verify(layerGroupService, never()).patchGroup(any(), any());
    verify(layerGroupLayerService, never()).countNonGlobalLayers(any());
  }

  @Test
  void patch_withoutAdminOrOwnerRole_throwsAccessDeniedException() {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.internalUserId()).thenReturn(UUID.randomUUID());
    when(principal.isGlobalAdmin()).thenReturn(false);
    when(auth.getEffectiveContainerRole(LG_ID, principal)).thenReturn(Optional.empty());

    assertThrows(AccessDeniedException.class, () -> useCase.patch(LG_ID, null, principal));
  }

  @Test
  void delete_withOwnerRole_delegatesToService() throws Exception {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.internalUserId()).thenReturn(UUID.randomUUID());
    when(principal.isGlobalAdmin()).thenReturn(false);
    when(auth.getEffectiveContainerRole(LG_ID, principal))
        .thenReturn(Optional.of(LayerGroupRole.OWNER));

    useCase.delete(LG_ID, principal);

    verify(layerGroupService).deleteGroup(LG_ID);
  }

  @Test
  void delete_withoutOwnerRole_throwsAccessDeniedException() {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.internalUserId()).thenReturn(UUID.randomUUID());
    when(principal.isGlobalAdmin()).thenReturn(false);
    when(auth.getEffectiveContainerRole(LG_ID, principal))
        .thenReturn(Optional.of(LayerGroupRole.ADMIN));

    assertThrows(AccessDeniedException.class, () -> useCase.delete(LG_ID, principal));
  }
}
