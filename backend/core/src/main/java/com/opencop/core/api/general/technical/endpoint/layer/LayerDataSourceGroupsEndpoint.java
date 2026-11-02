// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.layer;

import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupLayerEmbeddingManagement;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerAssignRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LayerDataSourceGroupsEndpoint {

  private final DataSourceGroupLayerEmbeddingManagement dataSourceGroupLayerUseCase;

  LayerDataSourceGroupsEndpoint(
      DataSourceGroupLayerEmbeddingManagement dataSourceGroupLayerUseCase) {
    this.dataSourceGroupLayerUseCase = dataSourceGroupLayerUseCase;
  }

  @GetMapping("/v1/layers/{layerId}/data-source-groups")
  public ResponseEntity<List<DataSourceGroupLayerResponse>> getDataSourceGroupsForLayer(
      @PathVariable UUID layerId, @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return ResponseEntity.ok(
        dataSourceGroupLayerUseCase.getDataSourceGroupsForLayer(layerId, authentication));
  }

  @PostMapping("/v1/layers/{layerId}/data-source-groups")
  public ResponseEntity<List<DataSourceGroupLayerResponse>> assignDataSourceGroupsToLayer(
      @PathVariable UUID layerId,
      @RequestBody DataSourceGroupLayerAssignRequest request,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            dataSourceGroupLayerUseCase.assignDataSourceGroupsToLayer(
                layerId, request, authentication));
  }

  @DeleteMapping("/v1/layers/{layerId}/data-source-groups/{groupId}")
  public ResponseEntity<Void> removeDataSourceGroupFromLayer(
      @PathVariable UUID layerId,
      @PathVariable UUID groupId,
      @AuthenticationPrincipal OpenCopPrincipal authentication) {
    dataSourceGroupLayerUseCase.removeDataSourceGroupFromLayer(layerId, groupId, authentication);
    return ResponseEntity.noContent().build();
  }
}
