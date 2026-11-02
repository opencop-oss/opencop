// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.technical.gateway.kafka.ByodSourcePublisher;
import com.opencop.core.sdk.model.datasource.ByodSourceState;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.ProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Startup reconciliation for BYOD (Kafka) data sources.
 *
 * <ol>
 *   <li>Re-publishes all active (enabled) BYOD sources to the compacted topic. No instance
 *       assignment — Kafka consumer groups handle distribution across Processor instances.
 *   <li>Re-removes write ACLs for disabled BYOD sources — heals the fail-open case where {@code
 *       StopConnectionUsecase} persisted {@code enabled=false} but {@code removeWriteAcl} failed
 *       before completing. Idempotent: removing an already-absent ACL is a no-op.
 * </ol>
 */
public class ByodReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(ByodReconciliationService.class);

  private final DataSourceRepository dataSourceRepository;
  private final ByodSourcePublisher byodSourcePublisher;
  private final KafkaProvisioningService kafkaProvisioningService;
  private final ProviderConfig providerConfig;

  public ByodReconciliationService(
      DataSourceRepository dataSourceRepository,
      ByodSourcePublisher byodSourcePublisher,
      KafkaProvisioningService kafkaProvisioningService,
      ProviderConfig providerConfig) {
    this.dataSourceRepository = dataSourceRepository;
    this.byodSourcePublisher = byodSourcePublisher;
    this.kafkaProvisioningService = kafkaProvisioningService;
    this.providerConfig = providerConfig;
  }

  public void reconcile() {
    var currentProvider = providerConfig.name();

    int excluded = dataSourceRepository.countExcludedKafkaSources(currentProvider);
    if (excluded > 0) {
      log.warn(
          "Found {} BYOD source(s) created under a different broker provider than current ({}) "
              + "— these sources are hidden and cannot be managed. "
              + "Switch to the original provider to delete them.",
          excluded,
          currentProvider);
    }

    var allKafkaSources =
        dataSourceRepository.findAll(currentProvider).stream()
            .filter(KafkaDataSource.class::isInstance)
            .map(KafkaDataSource.class::cast)
            .toList();

    int enabledCount = 0;
    for (var kafka : allKafkaSources) {
      if (kafka.enabled()) {
        byodSourcePublisher.publish(
            new ByodSourceState(
                kafka.id(),
                kafka.topicName(),
                kafka.username(),
                ByodSourceState.Status.STARTED,
                kafka.updatedAt(),
                ByodSourceState.CURRENT_VERSION));
        enabledCount++;
      }
    }
    log.info("Reconciled {} active BYOD sources to compacted topic", enabledCount);

    // Re-remove write ACLs and publish STOPPED for disabled sources — heals the case where
    // Core crashed between setEnabled(false) and publishing STOPPED. Without this, Processor
    // would keep running a consumer for a disabled source until the next explicit stop/delete.
    int disabledCount = 0;
    for (var kafka : allKafkaSources) {
      if (!kafka.enabled()) {
        kafkaProvisioningService.removeWriteAcl(kafka.username(), kafka.topicName());
        byodSourcePublisher.publish(
            new ByodSourceState(
                kafka.id(),
                kafka.topicName(),
                kafka.username(),
                ByodSourceState.Status.STOPPED,
                kafka.updatedAt(),
                ByodSourceState.CURRENT_VERSION));
        disabledCount++;
      }
    }
    if (disabledCount > 0) {
      log.info("Reconciled write ACLs for {} disabled BYOD sources", disabledCount);
    }
  }
}
