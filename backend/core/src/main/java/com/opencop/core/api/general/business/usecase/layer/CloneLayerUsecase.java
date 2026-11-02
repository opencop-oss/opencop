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
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.LayerCopyJobSubmission;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class CloneLayerUsecase {

  private static final Logger log = LoggerFactory.getLogger(CloneLayerUsecase.class);

  private final LayersRepository layersRepository;
  private final LayerMemberRepository layerMemberRepository;
  private final LayerManager<Layer, LayerMember> layerManager;
  private final LayerAuthorizationService layerAuthorizationService;
  private final TransactionTemplate transactionTemplate;
  private final JobPublisher jobPublisher;
  private final AuditRecorder auditRecorder;
  private final LayerAuditProjection projection;

  public CloneLayerUsecase(
      LayersRepository layersRepository,
      LayerMemberRepository layerMemberRepository,
      LayerManager<Layer, LayerMember> layerManager,
      LayerAuthorizationService layerAuthorizationService,
      PlatformTransactionManager transactionManager,
      JobPublisher jobPublisher,
      AuditRecorder auditRecorder,
      LayerAuditProjection projection) {
    this.layersRepository = layersRepository;
    this.layerMemberRepository = layerMemberRepository;
    this.layerManager = layerManager;
    this.layerAuthorizationService = layerAuthorizationService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    this.jobPublisher = jobPublisher;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  public Layer cloneLayer(
      UUID sourceLayerId, OpenCopPrincipal authentication, CloneLayerRequest request) {
    Instant startedAt = Instant.now();
    log.info(
        "Starting layer clone from source {} for user {} with name='{}', accessScope={}, copyRemarks={}, copyMedia={}",
        sourceLayerId,
        authentication.internalUserId(),
        request.name(),
        request.accessScope(),
        request.copyRemarks(),
        request.copyMedia());
    assertCloneCreatesPrivateLayer(request.accessScope());
    var sourceLayer = layerManager.get(sourceLayerId);
    ResourceLifecyclePolicy.assertLayerActive(sourceLayer, "clone layer");
    if (!authentication.isGlobalAdmin()) {
      log.debug(
          "Authorizing layer clone for source {} and user {} against OWNER/ADMIN roles",
          sourceLayerId,
          authentication.internalUserId());
      layerAuthorizationService.assertLayerRole(
          sourceLayerId,
          authentication,
          "Only layer owners and admins can clone this layer",
          LayerRole.OWNER,
          LayerRole.ADMIN);
    } else {
      log.debug(
          "Skipping layer role authorization for source {} because user {} is global admin",
          sourceLayerId,
          authentication.internalUserId());
    }

    return transactionTemplate.execute(
        _ -> {
          try {
            var layer =
                layersRepository.create(request.name(), null, authentication.internalUserId());
            log.info(
                "Created target layer {} while cloning source {}", layer.getId(), sourceLayerId);

            var members = new LinkedHashMap<UUID, LayerRole>();
            if (request.users() != null) {
              members.putAll(request.users());
            }
            members.put(authentication.internalUserId(), LayerRole.OWNER);
            var appliedMembers = layerMemberRepository.addLayerMembers(layer.getId(), members);
            log.debug("Added {} layer members to cloned layer {}", members.size(), layer.getId());

            // Two rows, one per ledger, from the same transaction: the copy records where it came
            // from, and the original records that it was copied, so neither drawer has to query
            // the other to answer an obvious question.
            var actor = Caller.of(authentication);
            auditRecorder.record(
                new AuditEvent(
                    EntityType.LAYER,
                    layer.getId(),
                    layer.getName(),
                    Operation.CREATED_FROM_CLONE,
                    actor,
                    projection.createdFromClone(sourceLayer, appliedMembers),
                    AuditEvent.CURRENT_CHANGE_VERSION));
            auditRecorder.record(
                new AuditEvent(
                    EntityType.LAYER,
                    sourceLayerId,
                    sourceLayer.getName(),
                    Operation.CLONED_TO,
                    actor,
                    projection.clonedTo(layer),
                    AuditEvent.CURRENT_CHANGE_VERSION));

            jobPublisher.scheduleAfterCommit(
                LayerCopyJobSubmission.create(
                    JobIssuer.user(authentication.internalUserId()),
                    sourceLayerId,
                    layer.getId(),
                    request.copyRemarks() == null || request.copyRemarks(),
                    request.copyMedia() == null || request.copyMedia()));
            log.info(
                "Scheduled layer copy job after commit for source {} into target {} initiated by {}",
                sourceLayerId,
                layer.getId(),
                JobIssuer.user(authentication.internalUserId()));
            return layer;
          } catch (RepositoryException e) {
            log.error(
                "Layer clone failed for source {} after {} ms due to repository exception {}",
                sourceLayerId,
                Duration.between(startedAt, Instant.now()).toMillis(),
                e.getCode(),
                e);
            throw mapRepositoryException(e);
          }
        });
  }

  private static void assertCloneCreatesPrivateLayer(LayerAccessScope accessScope) {
    if (accessScope == LayerAccessScope.GLOBAL) {
      throw new GlobalOpenCopException(
          "Cloned layers must always be private", HttpStatus.BAD_REQUEST);
    }
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
