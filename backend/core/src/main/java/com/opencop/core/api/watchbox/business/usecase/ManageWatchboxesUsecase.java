// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.usecase;

import com.opencop.core.api.general.ports.usecase.runtimefeature.CheckRuntimeFeatureAvailability;
import com.opencop.core.api.watchbox.business.factory.WatchboxConfigFactory;
import com.opencop.core.api.watchbox.business.model.WatchboxConfig;
import com.opencop.core.api.watchbox.business.policy.WatchboxLayerAccessPolicy;
import com.opencop.core.api.watchbox.business.policy.WatchboxPatchPolicy;
import com.opencop.core.api.watchbox.business.service.WatchboxDetectionLivenessService;
import com.opencop.core.api.watchbox.business.service.WatchboxDevStatusOverrideService;
import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.api.watchbox.business.service.WatchboxResponseAssembler;
import com.opencop.core.api.watchbox.business.service.WatchboxSourceEligibilityService;
import com.opencop.core.api.watchbox.business.service.WatchboxValidationService;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigAuditRecorder;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigRepository;
import com.opencop.core.api.watchbox.ports.usecase.ManageWatchboxes;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.sdk.model.runtimefeature.BackendRuntimeFeature;
import com.opencop.core.sdk.model.watchbox.WatchboxCreateRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.model.watchbox.WatchboxPatchRequest;
import com.opencop.core.sdk.model.watchbox.WatchboxResetReason;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.geojson.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

public class ManageWatchboxesUsecase implements ManageWatchboxes {

  private static final Logger log = LoggerFactory.getLogger(ManageWatchboxesUsecase.class);

  private final WatchboxConfigRepository repository;
  private final WatchboxValidationService validationService;
  private final WatchboxSourceEligibilityService sourceEligibilityService;
  private final WatchboxResponseAssembler responseAssembler;
  private final WatchboxLayerAccessPolicy layerAccessPolicy;
  private final WatchboxConfigFactory configFactory;
  private final WatchboxPatchPolicy patchPolicy;
  private final CheckRuntimeFeatureAvailability runtimeFeatureAvailability;
  private final WatchboxDetectionLivenessService detectionLiveness;
  private final WatchboxConfigAuditRecorder auditRecorder;
  private final Optional<WatchboxDevStatusOverrideService> devStatusOverrideService;

  public ManageWatchboxesUsecase(
      WatchboxConfigRepository repository,
      WatchboxValidationService validationService,
      WatchboxSourceEligibilityService sourceEligibilityService,
      WatchboxResponseAssembler responseAssembler,
      WatchboxLayerAccessPolicy layerAccessPolicy,
      WatchboxConfigFactory configFactory,
      WatchboxPatchPolicy patchPolicy,
      CheckRuntimeFeatureAvailability runtimeFeatureAvailability,
      WatchboxDetectionLivenessService detectionLiveness,
      WatchboxConfigAuditRecorder auditRecorder,
      Optional<WatchboxDevStatusOverrideService> devStatusOverrideService) {
    this.repository = repository;
    this.validationService = validationService;
    this.sourceEligibilityService = sourceEligibilityService;
    this.responseAssembler = responseAssembler;
    this.layerAccessPolicy = layerAccessPolicy;
    this.configFactory = configFactory;
    this.patchPolicy = patchPolicy;
    this.runtimeFeatureAvailability = runtimeFeatureAvailability;
    this.detectionLiveness = detectionLiveness;
    this.auditRecorder = auditRecorder;
    this.devStatusOverrideService = devStatusOverrideService;
  }

