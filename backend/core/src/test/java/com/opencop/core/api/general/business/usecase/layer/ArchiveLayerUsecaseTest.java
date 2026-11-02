// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.cache.EventGeoCacheEvictionGateway;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;

class ArchiveLayerUsecaseTest {

  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440101");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440201");
  private static final UUID OTHER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440301");

  private final AuditRecorder auditRecorder = mock(AuditRecorder.class);

  @Test
  void archive_whenActiveLayerAndOwner_archivesLayerBeforeEvictingSourceAndReturnsMembers() {
    var layerManager = mock(LayerManager.class);
    var layerAuth = mock(LayerAuthorizationService.class);
    var eventGeoCacheEvictionGateway = mock(EventGeoCacheEvictionGateway.class);
    var members = List.of(mock(LayerMember.class), mock(LayerMember.class));
    var activeLayer = new Layer.Builder().withId(LAYER_ID).withArchivedAt(null).build();
    when(layerManager.get(LAYER_ID)).thenReturn(activeLayer);
    when(layerManager.listLayerMembers(LAYER_ID)).thenReturn(members);
    var usecase = archiveUsecase(layerManager, layerAuth, eventGeoCacheEvictionGateway);

    var authentication = ownerPrincipal(OWNER_ID);
    var result = usecase.archiveLayer(LAYER_ID, authentication);

    assertEquals(members, result.members());
    var sequence = inOrder(layerManager, layerAuth, eventGeoCacheEvictionGateway);
    sequence.verify(layerManager).get(LAYER_ID);
    sequence
        .verify(layerAuth)
        .assertLayerRole(eq(LAYER_ID), eq(authentication), eq(LayerRole.OWNER));
    sequence.verify(layerManager).listLayerMembers(LAYER_ID);
    sequence.verify(layerManager).archive(LAYER_ID, authentication.internalUserId());
    sequence.verify(eventGeoCacheEvictionGateway).evictGeoSource(LAYER_ID);
    assertEquals(List.of(Operation.ARCHIVE), recordedOperations());
  }

  @Test
  void archive_whenLayerIsMissing_neverEvictsGeoCacheEntries() {
    var layerManager = mock(LayerManager.class);
    var layerAuth = mock(LayerAuthorizationService.class);
    var eventGeoCacheEvictionGateway = mock(EventGeoCacheEvictionGateway.class);
    when(layerManager.get(LAYER_ID))
        .thenThrow(new GlobalOpenCopException("layer is missing", HttpStatus.NOT_FOUND));
    var usecase = archiveUsecase(layerManager, layerAuth, eventGeoCacheEvictionGateway);
    var authentication = ownerPrincipal(OWNER_ID);

    assertThrows(
        GlobalOpenCopException.class, () -> usecase.archiveLayer(LAYER_ID, authentication));

    verify(layerAuth, never()).assertLayerRole(LAYER_ID, authentication, LayerRole.OWNER);
    verify(layerManager, never()).listLayerMembers(LAYER_ID);
    verify(layerManager, never()).archive(LAYER_ID, OWNER_ID);
    verifyNoInteractions(eventGeoCacheEvictionGateway);
  }

  @Test
  void archive_whenLayerIsAlreadyArchived_neverEvictsGeoCacheEntries() {
    var layerManager = mock(LayerManager.class);
    var layerAuth = mock(LayerAuthorizationService.class);
    var eventGeoCacheEvictionGateway = mock(EventGeoCacheEvictionGateway.class);
    var archivedLayer =
        new Layer.Builder()
            .withId(LAYER_ID)
            .withArchivedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();
    when(layerManager.get(LAYER_ID)).thenReturn(archivedLayer);
    var usecase = archiveUsecase(layerManager, layerAuth, eventGeoCacheEvictionGateway);
    var authentication = ownerPrincipal(OWNER_ID);

    assertThrows(
        GlobalOpenCopException.class, () -> usecase.archiveLayer(LAYER_ID, authentication));

    verify(layerManager).get(LAYER_ID);
    verify(layerAuth, never()).assertLayerRole(LAYER_ID, authentication, LayerRole.OWNER);
    verify(layerManager, never()).listLayerMembers(LAYER_ID);
    verify(layerManager, never()).archive(LAYER_ID, OWNER_ID);
    verifyNoInteractions(eventGeoCacheEvictionGateway);
  }

  @Test
  void archive_whenCallerIsNotAuthorized_neverEvictsGeoCacheEntries() {
    var layerManager = mock(LayerManager.class);
    var layerAuth = mock(LayerAuthorizationService.class);
    var eventGeoCacheEvictionGateway = mock(EventGeoCacheEvictionGateway.class);
    var activeLayer = new Layer.Builder().withId(LAYER_ID).withArchivedAt(null).build();
    var otherPrincipal = ownerPrincipal(OTHER_ID);
    when(layerManager.get(LAYER_ID)).thenReturn(activeLayer);
    doThrow(new AccessDeniedException("not owner"))
        .when(layerAuth)
        .assertLayerRole(LAYER_ID, otherPrincipal, LayerRole.OWNER);
    var usecase = archiveUsecase(layerManager, layerAuth, eventGeoCacheEvictionGateway);

    assertThrows(AccessDeniedException.class, () -> usecase.archiveLayer(LAYER_ID, otherPrincipal));

    verify(layerManager).get(LAYER_ID);
    verify(layerAuth).assertLayerRole(LAYER_ID, otherPrincipal, LayerRole.OWNER);
    verify(layerManager, never()).listLayerMembers(LAYER_ID);
    verify(layerManager, never()).archive(LAYER_ID, OTHER_ID);
    verifyNoInteractions(eventGeoCacheEvictionGateway);
  }

  @Test
  void archive_whenArchiveFails_neverEvictsAfterFailure() {
    var layerManager = mock(LayerManager.class);
    var layerAuth = mock(LayerAuthorizationService.class);
    var eventGeoCacheEvictionGateway = mock(EventGeoCacheEvictionGateway.class);
    var members = List.of(mock(LayerMember.class), mock(LayerMember.class));
    var activeLayer = new Layer.Builder().withId(LAYER_ID).withArchivedAt(null).build();
    when(layerManager.get(LAYER_ID)).thenReturn(activeLayer);
    when(layerManager.listLayerMembers(LAYER_ID)).thenReturn(members);
    var authentication = ownerPrincipal(OWNER_ID);
    doThrow(new IllegalStateException("failed to archive"))
        .when(layerManager)
        .archive(LAYER_ID, authentication.internalUserId());
    var usecase = archiveUsecase(layerManager, layerAuth, eventGeoCacheEvictionGateway);

    assertThrows(IllegalStateException.class, () -> usecase.archiveLayer(LAYER_ID, authentication));

    verify(layerAuth).assertLayerRole(LAYER_ID, authentication, LayerRole.OWNER);
    verify(layerManager).listLayerMembers(LAYER_ID);
    verify(layerManager).archive(LAYER_ID, authentication.internalUserId());
    verifyNoInteractions(eventGeoCacheEvictionGateway);
    verifyNoInteractions(auditRecorder);
  }

  @Test
  void archive_whenEvictionFails_afterArchive_restoresLayerAndFails() {
    var layerManager = mock(LayerManager.class);
    var layerAuth = mock(LayerAuthorizationService.class);
    var eventGeoCacheEvictionGateway = mock(EventGeoCacheEvictionGateway.class);
    var members = List.of(mock(LayerMember.class));
    var activeLayer = new Layer.Builder().withId(LAYER_ID).withArchivedAt(null).build();
    when(layerManager.get(LAYER_ID)).thenReturn(activeLayer);
    when(layerManager.listLayerMembers(LAYER_ID)).thenReturn(members);
    doThrow(new IllegalStateException("redis eviction failed"))
        .when(eventGeoCacheEvictionGateway)
        .evictGeoSource(LAYER_ID);
    var usecase = archiveUsecase(layerManager, layerAuth, eventGeoCacheEvictionGateway);
    var authentication = ownerPrincipal(OWNER_ID);

    var error =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.archiveLayer(LAYER_ID, authentication));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getHttpStatus());
    // Eviction spans several Redis commands, so a failure cannot promise the layer is unchanged.
    // The message may only offer a retryable layer, never an untouched one.
    assertEquals(
        "Cached events could not be evicted. The layer was restored so the archive can be retried,"
            + " but some of its cached events may already have been removed.",
        error.getMessage());
    var sequence = inOrder(layerManager, layerAuth, eventGeoCacheEvictionGateway);
    sequence.verify(layerManager).get(LAYER_ID);
    sequence.verify(layerAuth).assertLayerRole(LAYER_ID, authentication, LayerRole.OWNER);
    sequence.verify(layerManager).listLayerMembers(LAYER_ID);
    sequence.verify(layerManager).archive(LAYER_ID, authentication.internalUserId());
    sequence.verify(eventGeoCacheEvictionGateway).evictGeoSource(LAYER_ID);
    sequence.verify(layerManager).unarchive(LAYER_ID);
    assertEquals(List.of(Operation.ARCHIVE, Operation.UNARCHIVE), recordedOperations());
  }

  @Test
  void archive_whenEvictionAndRestoreBothFail_reportsServerError() {
    var layerManager = mock(LayerManager.class);
    var layerAuth = mock(LayerAuthorizationService.class);
    var eventGeoCacheEvictionGateway = mock(EventGeoCacheEvictionGateway.class);
    var members = List.of(mock(LayerMember.class));
    var activeLayer = new Layer.Builder().withId(LAYER_ID).withArchivedAt(null).build();
    when(layerManager.get(LAYER_ID)).thenReturn(activeLayer);
    when(layerManager.listLayerMembers(LAYER_ID)).thenReturn(members);
    doThrow(new IllegalStateException("redis eviction failed"))
        .when(eventGeoCacheEvictionGateway)
        .evictGeoSource(LAYER_ID);
    doThrow(new IllegalStateException("restore failed")).when(layerManager).unarchive(LAYER_ID);
    var usecase = archiveUsecase(layerManager, layerAuth, eventGeoCacheEvictionGateway);
    var authentication = ownerPrincipal(OWNER_ID);

    var error =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.archiveLayer(LAYER_ID, authentication));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getHttpStatus());
    verify(layerManager).archive(LAYER_ID, authentication.internalUserId());
    verify(layerManager).unarchive(LAYER_ID);
    assertEquals(List.of(Operation.ARCHIVE), recordedOperations());
  }

  private List<Operation> recordedOperations() {
    var recorded = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditRecorder, atLeastOnce()).record(recorded.capture());
    return recorded.getAllValues().stream().map(AuditEvent::operation).toList();
  }

  private static OpenCopPrincipal ownerPrincipal(UUID principalId) {
    return TestPrincipals.oidcPrincipal(principalId);
  }

  /**
   * The recorder is a mock: these tests pin which rows are written and in what order around the
   * eviction, not that a row commits or rolls back with the archive.
   */
  private ArchiveLayerUsecase archiveUsecase(
      LayerManager<Layer, LayerMember> layerManager,
      LayerAuthorizationService layerAuth,
      EventGeoCacheEvictionGateway eventGeoCacheEvictionGateway) {
    return new ArchiveLayerUsecase(
        layerManager,
        layerAuth,
        eventGeoCacheEvictionGateway,
        auditRecorder,
        new LayerAuditProjection(),
        mock(PlatformTransactionManager.class));
  }
}
