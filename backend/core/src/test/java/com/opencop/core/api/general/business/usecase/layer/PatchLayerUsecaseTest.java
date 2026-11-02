// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.iam.layer.LayerPatchRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupLayerRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PatchLayerUsecaseTest {

  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-bb0000000001");
  private static final UUID ACTOR_ID = UUID.fromString("550e8400-e29b-41d4-a716-bb0000000002");

  private LayerManager<Layer, LayerMember> layerManager;
  private LayerAuthorizationService layerAuth;
  private IamLayerGroupLayerRepository layerGroupLayerRepository;
  private AuditRecorder auditRecorder;
  private PatchLayerUsecase useCase;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    layerManager = mock(LayerManager.class);
    layerAuth = mock(LayerAuthorizationService.class);
    layerGroupLayerRepository = mock(IamLayerGroupLayerRepository.class);
    auditRecorder = mock(AuditRecorder.class);
    useCase =
        new PatchLayerUsecase(
            layerManager,
            layerAuth,
            layerGroupLayerRepository,
            auditRecorder,
            new LayerAuditProjection());
  }

  @Test
  void patchLayer_globalToPrivateWhenAssignedToGlobalLayerGroup_throwsConflict() {
    var request = new LayerPatchRequest(null, LayerAccessScope.PRIVATE, null);
    var principal = globalAdmin();
    when(layerManager.get(LAYER_ID)).thenReturn(layer(LayerAccessScope.GLOBAL));
    when(layerGroupLayerRepository.countGlobalLayerGroupsByLayerId(LAYER_ID)).thenReturn(1L);

    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> useCase.patchLayer(LAYER_ID, request, principal));

    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(ex.getMessage()).contains("GLOBAL layer group");
    verify(layerManager, never()).update(LAYER_ID, request, ACTOR_ID);
  }

  @Test
  void patchLayer_globalToPrivateWhenNotAssignedToGlobalLayerGroup_updatesLayer() {
    var request = new LayerPatchRequest(null, LayerAccessScope.PRIVATE, null);
    var principal = globalAdmin();
    when(layerManager.get(LAYER_ID)).thenReturn(layer(LayerAccessScope.GLOBAL));
    when(layerGroupLayerRepository.countGlobalLayerGroupsByLayerId(LAYER_ID)).thenReturn(0L);

    useCase.patchLayer(LAYER_ID, request, principal);

    verify(layerManager).update(LAYER_ID, request, ACTOR_ID);
  }

  @Test
  void patchLayer_locksTheLayerBeforeReadingItsState() {
    var principal = globalAdmin();
    when(layerManager.get(LAYER_ID)).thenReturn(layer(LayerAccessScope.GLOBAL));

    useCase.patchLayer(LAYER_ID, new LayerPatchRequest("Renamed", null, null), principal);

    var order = org.mockito.Mockito.inOrder(layerManager);
    order.verify(layerManager).lock(LAYER_ID);
    order.verify(layerManager).get(LAYER_ID);
    order.verify(layerManager).update(eq(LAYER_ID), any(), eq(ACTOR_ID));
    verify(auditRecorder).record(any());
  }

  @Test
  void patchLayer_whenNothingMoves_recordsNoRow() {
    var principal = globalAdmin();
    when(layerManager.get(LAYER_ID)).thenReturn(layer(LayerAccessScope.GLOBAL));

    useCase.patchLayer(LAYER_ID, new LayerPatchRequest("Layer", null, null), principal);

    verify(auditRecorder, never()).record(any());
    verify(layerManager, never()).update(any(), any(), any());
  }

  private static Layer layer(LayerAccessScope accessScope) {
    return new Layer.Builder()
        .withId(LAYER_ID)
        .withName("Layer")
        .withAccessScope(accessScope)
        .build();
  }

  private static OpenCopPrincipal globalAdmin() {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.isGlobalAdmin()).thenReturn(true);
    when(principal.internalUserId()).thenReturn(ACTOR_ID);
    return principal;
  }
}