  @Transactional
  public WatchboxResponse create(WatchboxCreateRequest request, WatchboxCaller caller) {
    requireRequest(request);
    assertWatchboxAvailable(caller.userId());
    Set<UUID> layerIds = validationService.validateLayerIds(request.layerIds());
    layerAccessPolicy.assertCanManage(layerIds, caller);
    validationService.validateActiveLayers(layerIds);

    boolean enabled = request.enabled() == null || request.enabled();
    if (enabled) {
      sourceEligibilityService.assertEligibleForEnabledWatchbox(layerIds);
    }

    String name = configFactory.normalizeName(request.name());
    String description = configFactory.normalizeDescription(request.description());
    var direction = configFactory.defaultDirection(request.direction());
    var polygon = validationService.validateAndNormalizePolygon(request.polygon());
    var filter = validationService.validateFilter(request.filter());
    var config =
        configFactory.create(name, description, enabled, direction, polygon, filter, layerIds);
    WatchboxConfig saved = repository.create(config, caller.userId());
    auditRecorder.recordCreate(saved, caller);
    WatchboxResponse response = assembleManagerResponse(saved, caller);
    log.info(
        "WATCHBOX_CONFIG operation=create watchbox_id={} actor_user_id={} enabled={} layer_count={} config_epoch={}",
        saved.id(),
        caller.userId(),
        saved.enabled(),
        saved.layerIds().size(),
        saved.configEpoch());
    return response;
  }

  @Transactional(readOnly = true)
  public List<WatchboxResponse> list(WatchboxCaller caller) {
    // Every request-scoped input is resolved once for the whole request, never per watchbox:
    // detection liveness is one global heartbeat read, layer access is one reachability read over
    // the union of every active watchbox's layers, and the assembler batches the source-status
    // and source-name reads across all visible watchboxes.
    var liveness = detectionLiveness.detectionLiveness();
    List<WatchboxConfig> configs = repository.listActive();
    Set<UUID> layerIds =
        configs.stream()
            .flatMap(config -> config.layerIds().stream())
            .collect(Collectors.toUnmodifiableSet());
    var access = layerAccessPolicy.contextFor(layerIds, caller);
    return responseAssembler.assembleVisibleAll(configs, access, liveness);
  }

  @Transactional(readOnly = true)
  public WatchboxResponse get(UUID watchboxId, WatchboxCaller caller) {
    var config = repository.findActive(watchboxId).orElseThrow(this::notFound);
    return responseAssembler
        .assembleVisible(config, caller, detectionLiveness.detectionLiveness())
        .orElseThrow(this::notFound);
  }

  @Transactional
  public WatchboxResponse patch(
      UUID watchboxId, WatchboxPatchRequest request, WatchboxCaller caller) {
    requireRequest(request);
    // Locked read: a reset's epoch is allocated as read-epoch + 1 and the V37 guard only rejects
    // epoch decreases, so two concurrent reset patches would otherwise both commit the same
    // generation. The lock serializes them; last-writer-wins semantics are unchanged.
    WatchboxConfig existing =
        repository.findActiveForUpdate(watchboxId).orElseThrow(this::notFound);
    layerAccessPolicy.assertVisible(existing.layerIds(), caller);
    layerAccessPolicy.assertCanManage(existing.layerIds(), caller);

    Set<UUID> nextLayerIds =
        request.layerIds() == null
            ? existing.layerIds()
            : validationService.validateLayerIds(request.layerIds());

    Polygon nextPolygon =
        request.polygon() == null
            ? existing.polygon()
            : validationService.validateAndNormalizePolygon(request.polygon());
    WatchboxFilter nextFilter =
        request.filter() == null
            ? existing.filter()
            : validationService.validateFilter(request.filter());
    var candidate = configFactory.patch(existing, request, nextLayerIds, nextPolygon, nextFilter);
    boolean cleanupOnlyPatch = patchPolicy.isCleanupOnlyPatch(existing, candidate);

    boolean featureAvailable =
        runtimeFeatureAvailability.isAvailable(BackendRuntimeFeature.WATCHBOX, caller.userId());
    boolean requiresEnabledValidation = featureAvailable && !cleanupOnlyPatch;
    if (!featureAvailable) {
      assertDisabledModePatchAllowed(cleanupOnlyPatch);
    }
    if (requiresEnabledValidation && request.layerIds() != null) {
      layerAccessPolicy.assertCanManage(nextLayerIds, caller);
    }
    if (requiresEnabledValidation) {
      validationService.validateActiveLayers(nextLayerIds);
    }

    var patchDecision = patchPolicy.decide(existing, candidate, request, cleanupOnlyPatch);

    if (candidate.enabled()) {
      sourceEligibilityService.assertEligibleForEnabledWatchbox(candidate.layerIds());
    }

    // Watchbox config intentionally follows the app's existing last-writer-wins convention until
    // conflict UX is designed end to end.
    var next = configFactory.withConfigEpoch(candidate, patchDecision.nextEpoch());
    WatchboxConfig saved = repository.update(next);
    var auditResetReasons =
        patchDecision.resetReasons().stream().map(WatchboxResetReason::valueOf).toList();
    auditRecorder.recordPatch(existing, saved, auditResetReasons, caller);
    WatchboxResponse response = assembleManagerResponse(saved, caller);
    log.info(
        "WATCHBOX_CONFIG operation=patch watchbox_id={} actor_user_id={} enabled={} layer_count={} config_epoch_before={} config_epoch_after={} reset_reasons={}",
        saved.id(),
        caller.userId(),
        saved.enabled(),
        saved.layerIds().size(),
        existing.configEpoch(),
        saved.configEpoch(),
        patchDecision.resetReasons());
    return response;
  }

