// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.datasourcegroup;

import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupService;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupContainerManagement;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupCreateRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class AdminDataSourceGroupsEndpoint {

  private final DataSourceGroupContainerManagement containerUseCase;
  private final DataSourceGroupService dataSourceGroupService;

  AdminDataSourceGroupsEndpoint(
      DataSourceGroupContainerManagement containerUseCase,
      DataSourceGroupService dataSourceGroupService) {
    this.containerUseCase = containerUseCase;
    this.dataSourceGroupService = dataSourceGroupService;
  }

  @PreAuthorize("hasRole('admin')")
  @PostMapping("/admin/data-source-groups")
  public ResponseEntity<DataSourceGroupResponse> createGroup(
      @RequestBody DataSourceGroupCreateRequest request,
      @AuthenticationPrincipal OpenCopPrincipal principal) {
    var created = containerUseCase.create(request, principal);
    return ResponseEntity.created(URI.create("/v1/data-source-groups/" + created.id()))
        .body(created);
  }

  @PreAuthorize("hasRole('admin')")
  @GetMapping("/admin/data-source-groups")
  public List<DataSourceGroupResponse> listGroups() {
    return dataSourceGroupService.listGroups();
  }
}
