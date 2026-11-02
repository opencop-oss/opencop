// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExportAccessPolicyTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449001");
  private static final UUID SUBJECT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449002");
  private static final UUID SECOND_SUBJECT_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655449003");

  private UserSpecificManager userManager;
  private ExportAccessPolicy policy;

  @BeforeEach
  void setUp() {
    userManager = mock(UserSpecificManager.class);
    policy = new ExportAccessPolicy(userManager);
  }

  @Test
  void assertCanExportLayer_whenLayerAdmin_passes() {
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of(layerRole(LayerRole.ADMIN)));

    assertDoesNotThrow(() -> policy.assertCanExportLayer(principal(false), SUBJECT_ID));
  }

  @Test
  void assertCanExportLayer_whenLayerMember_failsForbidden() {
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of(layerRole(LayerRole.MEMBER)));

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertCanExportLayer(principal(false), SUBJECT_ID));

    assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
  }

  @Test
  void assertCanExportSource_whenSourceAdmin_passes() {
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of(sourceRole(ServerRole.ADMIN)));

    assertDoesNotThrow(() -> policy.assertCanExportSource(principal(false), SUBJECT_ID));
  }

  @Test
  void assertCanExportSource_whenSourceViewer_failsForbidden() {
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of(sourceRole(ServerRole.VIEWER)));

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertCanExportSource(principal(false), SUBJECT_ID));

    assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
  }

  @Test
  void assertCanExportSource_whenGlobalAdmin_skipsLookup() {
    assertDoesNotThrow(() -> policy.assertCanExportSource(principal(true), SUBJECT_ID));

    verify(userManager, never()).getUserServers(USER_ID);
  }

  @Test
  void assertCanExportLayersAndSources_whenAllRequestedIdsAreAdmin_passes() {
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                layerRole(SUBJECT_ID, LayerRole.ADMIN),
                layerRole(SECOND_SUBJECT_ID, LayerRole.ADMIN)));
    when(userManager.getUserServers(USER_ID))
        .thenReturn(
            List.of(
                sourceRole(SUBJECT_ID, ServerRole.ADMIN),
                sourceRole(SECOND_SUBJECT_ID, ServerRole.ADMIN)));

    assertDoesNotThrow(
        () ->
            policy.assertCanExportLayers(principal(false), List.of(SUBJECT_ID, SECOND_SUBJECT_ID)));
    assertDoesNotThrow(
        () ->
            policy.assertCanExportSources(
                principal(false), List.of(SUBJECT_ID, SECOND_SUBJECT_ID)));
  }

  @Test
  void assertCanExportLayersAndSources_whenAnyRequestedIdIsMissingOrViewer_failsForbidden() {
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(List.of(layerRole(SUBJECT_ID, LayerRole.ADMIN)));
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(sourceRole(SUBJECT_ID, ServerRole.VIEWER)));

    var missingLayer =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.assertCanExportLayers(
                    principal(false), List.of(SUBJECT_ID, SECOND_SUBJECT_ID)));
    var viewerSource =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertCanExportSources(principal(false), List.of(SUBJECT_ID)));

    assertEquals(HttpStatus.FORBIDDEN, missingLayer.getHttpStatus());
    assertEquals(HttpStatus.FORBIDDEN, viewerSource.getHttpStatus());
  }

  private static LayerInfo layerRole(LayerRole role) {
    return layerRole(SUBJECT_ID, role);
  }

  private static LayerInfo layerRole(UUID layerId, LayerRole role) {
    return new LayerInfo(layerId, "layer", role, LayerAccessScope.PRIVATE, Instant.now(), true);
  }

  private static SourceRole sourceRole(ServerRole role) {
    return sourceRole(SUBJECT_ID, role);
  }

  private static SourceRole sourceRole(UUID sourceId, ServerRole role) {
    return new SourceRole(sourceId, role, null);
  }

  private static OpenCopPrincipal principal(boolean globalAdmin) {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(
        USER_ID, globalAdmin);
  }
}
