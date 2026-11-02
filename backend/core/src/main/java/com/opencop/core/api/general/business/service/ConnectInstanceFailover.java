// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles desired socket starts with automatic assignment failover.
 *
 * <p>If the preferred instance is unavailable, selects another route-fresh instance. Updates
 * durable desired state before publishing a best-effort Postgres reconciliation nudge.
 */
public class ConnectInstanceFailover {

  private static final Logger log = LoggerFactory.getLogger(ConnectInstanceFailover.class);
  private static final Duration SETTLE_WAIT = Duration.ofSeconds(3);

  private final SocketReconciliationNudgePublisher nudgePublisher;
  private final ConnectRoutingTargetResolver routingTargetResolver;
  private final DataSourceRepository dataSourceRepository;
  private final DataSourceRuntimeService runtimeService;

  public ConnectInstanceFailover(
      SocketReconciliationNudgePublisher nudgePublisher,
      ConnectRoutingTargetResolver routingTargetResolver,
      DataSourceRepository dataSourceRepository,
      DataSourceRuntimeService runtimeService) {
    this.nudgePublisher = nudgePublisher;
    this.routingTargetResolver = routingTargetResolver;
    this.dataSourceRepository = dataSourceRepository;
    this.runtimeService = runtimeService;
  }

  /**
   * Persist desired start state with automatic failover across route-fresh instances.
   *
   * @param sourceId The source to start
   * @param preferredInstance The preferred instance to try first (can be null)
   * @param updateAssignment Whether to update DB assignment when the selected owner changes
   * @param updateEnabled Whether to update DB enabled state
   * @param actorId The user driving the start, or null when system-initiated
   * @return The selected desired owner instance ID
   */
  public String startWithFailover(
      UUID sourceId,
      String preferredInstance,
      boolean updateAssignment,
      boolean updateEnabled,
      UUID actorId) {

    var target = routingTargetResolver.resolveOwnershipTarget(sourceId, preferredInstance, "START");
    var instanceId = target.instanceId();

    log.debug("Persisting desired start for source {} on instance {}", sourceId, instanceId);
    persistDesiredStateBeforeStart(sourceId, updateAssignment, updateEnabled, target, actorId);
    publishReconciliationNudge(sourceId);

    if (instanceId.equals(preferredInstance)) {
      log.info("Desired source {} start assigned to preferred instance {}", sourceId, instanceId);
    } else if (preferredInstance != null) {
      log.info(
          "Desired source {} start assigned to instance {} (failover from unavailable {})",
          sourceId,
          instanceId,
          preferredInstance);
    } else {
      log.info(
          "Desired source {} start assigned to instance {} (new assignment)", sourceId, instanceId);
    }

    return instanceId;
  }

  /**
   * Wakes Connect once a source's desired state committed, best-effort, then waits briefly for its
   * connection to settle, so the caller reports a concrete state rather than CONNECTING.
   */
  public void publishAndAwaitSettled(UUID sourceId) {
    publishReconciliationNudge(sourceId);
    waitUntilNotConnecting(sourceId, SETTLE_WAIT);
  }

  /**
   * Polls runtime observations until the connection transitions out of CONNECTING state.
   *
   * <p>This is a UX improvement so the UI shows "CONNECTED" instead of "CONNECTING" after
   * add/start. If timeout or error, returns silently - the operation already succeeded.
   *
   * <p>UNKNOWN is treated as retryable: Connect may not have written an effective runtime
   * observation yet immediately after start. We wait for a concrete state before returning.
   */
  public void waitUntilNotConnecting(UUID sourceId, Duration timeout) {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      try {
        var dataSourceRuntime = runtimeService.getWithRuntime(sourceId);
        if (dataSourceRuntime == null) return;
        var status = dataSourceRuntime.runtime();
        var state = status.state();
        if (state != ConnectorState.CONNECTING && state != ConnectorState.UNKNOWN) {
          return;
        }
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception e) {
        // Polling failed - don't fail the operation
        return;
      }
    }
  }

  private void persistDesiredStateBeforeStart(
      UUID sourceId,
      boolean updateAssignment,
      boolean updateEnabled,
      ConnectRoutingTargetResolver.OwnershipTarget target,
      UUID actorId) {
    if (updateAssignment && target.assignmentChanged()) {
      dataSourceRepository.setAssignedInstance(sourceId, target.instanceId(), actorId);
    }
    if (updateEnabled) {
      dataSourceRepository.setEnabled(sourceId, true, actorId);
    }
  }

  private void publishReconciliationNudge(UUID sourceId) {
    try {
      nudgePublisher.requestReconciliation();
    } catch (Exception e) {
      log.warn(
          "Desired state for source {} was persisted, but Postgres reconciliation nudge failed: {}",
          sourceId,
          e.getMessage(),
          e);
    }
  }
}
