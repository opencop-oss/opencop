// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reconciles enabled socket source ownership toward route-fresh Connect instances. */
public class DataSourceAssignmentReconciler {

  private static final Logger log = LoggerFactory.getLogger(DataSourceAssignmentReconciler.class);

  private final DataSourceRepository dataSourceRepository;
  private final ConnectRoutingTargetResolver routingTargetResolver;
  private final SocketReconciliationNudgePublisher nudgePublisher;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public DataSourceAssignmentReconciler(
      DataSourceRepository dataSourceRepository,
      ConnectRoutingTargetResolver routingTargetResolver,
      SocketReconciliationNudgePublisher nudgePublisher) {
    this.dataSourceRepository = dataSourceRepository;
    this.routingTargetResolver = routingTargetResolver;
    this.nudgePublisher = nudgePublisher;
  }

  public record ReconciliationResult(
      int enabledSocketCount,
      int unchangedCount,
      int reassignedCount,
      boolean skipped,
      boolean failed) {
    static ReconciliationResult skippedResult() {
      return new ReconciliationResult(0, 0, 0, true, false);
    }

    static ReconciliationResult failedResult() {
      return new ReconciliationResult(0, 0, 0, false, true);
    }
  }

  public ReconciliationResult reconcileOnce() {
    if (!running.compareAndSet(false, true)) {
      log.debug("Data source assignment reconciliation already running");
      return ReconciliationResult.skippedResult();
    }

    try {
      return doReconcileOnce();
    } finally {
      running.set(false);
    }
  }

  private ReconciliationResult doReconcileOnce() {
    List<SocketDataSource> enabledSocketSources;
    List<ConnectInstanceInfo> routableInstances;
    try {
      enabledSocketSources =
          dataSourceRepository
              .findAll(
                  new DataSourceReadFilter(null, null, RecordVisibility.ACTIVE, null, true, null))
              .stream()
              .filter(SocketDataSource.class::isInstance)
              .map(SocketDataSource.class::cast)
              .filter(SocketDataSource::enabled)
              .toList();
      routableInstances =
          enabledSocketSources.isEmpty()
              ? List.of()
              : routingTargetResolver.batchRoutableInstances();
    } catch (Exception e) {
      log.warn("Data source assignment reconciliation aborted: {}", e.getMessage(), e);
      return ReconciliationResult.failedResult();
    }

    if (enabledSocketSources.isEmpty()) {
      return new ReconciliationResult(0, 0, 0, false, false);
    }

    if (routableInstances.isEmpty()) {
      log.warn(
          "Data source assignment reconciliation found {} enabled socket sources but no routable"
              + " Connect instances",
          enabledSocketSources.size());
      return new ReconciliationResult(enabledSocketSources.size(), 0, 0, false, false);
    }

    var targetSelector =
        routingTargetResolver.batchOwnershipTargetSelector(
            routableInstances,
            enabledSocketSources.stream().map(SocketDataSource::assignedInstance).toList());
    var unchanged = 0;
    var reassigned = 0;

    for (var source : enabledSocketSources) {
      var assignedInstance = source.assignedInstance();
      var target =
          targetSelector.resolveOwnershipTarget(
              source.id(), assignedInstance, "ASSIGNMENT_RECONCILIATION");
      if (!target.assignmentChanged()) {
        unchanged++;
        continue;
      }

      try {
        // Reconciliation runs on a schedule, so there is no user behind the reassignment.
        dataSourceRepository.setAssignedInstance(source.id(), target.instanceId(), null);
        reassigned++;
      } catch (Exception e) {
        log.warn(
            "Failed to reassign source {} from {} to {}: {}",
            source.id(),
            assignedInstance,
            target.instanceId(),
            e.getMessage(),
            e);
        continue;
      }
    }

    if (reassigned > 0) {
      log.info(
          "Data source assignment reconciliation reassigned {} of {} enabled socket sources",
          reassigned,
          enabledSocketSources.size());
      publishReconciliationNudge(reassigned);
    }

    return new ReconciliationResult(
        enabledSocketSources.size(), unchanged, reassigned, false, false);
  }

  private void publishReconciliationNudge(int reassigned) {
    try {
      nudgePublisher.requestReconciliation();
    } catch (Exception e) {
      log.warn(
          "Published desired ownership for {} reassigned sources, but Postgres reconciliation nudge failed: {}",
          reassigned,
          e.getMessage(),
          e);
    }
  }
}
