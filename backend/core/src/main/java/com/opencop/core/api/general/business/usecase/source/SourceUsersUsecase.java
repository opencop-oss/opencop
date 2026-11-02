// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.source;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditChange;
import com.opencop.core.api.general.business.model.audit.AuditDeltas;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.usecase.source.ManageSourceUsers;
import com.opencop.core.api.general.technical.service.SourceRelationshipAuthorizationService;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.sdk.business.model.iam.access.Server;
import com.opencop.core.sdk.model.iam.source.UpdateSourcePermissionsRequest;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * User-permission changes on one data source: authorization and mutation under a single
 * transaction.
 *
 * <p>The source counterpart to {@link SourceLayerRelationshipUsecase}, which already owns the layer
 * side of the same endpoint pair.
 */
@Component
public class SourceUsersUsecase implements ManageSourceUsers {

  private final ServerManager<Server> serverManager;
  private final SourceRelationshipAuthorizationService relationshipAuthorization;
  private final ConnectionMetadataGateway connections;
  private final AuditRecorder auditRecorder;
  private final DataSourceAuditProjection projection;

  public SourceUsersUsecase(
      ServerManager<Server> serverManager,
      SourceRelationshipAuthorizationService relationshipAuthorization,
      ConnectionMetadataGateway connections,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection) {
    this.serverManager = serverManager;
    this.relationshipAuthorization = relationshipAuthorization;
    this.connections = connections;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  /**
   * Grants or changes roles for the users named in the request.
   *
   * <p>Per-id upsert, not a replacement of the whole permission set: ids the request does not name
   * keep whatever they had, and unknown ids are skipped.
   */
  @Override
  @Transactional
  public void updateUsers(
      UUID sourceId, UpdateSourcePermissionsRequest request, OpenCopPrincipal caller) {
    this.relationshipAuthorization.assertCanManageSource(caller, sourceId);

    recordApplied(sourceId, caller, () -> this.serverManager.updateUsers(sourceId, request));
  }

  @Override
  @Transactional
  public void deleteUsers(UUID sourceId, Set<UUID> userIds, OpenCopPrincipal caller) {
    this.relationshipAuthorization.assertCanManageSource(caller, sourceId);

    recordApplied(sourceId, caller, () -> this.serverManager.deleteUsers(sourceId, userIds));
  }

  /**
   * Runs a permission write under the source's lock and records what the permission set became.
   *
   * <p>The access service upserts per id and silently skips ids it cannot resolve, so only the
   * roles read on both sides of the write say what happened. A write that moved nothing — a
   * re-saved dialog, an unknown id — leaves no row.
   */
  private void recordApplied(UUID sourceId, OpenCopPrincipal caller, Runnable write) {
    var locked = this.connections.lockById(sourceId);
    var before = currentRoles(sourceId);
    write.run();
    var delta = AuditDeltas.diff(before, currentRoles(sourceId));
    if (delta.isEmpty()) {
      return;
    }
    record(sourceId, locked.name(), caller, this.projection.roleSetChanged(delta));
  }

  private Map<UUID, ServerRole> currentRoles(UUID sourceId) {
    var roles = new LinkedHashMap<UUID, ServerRole>();
    this.serverManager
        .getUsers(sourceId)
        .forEach(user -> roles.put(user.getUserId(), user.getRole()));
    return roles;
  }

  private void record(
      UUID sourceId, String sourceName, OpenCopPrincipal caller, AuditChange change) {
    this.auditRecorder.record(
        new AuditEvent(
            EntityType.DATA_SOURCE,
            sourceId,
            sourceName,
            Operation.MEMBERS_CHANGED,
            Caller.of(caller),
            change,
            AuditEvent.CURRENT_CHANGE_VERSION));
  }
}
