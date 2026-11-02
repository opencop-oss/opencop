// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.patch;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.ConnectionMetadataPatch;
import com.opencop.core.api.general.business.model.connection.ValidatedConnectionMetadataPatch;
import com.opencop.core.api.general.business.policy.connection.ConnectionMetadataPatchPolicy;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.business.usecase.connection.integration.ConnectionIntegrationSelection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.usecase.connection.patch.PatchConnection;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

public class PatchConnectionUsecase implements PatchConnection {

  private static final Logger log = LoggerFactory.getLogger(PatchConnectionUsecase.class);

  private final ConnectionAuthorization authorizationService;
  private final ConnectionMetadataGateway gateway;
  private final ConnectionMetadataPatchPolicy policy;
  private final ConnectionIntegrationSelection integrationSelection;
  private final Map<TransportKind, ConnectionProtocolPatch> protocolPatches;
  private final SocketReconciliationNudgePublisher nudgePublisher;
  private final TransactionTemplate transactionTemplate;
  private final AuditRecorder auditRecorder;
  private final DataSourceAuditProjection projection;

  public PatchConnectionUsecase(
      ConnectionAuthorization authorizationService,
      ConnectionMetadataGateway gateway,
      ConnectionMetadataPatchPolicy policy,
      ConnectionIntegrationSelection integrationSelection,
      List<ConnectionProtocolPatch> protocolPatches,
      SocketReconciliationNudgePublisher nudgePublisher,
      TransactionTemplate transactionTemplate,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection) {
    this.authorizationService = authorizationService;
    this.gateway = gateway;
    this.policy = policy;
    this.integrationSelection = integrationSelection;
    var byTransport = new EnumMap<TransportKind, ConnectionProtocolPatch>(TransportKind.class);
    for (var protocolPatch : protocolPatches) {
      if (byTransport.putIfAbsent(protocolPatch.transportKind(), protocolPatch) != null) {
        throw new IllegalStateException(
            "Duplicate connection patch for " + protocolPatch.transportKind());
      }
    }
    var missing = EnumSet.allOf(TransportKind.class);
    missing.removeAll(byTransport.keySet());
    if (!missing.isEmpty()) {
      throw new IllegalStateException("Missing connection patch for " + missing);
    }
    this.protocolPatches = Map.copyOf(byTransport);
    this.nudgePublisher = nudgePublisher;
    this.transactionTemplate = transactionTemplate;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  @Override
  public void patch(
      UUID sourceId,
      String name,
      Boolean defaultVisible,
      String access,
      IntegrationConnectionSchemaReference integration,
      Caller actor) {
    log.info("Request to patch source {} initiated by {}", sourceId, actor.userId());

    authorizationService.assertDirectOwnerOrAdmin(sourceId, actor.userId(), actor.globalAdmin());
    var patch = new ConnectionMetadataPatch(name, defaultVisible, access, integration);
    transactionTemplate.executeWithoutResult(_ -> applyPatch(sourceId, patch, actor));
  }

  private void applyPatch(UUID sourceId, ConnectionMetadataPatch patch, Caller actor) {
    var updatedBy = actor.userId();
    var current = gateway.lockById(sourceId);
    var validated = policy.validate(patch, current);

    var effective = effectiveChanges(validated, current);
    var requiresReconciliation =
        protocolPatches
            .get(current.transportKind())
            .validateAndRequiresReconciliation(current, patch, effective);
    if (effective.name() == null
        && effective.defaultVisible() == null
        && effective.access() == null
        && effective.integration() == null) {
      return;
    }

    if (effective.integration() != null) {
      var reference = effective.integration();
      integrationSelection.requireSelectableForUpdate(reference);
    }

    gateway.apply(sourceId, effective, updatedBy);

    // Inside the same locked transaction, and only past the effective-change gate above, so an
    // accepted patch that moved nothing writes no row.
    auditRecorder.record(
        new AuditEvent(
            EntityType.DATA_SOURCE,
            sourceId,
            effective.name() == null ? current.name() : effective.name(),
            Operation.PATCH,
            actor,
            projection.patch(current, effective),
            AuditEvent.CURRENT_CHANGE_VERSION));

    if (requiresReconciliation) {
      publishReconciliationNudge(sourceId);
    }
  }

  private static ValidatedConnectionMetadataPatch effectiveChanges(
      ValidatedConnectionMetadataPatch requested, ConnectionMetadata current) {
    return new ValidatedConnectionMetadataPatch(
        Objects.equals(requested.name(), current.name()) ? null : requested.name(),
        Objects.equals(requested.defaultVisible(), current.defaultVisible())
            ? null
            : requested.defaultVisible(),
        Objects.equals(requested.access(), current.access()) ? null : requested.access(),
        Objects.equals(requested.integration(), current.integration())
            ? null
            : requested.integration());
  }

  private void publishReconciliationNudge(UUID sourceId) {
    try {
      nudgePublisher.requestReconciliation();
    } catch (Exception e) {
      log.warn(
          "Connection patch for source {} was persisted, but Postgres reconciliation nudge failed: {}",
          sourceId,
          e.getMessage(),
          e);
    }
  }
}
