// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
import com.opencop.core.api.general.ports.gateway.layer.LayerMergeOperationRepository;
import com.opencop.core.api.general.ports.gateway.layer.MergeLayerValidationGateway;
import com.opencop.core.api.general.ports.gateway.layer.MergeLayerValidationGateway.PairCollisionCount;
import com.opencop.core.api.general.technical.model.MergeLayerRequest;
import com.opencop.core.api.general.technical.model.MergeLayerValidationRequest;
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
import com.opencop.core.sdk.model.job.LayerMergeJobSubmission;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperation;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperationPhase;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class MergeLayerUsecaseTest {

  private static final String MERGED_NAME = "Merged operations";
  private static final UUID FIRST_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655447001");
  private static final UUID SECOND_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655447002");
  private static final UUID THIRD_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655447003");
  private static final UUID TARGET_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655447004");
  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447005");
  private static final List<UUID> TWO_SOURCE_LAYER_IDS = List.of(FIRST_LAYER_ID, SECOND_LAYER_ID);
  private static final List<UUID> THREE_SOURCE_LAYER_IDS =
      List.of(FIRST_LAYER_ID, SECOND_LAYER_ID, THIRD_LAYER_ID);

  private LayersRepository layersRepository;
  private LayerMemberRepository layerMemberRepository;
  private LayerManager<Layer, LayerMember> layerManager;
  private LayerAuthorizationService layerAuthorizationService;
  private MergeLayerValidationGateway validationGateway;
  private PlatformTransactionManager transactionManager;
  private JobPublisher jobPublisher;
  private LayerMergeOperationRepository layerMergeOperationRepository;
  private AuditRecorder auditRecorder;
  private MergeLayerUsecase usecase;

  @BeforeEach
  void setUp() throws Exception {
    layersRepository = mock(LayersRepository.class);
    layerMemberRepository = mock(LayerMemberRepository.class);
    layerManager = mock(LayerManager.class);
    layerAuthorizationService = new LayerAuthorizationService(layerManager);
    validationGateway = mock(MergeLayerValidationGateway.class);
    transactionManager = mock(PlatformTransactionManager.class);
    jobPublisher = mock(JobPublisher.class);
    layerMergeOperationRepository = mock(LayerMergeOperationRepository.class);
    auditRecorder = mock(AuditRecorder.class);
    usecase =
        new MergeLayerUsecase(
            layersRepository,
            layerMemberRepository,
            layerManager,
            layerAuthorizationService,
            validationGateway,
            transactionManager,
            jobPublisher,
            layerMergeOperationRepository,
            auditRecorder,
            new LayerAuditProjection());

    TransactionStatus transactionStatus = new SimpleTransactionStatus();
    when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    when(layersRepository.createPendingMerge(MERGED_NAME, OWNER_ID))
        .thenReturn(
            new Layer.Builder()
                .withId(TARGET_LAYER_ID)
                .withName(MERGED_NAME)
                .withAccessScope(LayerAccessScope.PRIVATE)
                .withLifecycleState(LayerLifecycleState.PENDING_MERGE)
                .build());
    for (UUID layerId : THREE_SOURCE_LAYER_IDS) {
      when(layerManager.get(layerId))
          .thenReturn(
              new Layer.Builder()
                  .withId(layerId)
                  .withName("Source " + layerId)
                  .withLifecycleState(LayerLifecycleState.ACTIVE)
                  .build());
      when(layerManager.getMemberRole(layerId, OWNER_ID)).thenReturn(Optional.of(LayerRole.OWNER));
    }
    when(validationGateway.countActiveFeatures(TWO_SOURCE_LAYER_IDS))
        .thenReturn(Map.of(FIRST_LAYER_ID, 1L, SECOND_LAYER_ID, 1L));
    when(validationGateway.countActiveFeatures(THREE_SOURCE_LAYER_IDS))
        .thenReturn(Map.of(FIRST_LAYER_ID, 3L, SECOND_LAYER_ID, 2L, THIRD_LAYER_ID, 1L));
    when(validationGateway.countPairwiseCollisions(TWO_SOURCE_LAYER_IDS)).thenReturn(List.of());
    when(validationGateway.countPairwiseCollisions(THREE_SOURCE_LAYER_IDS))
        .thenReturn(
            List.of(
                new PairCollisionCount(FIRST_LAYER_ID, SECOND_LAYER_ID, 2L),
                new PairCollisionCount(FIRST_LAYER_ID, THIRD_LAYER_ID, 1L),
                new PairCollisionCount(SECOND_LAYER_ID, THIRD_LAYER_ID, 0L)));
  }

  @Test
  void mergeLayer_whenRequestIsValid_createsPendingPrivateTargetAndLocksSources() throws Exception {
    var response =
        usecase.mergeLayer(
            TestPrincipals.mockPrincipal(OWNER_ID),
            new MergeLayerRequest(MERGED_NAME, TWO_SOURCE_LAYER_IDS));

    assertEquals(TARGET_LAYER_ID, response.targetLayer().getId());
    assertEquals(LayerLifecycleState.PENDING_MERGE, response.targetLayer().getLifecycleState());
    assertEquals(LayerAccessScope.PRIVATE, response.targetLayer().getAccessScope());
    assertEquals(TWO_SOURCE_LAYER_IDS, response.sourceLayerIds());
    verify(layersRepository).createPendingMerge(MERGED_NAME, OWNER_ID);
    verify(layerMemberRepository)
        .addLayerMembers(TARGET_LAYER_ID, Map.of(OWNER_ID, LayerRole.OWNER));
    verify(layersRepository).lockMergeSources(TWO_SOURCE_LAYER_IDS, OWNER_ID);
    ArgumentCaptor<LayerMergeJobSubmission> jobCaptor =
        ArgumentCaptor.forClass(LayerMergeJobSubmission.class);
    verify(jobPublisher).scheduleAfterCommit(jobCaptor.capture(), any());
    var mergeJob = jobCaptor.getValue();
    assertEquals(JobIssuer.user(OWNER_ID), mergeJob.issuer());
    assertEquals(TARGET_LAYER_ID, mergeJob.targetLayerId());
    assertEquals(TWO_SOURCE_LAYER_IDS, mergeJob.sourceLayerIds());
    verify(layerMergeOperationRepository)
        .createPending(
            new LayerMergeOperation(
                TARGET_LAYER_ID,
                mergeJob.jobId(),
                TWO_SOURCE_LAYER_IDS,
                LayerMergeOperationPhase.PENDING,
                null));
  }

  /**
   * A merge is recorded when it is requested, not when it finishes: the target names every source
   * it absorbed, and each source points back at the target, so neither drawer is a dead end.
   */
  @Test
  void mergeLayer_recordsTheTargetAndEverySource() throws Exception {
    usecase.mergeLayer(
        TestPrincipals.mockPrincipal(OWNER_ID),
        new MergeLayerRequest(MERGED_NAME, TWO_SOURCE_LAYER_IDS));

    var recorded = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditRecorder, times(1 + TWO_SOURCE_LAYER_IDS.size())).record(recorded.capture());

    var target = recorded.getAllValues().getFirst();
    assertEquals(EntityType.LAYER, target.entityType());
    assertEquals(TARGET_LAYER_ID, target.entityId());
    assertEquals(Operation.MERGED_FROM, target.operation());
    assertEquals(MERGED_NAME, target.change().details().get("resultName"));

    var sourceRows = recorded.getAllValues().subList(1, recorded.getAllValues().size());
    assertEquals(TWO_SOURCE_LAYER_IDS, sourceRows.stream().map(AuditEvent::entityId).toList());
    sourceRows.forEach(
        row -> {
          assertEquals(Operation.MERGED_INTO, row.operation());
          assertEquals(TARGET_LAYER_ID.toString(), row.change().details().get("mergedIntoId"));
          assertEquals(MERGED_NAME, row.change().details().get("resultName"));
        });
  }

  @Test
  void mergeLayer_whenRequesterIsLayerAdmin_createsPendingPrivateTargetAndSchedulesJob()
      throws Exception {
    for (UUID layerId : TWO_SOURCE_LAYER_IDS) {
      when(layerManager.getMemberRole(layerId, OWNER_ID)).thenReturn(Optional.of(LayerRole.ADMIN));
    }

    var response =
        usecase.mergeLayer(
            TestPrincipals.mockPrincipal(OWNER_ID),
            new MergeLayerRequest(MERGED_NAME, TWO_SOURCE_LAYER_IDS));

    assertEquals(TARGET_LAYER_ID, response.targetLayer().getId());
    verify(layersRepository).lockMergeSources(TWO_SOURCE_LAYER_IDS, OWNER_ID);
    verify(jobPublisher)
        .scheduleAfterCommit(
            argThat(
                submission ->
                    submission instanceof LayerMergeJobSubmission mergeJob
                        && mergeJob.issuer().equals(JobIssuer.user(OWNER_ID))
                        && mergeJob.targetLayerId().equals(TARGET_LAYER_ID)
                        && mergeJob.sourceLayerIds().equals(TWO_SOURCE_LAYER_IDS)),
            any());
  }

  @Test
  void mergeLayer_whenRequesterIsGlobalAdmin_createsPendingPrivateTargetAndSchedulesJob()
      throws Exception {
    for (UUID layerId : TWO_SOURCE_LAYER_IDS) {
      when(layerManager.getMemberRole(layerId, OWNER_ID)).thenReturn(Optional.empty());
    }

    var response =
        usecase.mergeLayer(
            globalAdminPrincipal(), new MergeLayerRequest(MERGED_NAME, TWO_SOURCE_LAYER_IDS));

    assertEquals(TARGET_LAYER_ID, response.targetLayer().getId());
    verify(layersRepository).lockMergeSources(TWO_SOURCE_LAYER_IDS, OWNER_ID);
    verify(jobPublisher)
        .scheduleAfterCommit(
            argThat(
                submission ->
                    submission instanceof LayerMergeJobSubmission mergeJob
                        && mergeJob.issuer().equals(JobIssuer.user(OWNER_ID))
                        && mergeJob.targetLayerId().equals(TARGET_LAYER_ID)
                        && mergeJob.sourceLayerIds().equals(TWO_SOURCE_LAYER_IDS)),
            any());
  }

  @Test
  void mergeLayer_whenRequesterIsOnlyLayerMember_returnsForbiddenWithoutMutatingState()
      throws Exception {
    when(layerManager.getMemberRole(SECOND_LAYER_ID, OWNER_ID))
        .thenReturn(Optional.of(LayerRole.MEMBER));

    assertThrows(
        AccessDeniedException.class,
        () ->
            usecase.mergeLayer(
                TestPrincipals.mockPrincipal(OWNER_ID),
                new MergeLayerRequest(MERGED_NAME, TWO_SOURCE_LAYER_IDS)));

    verify(layersRepository, never()).createPendingMerge(any(), any());
    verify(layersRepository, never()).lockMergeSources(any(), any());
    verifyNoInteractions(jobPublisher, layerMergeOperationRepository);
  }

  @Test
  void mergeLayer_whenRequesterHasNoLayerRole_returnsForbiddenWithoutMutatingState()
      throws Exception {
    for (UUID layerId : TWO_SOURCE_LAYER_IDS) {
      when(layerManager.getMemberRole(layerId, OWNER_ID)).thenReturn(Optional.empty());
    }

    assertThrows(
        AccessDeniedException.class,
        () ->
            usecase.mergeLayer(
                TestPrincipals.mockPrincipal(OWNER_ID),
                new MergeLayerRequest(MERGED_NAME, TWO_SOURCE_LAYER_IDS)));

    verify(layersRepository, never()).createPendingMerge(any(), any());
    verify(layersRepository, never()).lockMergeSources(any(), any());
    verifyNoInteractions(jobPublisher, layerMergeOperationRepository);
  }

  @Test
  void mergeLayer_whenRequestHasOneLayer_returnsBadRequest() {
    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.mergeLayer(
                    TestPrincipals.mockPrincipal(OWNER_ID),
                    new MergeLayerRequest(MERGED_NAME, List.of(FIRST_LAYER_ID))));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    verifyNoInteractions(validationGateway, jobPublisher, layerMergeOperationRepository);
  }

  @Test
  void mergeLayer_whenRequestRepeatsLayer_returnsBadRequest() {
    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.mergeLayer(
                    TestPrincipals.mockPrincipal(OWNER_ID),
                    new MergeLayerRequest(MERGED_NAME, List.of(FIRST_LAYER_ID, FIRST_LAYER_ID))));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    verifyNoInteractions(validationGateway, jobPublisher, layerMergeOperationRepository);
  }

  @Test
  void mergeLayer_whenRequestHasMoreThanTenLayers_returnsBadRequestBeforeValidation() {
    var tooManyLayerIds =
        java.util.stream.IntStream.range(0, 11).mapToObj(_ -> UUID.randomUUID()).toList();

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.mergeLayer(
                    TestPrincipals.mockPrincipal(OWNER_ID),
                    new MergeLayerRequest(MERGED_NAME, tooManyLayerIds)));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    verifyNoInteractions(
        layerManager,
        validationGateway,
        layersRepository,
        jobPublisher,
        layerMergeOperationRepository);
  }

  @Test
  void mergeLayer_whenSourceLayerIsNotActive_returnsConflict() {
    when(layerManager.get(SECOND_LAYER_ID))
        .thenReturn(
            new Layer.Builder()
                .withId(SECOND_LAYER_ID)
                .withLifecycleState(LayerLifecycleState.ARCHIVED)
                .build());

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.mergeLayer(
                    TestPrincipals.mockPrincipal(OWNER_ID),
                    new MergeLayerRequest(MERGED_NAME, List.of(FIRST_LAYER_ID, SECOND_LAYER_ID))));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verifyNoInteractions(validationGateway, jobPublisher, layerMergeOperationRepository);
  }

  @Test
  void mergeLayer_whenSourceLayerIsAlreadyLocked_returnsConflict() {
    when(layerManager.get(SECOND_LAYER_ID))
        .thenReturn(
            new Layer.Builder()
                .withId(SECOND_LAYER_ID)
                .withLifecycleState(LayerLifecycleState.MERGE_SOURCE_LOCKED)
                .build());

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.mergeLayer(
                    TestPrincipals.mockPrincipal(OWNER_ID),
                    new MergeLayerRequest(MERGED_NAME, List.of(FIRST_LAYER_ID, SECOND_LAYER_ID))));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verifyNoInteractions(validationGateway, jobPublisher, layerMergeOperationRepository);
  }

  @Test
  void mergeLayer_whenSourceLayerHasNoActiveFeatures_returnsConflict() throws Exception {
    when(validationGateway.countActiveFeatures(TWO_SOURCE_LAYER_IDS))
        .thenReturn(Map.of(FIRST_LAYER_ID, 1L, SECOND_LAYER_ID, 0L));

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.mergeLayer(
                    TestPrincipals.mockPrincipal(OWNER_ID),
                    new MergeLayerRequest(MERGED_NAME, TWO_SOURCE_LAYER_IDS)));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verify(layersRepository, never()).createPendingMerge(any(), any());
    verifyNoInteractions(jobPublisher, layerMergeOperationRepository);
  }

  @Test
  void mergeLayer_whenTargetLayerNameAlreadyExists_returnsConflictWithoutLockingSources()
      throws Exception {
    when(layersRepository.createPendingMerge(MERGED_NAME, OWNER_ID))
        .thenThrow(new RepositoryException(RepositoryExceptionCodes.ALREADY_EXIST));

    var exception =
        assertThrows(
            LayerManagerException.class,
            () ->
                usecase.mergeLayer(
                    TestPrincipals.mockPrincipal(OWNER_ID),
                    new MergeLayerRequest(MERGED_NAME, TWO_SOURCE_LAYER_IDS)));

    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verify(layerMemberRepository, never()).addLayerMembers(any(), any());
    verify(layersRepository, never()).lockMergeSources(any(), any());
    verifyNoInteractions(jobPublisher, layerMergeOperationRepository);
  }

  @Test
  void validateMergeLayer_whenSourcesCollide_reportsTotalAndPairwiseCountsInRequestOrder()
      throws Exception {
    var response =
        usecase.validateMergeLayer(
            TestPrincipals.mockPrincipal(OWNER_ID),
            new MergeLayerValidationRequest(THREE_SOURCE_LAYER_IDS));

    assertEquals(THREE_SOURCE_LAYER_IDS, response.layerIds());
    assertEquals(
        Map.of(FIRST_LAYER_ID, 3L, SECOND_LAYER_ID, 2L, THIRD_LAYER_ID, 1L),
        response.activeFeatureCounts());
    assertEquals(3L, response.collisionSummary().totalCollisions());
    assertEquals(3, response.collisionSummary().pairwiseCounts().size());
    assertEquals(
        FIRST_LAYER_ID, response.collisionSummary().pairwiseCounts().get(0).firstLayerId());
    assertEquals(
        SECOND_LAYER_ID, response.collisionSummary().pairwiseCounts().get(0).secondLayerId());
    assertEquals(2L, response.collisionSummary().pairwiseCounts().get(0).count());
    assertEquals(
        FIRST_LAYER_ID, response.collisionSummary().pairwiseCounts().get(1).firstLayerId());
    assertEquals(
        THIRD_LAYER_ID, response.collisionSummary().pairwiseCounts().get(1).secondLayerId());
    assertEquals(1L, response.collisionSummary().pairwiseCounts().get(1).count());
    assertEquals(
        SECOND_LAYER_ID, response.collisionSummary().pairwiseCounts().get(2).firstLayerId());
    assertEquals(
        THIRD_LAYER_ID, response.collisionSummary().pairwiseCounts().get(2).secondLayerId());
    assertEquals(0L, response.collisionSummary().pairwiseCounts().get(2).count());
    verify(layersRepository, never()).createPendingMerge(any(), any());
    verify(layersRepository, never()).lockMergeSources(any(), any());
    verifyNoInteractions(jobPublisher);
  }

  @Test
  void validateMergeLayer_returnsAdvisoryCollisionSummaryWithoutOperationalSourceLock()
      throws Exception {
    usecase.validateMergeLayer(
        TestPrincipals.mockPrincipal(OWNER_ID),
        new MergeLayerValidationRequest(TWO_SOURCE_LAYER_IDS));

    InOrder inOrder = inOrder(validationGateway, layersRepository);
    inOrder.verify(validationGateway).countActiveFeatures(TWO_SOURCE_LAYER_IDS);
    inOrder.verify(validationGateway).countPairwiseCollisions(TWO_SOURCE_LAYER_IDS);
    verify(layersRepository, never()).createPendingMerge(any(), any());
    verify(layersRepository, never()).lockMergeSources(any(), any());
    verifyNoInteractions(jobPublisher);
  }

  @Test
  void validateMergeLayer_whenRequestHasMoreThanTenLayers_returnsBadRequestBeforeValidation() {
    var tooManyLayerIds =
        java.util.stream.IntStream.range(0, 11).mapToObj(_ -> UUID.randomUUID()).toList();

    var exception =
        assertThrows(
            GlobalOpenCopException.class,
            () ->
                usecase.validateMergeLayer(
                    TestPrincipals.mockPrincipal(OWNER_ID),
                    new MergeLayerValidationRequest(tooManyLayerIds)));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    verifyNoInteractions(layerManager, validationGateway, layersRepository, jobPublisher);
  }

  @Test
  void validateMergeLayer_whenRequestHasTenLayers_isAccepted() {
    var maxLayerIds =
        java.util.stream.IntStream.range(0, 10).mapToObj(_ -> UUID.randomUUID()).toList();
    var activeCounts = new LinkedHashMap<UUID, Long>();
    for (UUID layerId : maxLayerIds) {
      when(layerManager.get(layerId))
          .thenReturn(
              new Layer.Builder()
                  .withId(layerId)
                  .withLifecycleState(LayerLifecycleState.ACTIVE)
                  .build());
      when(layerManager.getMemberRole(layerId, OWNER_ID)).thenReturn(Optional.of(LayerRole.OWNER));
      activeCounts.put(layerId, 1L);
    }
    when(validationGateway.countActiveFeatures(maxLayerIds)).thenReturn(activeCounts);
    when(validationGateway.countPairwiseCollisions(maxLayerIds)).thenReturn(List.of());

    var response =
        usecase.validateMergeLayer(
            TestPrincipals.mockPrincipal(OWNER_ID), new MergeLayerValidationRequest(maxLayerIds));

    assertEquals(maxLayerIds, response.layerIds());
    assertEquals(activeCounts, response.activeFeatureCounts());
  }

  @Test
  @SuppressWarnings("unchecked")
  void mergeLayer_whenAfterCommitPublishFails_marksTargetFailedAndUnlocksSourcesAndFailsOperation()
      throws Exception {
    ArgumentCaptor<Consumer<Exception>> failureCaptor = ArgumentCaptor.forClass(Consumer.class);

    usecase.mergeLayer(
        TestPrincipals.mockPrincipal(OWNER_ID),
        new MergeLayerRequest(MERGED_NAME, TWO_SOURCE_LAYER_IDS));

    verify(jobPublisher)
        .scheduleAfterCommit(any(LayerMergeJobSubmission.class), failureCaptor.capture());
    var publishFailure = new IllegalStateException("kafka publish failed");
    failureCaptor.getValue().accept(publishFailure);

    var inOrder = inOrder(layerMergeOperationRepository, layersRepository);
    inOrder
        .verify(layerMergeOperationRepository)
        .markFailed(eq(TARGET_LAYER_ID), startsWith("PUBLISH_FAILED: IllegalStateException"));
    inOrder.verify(layersRepository).finalizeMergeFailure(TARGET_LAYER_ID, TWO_SOURCE_LAYER_IDS);
  }

  @Test
  @SuppressWarnings("unchecked")
  void mergeLayer_whenPublishFailureLifecycleCompensationFails_stillFailsOperation()
      throws Exception {
    ArgumentCaptor<Consumer<Exception>> failureCaptor = ArgumentCaptor.forClass(Consumer.class);
    doThrow(new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST))
        .when(layersRepository)
        .finalizeMergeFailure(TARGET_LAYER_ID, TWO_SOURCE_LAYER_IDS);

    usecase.mergeLayer(
        TestPrincipals.mockPrincipal(OWNER_ID),
        new MergeLayerRequest(MERGED_NAME, TWO_SOURCE_LAYER_IDS));

    verify(jobPublisher)
        .scheduleAfterCommit(any(LayerMergeJobSubmission.class), failureCaptor.capture());
    failureCaptor.getValue().accept(new IllegalStateException("kafka publish failed"));

    verify(layerMergeOperationRepository)
        .markFailed(eq(TARGET_LAYER_ID), startsWith("PUBLISH_FAILED: IllegalStateException"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void mergeLayer_whenPublishFailureMessageContainsSensitiveDetail_persistsStableFailureCode()
      throws Exception {
    ArgumentCaptor<Consumer<Exception>> failureCaptor = ArgumentCaptor.forClass(Consumer.class);

    usecase.mergeLayer(
        TestPrincipals.mockPrincipal(OWNER_ID),
        new MergeLayerRequest(MERGED_NAME, TWO_SOURCE_LAYER_IDS));

    verify(jobPublisher)
        .scheduleAfterCommit(any(LayerMergeJobSubmission.class), failureCaptor.capture());
    failureCaptor
        .getValue()
        .accept(new IllegalStateException("broker=https://example.test token=secret-token"));

    ArgumentCaptor<String> failureReasonCaptor = ArgumentCaptor.forClass(String.class);
    verify(layerMergeOperationRepository)
        .markFailed(eq(TARGET_LAYER_ID), failureReasonCaptor.capture());
    assertEquals("PUBLISH_FAILED: IllegalStateException", failureReasonCaptor.getValue());
    assertFalse(failureReasonCaptor.getValue().contains("https://example.test"));
    assertFalse(failureReasonCaptor.getValue().contains("secret-token"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void mergeLayer_whenPublishFailureOperationClaimFails_stillAttemptsLifecycleCompensation()
      throws Exception {
    ArgumentCaptor<Consumer<Exception>> failureCaptor = ArgumentCaptor.forClass(Consumer.class);
    doThrow(new IllegalStateException("operation already advanced"))
        .when(layerMergeOperationRepository)
        .markFailed(eq(TARGET_LAYER_ID), startsWith("PUBLISH_FAILED: IllegalStateException"));

    usecase.mergeLayer(
        TestPrincipals.mockPrincipal(OWNER_ID),
        new MergeLayerRequest(MERGED_NAME, TWO_SOURCE_LAYER_IDS));

    verify(jobPublisher)
        .scheduleAfterCommit(any(LayerMergeJobSubmission.class), failureCaptor.capture());
    failureCaptor.getValue().accept(new IllegalStateException("kafka publish failed"));

    verify(layersRepository).finalizeMergeFailure(TARGET_LAYER_ID, TWO_SOURCE_LAYER_IDS);
  }

  private static OpenCopPrincipal globalAdminPrincipal() {
    return TestPrincipals.oidcPrincipal(OWNER_ID, true);
  }
}
