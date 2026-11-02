// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.api.general.technical.model.CloneLayerRequest;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManagerException;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.LayerCopyJobSubmission;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CloneLayerUsecaseTest {

  private static final UUID SOURCE_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655446001");
  private static final UUID TARGET_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655446002");
  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655446003");
  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655446004");

  private LayersRepository layersRepository;
  private LayerMemberRepository layerMemberRepository;
  private LayerManager<Layer, LayerMember> layerManager;
  private LayerAuthorizationService layerAuthorizationService;
  private PlatformTransactionManager transactionManager;
  private TransactionStatus transactionStatus;
  private JobPublisher jobPublisher;
  private AuditRecorder auditRecorder;
  private CloneLayerUsecase usecase;

  @BeforeEach
  void setUp() {
    layersRepository = mock(LayersRepository.class);
    layerMemberRepository = mock(LayerMemberRepository.class);
    layerManager = mock(LayerManager.class);
    layerAuthorizationService = mock(LayerAuthorizationService.class);
    transactionManager = mock(PlatformTransactionManager.class);
    transactionStatus = new SimpleTransactionStatus();
    jobPublisher = mock(JobPublisher.class);
    auditRecorder = mock(AuditRecorder.class);
    usecase =
        new CloneLayerUsecase(
            layersRepository,
            layerMemberRepository,
            layerManager,
            layerAuthorizationService,
            transactionManager,
            jobPublisher,
            auditRecorder,
            new LayerAuditProjection());

    when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    when(layerManager.get(SOURCE_LAYER_ID)).thenReturn(new Layer());
  }

  /**
   * One clone is two facts: the copy records where it came from, and the original records that it
   * was copied. Both are written from the same transaction, so a reader of either drawer sees it.
   */
  @Test
  void cloneLayer_recordsARowInEachLayersLedger() throws Exception {
    var authentication = TestPrincipals.mockPrincipal(OWNER_ID);
    var source = new Layer.Builder().withId(SOURCE_LAYER_ID).withName("Recon North").build();
    when(layerManager.get(SOURCE_LAYER_ID)).thenReturn(source);
    var createdLayer = new Layer.Builder().withId(TARGET_LAYER_ID).withName("Cloned").build();
    when(layersRepository.create("Cloned", null, OWNER_ID)).thenReturn(createdLayer);
    when(layerMemberRepository.addLayerMembers(eq(TARGET_LAYER_ID), any()))
        .thenReturn(Map.of(OWNER_ID, LayerRole.OWNER));

    usecase.cloneLayer(
        SOURCE_LAYER_ID, authentication, new CloneLayerRequest("Cloned", null, null, null, null));

    var recorded = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditRecorder, times(2)).record(recorded.capture());

    var copy = recorded.getAllValues().getFirst();
    assertThat(copy.entityType()).isEqualTo(EntityType.LAYER);
    assertThat(copy.entityId()).isEqualTo(TARGET_LAYER_ID);
    assertThat(copy.operation()).isEqualTo(Operation.CREATED_FROM_CLONE);
    assertThat(copy.change().details())
        .containsEntry("clonedFromId", SOURCE_LAYER_ID.toString())
        .containsEntry("clonedFromName", "Recon North");
    assertThat(copy.change().delta().added()).containsOnlyKeys(OWNER_ID);

    var original = recorded.getAllValues().get(1);
    assertThat(original.entityId()).isEqualTo(SOURCE_LAYER_ID);
    assertThat(original.operation()).isEqualTo(Operation.CLONED_TO);
    assertThat(original.change().details())
        .containsEntry("cloneId", TARGET_LAYER_ID.toString())
        .containsEntry("cloneName", "Cloned");
  }

  @Test
  void cloneLayer_whenRequestIsValid_clonesInsideTransaction() throws Exception {
    var authentication = TestPrincipals.mockPrincipal(OWNER_ID);
    var request =
        new CloneLayerRequest("Cloned", Map.of(ADMIN_ID, LayerRole.ADMIN), null, null, null);
    var createdLayer = new Layer.Builder().withId(TARGET_LAYER_ID).withName("Cloned").build();
    when(layersRepository.create("Cloned", null, OWNER_ID)).thenReturn(createdLayer);

    var result = usecase.cloneLayer(SOURCE_LAYER_ID, authentication, request);

    assertEquals(TARGET_LAYER_ID, result.getId());
    verify(layerManager).get(SOURCE_LAYER_ID);
    verify(layerAuthorizationService)
        .assertLayerRole(
            SOURCE_LAYER_ID,
            authentication,
            "Only layer owners and admins can clone this layer",
            LayerRole.OWNER,
            LayerRole.ADMIN);
    verify(layersRepository).create("Cloned", null, OWNER_ID);
    verify(layerMemberRepository)
        .addLayerMembers(
            TARGET_LAYER_ID, Map.of(ADMIN_ID, LayerRole.ADMIN, OWNER_ID, LayerRole.OWNER));
    verify(jobPublisher)
        .scheduleAfterCommit(
            argThat(
                submission ->
                    submission instanceof LayerCopyJobSubmission layerCopyJob
                        && SOURCE_LAYER_ID.equals(layerCopyJob.sourceLayerId())
                        && TARGET_LAYER_ID.equals(layerCopyJob.targetLayerId())
                        && JobIssuer.user(OWNER_ID).equals(layerCopyJob.issuer())
                        && layerCopyJob.copyRemarks()
                        && layerCopyJob.copyMedia()
                        && layerCopyJob.jobId() != null));
  }

  @Test
  void cloneLayer_whenCopyFlagsProvided_passesFlagsThrough() throws Exception {
    var authentication = TestPrincipals.mockPrincipal(OWNER_ID);
    var request = new CloneLayerRequest("Cloned", Map.of(), null, false, false);
    when(layersRepository.create("Cloned", null, OWNER_ID))
        .thenReturn(new Layer.Builder().withId(TARGET_LAYER_ID).build());

    usecase.cloneLayer(SOURCE_LAYER_ID, authentication, request);

    verify(jobPublisher)
        .scheduleAfterCommit(
            argThat(
                submission ->
                    submission instanceof LayerCopyJobSubmission layerCopyJob
                        && SOURCE_LAYER_ID.equals(layerCopyJob.sourceLayerId())
                        && TARGET_LAYER_ID.equals(layerCopyJob.targetLayerId())
                        && JobIssuer.user(OWNER_ID).equals(layerCopyJob.issuer())
                        && !layerCopyJob.copyRemarks()
                        && !layerCopyJob.copyMedia()
                        && layerCopyJob.jobId() != null));
  }

  @Test
  void cloneLayer_whenCallerIsGlobalAdmin_skipsLayerRoleCheck() throws Exception {
    var authentication = globalAdminPrincipal();
    var request = new CloneLayerRequest("Cloned", Map.of(), null, null, null);
    when(layersRepository.create("Cloned", null, OWNER_ID))
        .thenReturn(new Layer.Builder().withId(TARGET_LAYER_ID).build());

    usecase.cloneLayer(SOURCE_LAYER_ID, authentication, request);

    verify(layerManager).get(SOURCE_LAYER_ID);
    verify(layerAuthorizationService, never())
        .assertLayerRole(
            any(UUID.class),
            any(OpenCopPrincipal.class),
            any(String.class),
            any(LayerRole.class),
            any(LayerRole.class));
  }

  @Test
  void cloneLayer_whenTargetAccessScopeIsGlobal_throwsBadRequest() {
    var request = new CloneLayerRequest("Cloned", Map.of(), LayerAccessScope.GLOBAL, null, null);

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.cloneLayer(
                    SOURCE_LAYER_ID, TestPrincipals.mockPrincipal(OWNER_ID), request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    verifyNoInteractions(layerManager, layerAuthorizationService, layersRepository);
  }

  @Test
  void cloneLayer_whenCreateFailsWithDuplicateName_mapsToConflict() throws Exception {
    var request = new CloneLayerRequest("Cloned", Map.of(), null, null, null);
    when(layersRepository.create("Cloned", null, OWNER_ID))
        .thenThrow(new RepositoryException(RepositoryExceptionCodes.ALREADY_EXIST));

    var exception =
        assertThrows(
            LayerManagerException.class,
            () ->
                usecase.cloneLayer(
                    SOURCE_LAYER_ID, TestPrincipals.mockPrincipal(OWNER_ID), request));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
  }

  @Test
  void cloneLayer_whenCreateFailsWithMissingDependency_mapsToNotFound() throws Exception {
    var request = new CloneLayerRequest("Cloned", Map.of(), null, null, null);
    when(layersRepository.create("Cloned", null, OWNER_ID))
        .thenThrow(new RepositoryException(RepositoryExceptionCodes.ELEMENT_NOT_FOUND));

    var exception =
        assertThrows(
            LayerManagerException.class,
            () ->
                usecase.cloneLayer(
                    SOURCE_LAYER_ID, TestPrincipals.mockPrincipal(OWNER_ID), request));

    assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
  }

  @Test
  void cloneLayer_whenSourceLayerArchived_returnsConflictWithoutCreatingLayer() {
    var authentication = TestPrincipals.mockPrincipal(OWNER_ID);
    var request = new CloneLayerRequest("Cloned", Map.of(), null, null, null);
    var archivedLayer =
        new Layer.Builder()
            .withId(SOURCE_LAYER_ID)
            .withName("Archived source")
            .withArchivedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();
    when(layerManager.get(SOURCE_LAYER_ID)).thenReturn(archivedLayer);

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () -> usecase.cloneLayer(SOURCE_LAYER_ID, authentication, request));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verify(layerManager).get(SOURCE_LAYER_ID);
    verifyNoInteractions(layersRepository, layerMemberRepository, jobPublisher);
    verify(layerAuthorizationService, never())
        .assertLayerRole(
            any(UUID.class),
            any(OpenCopPrincipal.class),
            any(String.class),
            any(LayerRole.class),
            any(LayerRole.class));
  }

  private static OpenCopPrincipal globalAdminPrincipal() {
    return TestPrincipals.oidcPrincipal(OWNER_ID, true);
  }
}
