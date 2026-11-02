// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.service;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.watchbox.business.model.WatchboxConfig;
import com.opencop.core.api.watchbox.business.model.WatchboxSourceStatusRecord;
import com.opencop.core.api.watchbox.business.policy.WatchboxLayerAccessPolicy;
import com.opencop.core.api.watchbox.business.policy.WatchboxLayerAccessPolicy.LayerAccessContext;
import com.opencop.core.api.watchbox.business.policy.WatchboxLayerAccessPolicy.LayerAccessDecision;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigRepository;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.model.watchbox.WatchboxAlertingStatus;
import com.opencop.core.sdk.model.watchbox.WatchboxCapabilities;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxSourceStatusResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusReason;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionLivenessReader.DetectionLiveness;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class WatchboxResponseAssembler {

  private final WatchboxConfigRepository repository;
  private final DataSourceRepository dataSourceRepository;
  private final WatchboxLayerAccessPolicy layerAccessPolicy;
  private final Optional<WatchboxDevStatusOverrideService> devStatusOverrideService;

  public WatchboxResponseAssembler(
      WatchboxConfigRepository repository,
      DataSourceRepository dataSourceRepository,
      WatchboxLayerAccessPolicy layerAccessPolicy,
      Optional<WatchboxDevStatusOverrideService> devStatusOverrideService) {
    this.repository = repository;
    this.dataSourceRepository = dataSourceRepository;
    this.layerAccessPolicy = layerAccessPolicy;
    this.devStatusOverrideService = devStatusOverrideService;
  }

  /**
   * {@code detectionLiveness} is the request-scoped Processor liveness answer; callers read it once
   * per request (it is one global heartbeat read, not per-watchbox) and must fail closed - a failed
   * read is passed as {@code DetectionLiveness.NOT_RUNNING}, never a healthy value.
   */
  public Optional<WatchboxResponse> assembleVisible(
      WatchboxConfig config, WatchboxCaller caller, DetectionLiveness detectionLiveness) {
    LayerAccessDecision decision = layerAccessPolicy.decide(config.layerIds(), caller);
    if (!decision.fullyVisible()) {
      return Optional.empty();
    }
    return Optional.of(assemble(config, decision, detectionLiveness));
  }

  /**
   * List-path batch variant: decides visibility for every config from the prefetched caller role
   * snapshot, then assembles all visible watchboxes from one bulk source-status read and at most
   * one source-name lookup across the manager-visible rows, instead of per-watchbox reads. {@code
   * access} must have been built for a superset of the configs' layer ids within the same request.
   * Output content and order match assembling each config individually.
   */
  public List<WatchboxResponse> assembleVisibleAll(
      List<WatchboxConfig> configs,
      LayerAccessContext access,
      DetectionLiveness detectionLiveness) {
    List<VisibleConfig> visible = new ArrayList<>();
    for (WatchboxConfig config : configs) {
      LayerAccessDecision decision = access.decide(config.layerIds());
      if (decision.fullyVisible()) {
        visible.add(new VisibleConfig(config, decision));
      }
    }
    if (visible.isEmpty()) {
      return List.of();
    }
    Map<UUID, List<WatchboxSourceStatusRecord>> statusesByWatchboxId =
        repository.findSourceStatusesForWatchboxes(
            visible.stream()
                .collect(
                    Collectors.toMap(
                        entry -> entry.config().id(), entry -> entry.config().configEpoch())));
    List<WatchboxSourceStatusRecord> managerStatuses =
        visible.stream()
            .filter(entry -> entry.decision().managesAll())
            .flatMap(
                entry -> statusesByWatchboxId.getOrDefault(entry.config().id(), List.of()).stream())
            .toList();
    Map<UUID, String> sourceNames = sourceNamesById(managerStatuses);
    return visible.stream()
        .map(
            entry ->
                assemble(
                    entry.config(),
                    entry.decision(),
                    detectionLiveness,
                    statusesByWatchboxId.getOrDefault(entry.config().id(), List.of()),
                    sourceNames))
        .toList();
  }

  private record VisibleConfig(WatchboxConfig config, LayerAccessDecision decision) {}

  private WatchboxResponse assemble(
      WatchboxConfig config, LayerAccessDecision decision, DetectionLiveness detectionLiveness) {
    List<WatchboxSourceStatusRecord> statuses =
        repository.findSourceStatuses(config.id(), config.configEpoch());
    Map<UUID, String> sourceNames = decision.managesAll() ? sourceNamesById(statuses) : Map.of();
    return assemble(config, decision, detectionLiveness, statuses, sourceNames);
  }

  private WatchboxResponse assemble(
      WatchboxConfig config,
      LayerAccessDecision decision,
      DetectionLiveness detectionLiveness,
      List<WatchboxSourceStatusRecord> statuses,
      Map<UUID, String> sourceNames) {
    boolean manager = decision.managesAll();
    Rollup rollup = rollup(config.enabled(), statuses, detectionLiveness);

    var response =
        new WatchboxResponse(
            config.id(),
            config.name(),
            config.description(),
            config.enabled(),
            config.direction(),
            config.polygon(),
            config.filter(),
            config.layerIds(),
            config.configEpoch(),
            rollup.status(),
            rollup.reasons(),
            manager ? sourceStatusResponses(statuses, sourceNames) : List.of(),
            new WatchboxCapabilities(manager, manager),
            config.createdAt(),
            config.updatedAt());
    return devStatusOverrideService
        .map(service -> service.apply(response, manager))
        .orElse(response);
  }

  private Rollup rollup(
      boolean enabled,
      List<WatchboxSourceStatusRecord> statuses,
      DetectionLiveness detectionLiveness) {
    if (!enabled) {
      return new Rollup(
          WatchboxAlertingStatus.DISABLED, Set.of(WatchboxStatusReason.WATCHBOX_DISABLED));
    }
    var reasons = new LinkedHashSet<WatchboxStatusReason>();
    boolean failed = false;
    boolean degraded = false;
    for (WatchboxSourceStatusRecord status : statuses) {
      reasons.add(status.reason());
      failed = failed || status.status() == WatchboxAlertingStatus.FAILED;
      degraded = degraded || status.status() == WatchboxAlertingStatus.DEGRADED;
    }
    switch (detectionLiveness) {
      case NOT_RUNNING -> {
        // Status rows are written by the Processor, so with the Processor down the rows cannot
        // carry the bad news themselves. Without this input an empty status list read as ACTIVE
        // while nothing was being detected.
        reasons.add(WatchboxStatusReason.WATCHBOX_DETECTION_NOT_RUNNING);
        degraded = true;
      }
      case PARTIALLY_STALLED -> {
        // Detection is alive but a wedged listener partition has stopped advancing past the
        // degrade threshold: alerts whose events land on that partition are silently delayed,
        // which an ACTIVE rollup would misrepresent.
        reasons.add(WatchboxStatusReason.WATCHBOX_DETECTION_PARTIALLY_STALLED);
        degraded = true;
      }
      case RUNNING -> {}
    }
    if (failed) {
      return new Rollup(WatchboxAlertingStatus.FAILED, Set.copyOf(reasons));
    }
    if (degraded) {
      return new Rollup(WatchboxAlertingStatus.DEGRADED, Set.copyOf(reasons));
    }
    return new Rollup(WatchboxAlertingStatus.ACTIVE, Set.of());
  }

  private List<WatchboxSourceStatusResponse> sourceStatusResponses(
      List<WatchboxSourceStatusRecord> statuses, Map<UUID, String> sourceNamesById) {
    return statuses.stream()
        .sorted(Comparator.comparing(WatchboxSourceStatusRecord::sourceId))
        .map(
            status ->
                new WatchboxSourceStatusResponse(
                    status.sourceId(),
                    sourceNamesById.get(status.sourceId()),
                    status.status(),
                    status.reason(),
                    status.sinceAt(),
                    status.observedCount(),
                    status.cap(),
                    status.updatedAt()))
        .toList();
  }

  private Map<UUID, String> sourceNamesById(List<WatchboxSourceStatusRecord> statuses) {
    Set<UUID> sourceIds =
        statuses.stream().map(WatchboxSourceStatusRecord::sourceId).collect(Collectors.toSet());
    if (sourceIds.isEmpty()) {
      return Map.of();
    }
    return dataSourceRepository
        .findAll(new DataSourceReadFilter(null, sourceIds, RecordVisibility.ALL, null, null, null))
        .stream()
        .collect(Collectors.toMap(DataSource::id, DataSource::name, (left, right) -> left));
  }

  private record Rollup(WatchboxAlertingStatus status, Set<WatchboxStatusReason> reasons) {}
}
