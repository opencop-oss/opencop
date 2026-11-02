// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.datasourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupLayerService;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerAssignRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerResponse;
import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataSourceGroupLayerEmbeddingUseCaseTest {

  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-656655442001");
  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-656655442002");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-656655442003");
  private static final UUID PRIVATE_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-656655442004");
  private static final UUID SECOND_GROUP_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-656655442005");

  private DataSourceGroupLayerService service;
  private EffectiveLayerAccessService effectiveLayerAccess;
  private DataSourceGroupAuthorization dataSourceGroupAuth;
  private ResourceLifecycleGuard lifecycleGuard;
  private DataSourceGroupLayerEmbeddingUseCase useCase;

  @BeforeEach
  void setUp() {
    service = mock(DataSourceGroupLayerService.class);
    effectiveLayerAccess = mock(EffectiveLayerAccessService.class);
    dataSourceGroupAuth = mock(DataSourceGroupAuthorization.class);
    lifecycleGuard = mock(ResourceLifecycleGuard.class);
    useCase =
        new DataSourceGroupLayerEmbeddingUseCase(
            service, effectiveLayerAccess, dataSourceGroupAuth, lifecycleGuard);
  }

  @Test
  void assignDataSourceGroupsToLayer_nullRequest_throwsBadRequest() throws Exception {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> useCase.assignDataSourceGroupsToLayer(LAYER_ID, null, nonAdmin()));

    assertEquals("At least one data source group must be specified", ex.getMessage());
    verify(service, never()).assignDataSourceGroupsToLayer(any(), any());
  }

  @Test
  void assignDataSourceGroupsToLayer_nullGroupSet_throwsBadRequest() throws Exception {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.assignDataSourceGroupsToLayer(
                    LAYER_ID, new DataSourceGroupLayerAssignRequest(null), nonAdmin()));

    assertEquals("At least one data source group must be specified", ex.getMessage());
    verify(service, never()).assignDataSourceGroupsToLayer(any(), any());
  }

  @Test
  void assignDataSourceGroupsToLayer_emptyGroupSet_throwsBadRequest() throws Exception {
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.assignDataSourceGroupsToLayer(
                    LAYER_ID, new DataSourceGroupLayerAssignRequest(Set.of()), nonAdmin()));

    assertEquals("At least one data source group must be specified", ex.getMessage());
    verify(service, never()).assignDataSourceGroupsToLayer(any(), any());
  }

  @Test
  void assignDataSourceGroupsToLayer_nullGroupId_throwsBadRequest() throws Exception {
    Set<UUID> ids = new HashSet<>();
    ids.add(GROUP_ID);
    ids.add(null);

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                useCase.assignDataSourceGroupsToLayer(
                    LAYER_ID, new DataSourceGroupLayerAssignRequest(ids), nonAdmin()));

    assertEquals("Data source group ids must not contain null", ex.getMessage());
    verify(service, never()).assignDataSourceGroupsToLayer(any(), any());
  }

  @Test
  void assignDataSourceGroupsToLayer_requiresLayerAdminOrOwner() throws Exception {
    var principal = nonAdmin();
    when(effectiveLayerAccess.getEffectiveRole(LAYER_ID, USER_ID))
        .thenReturn(Optional.of(LayerRole.EDITOR));

    var ex = assertThrows(AuthzAccessDeniedException.class, () -> assignSingleGroup(principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_SOURCE_OR_LAYER_ROLE, ex.reasonCode());
    verify(service, never()).assignDataSourceGroupsToLayer(any(), any());
  }

  @Test
  void assignDataSourceGroupsToLayer_layerMemberRoleFails() throws Exception {
    var principal = nonAdmin();
    when(effectiveLayerAccess.getEffectiveRole(LAYER_ID, USER_ID))
        .thenReturn(Optional.of(LayerRole.MEMBER));

    var ex = assertThrows(AuthzAccessDeniedException.class, () -> assignSingleGroup(principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_SOURCE_OR_LAYER_ROLE, ex.reasonCode());
    verify(service, never()).assignDataSourceGroupsToLayer(any(), any());
  }

  @Test
  void assignDataSourceGroupsToLayer_missingLayerRoleUsesAuditReason() throws Exception {
    var principal = nonAdmin();
    when(effectiveLayerAccess.getEffectiveRole(LAYER_ID, USER_ID)).thenReturn(Optional.empty());

    var ex = assertThrows(AuthzAccessDeniedException.class, () -> assignSingleGroup(principal));

    assertEquals(AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE, ex.reasonCode());
    verify(service, never()).assignDataSourceGroupsToLayer(any(), any());
  }

  @Test
  void assignDataSourceGroupsToLayer_requiresDataSourceGroupMaintainer() throws Exception {
    var principal = nonAdmin();
    allowLayer(principal, LayerRole.ADMIN);
    allowGroup(principal, GROUP_ID, DataSourceGroupRole.MEMBER);

    var ex = assertThrows(AuthzAccessDeniedException.class, () -> assignSingleGroup(principal));

    assertEquals(AuthzReasonCode.INSUFFICIENT_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(service, never()).assignDataSourceGroupsToLayer(any(), any());
  }

  @Test
  void assignDataSourceGroupsToLayer_layerOwnerAndDataSourceGroupOwnerDelegates() throws Exception {
    assertAssignAllowed(LayerRole.OWNER, DataSourceGroupRole.OWNER);
  }

  @Test
  void assignDataSourceGroupsToLayer_layerOwnerAndDataSourceGroupAdminDelegates() throws Exception {
    assertAssignAllowed(LayerRole.OWNER, DataSourceGroupRole.ADMIN);
  }

  @Test
  void assignDataSourceGroupsToLayer_layerOwnerAndDataSourceGroupMaintainerDelegates()
      throws Exception {
    assertAssignAllowed(LayerRole.OWNER, DataSourceGroupRole.MAINTAINER);
  }

  @Test
  void assignDataSourceGroupsToLayer_effectiveTransitiveMaintainerRoleDelegates() throws Exception {
    assertAssignAllowed(LayerRole.ADMIN, DataSourceGroupRole.MAINTAINER);
  }

  @Test
  void assignDataSourceGroupsToLayer_layerAdminAndDataSourceGroupOwnerDelegates() throws Exception {
    assertAssignAllowed(LayerRole.ADMIN, DataSourceGroupRole.OWNER);
  }

  @Test
  void assignDataSourceGroupsToLayer_layerAdminAndDataSourceGroupAdminDelegates() throws Exception {
    assertAssignAllowed(LayerRole.ADMIN, DataSourceGroupRole.ADMIN);
  }

  @Test
  void assignDataSourceGroupsToLayer_delegatesWhenBothSidesAuthorized() throws Exception {
    assertAssignAllowed(LayerRole.ADMIN, DataSourceGroupRole.MAINTAINER);
    verify(lifecycleGuard).assertLayerActive(LAYER_ID, "embed data source group in layer");
  }

  @Test
  void assignDataSourceGroupsToLayer_globalAdminDelegatesWithoutLayerOrGroupRoles()
      throws Exception {
    var principal = globalAdmin();

    assignSingleGroup(principal);

    verify(effectiveLayerAccess, never()).getEffectiveRole(any(), any());
    verify(dataSourceGroupAuth).getEffectiveContainerRole(GROUP_ID, principal);
    verify(service).assignDataSourceGroupsToLayer(LAYER_ID, Set.of(GROUP_ID));
  }

  @Test
  void assignDataSourceGroupsToLayer_missingDataSourceGroupRoleFails() throws Exception {
    var principal = nonAdmin();
    allowLayer(principal, LayerRole.ADMIN);
    when(dataSourceGroupAuth.getEffectiveContainerRole(GROUP_ID, principal))
        .thenReturn(Optional.empty());

    var ex = assertThrows(AuthzAccessDeniedException.class, () -> assignSingleGroup(principal));

    assertEquals(AuthzReasonCode.MISSING_DATA_SOURCE_GROUP_ROLE, ex.reasonCode());
    verify(service, never()).assignDataSourceGroupsToLayer(any(), any());
  }

  @Test
  void assignDataSourceGroupsToLayer_secondUnauthorizedGroupFailsBeforeServiceCall()
      throws Exception {
    var principal = nonAdmin();
    allowLayer(principal, LayerRole.ADMIN);
    allowGroup(principal, GROUP_ID, DataSourceGroupRole.OWNER);
    allowGroup(principal, SECOND_GROUP_ID, DataSourceGroupRole.MEMBER);

    assertThrows(
        AuthzAccessDeniedException.class,
        () ->
            useCase.assignDataSourceGroupsToLayer(
                LAYER_ID,
                new DataSourceGroupLayerAssignRequest(Set.of(GROUP_ID, SECOND_GROUP_ID)),
                principal));

    verify(service, never()).assignDataSourceGroupsToLayer(any(), any());
  }

  @Test
  void assignDataSourceGroupsToLayer_archivedLayerCheckPreventsAuthorizationAndServiceCall()
      throws Exception {
    var principal = nonAdmin();
    doThrow(
            new GlobalOpenCopException(
                "Layer is archived", org.springframework.http.HttpStatus.GONE))
        .when(lifecycleGuard)
        .assertLayerActive(LAYER_ID, "embed data source group in layer");

    assertThrows(GlobalOpenCopException.class, () -> assignSingleGroup(principal));

    verify(effectiveLayerAccess, never()).getEffectiveRole(any(), any());
    verify(service, never()).assignDataSourceGroupsToLayer(any(), any());
  }

  @Test
  void assignDataSourceGroupsToLayer_serviceFailureMapsToBadRequest() throws Exception {
    var principal = nonAdmin();
    allowLayer(principal, LayerRole.ADMIN);
    allowGroup(principal, GROUP_ID, DataSourceGroupRole.OWNER);
    when(service.assignDataSourceGroupsToLayer(LAYER_ID, Set.of(GROUP_ID)))
        .thenThrow(new PersistenceException("Could not find data source group: " + GROUP_ID));

    var ex = assertThrows(GlobalOpenCopException.class, () -> assignSingleGroup(principal));

    assertEquals("Could not find data source group: " + GROUP_ID, ex.getMessage());
    assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getHttpStatus());
  }

  @Test
  void removeDataSourceGroupFromLayer_requiresLayerAdminOrOwner() throws Exception {
    var principal = nonAdmin();
    when(effectiveLayerAccess.getEffectiveRole(LAYER_ID, USER_ID))
        .thenReturn(Optional.of(LayerRole.EDITOR));

    assertThrows(
        AuthzAccessDeniedException.class,
        () -> useCase.removeDataSourceGroupFromLayer(LAYER_ID, GROUP_ID, principal));

    verify(service, never()).removeDataSourceGroupFromLayer(any(), any());
  }

  @Test
  void removeDataSourceGroupFromLayer_requiresDataSourceGroupMaintainer() throws Exception {
    var principal = nonAdmin();
    allowLayer(principal, LayerRole.ADMIN);
    allowGroup(principal, GROUP_ID, DataSourceGroupRole.MEMBER);

    assertThrows(
        AuthzAccessDeniedException.class,
        () -> useCase.removeDataSourceGroupFromLayer(LAYER_ID, GROUP_ID, principal));

    verify(service, never()).removeDataSourceGroupFromLayer(any(), any());
  }

  @Test
  void removeDataSourceGroupFromLayer_delegatesWhenBothSidesAuthorized() throws Exception {
    var principal = nonAdmin();
    allowLayer(principal, LayerRole.OWNER);
    allowGroup(principal, GROUP_ID, DataSourceGroupRole.ADMIN);

    useCase.removeDataSourceGroupFromLayer(LAYER_ID, GROUP_ID, principal);

    verify(lifecycleGuard).assertLayerActive(LAYER_ID, "remove data source group from layer");
    verify(service).removeDataSourceGroupFromLayer(LAYER_ID, GROUP_ID);
  }

  @Test
  void getLayersForDataSourceGroup_filtersLayersWithoutEffectiveLayerAccess() throws Exception {
    var principal = nonAdmin();
    var visibleLayer = layerResponse(LAYER_ID, "Visible Ops");
    var privateLayer = layerResponse(PRIVATE_LAYER_ID, "Private Ops");
    allowGroup(principal, GROUP_ID, DataSourceGroupRole.MEMBER);
    when(service.getLayersForDataSourceGroup(GROUP_ID))
        .thenReturn(List.of(visibleLayer, privateLayer));
    when(effectiveLayerAccess.getEffectiveRole(LAYER_ID, USER_ID))
        .thenReturn(Optional.of(LayerRole.MEMBER));
    when(effectiveLayerAccess.getEffectiveRole(PRIVATE_LAYER_ID, USER_ID))
        .thenReturn(Optional.empty());

    var layers = useCase.getLayersForDataSourceGroup(GROUP_ID, principal);

    assertEquals(List.of(visibleLayer), layers);
  }

  @Test
  void getLayersForDataSourceGroup_globalAdminSeesAllEmbeddingLayers() throws Exception {
    var principal = globalAdmin();
    var visibleLayer = layerResponse(LAYER_ID, "Visible Ops");
    var privateLayer = layerResponse(PRIVATE_LAYER_ID, "Private Ops");
    when(service.getLayersForDataSourceGroup(GROUP_ID))
        .thenReturn(List.of(visibleLayer, privateLayer));

    var layers = useCase.getLayersForDataSourceGroup(GROUP_ID, principal);

    assertEquals(List.of(visibleLayer, privateLayer), layers);
    verify(effectiveLayerAccess, never()).getEffectiveRole(any(), any());
  }

  private void assertAssignAllowed(LayerRole layerRole, DataSourceGroupRole groupRole)
      throws PersistenceException {
    var principal = nonAdmin();
    allowLayer(principal, layerRole);
    allowGroup(principal, GROUP_ID, groupRole);

    assignSingleGroup(principal);

    verify(service).assignDataSourceGroupsToLayer(LAYER_ID, Set.of(GROUP_ID));
  }

  private void assignSingleGroup(OpenCopPrincipal principal) throws PersistenceException {
    useCase.assignDataSourceGroupsToLayer(
        LAYER_ID, new DataSourceGroupLayerAssignRequest(Set.of(GROUP_ID)), principal);
  }

  private void allowLayer(OpenCopPrincipal principal, LayerRole role) {
    when(effectiveLayerAccess.getEffectiveRole(LAYER_ID, principal.internalUserId()))
        .thenReturn(Optional.of(role));
  }

  private void allowGroup(
      OpenCopPrincipal principal, UUID dataSourceGroupId, DataSourceGroupRole role) {
    when(dataSourceGroupAuth.getEffectiveContainerRole(dataSourceGroupId, principal))
        .thenReturn(Optional.of(role));
  }

  private static DataSourceGroupLayerResponse layerResponse(UUID layerId, String layerName) {
    return new DataSourceGroupLayerResponse(
        GROUP_ID, "Operations Sources", layerId, layerName, Instant.parse("2024-01-01T00:00:00Z"));
  }

  private static OpenCopPrincipal nonAdmin() {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.isGlobalAdmin()).thenReturn(false);
    when(principal.internalUserId()).thenReturn(USER_ID);
    return principal;
  }

  private static OpenCopPrincipal globalAdmin() {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.isGlobalAdmin()).thenReturn(true);
    when(principal.internalUserId()).thenReturn(USER_ID);
    return principal;
  }
}
