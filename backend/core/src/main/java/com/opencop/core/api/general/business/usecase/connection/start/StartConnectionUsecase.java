// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.start;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.model.connection.CurrentBrokerProvider;
import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.business.service.ConnectInstanceFailover;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.kafka.ByodSourceStatePublisher;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.api.general.ports.usecase.connection.start.StartConnection;
import com.opencop.core.sdk.business.policy.LegacySocketAccessModePolicy;
import com.opencop.core.sdk.model.datasource.ByodSourceState;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class StartConnectionUsecase implements StartConnection {

  private static final Logger log = LoggerFactory.getLogger(StartConnectionUsecase.class);
  private final ConnectionAuthorization authorization;
  private final DataSourceRepository dataSourceRepository;
  private final ConnectInstanceFailover instanceFailover;
  private final ConnectionMetadataGateway connections;
  private final String currentBrokerProvider;
  private final KafkaProvisioningService kafkaProvisioner;
  private final ByodSourceStatePublisher sourcePublisher;
  private final AuditRecorder auditRecorder;
  private final DataSourceAuditProjection projection;
  private final TransactionTemplate transactionTemplate;

  public StartConnectionUsecase(
      ConnectionAuthorization authorization,
      DataSourceRepository dataSourceRepository,
      ConnectInstanceFailover instanceFailover,
      ConnectionMetadataGateway connections,
      CurrentBrokerProvider currentBrokerProvider,
      KafkaProvisioningService kafkaProvisioner,
      ByodSourceStatePublisher sourcePublisher,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection,
      PlatformTransactionManager transactionManager) {
    this.auditRecorder = auditRecorder;
    this.projection = projection;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.authorization = authorization;
    this.dataSourceRepository = dataSourceRepository;
    this.instanceFailover = instanceFailover;
    this.connections = connections;
    this.currentBrokerProvider = currentBrokerProvider.name();
    this.kafkaProvisioner = kafkaProvisioner;
    this.sourcePublisher = sourcePublisher;
  }

  @Override
  public void start(UUID sourceId, Caller actor) {
    var userId = actor.userId();
    log.info("Request to start source {} initiated by {}", sourceId, userId);

    authorization.assertDirectOwnerOrAdmin(sourceId, userId, actor.globalAdmin());

    var dataSource =
        dataSourceRepository
            .findById(sourceId)
            .orElseThrow(
                () ->
                    new GlobalOpenCopException(
                        "Data source not found: " + sourceId, HttpStatus.NOT_FOUND));

    // Kafka sources belong to a specific broker provider — reject if this instance uses a
    // different one (the source would have been filtered out of findAll results anyway).
    if (dataSource instanceof KafkaDataSource kds
        && kds.brokerProvider() != null
        && !kds.brokerProvider().equals(currentBrokerProvider)) {
      log.warn(
          "Source {} belongs to broker provider {} but current is {} — returning 404",
          sourceId,
          kds.brokerProvider(),
          currentBrokerProvider);
      throw new GlobalOpenCopException("Data source not found: " + sourceId, HttpStatus.NOT_FOUND);
    }

    ResourceLifecyclePolicy.assertDataSourceActive(dataSource, "start");

    switch (dataSource) {
      case KafkaDataSource kafka -> {
        // Re-add WRITE ACL so external producer can write again. Deliberately before the
        // transaction below: an audit write that fails rolls the enable back but not the ACL, so
        // the topic is briefly writable while the source stays disabled. The next successful start
        // converges it; reconciliation does not re-publish STARTED for a disabled source.
        kafkaProvisioner.addWriteAcl(kafka.username(), kafka.topicName());
        // Committed with its row, so the enabled flag never exists without its history.
        var updatedAt =
            transactionTemplate.execute(
                _ -> {
                  // Read under the row lock inside the write's own transaction: a source already
                  // running leaves no row, and two starts racing each other produce one row
                  // rather than two. The ledger records what changed.
                  var moved = !connections.lockById(sourceId).enabled();
                  var enabledAt = dataSourceRepository.setEnabled(sourceId, true, userId);
                  if (moved) {
                    recordRunState(sourceId, dataSource, actor, Operation.CONNECT);
                  }
                  return enabledAt;
                });
        log.info("Started KAFKA source {}", sourceId);
        sourcePublisher.publish(
            new ByodSourceState(
                sourceId,
                kafka.topicName(),
                kafka.username(),
                ByodSourceState.Status.STARTED,
                updatedAt,
                ByodSourceState.CURRENT_VERSION));
      }
      case SocketDataSource socket -> {
        if (LegacySocketAccessModePolicy.isIncompatible(socket)) {
          throw new ConnectionManagementException(ConnectionExceptionCodes.UNSUPPORTED_ACCESS_MODE);
        }

        // TCP/UDP — start on Connect instance with failover
        instanceFailover.startWithFailover(sourceId, socket.assignedInstance(), true, true, userId);

        // Wait briefly for connection to establish (improves UX)
        instanceFailover.waitUntilNotConnecting(sourceId, Duration.ofSeconds(3));

        // The failover is not transactional at all: it issues `setAssignedInstance` and
        // `setEnabled` as independent statements, so this row cannot join it and the two flags are
        // not atomic with each other either. Accepted, with three consequences: `socket` is the
        // pre-failover read and so the only pre-state available here; an audit write that fails
        // returns 5xx for a source that is by then already running; and because the comparison is
        // not under a lock, unlike the Kafka branch, two concurrent starts can still write two
        // rows. A branch that threw never started the source and so leaves no row.
        if (!socket.enabled()) {
          transactionTemplate.executeWithoutResult(
              _ -> recordRunState(sourceId, dataSource, actor, Operation.CONNECT));
        }
      }
    }
  }

  private void recordRunState(
      UUID sourceId, DataSource dataSource, Caller actor, Operation operation) {
    auditRecorder.record(
        new AuditEvent(
            EntityType.DATA_SOURCE,
            sourceId,
            dataSource.name(),
            operation,
            actor,
            projection.transportOnly(dataSource),
            AuditEvent.CURRENT_CHANGE_VERSION));
  }
}
