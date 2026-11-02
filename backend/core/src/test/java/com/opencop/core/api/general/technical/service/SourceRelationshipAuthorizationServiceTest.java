// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.model.iam.source.AccessVia;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.model.iam.source.UpdateSourcePermissionsRequest;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SourceRelationshipAuthorizationServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448001");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448002");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448003");
  private static final UUID OTHER_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655448004");

  private UserSpecificManager userManager;
  private SourceRelationshipAuthorizationService service;
  private OpenCopPrincipal principal;

  @BeforeEach
  void setUp() {
    userManager = mock(UserSpecificManager.class);
    service = new SourceRelationshipAuthorizationService(userManager);
    principal = principal(false);
    when(userManager.getUserLayers(USER_ID, RecordVisibility.ALL)).thenReturn(List.of());
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
  }

  @Test
  void add_globalAdminBypassesResourceLookups() {
    var admin = principal(true);

    assertDoesNotThrow(
        () ->
            service.assertCanAdd(admin, SOURCE_ID, request(Set.of(), Set.of(), Set.of(LAYER_ID))));

    verify(userManager, never()).getUserLayers(USER_ID, RecordVisibility.ALL);
    verify(userManager, never()).getUserServers(USER_ID);
  }

  @Test
  void add_layerAdminMayAttachSourceAsAdminWithoutSourceAccess() {
    when(userManager.getUserLayers(USER_ID, RecordVisibility.ALL))
        .thenReturn(List.of(layer(LAYER_ID, LayerRole.ADMIN)));

    assertDoesNotThrow(
        () ->
            service.assertCanAdd(
                principal, SOURCE_ID, request(Set.of(), Set.of(LAYER_ID), Set.of())));
  }

  @Test
  void add_layerAdminMayNotBootstrapSourceOwnership() {
    when(userManager.getUserLayers(USER_ID, RecordVisibility.ALL))
        .thenReturn(List.of(layer(LAYER_ID, LayerRole.ADMIN)));

    assertThrows(
        GlobalOpenCopException.class,
        () ->
            service.assertCanAdd(
                principal, SOURCE_ID, request(Set.of(), Set.of(), Set.of(LAYER_ID))));
  }

  @Test
  void add_ownerBucketIsDeniedEvenWhenManagedLayerAlsoAppearsInAdminBucket() {
    when(userManager.getUserLayers(USER_ID, RecordVisibility.ALL))
        .thenReturn(List.of(layer(LAYER_ID, LayerRole.ADMIN)));

    assertThrows(
        GlobalOpenCopException.class,
        () ->
            service.assertCanAdd(
                principal, SOURCE_ID, request(Set.of(), Set.of(LAYER_ID), Set.of(LAYER_ID))));
  }

  @Test
  void add_effectiveSourceOwnerMayAttachToMemberLayer() {
    when(userManager.getUserLayers(USER_ID, RecordVisibility.ALL))
        .thenReturn(List.of(layer(LAYER_ID, LayerRole.MEMBER)));
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.OWNER, AccessVia.INDIRECT)));

    assertDoesNotThrow(
        () ->
            service.assertCanAdd(
                principal, SOURCE_ID, request(Set.of(), Set.of(), Set.of(LAYER_ID))));
  }

  @Test
  void add_effectiveSourceOwnerMayNotAttachToUnrelatedLayer() {
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.OWNER, AccessVia.DIRECT)));

    assertThrows(
        GlobalOpenCopException.class,
        () ->
            service.assertCanAdd(
                principal, SOURCE_ID, request(Set.of(), Set.of(), Set.of(LAYER_ID))));
  }

  @Test
  void add_rejectsBatchWhenAnyLayerIsOutsideCapabilityEnvelope() {
    when(userManager.getUserLayers(USER_ID, RecordVisibility.ALL))
        .thenReturn(List.of(layer(LAYER_ID, LayerRole.ADMIN)));

    assertThrows(
        GlobalOpenCopException.class,
        () ->
            service.assertCanAdd(
                principal,
                SOURCE_ID,
                request(Set.of(), Set.of(LAYER_ID, OTHER_LAYER_ID), Set.of())));
  }

  @Test
  void remove_effectiveSourceOwnerMayRemoveAnyRelationshipForSource() {
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.OWNER, AccessVia.INDIRECT)));

    assertDoesNotThrow(() -> service.assertCanRemove(principal, SOURCE_ID, Set.of(LAYER_ID)));
  }

  @Test
  void remove_requiresManagementRoleOnEveryLayerWhenCallerIsNotSourceOwner() {
    when(userManager.getUserLayers(USER_ID, RecordVisibility.ALL))
        .thenReturn(List.of(layer(LAYER_ID, LayerRole.OWNER)));

    assertThrows(
        GlobalOpenCopException.class,
        () -> service.assertCanRemove(principal, SOURCE_ID, Set.of(LAYER_ID, OTHER_LAYER_ID)));
  }

  @Test
  void manageSource_effectiveOwnerIsAllowed() {
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.OWNER, AccessVia.INDIRECT)));

    assertDoesNotThrow(() -> service.assertCanManageSource(principal, SOURCE_ID));
  }

  @Test
  void manageSource_nonOwnerIsDenied() {
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.ADMIN, AccessVia.DIRECT)));

    assertThrows(
        GlobalOpenCopException.class, () -> service.assertCanManageSource(principal, SOURCE_ID));
  }

  private static LayerInfo layer(UUID layerId, LayerRole role) {
    return new LayerInfo(layerId, "Layer", role, LayerAccessScope.PRIVATE, null, true);
  }

  private static UpdateSourcePermissionsRequest request(
      Set<UUID> viewers, Set<UUID> admins, Set<UUID> owners) {
    return new UpdateSourcePermissionsRequest(viewers, admins, owners);
  }

  private static OpenCopPrincipal principal(boolean globalAdmin) {
    var principal = mock(OpenCopPrincipal.class);
    when(principal.internalUserId()).thenReturn(USER_ID);
    when(principal.isGlobalAdmin()).thenReturn(globalAdmin);
    return principal;
  }
}
