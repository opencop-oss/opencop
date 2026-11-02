// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.model.connection.CurrentBrokerProvider;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.cache.EventGeoCacheEvictionGateway;
import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.kafka.ByodSourceStatePublisher;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.model.datasource.ByodSourceState;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes the ordered persistence and infrastructure lifecycle for an authorized source archive.
 */
public class ConnectionArchiveLifecycleService {

  private static final Logger log =
      LoggerFactory.getLogger(ConnectionArchiveLifecycleService.class);
  private static final String EVICTION_FAILED_MESSAGE =
      "Cached events could not be evicted. The data source was not archived so the delete can be"
          + " retried, but some of its cached events may already have been removed.";

  private final SocketReconciliationNudgePublisher nudgePublisher;
  private final CurrentBrokerProvider currentBrokerProvider;
  private final DataSourceRepository dataSourceRepository;
  private final ConnectionStore connectionStore;
  private final EventGeoCacheEvictionGateway eventGeoCacheEvictionGateway;
  private final KafkaProvisioningService kafkaProvisioner;
  private final ByodSourceStatePublisher sourcePublisher;
  private final AuditRecorder auditRecorder;
  private final DataSourceAuditProjection projection;
  private final TransactionTemplate transactionTemplate;

  public ConnectionArchiveLifecycleService(
      SocketReconciliationNudgePublisher nudgePublisher,
      CurrentBrokerProvider currentBrokerProvider,
      DataSourceRepository dataSourceRepository,
      ConnectionStore connectionStore,
      EventGeoCacheEvictionGateway eventGeoCacheEvictionGateway,
      KafkaProvisioningService kafkaProvisioner,
      ByodSourceStatePublisher sourcePublisher,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection,
      PlatformTransactionManager transactionManager) {
    this.auditRecorder = auditRecorder;
    this.projection = projection;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.nudgePublisher = nudgePublisher;
    this.currentBrokerProvider = currentBrokerProvider;
    this.dataSourceRepository = dataSourceRepository;
    this.connectionStore = connectionStore;
    this.eventGeoCacheEvictionGateway = eventGeoCacheEvictionGateway;
    this.kafkaProvisioner = kafkaProvisioner;
    this.sourcePublisher = sourcePublisher;
  }

  public void archive(UUID sourceId, Caller actor) {
    var userId = actor.userId();
    var dataSource =
        dataSourceRepository
            .findByIdForProvider(sourceId, currentBrokerProvider.name())
            .orElseThrow(
                () -> new GlobalOpenCopException("Data source not found", HttpStatus.NOT_FOUND));
    if (dataSource.archivedAt() != null) {
      throw new GlobalOpenCopException("Data source not found", HttpStatus.NOT_FOUND);
    }

    // Evict cached events before anything irreversible happens. The entries are reachable only
    // through the source id and an archived source is invisible to every read path, so evicting
    // after the archive would strand them in Redis when Redis is unavailable. Evicting first means
    // a failure leaves the source row untouched and the delete retryable. It does not mean the
    // cache is untouched: eviction is several Redis commands, so see evictGeoCache.
    evictGeoCache(sourceId);

    // Publish DELETED — Processor must stop the consumer before we remove infrastructure.
    // Abort on failure: proceeding would leave an orphaned consumer with no way to signal deletion.
    // This is a one-way door: shouldApply treats DELETED as terminal, so the Processor ignores
    // every later state for this source. Nothing after this point may be rolled back.
    if (dataSource instanceof KafkaDataSource kafka) {
      sourcePublisher.publish(
          new ByodSourceState(
              sourceId,
              kafka.topicName(),
              kafka.username(),
              ByodSourceState.Status.DELETED,
              null,
              ByodSourceState.CURRENT_VERSION));
    }

    // The current spec references the secrets of the latest provisioning. A source created before
    // provisionings had generations stored its secrets at fixed paths instead, so those go too.
    if (dataSource instanceof SocketDataSource socket) {
      connectionStore.delete(socket.spec().secretRefs());
      connectionStore.deleteLegacy(sourceId);
    }

    // DB delete — abort on failure. The DELETED above has already stopped any Kafka consumer, so a
    // failure here leaves the source addressable but no longer ingesting until the delete is
    // retried. Reconciliation will not re-publish STARTED for it.
    // The row joins this transaction, so the archive and its history commit together. By this
    // point Kafka DELETED and the Vault deletes have already happened and cannot be undone; a
    // failure here therefore leaves no row, and the existing retry path owns the cleanup.
    transactionTemplate.executeWithoutResult(
        _ -> {
          dataSourceRepository.archive(sourceId, userId);
          auditRecorder.record(
              new AuditEvent(
                  EntityType.DATA_SOURCE,
                  sourceId,
                  dataSource.name(),
                  Operation.ARCHIVE,
                  actor,
                  projection.transportOnly(dataSource),
                  AuditEvent.CURRENT_CHANGE_VERSION));
        });

    log.info("Deleted source {}", sourceId);

    // Kafka deprovisioning removes the topic, SCRAM user, ACLs, quota, and Vault credentials.
    // It is best-effort: deprovision() logs failures and does not throw.
    switch (dataSource) {
      case KafkaDataSource kafka ->
          kafkaProvisioner.deprovision(kafka.topicName(), kafka.username(), sourceId);
      case SocketDataSource socket -> {
        deleteSecretsCommittedDuringArchive(sourceId, socket.spec().secretRefs());
        publishReconciliationNudge(sourceId);
      }
    }
  }

