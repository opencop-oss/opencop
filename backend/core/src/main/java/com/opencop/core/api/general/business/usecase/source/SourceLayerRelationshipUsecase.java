// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.source;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditDeltas;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.business.usecase.layer.LayerAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.business.model.iam.access.Server;
import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.model.datasource.DataSourceRuntime;
import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.iam.source.UpdateSourcePermissionsRequest;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SourceLayerRelationshipUsecase {

  private static final String UPDATE_SOURCE_LAYERS_OPERATION = "update source-layer relationship";
  private static final String DELETE_SOURCE_LAYERS_OPERATION = "delete source-layer relationship";

  private final ServerManager<Server> serverManager;
  private final LayerManager<Layer, LayerMember> layerManager;
  private final DataSourceRuntimeService runtimeService;
  private final ConnectionMetadataGateway connections;
  private final AuditRecorder auditRecorder;
  private final DataSourceAuditProjection sourceProjection;
  private final LayerAuditProjection layerProjection;

  public SourceLayerRelationshipUsecase(
      ServerManager<Server> serverManager,
      LayerManager<Layer, LayerMember> layerManager,
      DataSourceRuntimeService runtimeService,
      ConnectionMetadataGateway connections,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection sourceProjection,
      LayerAuditProjection layerProjection) {
    this.serverManager = serverManager;
    this.layerManager = layerManager;
    this.runtimeService = runtimeService;
    this.connections = connections;
    this.auditRecorder = auditRecorder;
    this.sourceProjection = sourceProjection;
    this.layerProjection = layerProjection;
  }

  @Transactional
  public SourceLayerMutationResult updateSourceLayers(
      UUID sourceId, UpdateSourcePermissionsRequest request, Caller actor) {
    var layerIds = new HashSet<UUID>();
    layerIds.addAll(request.owners());
    layerIds.addAll(request.admins());
    layerIds.addAll(request.viewers());
    assertSourceActiveForSourceLayerMutation(sourceId, UPDATE_SOURCE_LAYERS_OPERATION);
    assertLayersActiveForSourceLayerMutation(layerIds, UPDATE_SOURCE_LAYERS_OPERATION);

    recordApplied(sourceId, actor, () -> this.serverManager.updateLayers(sourceId, request));

    var notifications = new ArrayList<SourceLayerNotificationTarget>();
    addNotificationTargets(notifications, request.owners(), ServerRole.OWNER);
    addNotificationTargets(notifications, request.admins(), ServerRole.ADMIN);
    addNotificationTargets(notifications, request.viewers(), ServerRole.VIEWER);
    return new SourceLayerMutationResult(notifications);
  }

  @Transactional
  public SourceLayerMutationResult deleteSourceLayers(
      UUID sourceId, Set<UUID> layerIds, Caller actor) {
    // No source-active check: detaching an archived source is cleanup, not new wiring, and must
    // stay possible or the source can never be removed from a layer again.
    assertLayersActiveForSourceLayerMutation(layerIds, DELETE_SOURCE_LAYERS_OPERATION);

    recordApplied(sourceId, actor, () -> this.serverManager.deleteLayers(sourceId, layerIds));

    var notifications = new ArrayList<SourceLayerNotificationTarget>();
    addNotificationTargets(notifications, layerIds, null);
    return new SourceLayerMutationResult(notifications);
  }

  /**
   * One act, two ledgers: a row on the source saying which layers moved, and a row on each layer
   * saying this source did. The deltas are computed once, so the source's drawer and the layer's
   * drawer cannot disagree about what happened.
   */
  private void recordApplied(UUID sourceId, Caller actor, Runnable write) {
    this.connections.lockById(sourceId);
    var before = currentRoles(sourceId);
    write.run();
    var delta = AuditDeltas.diff(before, currentRoles(sourceId));
    if (delta.isEmpty()) {
      return;
    }

    this.auditRecorder.record(
        new AuditEvent(
            EntityType.DATA_SOURCE,
            sourceId,
            sourceName(sourceId),
            Operation.LAYERS_CHANGED,
            actor,
            this.sourceProjection.roleSetChanged(delta),
            AuditEvent.CURRENT_CHANGE_VERSION));

    var names =
        layerNames(union(delta.added().keySet(), delta.removed(), delta.roleChanged().keySet()));
    names.forEach(
        (layerId, layerName) ->
            this.auditRecorder.record(
                new AuditEvent(
                    EntityType.LAYER,
                    layerId,
                    layerName,
                    Operation.DATA_SOURCES_CHANGED,
                    actor,
                    this.layerProjection.roleSetChanged(
                        new AuditDeltas.Delta<>(
                            roleFor(delta.added(), layerId, sourceId),
                            delta.removed().contains(layerId) ? Set.of(sourceId) : Set.of(),
                            roleFor(delta.roleChanged(), layerId, sourceId))),
                    AuditEvent.CURRENT_CHANGE_VERSION)));
  }

  /** The source's side of one layer's row: the role it took, or nothing when it did not move. */
  private static Map<UUID, ServerRole> roleFor(
      Map<UUID, ServerRole> moved, UUID layerId, UUID sourceId) {
    var role = moved.get(layerId);
    return role == null ? Map.of() : Map.of(sourceId, role);
  }

  @SafeVarargs
  private static Set<UUID> union(Set<UUID>... sets) {
    var all = new LinkedHashSet<UUID>();
    for (var set : sets) {
      all.addAll(set);
    }
    return all;
  }

  private Map<UUID, ServerRole> currentRoles(UUID sourceId) {
    var roles = new LinkedHashMap<UUID, ServerRole>();
    for (SourceLayer link : this.serverManager.getLayers(sourceId)) {
      if (link.getLayer() != null) {
        roles.put(link.getLayer().getId(), link.getRole());
      }
    }
    return roles;
  }

  /** Names for the rows, keyed by id; a layer the read does not answer for keeps a null name. */
  private Map<UUID, String> layerNames(Set<UUID> layerIds) {
    var names = new LinkedHashMap<UUID, String>();
    layerIds.forEach(layerId -> names.put(layerId, null));
    if (layerIds.isEmpty()) {
      return names;
    }
    this.layerManager
        .list(new LayerReadFilter(RecordVisibility.ALL, null, null, null, layerIds))
        .forEach(layer -> names.put(layer.getId(), layer.getName()));
    return names;
  }

  private String sourceName(UUID sourceId) {
    var runtime = this.runtimeService.getWithRuntime(sourceId, RecordVisibility.ALL);
    return runtime == null ? null : runtime.dataSource().name();
  }

  private void addNotificationTargets(
      List<SourceLayerNotificationTarget> notifications,
      Set<UUID> layerIds,
      @Nullable ServerRole role) {
    layerIds.forEach(
        layerId ->
            notifications.add(
                new SourceLayerNotificationTarget(
                    layerId, this.layerManager.listLayerMembers(layerId), role)));
  }

  private void assertSourceActiveForSourceLayerMutation(UUID sourceId, String operation) {
    DataSourceRuntime sourceRuntime = runtimeService.getWithRuntime(sourceId, RecordVisibility.ALL);
    if (sourceRuntime != null) {
      ResourceLifecyclePolicy.assertDataSourceActive(sourceRuntime.dataSource(), operation);
    }
  }

  private void assertLayersActiveForSourceLayerMutation(Set<UUID> layerIds, String operation) {
    if (layerIds.isEmpty()) {
      return;
    }

    var filter = new LayerReadFilter(RecordVisibility.ALL, null, null, null, layerIds);
    var layers = layerManager.list(filter);
    for (Layer layer : layers) {
      ResourceLifecyclePolicy.assertLayerActive(layer, operation);
    }
  }

  public record SourceLayerMutationResult(List<SourceLayerNotificationTarget> notifications) {}

  public record SourceLayerNotificationTarget(
      UUID layerId, List<LayerMember> members, @Nullable ServerRole role) {}
}
