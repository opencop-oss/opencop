// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layer;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.api.general.ports.gateway.layer.LayerMergeOperationRepository;
import com.opencop.core.api.general.ports.gateway.layer.MergeLayerValidationGateway;
import com.opencop.core.api.general.technical.model.MergeLayerRequest;
import com.opencop.core.api.general.technical.model.MergeLayerResponse;
import com.opencop.core.api.general.technical.model.MergeLayerValidationRequest;
import com.opencop.core.api.general.technical.model.MergeLayerValidationResponse;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManagerException;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.LayerMergeJobSubmission;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperation;
import com.opencop.core.sdk.model.layermerge.LayerMergeOperationPhase;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class MergeLayerUsecase {

  private static final Logger log = LoggerFactory.getLogger(MergeLayerUsecase.class);
  private static final int MINIMUM_SOURCE_LAYER_COUNT = 2;
  private static final int MAXIMUM_SOURCE_LAYER_COUNT = 10;
  private static final String INVALID_LAYER_COUNT_MESSAGE =
      "At least two layers are required for merge";
  private static final String TOO_MANY_LAYERS_MESSAGE = "At most 10 layers can be merged at a time";
  private static final String DUPLICATE_LAYER_MESSAGE = "Layer merge sources must be unique";
  private static final String EMPTY_LAYER_MESSAGE =
      "All merge source layers must contain active features";
  private static final String MERGE_ROLE_REQUIRED_MESSAGE =
      "Only layer owners and admins can merge source layers";
  private static final String PUBLISH_FAILURE_REASON_PREFIX = "PUBLISH_FAILED";

  private final LayersRepository layersRepository;
  private final LayerMemberRepository layerMemberRepository;
  private final LayerManager<Layer, LayerMember> layerManager;
  private final LayerAuthorizationService layerAuthorizationService;
  private final MergeLayerValidationGateway validationGateway;
  private final TransactionTemplate transactionTemplate;
  private final JobPublisher jobPublisher;
  private final LayerMergeOperationRepository layerMergeOperationRepository;
  private final AuditRecorder auditRecorder;
  private final LayerAuditProjection projection;

  public MergeLayerUsecase(
      LayersRepository layersRepository,
      LayerMemberRepository layerMemberRepository,
      LayerManager<Layer, LayerMember> layerManager,
      LayerAuthorizationService layerAuthorizationService,
      MergeLayerValidationGateway validationGateway,
      PlatformTransactionManager transactionManager,
      JobPublisher jobPublisher,
      LayerMergeOperationRepository layerMergeOperationRepository,
      AuditRecorder auditRecorder,
      LayerAuditProjection projection) {
    this.layersRepository = layersRepository;
    this.layerMemberRepository = layerMemberRepository;
    this.layerManager = layerManager;
    this.layerAuthorizationService = layerAuthorizationService;
    this.validationGateway = validationGateway;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    this.jobPublisher = jobPublisher;
    this.layerMergeOperationRepository = layerMergeOperationRepository;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  public MergeLayerResponse mergeLayer(OpenCopPrincipal authentication, MergeLayerRequest request) {
    var layerIds = validateShape(request == null ? null : request.layerIds());
    validateSourceLayers(authentication, layerIds);

    var targetLayer =
        transactionTemplate.execute(
            _ -> {
              try {
                var layer =
                    layersRepository.createPendingMerge(
                        request.name(), authentication.internalUserId());
                layerMemberRepository.addLayerMembers(
                    layer.getId(), Map.of(authentication.internalUserId(), LayerRole.OWNER));
                layersRepository.lockMergeSources(layerIds, authentication.internalUserId());
                var job =
                    LayerMergeJobSubmission.create(
                        JobIssuer.user(authentication.internalUserId()), layer.getId(), layerIds);
                layerMergeOperationRepository.createPending(
                    new LayerMergeOperation(
                        job.targetLayerId(),
                        job.jobId(),
                        job.sourceLayerIds(),
                        LayerMergeOperationPhase.PENDING,
                        null));
                recordMergeRequested(authentication, layer, layerIds);

                jobPublisher.scheduleAfterCommit(
                    job, failure -> compensatePublishFailure(layer.getId(), layerIds, failure));
                return layer;
              } catch (RepositoryException e) {
                throw mapRepositoryException(e);
              }
            });

    return new MergeLayerResponse(targetLayer, layerIds);
  }

  /**
   * Records that a merge was <em>requested</em>, on the target and on each source.
   *
   * <p>The merge itself finishes in the processor service, which runs with no user behind it and so
   * records nothing. Writing both sides here, at request time, is what keeps the person who asked
   * for the merge attached to it. The sources are not also given an ARCHIVE row: the processor
   * archives them as part of completing this one action, and a second row would read as a separate
   * decision somebody made. Whether the merge then succeeded is visible on the layer's own
   * lifecycle state, which the ledger does not duplicate.
   */
  private void recordMergeRequested(
      OpenCopPrincipal authentication, Layer target, List<UUID> sourceLayerIds) {
    var actor = Caller.of(authentication);
    var sources = sourceLayerIds.stream().map(layerManager::get).toList();

    auditRecorder.record(
        new AuditEvent(
            EntityType.LAYER,
            target.getId(),
            target.getName(),
            Operation.MERGED_FROM,
            actor,
            projection.mergedFrom(sources, target.getName()),
            AuditEvent.CURRENT_CHANGE_VERSION));

    for (var source : sources) {
      auditRecorder.record(
          new AuditEvent(
              EntityType.LAYER,
              source.getId(),
              source.getName(),
              Operation.MERGED_INTO,
              actor,
              projection.mergedInto(target, target.getName()),
              AuditEvent.CURRENT_CHANGE_VERSION));
    }
  }

  public MergeLayerValidationResponse validateMergeLayer(
      OpenCopPrincipal authentication, MergeLayerValidationRequest request) {
    var layerIds = validateShape(request == null ? null : request.layerIds());
    var activeFeatureCounts = validateSourceLayers(authentication, layerIds);
    return new MergeLayerValidationResponse(
        layerIds, activeFeatureCounts, collisionSummary(layerIds));
  }

  private static List<UUID> validateShape(List<UUID> layerIds) {
    if (layerIds == null || layerIds.size() < MINIMUM_SOURCE_LAYER_COUNT) {
      throw new GlobalOpenCopException(INVALID_LAYER_COUNT_MESSAGE, HttpStatus.BAD_REQUEST);
    }
    if (layerIds.size() > MAXIMUM_SOURCE_LAYER_COUNT) {
      throw new GlobalOpenCopException(TOO_MANY_LAYERS_MESSAGE, HttpStatus.BAD_REQUEST);
    }
    if (new HashSet<>(layerIds).size() != layerIds.size()) {
      throw new GlobalOpenCopException(DUPLICATE_LAYER_MESSAGE, HttpStatus.BAD_REQUEST);
    }
    return List.copyOf(layerIds);
  }

  private Map<UUID, Long> validateSourceLayers(
      OpenCopPrincipal authentication, List<UUID> layerIds) {
    layerIds.forEach(
        layerId -> {
          ResourceLifecyclePolicy.assertLayerActive(layerManager.get(layerId), "merge layer");
          layerAuthorizationService.assertLayerRole(
              layerId,
              authentication,
              MERGE_ROLE_REQUIRED_MESSAGE,
              LayerRole.OWNER,
              LayerRole.ADMIN);
        });
    var activeFeatureCounts = validationGateway.countActiveFeatures(layerIds);
    if (layerIds.stream()
        .anyMatch(layerId -> activeFeatureCounts.getOrDefault(layerId, 0L) <= 0L)) {
      throw new GlobalOpenCopException(EMPTY_LAYER_MESSAGE, HttpStatus.CONFLICT);
    }
    return activeFeatureCounts;
  }

  private MergeLayerValidationResponse.CollisionSummary collisionSummary(List<UUID> layerIds) {
    var pairwiseCounts =
        validationGateway.countPairwiseCollisions(layerIds).stream()
            .map(
                collision ->
                    new MergeLayerValidationResponse.PairwiseCollisionCount(
                        collision.firstLayerId(), collision.secondLayerId(), collision.count()))
            .toList();
    var totalCollisions =
        pairwiseCounts.stream()
            .mapToLong(MergeLayerValidationResponse.PairwiseCollisionCount::count)
            .sum();
    return new MergeLayerValidationResponse.CollisionSummary(totalCollisions, pairwiseCounts);
  }

  /**
   * Unlocks the sources after a failed publish. The MERGED_FROM / MERGED_INTO rows committed with
   * the request and are deliberately left in place: a merge really was requested, so removing them
   * would make history lie. A reader therefore sees merge rows on a source that is active again,
   * which is a product call rather than a bug.
   */
  private void compensatePublishFailure(
      UUID targetLayerId, List<UUID> sourceLayerIds, Exception publishFailure) {
    markOperationFailedAfterPublishFailure(targetLayerId, publishFailure);
    try {
      layersRepository.finalizeMergeFailure(targetLayerId, sourceLayerIds);
      log.warn(
          "Marked target layer {} as MERGE_FAILED and unlocked source layers {} after layer merge job publish failure",
          targetLayerId,
          sourceLayerIds,
          publishFailure);
    } catch (RepositoryException compensationFailure) {
      log.error(
          "Failed to compensate layer merge job publish failure for target {} and source layers {}",
          targetLayerId,
          sourceLayerIds,
          compensationFailure);
    }
  }

  private void markOperationFailedAfterPublishFailure(
      UUID targetLayerId, Exception publishFailure) {
    try {
      layerMergeOperationRepository.markFailed(targetLayerId, publishFailureReason(publishFailure));
    } catch (RuntimeException operationFailure) {
      log.error(
          "Failed to mark layer merge operation as FAILED after job publish failure for target {}",
          targetLayerId,
          operationFailure);
    }
  }

  private static String publishFailureReason(Exception publishFailure) {
    if (publishFailure == null) {
      return PUBLISH_FAILURE_REASON_PREFIX;
    }
    return PUBLISH_FAILURE_REASON_PREFIX + ": " + publishFailure.getClass().getSimpleName();
  }

  private static LayerManagerException mapRepositoryException(RepositoryException e) {
    var code = e.getCode();
    if (code.equals(RepositoryExceptionCodes.ALREADY_EXIST.code())
        || code.equals(RepositoryExceptionCodes.NAME_ALREADY_TAKEN.code())) {
      return new LayerManagerException(code, e.getMessage(), HttpStatus.CONFLICT);
    }
    if (code.equals(RepositoryExceptionCodes.DOES_NOT_EXIST.code())
        || code.equals(RepositoryExceptionCodes.ELEMENT_NOT_FOUND.code())) {
      return new LayerManagerException(code, e.getMessage(), HttpStatus.NOT_FOUND);
    }
    return new LayerManagerException(code, e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