  /**
   * Deletes the secrets of a setup that committed between this archive's read of the source and its
   * archive. The archive waits on that setup's row lock, and a setup locking the row after the
   * archive is rejected, so the archived row references the last secrets ever committed. The source
   * is already archived, so a failure is logged rather than thrown.
   */
  private void deleteSecretsCommittedDuringArchive(UUID sourceId, List<String> deletedRefs) {
    try {
      var undeletedRefs =
          dataSourceRepository
              .findById(sourceId)
              .filter(SocketDataSource.class::isInstance)
              .map(SocketDataSource.class::cast)
              .map(socket -> socket.spec().secretRefs())
              .orElse(List.of())
              .stream()
              .filter(ref -> !deletedRefs.contains(ref))
              .toList();
      if (!undeletedRefs.isEmpty()) {
        connectionStore.delete(undeletedRefs);
      }
    } catch (RuntimeException e) {
      log.warn(
          "Archived source {}, but the secrets of a setup committed during the archive could not"
              + " be deleted: {}",
          sourceId,
          e.getMessage(),
          e);
    }
  }

  /**
   * Evicts the source's cached events, aborting the delete when that fails. Runs before any
   * irreversible step, so a failure leaves the source itself fully intact: not archived, still
   * enabled, still consuming, with its secrets and Kafka consumer untouched. The caller retries
   * once Redis recovers.
   *
   * <p>A thrown eviction is not proof that the cache is untouched. The bulk delete and the index
   * removal are separate commands, so a failure in the later ones leaves an active source with some
   * or all of its cached events already gone, and no read path rebuilds that projection on demand.
   * The failure message promises a retryable delete rather than an unchanged source for that
   * reason.
   */
  private void evictGeoCache(UUID sourceId) {
    try {
      eventGeoCacheEvictionGateway.evictGeoSource(sourceId);
    } catch (Exception evictionFailure) {
      log.warn(
          "Redis geo cache eviction failed for source {}, aborting the delete so it can be retried:"
              + " {}",
          sourceId,
          evictionFailure.getMessage(),
          evictionFailure);
      throw new GlobalOpenCopException(EVICTION_FAILED_MESSAGE, HttpStatus.SERVICE_UNAVAILABLE);
    }
  }

  private void publishReconciliationNudge(UUID sourceId) {
    try {
      nudgePublisher.requestReconciliation();
    } catch (Exception e) {
      log.warn(
          "Deleted source {}, but Postgres reconciliation nudge failed: {}",
          sourceId,
          e.getMessage(),
          e);
    }
  }
}
