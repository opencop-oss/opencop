// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.orchestrator;

import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.business.service.ConnectRoutingTargetResolver;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.api.general.ports.usecase.connection.restart.RestartConnection;
import com.opencop.core.sdk.business.policy.LegacySocketAccessModePolicy;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Orchestrates a connection restart by advancing a durable restart epoch and nudging Connect.
 *
 * <p>Restart does not change final desired state. Connect rereads Postgres desired ownership before
 * applying the bounce.
 */
public class RestartOrchestrator implements RestartConnection {

  private static final Logger log = LoggerFactory.getLogger(RestartOrchestrator.class);

  private final DataSourceRepository dataSourceRepository;
  private final ConnectRoutingTargetResolver routingTargetResolver;
  private final ConnectorRestart connectorRestart;

  public RestartOrchestrator(
      DataSourceRepository dataSourceRepository,
      ConnectRoutingTargetResolver routingTargetResolver,
      ConnectorRestart connectorRestart) {
    this.dataSourceRepository = dataSourceRepository;
    this.routingTargetResolver = routingTargetResolver;
    this.connectorRestart = connectorRestart;
  }

  /** {@inheritDoc} */
  @Override
  public void restart(UUID sourceId, UUID userId) {
    log.info(
        "Restart requested for source {}{}",
        sourceId,
        userId != null ? " by user " + userId : " (internal)");

    var dataSource =
        dataSourceRepository
            .findById(sourceId)
            .orElseThrow(
                () ->
                    new GlobalOpenCopException(
                        "Data source not found: " + sourceId, HttpStatus.NOT_FOUND));

    ResourceLifecyclePolicy.assertDataSourceActive(dataSource, "restart");

    // Only socket-based sources can be restarted (restart = stop + start on Connect instance)
    if (!(dataSource instanceof SocketDataSource socket)) {
      throw new GlobalOpenCopException(
          "Restart is only supported for TCP/UDP sources", HttpStatus.BAD_REQUEST);
    }
    if (!socket.enabled()) {
      throw new GlobalOpenCopException(
          "Restart is only supported for enabled TCP/UDP sources", HttpStatus.CONFLICT);
    }
    if (LegacySocketAccessModePolicy.isIncompatible(socket)) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.UNSUPPORTED_ACCESS_MODE);
    }

    var routeWaitStartedAt = Instant.now();
    log.info("Resolving route-fresh Connect target for restart of source {}", sourceId);
    var target =
        routingTargetResolver.resolveOwnershipTarget(
            sourceId, socket.assignedInstance(), "RESTART");
    String instanceId = target.instanceId();
    log.info(
        "Resolved route-fresh Connect target {} for restart of source {} in {} ms",
        instanceId,
        sourceId,
        Duration.between(routeWaitStartedAt, Instant.now()).toMillis());
    if (target.assignmentChanged()) {
      // Orchestrated restart, not a user edit.
      dataSourceRepository.setAssignedInstance(sourceId, instanceId, null);
    }

    // No transaction spans the request, so the epoch commits before Connect is woken.
    connectorRestart.applyAndObserve(connectorRestart.request(sourceId, instanceId));
  }
}
