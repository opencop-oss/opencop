// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.group;

import com.opencop.core.api.general.ports.gateway.group.GroupService;
import com.opencop.core.api.general.technical.gateway.db.group.AppGroupLayerService;
import com.opencop.core.api.general.technical.model.group.GroupCreateRequest;
import com.opencop.core.api.general.technical.model.group.GroupLayerAssignRequest;
import com.opencop.core.api.general.technical.model.group.GroupLayerResponse;
import com.opencop.core.api.general.technical.model.group.GroupMemberResponse;
import com.opencop.core.api.general.technical.model.group.GroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.group.GroupPatchRequest;
import com.opencop.core.api.general.technical.model.group.GroupResponse;
import com.opencop.core.api.general.technical.model.group.GroupSearchResult;
import com.opencop.core.sdk.model.iam.group.GroupReadFilter;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/v1")
public class GroupsEndpoint {

  private final GroupService groupService;
  private final AppGroupLayerService groupLayerService;

  GroupsEndpoint(final GroupService groupService, final AppGroupLayerService groupLayerService) {
    this.groupService = groupService;
    this.groupLayerService = groupLayerService;
  }

  /**
   * Paged, searchable user groups under the minimal picker projection.
   *
   * <p>Open to any authenticated caller, unlike the admin listing below. It is safe by projection
   * rather than by row filtering: {@link GroupSearchResult} puts only id and name on the wire, and
   * the sort allowlist admits only those two fields, so neither the body nor the ordering can
   * reveal an attribute the projection withholds.
   */
  @GetMapping("/user-groups/search")
  public ResponseEntity<PageResponse<GroupSearchResult>> searchGroups(
      @RequestParam(required = false) String search,
      @PageableDefault(size = 25) Pageable pageable) {
    return ResponseEntity.ok(groupService.getPage(new GroupReadFilter(search), pageable));
  }

  // =========================================================
  // ===================== ADMIN ONLY ========================
  // =========================================================

  @PreAuthorize("hasRole('admin')")
  @GetMapping("/admin/user-groups")
  public List<GroupResponse> listGroups() {
    return groupService.listGroups();
  }

  @PreAuthorize("hasRole('admin')")
  @GetMapping("/admin/user-groups/{userGroupId}/members")
  public List<GroupMemberResponse> getMembers(@PathVariable UUID userGroupId) {
    try {
      return groupService.getMembers(userGroupId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @PreAuthorize("hasRole('admin')")
  @PostMapping("/admin/user-groups")
  public ResponseEntity<GroupResponse> createGroup(@RequestBody GroupCreateRequest request) {
    try {
      var created = groupService.createGroup(request);
      return ResponseEntity.created(URI.create("/v1/admin/user-groups/" + created.id()))
          .body(created);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PreAuthorize("hasRole('admin')")
  @DeleteMapping("/admin/user-groups/{userGroupId}")
  public ResponseEntity<Void> deleteGroup(@PathVariable UUID userGroupId) {
    try {
      groupService.deleteGroup(userGroupId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("hasRole('admin')")
  @PatchMapping("/admin/user-groups/{userGroupId}")
  public ResponseEntity<GroupResponse> patchGroup(
      @PathVariable UUID userGroupId, @RequestBody GroupPatchRequest request) {
    try {
      return ResponseEntity.ok(groupService.patchGroup(userGroupId, request));
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PreAuthorize("hasRole('admin')")
  @PatchMapping("/admin/user-groups/{userGroupId}/members")
  public ResponseEntity<Void> patchMembers(
      @PathVariable UUID userGroupId, @RequestBody GroupMembersPatchRequest request) {
    try {
      groupService.patchMembers(userGroupId, request);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
    return ResponseEntity.ok().build();
  }

  // =========================================================
  // ================= GROUP <-> LAYER =======================
  // =========================================================

  @PreAuthorize("hasRole('admin')")
  @GetMapping("/admin/user-groups/{userGroupId}/layers")
  public List<GroupLayerResponse> getLayersForGroup(@PathVariable UUID userGroupId) {
    try {
      return groupLayerService.getLayersForGroup(userGroupId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }

  @PreAuthorize("hasRole('admin')")
  @PostMapping("/admin/user-groups/{userGroupId}/layers")
  public ResponseEntity<GroupLayerResponse> assignGroupToLayer(
      @PathVariable UUID userGroupId, @RequestBody GroupLayerAssignRequest request) {
    try {
      var created = groupLayerService.assignGroupToLayer(userGroupId, request);
      return ResponseEntity.created(
              URI.create("/v1/admin/user-groups/" + userGroupId + "/layers/" + created.layerId()))
          .body(created);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }

  @PreAuthorize("hasRole('admin')")
  @DeleteMapping("/admin/user-groups/{userGroupId}/layers/{layerId}")
  public ResponseEntity<Void> removeGroupFromLayer(
      @PathVariable UUID userGroupId, @PathVariable UUID layerId) {
    try {
      groupLayerService.removeGroupFromLayer(userGroupId, layerId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("hasRole('admin')")
  @GetMapping("/admin/layers/{layerId}/user-groups")
  public List<GroupLayerResponse> getGroupsForLayer(@PathVariable UUID layerId) {
    try {
      return groupLayerService.getGroupsForLayer(layerId);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    }
  }
}