  @Transactional
  public void delete(UUID watchboxId, WatchboxCaller caller) {
    WatchboxConfig existing =
        repository.findActiveForUpdate(watchboxId).orElseThrow(this::notFound);
    layerAccessPolicy.assertVisible(existing.layerIds(), caller);
    layerAccessPolicy.assertCanManage(existing.layerIds(), caller);
    repository.archive(watchboxId, caller.userId());
    auditRecorder.recordArchive(existing, caller);
    devStatusOverrideService.ifPresent(service -> service.clear(watchboxId));
    log.info(
        "WATCHBOX_CONFIG operation=delete watchbox_id={} actor_user_id={}",
        watchboxId,
        caller.userId());
  }

  private WatchboxResponse assembleManagerResponse(WatchboxConfig config, WatchboxCaller caller) {
    // A concurrent IAM change between initial authorization and this post-write revalidation can
    // make the manager's own watchbox invisible. Treat that as authorization loss rather than a
    // foreign 500; throwing here rolls back the transaction and emits no success-shaped log.
    return responseAssembler
        .assembleVisible(config, caller, detectionLiveness.detectionLiveness())
        .orElseThrow(
            () ->
                new WatchboxException(
                    WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED,
                    "Watchbox management role is required",
                    HttpStatus.FORBIDDEN,
                    List.of("layerIds")));
  }

  private void assertWatchboxAvailable(UUID userId) {
    if (!runtimeFeatureAvailability.isAvailable(BackendRuntimeFeature.WATCHBOX, userId)) {
      throw new WatchboxException(
          WatchboxErrorCode.WATCHBOX_FEATURE_DISABLED,
          "Watchbox feature is disabled",
          HttpStatus.BAD_REQUEST,
          List.of(),
          List.of());
    }
  }

  private void requireRequest(Object request) {
    if (request == null) {
      throw new WatchboxException(
          WatchboxErrorCode.WATCHBOX_INVALID_REQUEST,
          "Watchbox request body is required",
          HttpStatus.BAD_REQUEST,
          List.of("body"));
    }
  }

  private void assertDisabledModePatchAllowed(boolean cleanupOnlyPatch) {
    if (!cleanupOnlyPatch) {
      throw new WatchboxException(
          WatchboxErrorCode.WATCHBOX_FEATURE_DISABLED,
          "Watchbox feature is disabled",
          HttpStatus.BAD_REQUEST,
          List.of(),
          List.of());
    }
  }

  private WatchboxException notFound() {
    return new WatchboxException(
        WatchboxErrorCode.WATCHBOX_NOT_FOUND,
        "Watchbox not found",
        HttpStatus.NOT_FOUND,
        List.of(),
        List.of());
  }
}
