// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.persistence;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.model.connection.NewConnection;
import com.opencop.core.api.general.business.model.connection.NewSocketConnection;
import com.opencop.core.api.general.business.policy.connection.ListenPortExclusivityPolicy;
import com.opencop.core.api.general.business.service.ConnectInstanceFailover;
import com.opencop.core.api.general.business.service.ConnectRoutingTargetResolver;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionIntegrationSettingsGateway;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceListenPortConflictException;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceNameConflictException;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.api.general.ports.usecase.connection.permissions.CreateSourcePermissions;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shared persistence flow for TCP, UDP, and RTSP connection creation.
 *
 * <p>Stores the connection its caller already validated and built. Within the creation transaction
 * it rechecks only what a concurrent change could have broken since that validation: it locks the
 * connection's integration and asserts it is still selectable, and asserts the listen port is still
 * free, mapping a reservation that races it to the {@link ListenPortExclusivityPolicy} error. It
 * then writes the data source with its permissions and instance assignment. After commit it
 * publishes the desired-state nudge.
 */
// TODO (in_progress): Split the Connect notification into a focused class. Reason: the constructor
// still reflects both persistence and notification.
@Component
public class ConnectionPersistenceHelper {

  private static final Logger log = LoggerFactory.getLogger(ConnectionPersistenceHelper.class);

  // TODO: Tenant is in DB schema but not yet implemented in the API.
  // When multi-tenancy is added, tenant should come from the request/auth context.
  private static final String DEFAULT_TENANT = "default";

  private final DataSourceRepository dataSourceRepository;
  private final CreateSourcePermissions permissionsService;
  private final ConnectRoutingTargetResolver routingTargetResolver;
  private final ConnectInstanceFailover instanceFailover;
  private final CompensatedTransaction transaction;
  private final ConnectionSetupRechecks rechecks;
  private final ConnectionIntegrationSettingsGateway settingsGateway;
  private final AuditRecorder auditRecorder;
  private final DataSourceAuditProjection projection;

  ConnectionPersistenceHelper(
      DataSourceRepository dataSourceRepository,
      CreateSourcePermissions permissionsService,
      ConnectRoutingTargetResolver routingTargetResolver,
      ConnectInstanceFailover instanceFailover,
      CompensatedTransaction transaction,
      ConnectionSetupRechecks rechecks,
      ConnectionIntegrationSettingsGateway settingsGateway,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection) {
    this.auditRecorder = auditRecorder;
    this.projection = projection;
    this.dataSourceRepository = dataSourceRepository;
    this.permissionsService = permissionsService;
    this.routingTargetResolver = routingTargetResolver;
    this.instanceFailover = instanceFailover;
    this.transaction = transaction;
    this.rechecks = rechecks;
    this.settingsGateway = settingsGateway;
  }

  /**
   * Persists a new connection with its permissions, setup prefill and settings in one {@link
   * CompensatedTransaction}, then wakes Connect and waits for it to settle once that committed.
   *
   * <p>The Connect instance that will run it is resolved first, so a creation with no route-fresh
   * instance fails before anything is stored. Should storing fail before the commit, the setup's
   * cleanup deletes what was provisioned for it; a commit whose outcome is unknown preserves it.
   *
   * @param connection the connection to create, whose owner is granted OWNER permissions
   * @throws GlobalOpenCopException if the name or listen port is already held, or no instances are
   *     available
   */
  public void persistAndStart(NewSocketConnection connection) {
    var setup = connection.setup();
    persistAndStart(
        new NewConnection(
            connection.sourceId(),
            connection.name(),
            setup.access(),
            setup.integration(),
            setup.settings(),
            setup.prefill()),
        setup.spec(),
        connection.owner(),
        setup.cleanup());
  }

  private void persistAndStart(
      NewConnection connection,
      ConnectionSpec spec,
      Caller actor,
      Optional<Runnable> persistenceCompensation) {
    var sourceId = connection.sourceId();
    var kind = spec.transport().kind();
    ConnectRoutingTargetResolver.OwnershipTarget target;
    try {
      target = routingTargetResolver.resolveOwnershipTarget(sourceId, null, "ADD");
    } catch (RuntimeException failure) {
      persistenceCompensation.ifPresent(
          compensation -> transaction.compensate(sourceId, compensation, failure));
      throw failure;
    }
    transaction.runWithoutResult(
        sourceId,
        kind,
        () -> persistToDatabase(connection, spec, actor, target),
        persistenceCompensation);

    instanceFailover.publishAndAwaitSettled(connection.sourceId());
  }

  /** Persist data source, permissions and integration settings atomically. */
  private void persistToDatabase(
      NewConnection connection,
      ConnectionSpec spec,
      Caller actor,
      ConnectRoutingTargetResolver.OwnershipTarget target) {
    rechecks.recheck(connection.sourceId(), connection.integration(), connection.access(), spec);

    // TODO: When multi-tenancy is implemented, get tenant from request/auth context
    var tenant = DEFAULT_TENANT;

    var dataSource =
        SocketDataSource.fromSpec(
            connection.sourceId(),
            connection.sourceName(),
            connection.access().ingressMapperId().orElse(null),
            connection.access().egressMapperId().orElse(null),
            tenant,
            spec,
            connection.integration(),
            connection.access().mode(),
            true,
            actor.userId());

    var instanceId = target.instanceId();
    log.info("Assigning new connection {} to instance {}", connection.sourceId(), instanceId);

    // Persist to Core DB
    try {
      dataSourceRepository.persist(dataSource);
    } catch (DataSourceNameConflictException _) {
      throw new ConnectionManagementException(
          ConnectionExceptionCodes.CONNECTION_NAME_ALREADY_EXISTS, connection.sourceName());
    } catch (DataSourceListenPortConflictException e) {
      throw ListenPortExclusivityPolicy.portAlreadyReserved(e.listenPort());
    }
    dataSourceRepository.setAssignedInstance(connection.sourceId(), instanceId, actor.userId());

    // Create permissions (same transaction as data source persist)
    permissionsService.createPermissions(connection.sourceId(), actor.userId());
    dataSourceRepository.setSetupPrefill(connection.sourceId(), connection.prefill());

    SettingsOverrides.of(connection.settings())
        .ifPresent(
            overrides ->
                settingsGateway.save(connection.sourceId(), connection.integration(), overrides));

    // Inside the compensated transaction that persists the source, so a creation that rolls back
    // takes its history row with it. Kafka sources record from KafkaConnectionPersistenceService.
    auditRecorder.record(
        new AuditEvent(
            EntityType.DATA_SOURCE,
            connection.sourceId(),
            connection.sourceName(),
            Operation.CREATE,
            actor,
            projection.create(dataSource.name(), connection.access().mode()),
            AuditEvent.CURRENT_CHANGE_VERSION));
  }
}
