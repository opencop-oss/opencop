// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import com.opencop.core.api.general.business.service.ConnectRoutingTargetResolver.BatchOwnershipTargetSelector;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.business.policy.LegacySocketAccessModePolicy;
import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import jakarta.annotation.Nullable;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service to auto-start all enabled data sources.
 *
 * <p>Reads all enabled data sources from the DB and publishes a reconciliation nudge after desired
 * assignment is persisted. If a data source has no assigned instance, one is assigned via
 * least-loaded route-fresh selection.
 *
 * <p>Used by:
 *
 * <ul>
 *   <li>{@code DataSourceAutoStartConfig} - on Core startup (if enabled)
 *   <li>{@code DataSourceAutoStartEndpoint} - via admin API
 * </ul>
 */
@Service
public class DataSourceAutoStartService {

  private static final Logger log = LoggerFactory.getLogger(DataSourceAutoStartService.class);

  private final DataSourceRepository dataSourceRepository;
  private final SocketReconciliationNudgePublisher nudgePublisher;
  private final ConnectRoutingTargetResolver routingTargetResolver;
  private final AuditRecorder auditRecorder;
  private final DataSourceAuditProjection projection;
  private final TransactionTemplate transactionTemplate;

  public DataSourceAutoStartService(
      DataSourceRepository dataSourceRepository,
      SocketReconciliationNudgePublisher nudgePublisher,
      ConnectRoutingTargetResolver routingTargetResolver,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection,
      PlatformTransactionManager transactionManager) {
    this.dataSourceRepository = dataSourceRepository;
    this.nudgePublisher = nudgePublisher;
    this.routingTargetResolver = routingTargetResolver;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  public record AutoStartResult(int successCount, int failCount, int totalEnabled) {}

  /**
   * Auto-starts all enabled data sources.
   *
   * <p>Writes no history, on either the startup path or the admin endpoint. Every source it touches
   * is already enabled — that is how it selects them — so the run reconciles what is running
   * against what should be, and moves no state the ledger tracks. Recording it would add one row
   * per source on every invocation for a change that did not happen.
   *
   * @return result with counts of successful starts, failed starts, and total enabled sources
   */
  public AutoStartResult autoStartEnabledDataSources() {
    return autoStartEnabledDataSources(null);
  }

  /**
   * Auto-starts all enabled data sources using a pre-resolved route-fresh worker set when provided.
   *
   * @return result with counts of successful starts, failed starts, and total enabled sources
   */
  public AutoStartResult autoStartEnabledDataSources(
      @Nullable List<ConnectInstanceInfo> preResolvedRoutableInstances) {
    var enabledSources =
        dataSourceRepository.findAll(
            new DataSourceReadFilter(null, null, RecordVisibility.ACTIVE, null, true, null));

    if (enabledSources.isEmpty()) {
      log.info("No enabled data sources to start");
      return new AutoStartResult(0, 0, 0);
    }

    log.info("Found {} enabled data sources to start", enabledSources.size());

    var socketSources =
        enabledSources.stream()
            .filter(SocketDataSource.class::isInstance)
            .map(SocketDataSource.class::cast)
            .toList();
    List<ConnectInstanceInfo> resolvedRoutableInstances;
    if (socketSources.isEmpty()) {
      resolvedRoutableInstances = List.of();
    } else if (preResolvedRoutableInstances != null) {
      resolvedRoutableInstances = preResolvedRoutableInstances;
    } else {
      resolvedRoutableInstances = routingTargetResolver.batchRoutableInstances();
    }
    var targetSelector =
        routingTargetResolver.batchOwnershipTargetSelector(
            resolvedRoutableInstances,
            socketSources.stream().map(SocketDataSource::assignedInstance).toList());

    int successCount = 0;
    int failCount = 0;

    for (SocketDataSource socket : socketSources) {
      if (LegacySocketAccessModePolicy.isIncompatible(socket)) {
        log.atWarn()
            .addKeyValue("source", socket.id())
            .addKeyValue("transport", socket.provider())
            .addKeyValue("mode", socket.access().getAccess())
            .addKeyValue("cause", "incompatible_transport_access")
            .log("Skipping auto-start for incompatible socket access mode");
        failCount++;
        continue;
      }
      try {
        String startedOn = startWithFailover(socket, targetSelector);
        log.info(
            "Published auto-start desired state for data source: {} (name: {}, transport: {}) on"
                + " instance {}",
            socket.id(),
            socket.name(),
            socket.provider(),
            startedOn);
        successCount++;

      } catch (Exception e) {
        log.error(
            "Unexpected error starting data source {} ({}): {}",
            socket.id(),
            socket.name(),
            e.getMessage(),
            e);
        failCount++;
      }
    }

    log.info(
        "Auto-start enabled data sources complete: {} started, {} failed", successCount, failCount);
    if (successCount > 0) {
      publishReconciliationNudge(successCount);
    }
    return new AutoStartResult(successCount, failCount, enabledSources.size());
  }

  /**
   * Persists desired assignment for the selected route-fresh owner.
   *
   * @return the instance ID where the data source was started
   */
  private String startWithFailover(
      SocketDataSource ds, BatchOwnershipTargetSelector targetSelector) {
    var target = targetSelector.resolveOwnershipTarget(ds.id(), ds.assignedInstance(), "AUTOSTART");
    var instanceId = target.instanceId();

    persistDesiredAssignmentBeforeStart(ds, target);

    if (target.assignmentChanged()) {
      if (ds.assignedInstance() != null) {
        log.info(
            "Reassigned source {} from unavailable {} to {}",
            ds.id(),
            ds.assignedInstance(),
            instanceId);
      } else {
        log.info("Assigned source {} to instance {}", ds.id(), instanceId);
      }
    }
    return instanceId;
  }

  private void persistDesiredAssignmentBeforeStart(
      SocketDataSource ds, ConnectRoutingTargetResolver.OwnershipTarget target) {
    if (target.assignmentChanged()) {
      // Auto-start is system-initiated; no user drove this assignment.
      dataSourceRepository.setAssignedInstance(ds.id(), target.instanceId(), null);
    }
  }

  private void publishReconciliationNudge(int successCount) {
    try {
      nudgePublisher.requestReconciliation();
    } catch (Exception e) {
      log.warn(
          "Auto-start desired state for {} socket sources was persisted, but Postgres reconciliation nudge failed: {}",
          successCount,
          e.getMessage(),
          e);
    }
  }
}
