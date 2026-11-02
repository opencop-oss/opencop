// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.source;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.general.business.usecase.source.SourceLayerRelationshipUsecase;
import com.opencop.core.api.general.ports.usecase.source.ManageSourceUsers;
import com.opencop.core.api.general.technical.gateway.notification.NotificationWorker;
import com.opencop.core.api.general.technical.service.SourceRelationshipAuthorizationService;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.business.model.iam.access.Server;
import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.business.model.iam.access.SourceUser;
import com.opencop.core.sdk.model.datasource.DataSourceRuntime;
import com.opencop.core.sdk.model.iam.source.UpdateSourcePermissionsRequest;
import com.opencop.core.sdk.model.notification.NotificationCommand;
import com.opencop.core.sdk.model.notification.NotificationSeverityType;
import com.opencop.core.sdk.model.notification.NotificationType;
import com.opencop.core.sdk.model.notification.content.AccessOperationType;
import com.opencop.core.sdk.model.notification.content.DataSourceAccessNotification;
import com.opencop.core.sdk.model.notification.content.LayerSourceNotification;
import com.opencop.core.sdk.model.notification.content.NotificationContent;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/sources")
public class SourcesEndpoint {

  private final ServerManager<Server> serverManager;
  private final SourceLayerRelationshipUsecase sourceLayerRelationshipUsecase;
  private final ManageSourceUsers sourceUsers;
  private final DataSourceRuntimeService runtimeService;
  private final NotificationWorker userNotification;
  private final SourceRelationshipAuthorizationService relationshipAuthorization;

  SourcesEndpoint(
      final ServerManager<Server> serverManager,
      final SourceLayerRelationshipUsecase sourceLayerRelationshipUsecase,
      final ManageSourceUsers sourceUsers,
      final DataSourceRuntimeService runtimeService,
      final NotificationWorker userNotification,
      final SourceRelationshipAuthorizationService relationshipAuthorization) {
    this.serverManager = serverManager;
    this.sourceLayerRelationshipUsecase = sourceLayerRelationshipUsecase;
    this.sourceUsers = sourceUsers;
    this.runtimeService = runtimeService;
    this.userNotification = userNotification;
    this.relationshipAuthorization = relationshipAuthorization;
  }

  @GetMapping("/{sourceId}/layers")
  public ResponseEntity<Set<SourceLayer>> getSourceLayers(@PathVariable UUID sourceId) {
    var sourceLayers = this.serverManager.getLayers(sourceId);

    // Fetch data source with runtime status (cached, discrepancy-checked)
    DataSourceRuntime dsr = runtimeService.getWithRuntime(sourceId);

    if (dsr != null) {
      sourceLayers.forEach(
          sl -> {
            sl.setServerName(dsr.name());
            sl.setServerState(dsr.runtime().state().name());
            sl.setLastMessageAt(dsr.runtime().lastMessageAt());
          });
    }

    return ResponseEntity.ok(sourceLayers);
  }

  @PostMapping("/{sourceId}/layers")
  public ResponseEntity<Void> updateSourceLayers(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @RequestBody UpdateSourcePermissionsRequest request) {
    relationshipAuthorization.assertCanAdd(authentication, sourceId, request);
    var result =
        this.sourceLayerRelationshipUsecase.updateSourceLayers(
            sourceId, request, Caller.of(authentication));
    result
        .notifications()
        .forEach(
            notification ->
                this.sendSourceLayerNotification(
                    authentication.internalUserId(),
                    notification.members(),
                    sourceId,
                    notification.layerId(),
                    AccessOperationType.MODIFIED,
                    notification.role()));

    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @DeleteMapping("/{sourceId}/layers")
  public ResponseEntity<Void> deleteSourceLayers(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @RequestBody Set<UUID> layerIds) {
    relationshipAuthorization.assertCanRemove(authentication, sourceId, layerIds);
    var result =
        this.sourceLayerRelationshipUsecase.deleteSourceLayers(
            sourceId, layerIds, Caller.of(authentication));
    result
        .notifications()
        .forEach(
            notification ->
                this.sendSourceLayerNotification(
                    authentication.internalUserId(),
                    notification.members(),
                    sourceId,
                    notification.layerId(),
                    AccessOperationType.REMOVED,
                    notification.role()));

    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @GetMapping("/{sourceId}/users")
  public ResponseEntity<Set<SourceUser>> getSourceUser(@PathVariable UUID sourceId) {
    return ResponseEntity.ok(this.serverManager.getUsers(sourceId));
  }

  @PostMapping("/{sourceId}/users")
  public ResponseEntity<Void> updateSourceUser(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @RequestBody UpdateSourcePermissionsRequest request) {
    this.sourceUsers.updateUsers(sourceId, request, authentication);

    sendNotification(
        authentication.internalUserId(),
        sourceId,
        request.owners(),
        AccessOperationType.MODIFIED,
        ServerRole.OWNER);
    sendNotification(
        authentication.internalUserId(),
        sourceId,
        request.admins(),
        AccessOperationType.MODIFIED,
        ServerRole.ADMIN);
    sendNotification(
        authentication.internalUserId(),
        sourceId,
        request.viewers(),
        AccessOperationType.MODIFIED,
        ServerRole.VIEWER);

    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @DeleteMapping("/{sourceId}/users")
  public ResponseEntity<Void> deleteSourceUser(
      @AuthenticationPrincipal OpenCopPrincipal authentication,
      @PathVariable UUID sourceId,
      @RequestBody Set<UUID> userIds) {
    this.sourceUsers.deleteUsers(sourceId, userIds, authentication);

    sendNotification(
        authentication.internalUserId(), sourceId, userIds, AccessOperationType.REMOVED, null);

    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  private void sendSourceLayerNotification(
      UUID issuerUserId,
      List<LayerMember> members,
      UUID sourceId,
      UUID layerId,
      AccessOperationType type,
      @Nullable ServerRole role) {
    NotificationContent content =
        new LayerSourceNotification(layerId, sourceId, type, role != null ? role.name() : null);
    List<NotificationCommand> commands = new ArrayList<>();
    UUID correlationId = null;
    if (members.size() > 1) {
      correlationId = UUID.randomUUID();
    }
    for (LayerMember member : members) {
      commands.add(
          new NotificationCommand(
              correlationId,
              issuerUserId,
              member.getUserId(),
              NotificationType.LAYER_SOURCE,
              NotificationSeverityType.IMPORTANT,
              content));
    }
    userNotification.sendAllAsync(commands);
  }

  private void sendNotification(
      UUID issuerUserId,
      UUID sourceId,
      Set<UUID> userIds,
      AccessOperationType type,
      @Nullable ServerRole role) {
    NotificationContent content =
        new DataSourceAccessNotification(sourceId, type, role != null ? role.name() : null);
    Set<NotificationCommand> commands = new HashSet<>();
    UUID correlationId = null;
    if (userIds.size() > 1) {
      correlationId = UUID.randomUUID();
    }
    for (UUID userId : userIds) {
      commands.add(
          new NotificationCommand(
              correlationId,
              issuerUserId,
              userId,
              NotificationType.SOURCE_ACCESS,
              NotificationSeverityType.IMPORTANT,
              content));
    }
    userNotification.sendAllAsync(commands);
  }
}
