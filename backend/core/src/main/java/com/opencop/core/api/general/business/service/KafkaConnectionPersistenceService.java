// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.policy.connection.ConnectionIntegrationReferencePolicy;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.permissions.CreateSourcePermissions;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import java.time.Instant;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** Persists a new Kafka connection and its owner permission as one database unit. */
public class KafkaConnectionPersistenceService {

  private final DataSourceRepository repository;
  private final CreateSourcePermissions permissions;
  private final IntegrationCatalogGateway integrationCatalogGateway;
  private final SelectedMapperLock selectedMapperLock;
  private final AuditRecorder auditRecorder;
  private final DataSourceAuditProjection projection;

  public KafkaConnectionPersistenceService(
      DataSourceRepository repository,
      CreateSourcePermissions permissions,
      IntegrationCatalogGateway integrationCatalogGateway,
      SelectedMapperLock selectedMapperLock,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection) {
    this.auditRecorder = auditRecorder;
    this.projection = projection;
    this.repository = repository;
    this.permissions = permissions;
    this.integrationCatalogGateway = integrationCatalogGateway;
    this.selectedMapperLock = selectedMapperLock;
  }

  /**
   * @param integration the integration the connection is stored with, locked so it cannot be
   *     deactivated before this commits
   * @param requestedBy who created it, recorded in the same transaction as the source itself
   */
  @Transactional
  public Instant persistNewConnection(
      KafkaDataSource dataSource,
      IntegrationConnectionSchemaReference integration,
      SetupPrefill prefill,
      Caller requestedBy) {
    ConnectionIntegrationReferencePolicy.assertSelectable(
        integration,
        integrationCatalogGateway.findSchemaOwnerForUpdate(
            new IntegrationConnectionSchemaKey(
                integration.integrationId(), integration.schemaVersion())));

    selectedMapperLock.hold(Optional.ofNullable(dataSource.ingressMapperId()).stream().toList());

    var updatedAt = repository.persist(dataSource);
    permissions.createPermissions(dataSource.id(), dataSource.createdBy());
    repository.setSetupPrefill(dataSource.id(), prefill);
    auditRecorder.record(
        new AuditEvent(
            EntityType.DATA_SOURCE,
            dataSource.id(),
            dataSource.name(),
            Operation.CREATE,
            requestedBy,
            projection.create(dataSource.name(), dataSource.access()),
            AuditEvent.CURRENT_CHANGE_VERSION));
    return updatedAt;
  }
}
