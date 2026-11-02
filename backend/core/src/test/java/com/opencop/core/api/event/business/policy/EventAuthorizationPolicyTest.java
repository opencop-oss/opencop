// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.ports.audit.AuthzAuditReporting;
import com.opencop.core.sdk.ports.audit.AuthzDenialEvent;
import com.opencop.core.sdk.ports.audit.AuthzPermission;
import com.opencop.core.sdk.ports.audit.AuthzPrincipalType;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.audit.AuthzResourceType;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.NotFoundException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class EventAuthorizationPolicyTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String USER_EMAIL = "frank.audit@example.test";
  private static final String OBJECT_ID = UUID.randomUUID().toString();
  private static final UUID SOURCE_ID = UUID.randomUUID();

  private PostGisService postGisService;
  private UserSpecificManager userManager;
  private DataSourceRepository dataSourceRepository;
  private LayerManager<Layer, LayerMember> layerManager;
  private EventAuthorizationPolicy policy;
  private ResourceLifecycleGuard lifecycleGuard;
  private AuthzAuditReporting auditReporter;

  @BeforeEach
  void setUp() {
    postGisService = mock(PostGisService.class);
    userManager = mock(UserSpecificManager.class);
    dataSourceRepository = mock(DataSourceRepository.class);
    layerManager = mock(LayerManager.class);
    lifecycleGuard = new ResourceLifecycleGuard(dataSourceRepository, layerManager);
    auditReporter = mock(AuthzAuditReporting.class);
    policy =
        new EventAuthorizationPolicy(postGisService, userManager, lifecycleGuard, auditReporter);
  }

  @Test
  void assertEditorForActiveResource_activeDataSource_passesWithoutLayerLookup() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.ADMIN, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());
    when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(activeDataSource()));

    assertDoesNotThrow(
        () ->
            policy.assertEditorForActiveResource(
                USER_ID, SOURCE_ID, OBJECT_ID, "mutate event remarks"));
    verify(layerManager, never()).get(SOURCE_ID);
  }

  @Test
  void assertEditorForActiveResource_archivedDataSource_returnsConflict() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.ADMIN, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());
    when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(archivedDataSource()));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.assertEditorForActiveResource(
                    USER_ID, SOURCE_ID, OBJECT_ID, "mutate event remarks"));
    assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
  }

  @Test
  void assertEditorForActiveResource_activeLayer_passes() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    SOURCE_ID,
                    "test",
                    LayerRole.EDITOR,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.empty());
    when(layerManager.get(SOURCE_ID)).thenReturn(new Layer.Builder().withId(SOURCE_ID).build());

    assertDoesNotThrow(
        () ->
            policy.assertEditorForActiveResource(
                principal(), SOURCE_ID, OBJECT_ID, "mutate event attachments"));
  }

  @Test
  void assertEditorForActiveResource_archivedLayer_returnsConflict() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    SOURCE_ID,
                    "test",
                    LayerRole.EDITOR,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.empty());
    when(layerManager.get(SOURCE_ID))
        .thenReturn(new Layer.Builder().withId(SOURCE_ID).withArchivedAt(Instant.now()).build());

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.assertEditorForActiveResource(
                    principal(), SOURCE_ID, OBJECT_ID, "mutate event attachments"));
    assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
  }

  @Test
  void assertEditorForActiveResource_missingResource_returnsNotFound() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.ADMIN, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());
    when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.empty());
    when(layerManager.get(SOURCE_ID))
        .thenThrow(new GlobalOpenCopException("Layer not found", HttpStatus.NOT_FOUND));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.assertEditorForActiveResource(
                    USER_ID, SOURCE_ID, OBJECT_ID, "mutate event remarks"));
    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  @Test
  void assertEditorForActiveResource_unauthorizedReturnsForbiddenBeforeLifecycleCheck()
      throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.assertEditorForActiveResource(
                    USER_ID, SOURCE_ID, OBJECT_ID, "mutate event remarks"));
    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    verify(dataSourceRepository, never()).findById(SOURCE_ID);
  }

  @Test
  void auditedAssertEditorForActiveResource_noRoles_reportsUploadDenialAndSkipsLifecycle()
      throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.assertEditorForActiveResource(
                    principal(), SOURCE_ID, OBJECT_ID, "upload attachment"));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    assertAuditEvent(
        captureAuditEvent(),
        AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
        List.of(AuthzPermission.NONE),
        List.of(AuthzPermission.SERVER_ADMIN, AuthzPermission.LAYER_EDITOR));
    verify(dataSourceRepository, never()).findById(SOURCE_ID);
  }

  @Test
  void auditedAssertMember_noRoles_reportsListDenialWithViewRequirements() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertMember(principal(), SOURCE_ID, OBJECT_ID));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    assertAuditEvent(
        captureAuditEvent(),
        AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
        List.of(AuthzPermission.NONE),
        List.of(AuthzPermission.SERVER_VIEWER, AuthzPermission.LAYER_MEMBER));
  }

  @Test
  void auditedAssertEditor_wrongSourceRole_reportsMissingRole() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(UUID.randomUUID(), ServerRole.ADMIN, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.assertEditorForActiveResource(
                    principal(), SOURCE_ID, OBJECT_ID, "upload attachment"));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    assertAuditEvent(
        captureAuditEvent(),
        AuthzReasonCode.MISSING_SOURCE_OR_LAYER_ROLE,
        List.of(AuthzPermission.NONE),
        List.of(AuthzPermission.SERVER_ADMIN, AuthzPermission.LAYER_EDITOR));
  }

  @Test
  void auditedAssertEditor_serverViewer_reportsInsufficientServerRole() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.VIEWER, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.assertEditorForActiveResource(
                    principal(), SOURCE_ID, OBJECT_ID, "upload attachment"));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    assertAuditEvent(
        captureAuditEvent(),
        AuthzReasonCode.INSUFFICIENT_SOURCE_OR_LAYER_ROLE,
        List.of(AuthzPermission.SERVER_VIEWER),
        List.of(AuthzPermission.SERVER_ADMIN, AuthzPermission.LAYER_EDITOR));
  }

  @Test
  void auditedAssertEditor_layerMember_reportsInsufficientLayerRole() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    SOURCE_ID,
                    "test",
                    LayerRole.MEMBER,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.assertEditorForActiveResource(
                    principal(), SOURCE_ID, OBJECT_ID, "upload attachment"));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    assertAuditEvent(
        captureAuditEvent(),
        AuthzReasonCode.INSUFFICIENT_SOURCE_OR_LAYER_ROLE,
        List.of(AuthzPermission.LAYER_MEMBER),
        List.of(AuthzPermission.SERVER_ADMIN, AuthzPermission.LAYER_EDITOR));
  }

  @Test
  void auditedAssertEditor_mixedInsufficientRoles_reportsServerBeforeLayer() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.VIEWER, null)));
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    SOURCE_ID,
                    "test",
                    LayerRole.MEMBER,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.assertEditorForActiveResource(
                    principal(), SOURCE_ID, OBJECT_ID, "upload attachment"));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    assertAuditEvent(
        captureAuditEvent(),
        AuthzReasonCode.INSUFFICIENT_SOURCE_OR_LAYER_ROLE,
        List.of(AuthzPermission.SERVER_VIEWER, AuthzPermission.LAYER_MEMBER),
        List.of(AuthzPermission.SERVER_ADMIN, AuthzPermission.LAYER_EDITOR));
  }

  @Test
  void auditedAssertMember_serverViewer_passesWithoutAudit() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.VIEWER, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    assertDoesNotThrow(() -> policy.assertMember(principal(), SOURCE_ID, OBJECT_ID));

    verifyNoInteractions(auditReporter);
  }

  @Test
  void auditedAssertMember_layerMember_passesWithoutAudit() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    SOURCE_ID,
                    "test",
                    LayerRole.MEMBER,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));

    assertDoesNotThrow(() -> policy.assertMember(principal(), SOURCE_ID, OBJECT_ID));

    verifyNoInteractions(auditReporter);
  }

  @Test
  void auditedAssertEditor_serverAdmin_passesLifecycleWithoutAudit() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.ADMIN, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());
    when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(activeDataSource()));

    assertDoesNotThrow(
        () ->
            policy.assertEditorForActiveResource(
                principal(), SOURCE_ID, OBJECT_ID, "upload attachment"));

    verifyNoInteractions(auditReporter);
  }

  @Test
  void auditedAssertEditor_layerEditor_passesLifecycleWithoutAudit() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    SOURCE_ID,
                    "test",
                    LayerRole.EDITOR,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));
    when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.empty());
    when(layerManager.get(SOURCE_ID)).thenReturn(new Layer.Builder().withId(SOURCE_ID).build());

    assertDoesNotThrow(
        () ->
            policy.assertEditorForActiveResource(
                principal(), SOURCE_ID, OBJECT_ID, "upload attachment"));

    verifyNoInteractions(auditReporter);
  }

  @Test
  void auditedAssertMember_globalAdmin_passesWithoutAuditOrGrantLookups() throws Exception {
    setupEventSource();

    assertDoesNotThrow(() -> policy.assertMember(adminPrincipal(), SOURCE_ID, OBJECT_ID));

    verifyNoInteractions(auditReporter);
    verifyNoInteractions(userManager);
  }

  @Test
  void auditedAssertEditor_globalAdmin_passesLifecycleWithoutAudit() throws Exception {
    setupEventSource();
    when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(activeDataSource()));

    assertDoesNotThrow(
        () ->
            policy.assertEditorForActiveResource(
                adminPrincipal(), SOURCE_ID, OBJECT_ID, "upload attachment"));

    verifyNoInteractions(auditReporter);
    // The admin bypass skips authorization only; the lifecycle guard still runs.
    verify(dataSourceRepository).findById(SOURCE_ID);
  }

  @Test
  void auditedAssertMember_eventNotFound_returns404WithoutAudit() throws Exception {
    when(postGisService.getLatestDetails(SOURCE_ID, OBJECT_ID))
        .thenThrow(new NotFoundException("not found"));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertMember(principal(), SOURCE_ID, OBJECT_ID));

    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    verifyNoInteractions(auditReporter);
  }

  @Test
  void auditedAssertEditor_persistenceError_returns500WithoutAudit() throws Exception {
    when(postGisService.getLatestDetails(SOURCE_ID, OBJECT_ID))
        .thenThrow(new PersistenceException("Could not parse properties"));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.assertEditorForActiveResource(
                    principal(), SOURCE_ID, OBJECT_ID, "upload attachment"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatus());
    verifyNoInteractions(auditReporter);
  }

  @Test
  void auditedAssertEditor_lifecycleConflictDoesNotAudit() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.ADMIN, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());
    when(dataSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(archivedDataSource()));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                policy.assertEditorForActiveResource(
                    principal(), SOURCE_ID, OBJECT_ID, "upload attachment"));

    assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    verifyNoInteractions(auditReporter);
  }

  @Test
  void assertEditor_serverAdmin_passes() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.ADMIN, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    assertDoesNotThrow(() -> policy.assertEditor(principal(), SOURCE_ID, OBJECT_ID));
    verify(postGisService).getLatestDetails(SOURCE_ID, OBJECT_ID);
  }

  @Test
  void assertEditor_layerEditor_passes() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    SOURCE_ID,
                    "test",
                    LayerRole.EDITOR,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));

    assertDoesNotThrow(() -> policy.assertEditor(principal(), SOURCE_ID, OBJECT_ID));
  }

  @Test
  void assertEditor_layerMember_denied() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    SOURCE_ID,
                    "test",
                    LayerRole.MEMBER,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertEditor(principal(), SOURCE_ID, OBJECT_ID));
    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
  }

  @Test
  void assertEditor_serverViewer_denied() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.VIEWER, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertEditor(principal(), SOURCE_ID, OBJECT_ID));
    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
  }

  @Test
  void assertMember_layerMember_passes() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    SOURCE_ID,
                    "test",
                    LayerRole.MEMBER,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));

    assertDoesNotThrow(() -> policy.assertMember(principal(), SOURCE_ID, OBJECT_ID));
  }

  @Test
  void assertMember_serverViewer_passes() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.VIEWER, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    assertDoesNotThrow(() -> policy.assertMember(principal(), SOURCE_ID, OBJECT_ID));
  }

  @Test
  void assertMember_fetchesEventDetailsOnlyOnce() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.VIEWER, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    assertDoesNotThrow(() -> policy.assertMember(principal(), SOURCE_ID, OBJECT_ID));

    verify(postGisService, times(1)).getLatestDetails(SOURCE_ID, OBJECT_ID);
  }

  @Test
  void assertMember_noRoles_denied() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertMember(principal(), SOURCE_ID, OBJECT_ID));
    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
  }

  @Test
  void canMember_noRolesReturnsFalseWithoutAuditReport() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    assertFalse(policy.canMember(principal(), SOURCE_ID, OBJECT_ID));

    verifyNoInteractions(auditReporter);
  }

  @Test
  void canMember_serverViewerReturnsTrue() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.VIEWER, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    assertTrue(policy.canMember(principal(), SOURCE_ID, OBJECT_ID));
  }

  @Test
  void canMember_serverViewerDoesNotCallActiveResourceGuardForPlayback() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.VIEWER, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    assertTrue(policy.canMember(principal(), SOURCE_ID, OBJECT_ID));

    verifyNoInteractions(dataSourceRepository);
    verifyNoInteractions(layerManager);
  }

  @Test
  void canMember_layerMemberDoesNotCallActiveResourceGuardForPlayback() throws Exception {
    setupEventSource();
    when(userManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    SOURCE_ID,
                    "archived-layer-grant",
                    LayerRole.MEMBER,
                    LayerAccessScope.PRIVATE,
                    Instant.now(),
                    true)));

    assertTrue(policy.canMember(principal(), SOURCE_ID, OBJECT_ID));

    verifyNoInteractions(dataSourceRepository);
    verifyNoInteractions(layerManager);
  }

  @Test
  void assertMember_eventNotFound_returns404() throws Exception {
    when(postGisService.getLatestDetails(SOURCE_ID, OBJECT_ID))
        .thenThrow(new NotFoundException("not found"));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertMember(principal(), SOURCE_ID, OBJECT_ID));
    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  @Test
  void assertEditor_eventNotFound_returns404() throws Exception {
    when(postGisService.getLatestDetails(SOURCE_ID, OBJECT_ID))
        .thenThrow(new NotFoundException("not found"));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertEditor(principal(), SOURCE_ID, OBJECT_ID));
    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  @Test
  void assertEditor_globalAdmin_eventExists_passes() throws Exception {
    setupEventSource();

    assertDoesNotThrow(() -> policy.assertEditor(adminPrincipal(), SOURCE_ID, OBJECT_ID));
  }

  @Test
  void assertEditor_globalAdmin_eventNotFound_returns404() throws Exception {
    when(postGisService.getLatestDetails(SOURCE_ID, OBJECT_ID))
        .thenThrow(new NotFoundException("not found"));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertEditor(adminPrincipal(), SOURCE_ID, OBJECT_ID));
    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  @Test
  void assertMember_globalAdmin_eventExists_passes() throws Exception {
    setupEventSource();

    assertDoesNotThrow(() -> policy.assertMember(adminPrincipal(), SOURCE_ID, OBJECT_ID));
  }

  @Test
  void assertMember_globalAdmin_eventNotFound_returns404() throws Exception {
    when(postGisService.getLatestDetails(SOURCE_ID, OBJECT_ID))
        .thenThrow(new NotFoundException("not found"));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertMember(adminPrincipal(), SOURCE_ID, OBJECT_ID));
    assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
  }

  @Test
  void assertEditor_persistenceError_returns500() throws Exception {
    when(postGisService.getLatestDetails(SOURCE_ID, OBJECT_ID))
        .thenThrow(new PersistenceException("Could not parse properties"));

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertEditor(principal(), SOURCE_ID, OBJECT_ID));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatus());
  }

  @Test
  void assertEditor_wrongSource_denied() throws Exception {
    setupEventSource();
    var otherSource = UUID.randomUUID();
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(otherSource, ServerRole.ADMIN, null)));
    when(userManager.getUserLayers(USER_ID)).thenReturn(List.of());

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> policy.assertEditor(principal(), SOURCE_ID, OBJECT_ID));
    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
  }

  @Test
  void canViewSource_whenUserHasSourceRole_returnsTrue_withoutExistenceProbeOrAudit() {
    when(userManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.VIEWER, null)));
    assertTrue(policy.canViewSource(USER_ID, false, SOURCE_ID));
    verifyNoInteractions(postGisService, auditReporter);
  }

  @Test
  void canViewSource_whenNoRole_returnsFalse_withoutAudit() {
    assertFalse(policy.canViewSource(USER_ID, false, SOURCE_ID));
    verifyNoInteractions(postGisService, auditReporter);
  }

  @Test
  void canViewSource_whenGlobalAdmin_returnsTrue_withoutUserManagerReads() {
    assertTrue(policy.canViewSource(USER_ID, true, SOURCE_ID));
    verifyNoInteractions(userManager, postGisService, auditReporter);
  }

  @Test
  void assertSourceMember_whenDenied_throwsForbidden_withoutAudit() {
    var ex =
        assertThrows(
            GlobalOpenCopException.class, () -> policy.assertSourceMember(principal(), SOURCE_ID));
    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    verifyNoInteractions(postGisService, auditReporter);
  }

  private static OpenCopPrincipal principal() {
    return new OpenCopPrincipal(
        USER_ID,
        "https://issuer.example.test",
        "stable-id",
        "subject",
        new OpenCopPrincipal.Profile(null, null, null, null, USER_EMAIL, null),
        0,
        Instant.EPOCH);
  }

  private static OpenCopPrincipal adminPrincipal() {
    return principal().withRequestCurrentGlobalAdministrator(true);
  }

  private void setupEventSource() throws Exception {
    var props = mock(OpenCopProperties.class);
    when(props.getSource()).thenReturn(SOURCE_ID);
    when(postGisService.getLatestDetails(SOURCE_ID, OBJECT_ID)).thenReturn(props);
  }

  private AuthzDenialEvent captureAuditEvent() {
    var captor = ArgumentCaptor.forClass(AuthzDenialEvent.class);
    verify(auditReporter).reportDenied(captor.capture());
    return captor.getValue();
  }

  private static void assertAuditEvent(
      AuthzDenialEvent event,
      AuthzReasonCode reasonCode,
      List<AuthzPermission> effectivePermissions,
      List<AuthzPermission> requiredPermissions) {
    assertEquals(AuthzPrincipalType.AUTHENTICATED, event.principalType());
    assertEquals(USER_ID, event.actorUserId());
    assertEquals(USER_EMAIL, event.actorEmail());
    assertEquals(AuthzResourceType.EVENT, event.resource().type());
    assertEquals("sourceId=" + SOURCE_ID + ",objectId=" + OBJECT_ID, event.resource().id());
    // Unit scope has no request context, so the emitter-derived action is
    // honestly UNKNOWN here; the component IT pins the real request line.
    assertEquals("UNKNOWN", event.action());
    assertEquals(reasonCode, event.reasonCode());
    assertEquals(effectivePermissions, event.effectivePermissions());
    assertEquals(requiredPermissions, event.requiredPermissions());
  }

  private KafkaDataSource activeDataSource() {
    return new KafkaDataSource(
        SOURCE_ID,
        "source",
        "tenant",
        "topic",
        "username",
        null,
        null,
        AccessMode.READ_WRITE,
        true,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        TransportKind.KAFKA.name(),
        true);
  }

  private KafkaDataSource archivedDataSource() {
    return new KafkaDataSource(
        SOURCE_ID,
        "source",
        "tenant",
        "topic",
        "username",
        null,
        null,
        AccessMode.READ_WRITE,
        true,
        Instant.now(),
        UUID.randomUUID(),
        Instant.now(),
        null,
        Instant.now(),
        null,
        null,
        TransportKind.KAFKA.name(),
        true);
  }
}
