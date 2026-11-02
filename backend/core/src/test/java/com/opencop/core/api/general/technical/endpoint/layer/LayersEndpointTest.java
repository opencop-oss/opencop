// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.general.business.usecase.layer.ArchiveLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.CloneLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.CreateLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.LayerAuditProjection;
import com.opencop.core.api.general.business.usecase.layer.LayerMembersUsecase;
import com.opencop.core.api.general.business.usecase.layer.MergeLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.PatchLayerUsecase;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.technical.gateway.db.group.AppGroupLayerService;
import com.opencop.core.api.general.technical.gateway.db.layergroup.AppLayerGroupLayerService;
import com.opencop.core.api.general.technical.gateway.notification.NotificationWorker;
import com.opencop.core.api.general.technical.model.group.GroupLayerResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayerResponse;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.general.technical.service.LayerGroupAuthorizationService;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.model.iam.group.GroupManagementSource;
import com.opencop.core.sdk.model.iam.layer.UpdateLayerRoleRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LayersEndpointTest {

  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448001");
  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448002");
  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448003");
  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448004");
  private static final UUID OTHER_GROUP_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655448005");

  private LayerManager<Layer, LayerMember> layerManager;
  private LayerAuthorizationService layerAuth;
  private AppGroupLayerService groupLayerService;
  private AppLayerGroupLayerService layerGroupLayerService;
  private LayerGroupAuthorizationService layerGroupAuth;
  private AuditRecorder auditRecorder;
  private LayerAuditProjection layerAuditProjection;
  private LayersEndpoint endpoint;

  @BeforeEach
  void setUp() {
    auditRecorder = mock(AuditRecorder.class);
    layerAuditProjection = new LayerAuditProjection();
    layerManager = mock(LayerManager.class);
    layerAuth = mock(LayerAuthorizationService.class);
    groupLayerService = mock(AppGroupLayerService.class);
    layerGroupLayerService = mock(AppLayerGroupLayerService.class);
    layerGroupAuth = mock(LayerGroupAuthorizationService.class);
    endpoint =
        new LayersEndpoint(
            layerManager,
            layerAuth,
            mock(ArchiveLayerUsecase.class),
            mock(CloneLayerUsecase.class),
            mock(MergeLayerUsecase.class),
            mock(PatchLayerUsecase.class),
            new CreateLayerUsecase(layerManager, auditRecorder, layerAuditProjection),
            // Real, not mocked: the member-endpoint authorization these tests assert on now lives
            // in this use case, so a mock would silently pass every auth case.
            new LayerMembersUsecase(layerManager, layerAuth, auditRecorder, layerAuditProjection),
            mock(DataSourceRuntimeService.class),
            mock(NotificationWorker.class),
            groupLayerService,
            layerGroupLayerService,
            layerGroupAuth);
  }

  @Test
  void getLayer_whenLayerIsPrivate_requiresLayerMember() {
    var authentication = TestPrincipals.mockPrincipal(ADMIN_ID);
    var layer =
        new Layer.Builder().withId(LAYER_ID).withAccessScope(LayerAccessScope.PRIVATE).build();
    when(layerManager.get(LAYER_ID)).thenReturn(layer);

    var response = endpoint.getLayer(LAYER_ID, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(layer, response.getBody());
    verify(layerAuth).assertLayerMember(LAYER_ID, authentication);
  }

  @Test
  void getLayer_whenLayerIsGlobal_skipsLayerMemberCheck() {
    var authentication = TestPrincipals.mockPrincipal(ADMIN_ID);
    var layer =
        new Layer.Builder().withId(LAYER_ID).withAccessScope(LayerAccessScope.GLOBAL).build();
    when(layerManager.get(LAYER_ID)).thenReturn(layer);

    var response = endpoint.getLayer(LAYER_ID, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(layer, response.getBody());
    verify(layerAuth, never()).assertLayerMember(LAYER_ID, authentication);
  }

  @Test
  void getGroupsForLayer_delegatesToServiceAndReturnsResult() throws Exception {
    var authentication = TestPrincipals.mockPrincipal(ADMIN_ID);
    var expected =
        List.of(
            new GroupLayerResponse(
                GROUP_ID,
                "Alpha",
                GroupManagementSource.OPENCOP,
                LAYER_ID,
                "Ops",
                LayerRole.MEMBER,
                Instant.now()));
    when(groupLayerService.getGroupsForLayer(LAYER_ID)).thenReturn(expected);

    var response = endpoint.getGroupsForLayer(LAYER_ID, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(expected, response.getBody());
  }

  @Test
  void getGroupsForLayer_whenServiceThrowsPersistenceException_returnsEmptyList() throws Exception {
    var authentication = TestPrincipals.mockPrincipal(ADMIN_ID);
    when(groupLayerService.getGroupsForLayer(LAYER_ID))
        .thenThrow(new PersistenceException("layer not found"));

    var response = endpoint.getGroupsForLayer(LAYER_ID, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(List.of(), response.getBody());
  }

  @Test
  void addLayerMembers_requiresOwnerWhenRequestAddsOwner() {
    var authentication = TestPrincipals.mockPrincipal(ADMIN_ID);
    var request = Map.of(OWNER_ID, LayerRole.OWNER);

    endpoint.addLayerMembers(LAYER_ID, request, authentication);

    verify(layerAuth)
        .assertLayerRole(
            eq(LAYER_ID),
            eq(authentication),
            eq("Only layer owners can modify the owner list"),
            eq(LayerRole.OWNER));
    verify(layerAuth, never())
        .assertLayerRole(
            eq(LAYER_ID), eq(authentication), eq(LayerRole.OWNER), eq(LayerRole.ADMIN));
    verify(layerManager).addLayerMembers(LAYER_ID, request);
  }

  @Test
  void addLayerMembers_allowsOwnerOrAdminWhenRequestAddsNonOwner() {
    var authentication = TestPrincipals.mockPrincipal(ADMIN_ID);
    var request = Map.of(OWNER_ID, LayerRole.MEMBER);

    endpoint.addLayerMembers(LAYER_ID, request, authentication);

    verify(layerAuth)
        .assertLayerRole(
            eq(LAYER_ID), eq(authentication), eq(LayerRole.OWNER), eq(LayerRole.ADMIN));
    verify(layerManager).addLayerMembers(LAYER_ID, request);
  }

  @Test
  void addLayerMembers_whenRequestIsNull_returnsBadRequest() {
    var authentication = TestPrincipals.mockPrincipal(ADMIN_ID);

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> endpoint.addLayerMembers(LAYER_ID, null, authentication));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    verify(layerAuth, never())
        .assertLayerRole(
            eq(LAYER_ID), eq(authentication), eq(LayerRole.OWNER), eq(LayerRole.ADMIN));
    verify(layerManager, never()).addLayerMembers(eq(LAYER_ID), eq(null));
  }

  @Test
  void getLayerGroupsForLayer_assertsLayerMembershipThenFiltersToViewableGroups() {
    var authentication = TestPrincipals.mockPrincipal(ADMIN_ID);
    var visible =
        new LayerGroupLayerResponse(
            GROUP_ID, "Alpha", LAYER_ID, "Ops", LayerRole.EDITOR, Instant.now());
    when(layerGroupLayerService.getLayerGroupsForLayer(LAYER_ID)).thenReturn(List.of(visible));
    when(layerGroupAuth.viewableLayerGroupIds(authentication)).thenReturn(Set.of(GROUP_ID));

    var response = endpoint.getLayerGroupsForLayer(LAYER_ID, authentication);

    // Layer membership gates the question; group visibility narrows the answer.
    verify(layerAuth).assertLayerMember(LAYER_ID, authentication);
    assertEquals(List.of(visible), response);
  }

  @Test
  void getLayerGroupsForLayer_omitsGroupsTheCallerCannotView() {
    var authentication = TestPrincipals.mockPrincipal(ADMIN_ID);
    var visible =
        new LayerGroupLayerResponse(
            GROUP_ID, "Alpha", LAYER_ID, "Ops", LayerRole.EDITOR, Instant.now());
    var hidden =
        new LayerGroupLayerResponse(
            OTHER_GROUP_ID, "Private Bravo", LAYER_ID, "Ops", LayerRole.MEMBER, Instant.now());
    when(layerGroupLayerService.getLayerGroupsForLayer(LAYER_ID))
        .thenReturn(List.of(visible, hidden));
    when(layerGroupAuth.viewableLayerGroupIds(authentication)).thenReturn(Set.of(GROUP_ID));

    var response = endpoint.getLayerGroupsForLayer(LAYER_ID, authentication);

    // Access to the layer must not leak the name/role/timestamp of a private group attached to it
    // that the caller holds no role on.
    assertEquals(List.of(visible), response);
  }

  @Test
  void getLayerGroupsForLayer_whenGlobalAdmin_skipsFilteringEntirely() {
    var authentication = TestPrincipals.oidcPrincipal(ADMIN_ID, true);
    var all =
        List.of(
            new LayerGroupLayerResponse(
                GROUP_ID, "Alpha", LAYER_ID, "Ops", LayerRole.EDITOR, Instant.now()),
            new LayerGroupLayerResponse(
                OTHER_GROUP_ID, "Private Bravo", LAYER_ID, "Ops", LayerRole.MEMBER, Instant.now()));
    when(layerGroupLayerService.getLayerGroupsForLayer(LAYER_ID)).thenReturn(all);

    var response = endpoint.getLayerGroupsForLayer(LAYER_ID, authentication);

    assertEquals(all, response);
    verify(layerGroupAuth, never()).viewableLayerGroupIds(authentication);
  }

  @Test
  void updateLayerMemberRole_requiresOwnerWhenRequestDemotesOwnerToEditor() {
    var authentication = TestPrincipals.mockPrincipal(ADMIN_ID);
    var request = new UpdateLayerRoleRequest(Set.of(), Set.of(OWNER_ID), Set.of(), Set.of());
    when(layerManager.listLayerMembers(LAYER_ID))
        .thenReturn(List.of(member(OWNER_ID, LayerRole.OWNER), member(ADMIN_ID, LayerRole.ADMIN)));

    endpoint.updateLayerMemberRole(LAYER_ID, request, authentication);

    verify(layerAuth)
        .assertLayerRole(
            eq(LAYER_ID),
            eq(authentication),
            eq("Only layer owners can modify the owner list"),
            eq(LayerRole.OWNER));
    verify(layerAuth, never())
        .assertLayerRole(
            eq(LAYER_ID), eq(authentication), eq(LayerRole.OWNER), eq(LayerRole.ADMIN));
    verify(layerManager).updateLayerMemberRole(LAYER_ID, request);
  }

  private static LayerMember member(UUID userId, LayerRole role) {
    return new LayerMember.Builder().withUserId(userId).withRole(role).build();
  }
}
