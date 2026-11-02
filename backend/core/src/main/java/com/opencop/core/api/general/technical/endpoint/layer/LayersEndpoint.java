// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.layer;

import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.general.business.usecase.layer.ArchiveLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.CloneLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.MergeLayerUsecase;
import com.opencop.core.api.general.business.usecase.layer.PatchLayerUsecase;
import com.opencop.core.api.general.ports.usecase.layer.CreateLayer;
import com.opencop.core.api.general.ports.usecase.layer.ManageLayerMembers;
import com.opencop.core.api.general.technical.gateway.db.group.AppGroupLayerService;
import com.opencop.core.api.general.technical.gateway.db.layergroup.AppLayerGroupLayerService;
import com.opencop.core.api.general.technical.gateway.notification.NotificationWorker;
import com.opencop.core.api.general.technical.model.CloneLayerRequest;
import com.opencop.core.api.general.technical.model.LayerCreateWithUsersRequest;
import com.opencop.core.api.general.technical.model.MergeLayerRequest;
import com.opencop.core.api.general.technical.model.MergeLayerResponse;
import com.opencop.core.api.general.technical.model.MergeLayerValidationRequest;
import com.opencop.core.api.general.technical.model.MergeLayerValidationResponse;
import com.opencop.core.api.general.technical.model.group.GroupLayerResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayerResponse;
import com.opencop.core.api.general.technical.service.LayerAuthorizationService;
import com.opencop.core.api.general.technical.service.LayerGroupAuthorizationService;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.model.datasource.DataSourceRuntime;
import com.opencop.core.sdk.model.iam.layer.LayerPatchRequest;
import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.iam.layer.UpdateLayerRoleRequest;
import com.opencop.core.sdk.model.notification.NotificationCommand;
import com.opencop.core.sdk.model.notification.NotificationSeverityType;
import com.opencop.core.sdk.model.notification.NotificationType;
import com.opencop.core.sdk.model.notification.content.AccessOperationType;
import com.opencop.core.sdk.model.notification.content.LayerAccessNotification;
import com.opencop.core.sdk.model.notification.content.NotificationContent;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class LayersEndpoint {

  private final LayerManager<Layer, LayerMember> layerManager;
  private final LayerAuthorizationService layerAuth;
  private final ArchiveLayerUsecase archiveLayerUsecase;
  private final CloneLayerUsecase cloneLayerUsecase;
  private final MergeLayerUsecase mergeLayerUsecase;
  private final PatchLayerUsecase patchLayerUsecase;
  private final CreateLayer createLayer;
  private final ManageLayerMembers layerMembers;
  private final DataSourceRuntimeService runtimeService;
  private final NotificationWorker notificationService;
  private final AppGroupLayerService groupLayerService;
  private final AppLayerGroupLayerService layerGroupLayerService;
  private final LayerGroupAuthorizationService layerGroupAuth;

  LayersEndpoint(
      final LayerManager<Layer, LayerMember> layerManager,
      final LayerAuthorizationService layerAuth,
      final ArchiveLayerUsecase archiveLayerUsecase,
      final CloneLayerUsecase cloneLayerUsecase,
      final MergeLayerUsecase mergeLayerUsecase,
      final PatchLayerUsecase patchLayerUsecase,
      final CreateLayer createLayer,
      final ManageLayerMembers layerMembers,
      final DataSourceRuntimeService runtimeService,
      final NotificationWorker notificationService,
      final AppGroupLayerService groupLayerService,
      final AppLayerGroupLayerService layerGroupLayerService,
      final LayerGroupAuthorizationService layerGroupAuth) {
    this.layerManager = layerManager;
    this.layerAuth = layerAuth;
    this.archiveLayerUsecase = archiveLayerUsecase;
    this.cloneLayerUsecase = cloneLayerUsecase;
    this.mergeLayerUsecase = mergeLayerUsecase;
    this.patchLayerUsecase = patchLayerUsecase;
    this.createLayer = createLayer;
    this.layerMembers = layerMembers;
    this.runtimeService = runtimeService;
    this.notificationService = notificationService;
    this.groupLayerService = groupLayerService;
    this.layerGroupLayerService = layerGroupLayerService;
    this.layerGroupAuth = layerGroupAuth;
  }

  /**
   * Creates a layer and adds the creator as OWNER. Optionally adds initial users.
   *
   * <p>Note: IAM service is planned to be merged into Core. Once that happens, this can be
   * simplified to handle layer creation and member assignment in a single transaction rather than
   * separate HTTP calls to IAM.
   */
  @PostMapping("/v1/layers")
  public ResponseEntity<Layer> create(
      @RequestBody LayerCreateWithUsersRequest request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    var layer =
        this.createLayer.create(
            request.name(), request.accessScope(), request.users(), authentication);

    this.sendNotification(
        authentication.internalUserId(),
        layer.getId(),
        Set.of(authentication.internalUserId()),
        AccessOperationType.ADDED,
        LayerRole.OWNER,
        NotificationSeverityType.NONE);

    return ResponseEntity.status(HttpStatus.CREATED).body(layer);
  }

  @PostMapping("/v1/layers/{sourceLayerId}/clone")
  public ResponseEntity<Layer> cloneLayer(
      @PathVariable UUID sourceLayerId,
      @RequestBody CloneLayerRequest request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    var layer = cloneLayerUsecase.cloneLayer(sourceLayerId, authentication, request);
    this.sendNotification(
        authentication.internalUserId(),
        layer.getId(),
        Set.of(authentication.internalUserId()),
        AccessOperationType.ADDED,
        LayerRole.OWNER,
        NotificationSeverityType.NONE);
    return ResponseEntity.status(HttpStatus.CREATED).body(layer);
  }

  @PostMapping("/v1/layers/merge")
  public ResponseEntity<MergeLayerResponse> mergeLayers(
      @RequestBody MergeLayerRequest request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mergeLayerUsecase.mergeLayer(authentication, request));
  }

  @PostMapping("/v1/layers/merge/validate")
  public ResponseEntity<MergeLayerValidationResponse> validateMergeLayers(
      @RequestBody MergeLayerValidationRequest request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return ResponseEntity.ok(mergeLayerUsecase.validateMergeLayer(authentication, request));
  }

  @GetMapping("/v1/layers/{layerId}")
  public ResponseEntity<Layer> getLayer(
      @PathVariable UUID layerId, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    var layer = this.layerManager.get(layerId);
    if (layer.getAccessScope() != LayerAccessScope.GLOBAL) {
      layerAuth.assertLayerMember(layerId, authentication);
    }
    return ResponseEntity.ok(layer);
  }

  @PreAuthorize("hasRole('admin')")
  @GetMapping("/v1/admin/layers")
  public ResponseEntity<List<Layer>> getLayers(
      @RequestParam(required = false) RecordVisibility recordVisibility,
      @RequestParam(required = false) LayerAccessScope accessScope,
      @RequestParam(required = false) String name) {
    return ResponseEntity.ok(
        this.layerManager.list(new LayerReadFilter(recordVisibility, accessScope, name)));
  }

  @PatchMapping("/v1/layers/{layerId}")
  public ResponseEntity<Void> patchLayer(
      @PathVariable UUID layerId,
      @RequestBody LayerPatchRequest request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    this.patchLayerUsecase.patchLayer(layerId, request, authentication);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @DeleteMapping("/v1/layers/{layerId}")
  public ResponseEntity<Void> deleteLayer(
      @PathVariable UUID layerId, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    var archiveResult = this.archiveLayerUsecase.archiveLayer(layerId, authentication);

    this.sendLayerMembersNotification(
        authentication.internalUserId(),
        layerId,
        archiveResult.members(),
        AccessOperationType.DELETED,
        null,
        null);

    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @GetMapping("/v1/layers/{layerId}/members")
  public ResponseEntity<List<LayerMember>> getLayerMembers(@PathVariable UUID layerId) {
    return ResponseEntity.ok(this.layerManager.listLayerMembers(layerId));
  }

  @PostMapping("/v1/layers/{layerId}/members")
  public ResponseEntity<Void> addLayerMembers(
      @PathVariable UUID layerId,
      @RequestBody Map<UUID, LayerRole> request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    this.layerMembers.addMembers(layerId, request, authentication);

    request.forEach(
        (userId, role) ->
            this.sendNotification(
                authentication.internalUserId(),
                layerId,
                Set.of(userId),
                AccessOperationType.ADDED,
                role,
                null));

    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @PatchMapping("/v1/layers/{layerId}/members")
  public ResponseEntity<Void> updateLayerMemberRole(
      @PathVariable UUID layerId,
      @RequestBody UpdateLayerRoleRequest request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    this.layerMembers.updateMemberRole(layerId, request, authentication);

    this.sendNotification(
        authentication.internalUserId(),
        layerId,
        request.members(),
        AccessOperationType.MODIFIED,
        LayerRole.MEMBER,
        null);
    this.sendNotification(
        authentication.internalUserId(),
        layerId,
        request.admins(),
        AccessOperationType.MODIFIED,
        LayerRole.ADMIN,
        null);
    this.sendNotification(
        authentication.internalUserId(),
        layerId,
        request.editors(),
        AccessOperationType.MODIFIED,
        LayerRole.EDITOR,
        null);
    this.sendNotification(
        authentication.internalUserId(),
        layerId,
        request.owners(),
        AccessOperationType.MODIFIED,
        LayerRole.OWNER,
        null);

    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @DeleteMapping("/v1/layers/{layerId}/members")
  public ResponseEntity<Void> deleteMemberFromLayer(
      @PathVariable UUID layerId,
      @RequestBody Set<UUID> userIds,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    this.layerMembers.removeMembers(layerId, userIds, authentication);
    this.sendNotification(
        authentication.internalUserId(), layerId, userIds, AccessOperationType.REMOVED, null, null);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  @GetMapping("/v1/layers/{layerId}/user-groups")
  public ResponseEntity<List<GroupLayerResponse>> getGroupsForLayer(
      @PathVariable UUID layerId, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    layerAuth.assertLayerMember(layerId, authentication);
    try {
      return ResponseEntity.ok(groupLayerService.getGroupsForLayer(layerId));
    } catch (PersistenceException _) {
      // The IAM access check already validated the layer exists and the user is a member.
      // If the group-layer service can't locate the layer in its repo (e.g. no group
      // assignments exist yet), return an empty list rather than propagating a 404.
      return ResponseEntity.ok(List.of());
    }
  }

  /**
   * The layer groups this layer belongs to — the reverse of {@code GET
   * /v1/layer-groups/{id}/layers}, and the layer-group counterpart to {@link #getGroupsForLayer}.
   *
   * <p>Two gates, because the question spans two containers. Layer membership decides whether the
   * caller may ask about this layer at all; the result is then narrowed to the groups the caller
   * may actually view. Without that second gate, membership in one layer would expose the id, name,
   * role, and assignment time of every other group the layer is attached to — including private
   * groups the caller has no role on, which {@code docs/layer-groups/permissions-reference.md}
   * forbids. The forward route gates on container membership for the same reason.
   *
   * <p>A layer in no visible groups returns an empty list. For a global admin the layer gate
   * short-circuits before consulting the layer manager, so an unknown layer id also yields {@code
   * 200 []} rather than a 404 — matching the {@code /user-groups} sibling.
   */
  @GetMapping("/v1/layers/{layerId}/layer-groups")
  public List<LayerGroupLayerResponse> getLayerGroupsForLayer(
      @PathVariable UUID layerId, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    layerAuth.assertLayerMember(layerId, authentication);
    var assignments = layerGroupLayerService.getLayerGroupsForLayer(layerId);
    if (authentication.isGlobalAdmin()) {
      return assignments;
    }
    var viewable = layerGroupAuth.viewableLayerGroupIds(authentication);
    return assignments.stream()
        .filter(assignment -> viewable.contains(assignment.layerGroupId()))
        .toList();
  }

  @GetMapping("/v1/layers/{layerId}/sources")
  public ResponseEntity<Set<SourceLayer>> getLayerServers(@PathVariable UUID layerId) {
    var sourceLayers = this.layerManager.listLayerServers(layerId);

    // Collect IDs and batch fetch with runtime status (cached, discrepancy-checked)
    var sourceIds = sourceLayers.stream().map(SourceLayer::getServerId).collect(Collectors.toSet());
    Map<UUID, DataSourceRuntime> runtimeMap = runtimeService.getWithRuntime(sourceIds);

    // Enrich with metadata and runtime status
    sourceLayers.forEach(
        sl -> {
          var dsr = runtimeMap.get(sl.getServerId());
          if (dsr != null) {
            sl.setServerName(dsr.name());
            sl.setServerState(dsr.runtime().state().name());
          }
        });
    return ResponseEntity.ok(sourceLayers);
  }

  private void sendLayerMembersNotification(
      UUID issuerUserId,
      UUID sourceId,
      List<LayerMember> members,
      AccessOperationType type,
      @Nullable LayerRole role,
      @Nullable NotificationSeverityType severity) {
    NotificationContent content =
        new LayerAccessNotification(sourceId, type, role != null ? role.name() : null);
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
              NotificationType.LAYER_ACCESS,
              severity != null ? severity : NotificationSeverityType.IMPORTANT,
              content));
    }
    notificationService.sendAllAsync(commands);
  }

  private void sendNotification(
      UUID issuerUserId,
      UUID sourceId,
      Set<UUID> userIds,
      AccessOperationType type,
      @Nullable LayerRole role,
      @Nullable NotificationSeverityType severity) {
    NotificationContent content =
        new LayerAccessNotification(sourceId, type, role != null ? role.name() : null);
    List<NotificationCommand> commands = new ArrayList<>();
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
              NotificationType.LAYER_ACCESS,
              severity != null ? severity : NotificationSeverityType.IMPORTANT,
              content));
    }
    notificationService.sendAllAsync(commands);
  }
}
