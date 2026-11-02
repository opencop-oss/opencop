// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class LayerAuthorizationServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442001");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442002");

  private LayerManager<Layer, LayerMember> layerManager;
  private LayerAuthorizationService service;

  @BeforeEach
  void setUp() {
    layerManager = mock(LayerManager.class);
    service = new LayerAuthorizationService(layerManager);
  }

  @Test
  void assertLayerRole_whenGlobalAdmin_allowsWithoutCheckingMembership() {
    assertDoesNotThrow(() -> service.assertLayerRole(LAYER_ID, principal(true), LayerRole.OWNER));

    verify(layerManager, never()).getMemberRole(LAYER_ID, USER_ID);
  }

  @Test
  void assertLayerRole_whenUserHasAllowedRole_allows() {
    when(layerManager.getMemberRole(LAYER_ID, USER_ID)).thenReturn(Optional.of(LayerRole.ADMIN));

    assertDoesNotThrow(
        () ->
            service.assertLayerRole(LAYER_ID, principal(false), LayerRole.OWNER, LayerRole.ADMIN));
  }

  @Test
  void assertLayerRole_whenUserHasDisallowedRole_throwsAccessDenied() {
    when(layerManager.getMemberRole(LAYER_ID, USER_ID)).thenReturn(Optional.of(LayerRole.MEMBER));

    var exception =
        assertThrows(
            AccessDeniedException.class,
            () ->
                service.assertLayerRole(
                    LAYER_ID,
                    principal(false),
                    "custom message",
                    LayerRole.OWNER,
                    LayerRole.ADMIN));

    assertEquals("custom message", exception.getMessage());
  }

  @Test
  void assertLayerRole_whenUserIsNotMember_throwsAccessDenied() {
    when(layerManager.getMemberRole(LAYER_ID, USER_ID)).thenReturn(Optional.empty());

    assertThrows(
        AccessDeniedException.class,
        () ->
            service.assertLayerRole(LAYER_ID, principal(false), LayerRole.OWNER, LayerRole.ADMIN));
  }

  @Test
  void assertLayerMember_acceptsEditorRole() {
    when(layerManager.getMemberRole(LAYER_ID, USER_ID)).thenReturn(Optional.of(LayerRole.EDITOR));

    assertDoesNotThrow(() -> service.assertLayerMember(LAYER_ID, principal(false)));
  }

  @Test
  void assertLayerMember_whenGlobalAdmin_allowsWithoutCheckingMembership() {
    assertDoesNotThrow(() -> service.assertLayerMember(LAYER_ID, principal(true)));

    verify(layerManager, never()).getMemberRole(LAYER_ID, USER_ID);
  }

  private static OpenCopPrincipal principal(boolean admin) {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(USER_ID, admin);
  }
}
