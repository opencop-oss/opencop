// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.stop;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.model.connection.CurrentBrokerProvider;
import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.kafka.ByodSourceStatePublisher;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.stop.StopConnection;
import com.opencop.core.sdk.model.datasource.ByodSourceState;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class StopConnectionUsecase implements StopConnection {

  private static final Logger log = LoggerFactory.getLogger(StopConnectionUsecase.class);
  private final ConnectionAuthorization authorization;
  private final SocketReconciliationNudgePublisher nudgePublisher;
  private final DataSourceRepository dataSourceRepository;
  private final ConnectionMetadataGateway connections;
  private final String currentBrokerProvider;
  private final KafkaProvisioningService kafkaProvisioner;
  private final ByodSourceStatePublisher sourcePublisher;
  private final AuditRecorder auditRecorder;
  private final DataSourceAuditProjection projection;
  private final TransactionTemplate transactionTemplate;

  public StopConnectionUsecase(
      ConnectionAuthorization authorization,
      SocketReconciliationNudgePublisher nudgePublisher,
      DataSourceRepository dataSourceRepository,
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
    this.nudgePublisher = nudgePublisher;
    this.dataSourceRepository = dataSourceRepository;
    this.connections = connections;
    this.currentBrokerProvider = currentBrokerProvider.name();
    this.kafkaProvisioner = kafkaProvisioner;
    this.sourcePublisher = sourcePublisher;
  }

  @Override
  public void stop(UUID sourceId, Caller actor) {
    var userId = actor.userId();
    log.info("Request to stop source {} initiated by {}", sourceId, userId);

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

    ResourceLifecyclePolicy.assertDataSourceActive(dataSource, "stop");

    // Persist user intent first. If the stop operation below fails:
    //   - Socket: connection may stay open until next service bounce, which won't restart
    //     it (enabled=false). No new data flows once the process exits.
    //   - Kafka: topic stays writable until Core restart, when ACL reconciliation removes
    //     the write ACL and publishes STOPPED to halt the Processor consumer.
    // Either way, user intent is eventually applied. This is an intentional fail-open design.
    // Committed together with its row: the intent and the history of it stand or fall as one, and
    // if the Kafka or nudge work below fails the source is still recorded as stopped because in
    // the database it is. Its own transaction, since this method deliberately has no enclosing one.
    var updatedAt =
        transactionTemplate.execute(
            _ -> {
              // Read under the row lock inside this transaction: a source already stopped leaves
              // no row, and two stops racing each other produce one row rather than two. The
              // ledger records what changed.
              var moved = connections.lockById(sourceId).enabled();
              var enabledAt = dataSourceRepository.setEnabled(sourceId, false, userId);
              if (moved) {
                recordRunState(sourceId, dataSource, actor, Operation.DISCONNECT);
              }
              return enabledAt;
            });

    switch (dataSource) {
      case KafkaDataSource kafka -> {
        // Remove WRITE ACL so external producer gets rejected. Topic, SCRAM user, and DESCRIBE
        // ACL stay intact — source is paused, not deleted.
        kafkaProvisioner.removeWriteAcl(kafka.username(), kafka.topicName());
        log.info("Stopped KAFKA source {}", sourceId);
        sourcePublisher.publish(
            new ByodSourceState(
                sourceId,
                kafka.topicName(),
                kafka.username(),
                ByodSourceState.Status.STOPPED,
                updatedAt,
                ByodSourceState.CURRENT_VERSION));
      }
      case SocketDataSource socket -> {
        publishReconciliationNudge(sourceId);
        log.info("Persisted stopped desired state for socket source {}", socket.id());
      }
    }
  }

  private void publishReconciliationNudge(UUID sourceId) {
    try {
      nudgePublisher.requestReconciliation();
    } catch (Exception e) {
      log.warn(
          "Desired stop for source {} was persisted, but Postgres reconciliation nudge failed: {}",
          sourceId,
          e.getMessage(),
          e);
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
