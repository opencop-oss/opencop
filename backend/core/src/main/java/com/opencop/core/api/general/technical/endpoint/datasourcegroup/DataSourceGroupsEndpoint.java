// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.datasourcegroup;

import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupContainerManagement;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupDataSourceManagement;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupLayerEmbeddingManagement;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupMemberManagement;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupUserGroupManagement;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceAssignRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMemberResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupUserGroupResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupUserGroupsPatchRequest;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.NotFoundException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
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
public class DataSourceGroupsEndpoint {

  private final DataSourceGroupContainerManagement containerUseCase;
  private final DataSourceGroupMemberManagement membersUseCase;
  private final DataSourceGroupUserGroupManagement userGroupsUseCase;
  private final DataSourceGroupLayerEmbeddingManagement layerEmbeddingUseCase;
  private final DataSourceGroupDataSourceManagement dataSourcesUseCase;

  DataSourceGroupsEndpoint(
      DataSourceGroupContainerManagement containerUseCase,
      DataSourceGroupMemberManagement membersUseCase,
      DataSourceGroupUserGroupManagement userGroupsUseCase,
      DataSourceGroupLayerEmbeddingManagement layerEmbeddingUseCase,
      DataSourceGroupDataSourceManagement dataSourcesUseCase) {
    this.containerUseCase = containerUseCase;
    this.membersUseCase = membersUseCase;
    this.userGroupsUseCase = userGroupsUseCase;
    this.layerEmbeddingUseCase = layerEmbeddingUseCase;
    this.dataSourcesUseCase = dataSourcesUseCase;
  }

  @GetMapping("/data-source-groups/{id}")
  public DataSourceGroupResponse getDataSourceGroup(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal principal) {
    return containerUseCase.get(id, principal);
  }

  @PatchMapping("/data-source-groups/{id}")
  public ResponseEntity<DataSourceGroupResponse> patchDataSourceGroup(
      @PathVariable UUID id,
      @RequestBody DataSourceGroupPatchRequest request,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    return ResponseEntity.ok(containerUseCase.patch(id, request, principal));
  }

  @DeleteMapping("/data-source-groups/{id}")
  public ResponseEntity<Void> deleteDataSourceGroup(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal principal) {
    containerUseCase.delete(id, principal);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/data-source-groups/{id}/members")
  public List<DataSourceGroupMemberResponse> getMembers(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal principal) {
    return membersUseCase.getMembers(id, principal);
  }

  @PatchMapping("/data-source-groups/{id}/members")
  public ResponseEntity<Void> patchMembers(
      @PathVariable UUID id,
      @RequestBody DataSourceGroupMembersPatchRequest request,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    membersUseCase.patchMembers(id, request, principal);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/data-source-groups/{id}/user-groups")
  public ResponseEntity<List<DataSourceGroupUserGroupResponse>> getUserGroups(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal principal) {
    return ResponseEntity.ok(userGroupsUseCase.getUserGroups(id, principal));
  }

  @PatchMapping("/data-source-groups/{id}/user-groups")
  public ResponseEntity<Void> patchUserGroups(
      @PathVariable UUID id,
      @RequestBody DataSourceGroupUserGroupsPatchRequest request,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    try {
      userGroupsUseCase.patchUserGroups(id, request, principal);
    } catch (NotFoundException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.NOT_FOUND);
    } catch (PersistenceException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.BAD_REQUEST);
    }
    return ResponseEntity.ok().build();
  }

  @GetMapping("/data-source-groups/{id}/layers")
  public ResponseEntity<List<DataSourceGroupLayerResponse>> getLayersForDataSourceGroup(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal principal) {
    return ResponseEntity.ok(layerEmbeddingUseCase.getLayersForDataSourceGroup(id, principal));
  }

  @GetMapping("/data-source-groups/{id}/data-sources")
  public ResponseEntity<List<DataSourceGroupDataSourceResponse>> getDataSources(
      @PathVariable UUID id, @AuthenticationPrincipal OpenCopPrincipal principal) {
    return ResponseEntity.ok(dataSourcesUseCase.getDataSources(id, principal));
  }

  @PostMapping("/data-source-groups/{id}/data-sources")
  public ResponseEntity<List<DataSourceGroupDataSourceResponse>> assignDataSources(
      @PathVariable UUID id,
      @RequestBody DataSourceGroupDataSourceAssignRequest request,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(dataSourcesUseCase.assignDataSources(id, request, principal));
  }

  @DeleteMapping("/data-source-groups/{id}/data-sources/{sourceId}")
  public ResponseEntity<Void> removeDataSource(
      @PathVariable UUID id,
      @PathVariable UUID sourceId,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    dataSourcesUseCase.removeDataSource(id, sourceId, principal);
    return ResponseEntity.noContent().build();
  }
}
