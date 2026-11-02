// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.layergroup;

import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupContainerManagement;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupLayerManagement;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupMemberManagement;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupUserGroupManagement;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupCreateRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayerResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayersAssignRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMemberResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupsPatchRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class LayerGroupsEndpoint {

  private final LayerGroupContainerManagement containerUseCase;
  private final LayerGroupMemberManagement membersUseCase;
  private final LayerGroupLayerManagement layersUseCase;
  private final LayerGroupUserGroupManagement userGroupsUseCase;

  LayerGroupsEndpoint(
      LayerGroupContainerManagement containerUseCase,
      LayerGroupMemberManagement membersUseCase,
      LayerGroupLayerManagement layersUseCase,
      LayerGroupUserGroupManagement userGroupsUseCase) {
    this.containerUseCase = containerUseCase;
    this.membersUseCase = membersUseCase;
    this.layersUseCase = layersUseCase;
    this.userGroupsUseCase = userGroupsUseCase;
  }

  /**
   * Creates a layer group owned by the caller. Open to any authenticated user: the use case
   * defaults the scope to PRIVATE, rejects GLOBAL for callers who are not global admins, and adds
   * the creator as the group's OWNER.
   */
  @PostMapping("/layer-groups")
  public ResponseEntity<LayerGroupResponse> createLayerGroup(
      @RequestBody LayerGroupCreateRequest request,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    var created = containerUseCase.create(request, principal);
    return ResponseEntity.created(URI.create("/v1/layer-groups/" + created.id())).body(created);
  }

  @GetMapping("/layer-groups/{id}")
  public LayerGroupResponse getLayerGroup(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal principal) {
    return containerUseCase.get(id, principal);
  }

  @PatchMapping("/layer-groups/{id}")
  public ResponseEntity<LayerGroupResponse> patchLayerGroup(
      @PathVariable UUID id,
      @RequestBody LayerGroupPatchRequest request,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    return ResponseEntity.ok(containerUseCase.patch(id, request, principal));
  }

  @DeleteMapping("/layer-groups/{id}")
  public ResponseEntity<Void> deleteLayerGroup(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal principal) {
    containerUseCase.delete(id, principal);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/layer-groups/{id}/members")
  public List<LayerGroupMemberResponse> getMembers(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal principal) {
    return membersUseCase.getMembers(id, principal);
  }

  @PatchMapping("/layer-groups/{id}/members")
  public ResponseEntity<Void> patchMembers(
      @PathVariable UUID id,
      @RequestBody LayerGroupMembersPatchRequest request,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    membersUseCase.patchMembers(id, request, principal);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/layer-groups/{id}/layers")
  public List<LayerGroupLayerResponse> getLayersForLayerGroup(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal principal) {
    return layersUseCase.getLayers(id, principal);
  }

  @PostMapping("/layer-groups/{id}/layers")
  public ResponseEntity<List<LayerGroupLayerResponse>> assignLayersToLayerGroup(
      @PathVariable UUID id,
      @RequestBody LayerGroupLayersAssignRequest request,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    var created = layersUseCase.assignLayers(id, request, principal);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @DeleteMapping("/layer-groups/{id}/layers/{layerId}")
  public ResponseEntity<Void> removeLayerFromLayerGroup(
      @PathVariable UUID id,
      @PathVariable UUID layerId,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    layersUseCase.removeLayer(id, layerId, principal);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/layer-groups/{id}/user-groups")
  public ResponseEntity<List<LayerGroupUserGroupResponse>> getUserGroups(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal principal) {
    return ResponseEntity.ok(userGroupsUseCase.getUserGroups(id, principal));
  }

  @PatchMapping("/layer-groups/{id}/user-groups")
  public ResponseEntity<Void> patchUserGroups(
      @PathVariable UUID id,
      @RequestBody LayerGroupUserGroupsPatchRequest request,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    try {
      userGroupsUseCase.patchUserGroups(id, request, principal);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
    return ResponseEntity.ok().build();
  }
}
