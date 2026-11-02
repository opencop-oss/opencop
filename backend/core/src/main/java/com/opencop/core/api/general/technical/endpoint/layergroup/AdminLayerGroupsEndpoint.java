// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.layergroup;

import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupService;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin-only slice of layer-group management. Creation is deliberately absent: {@code POST
 * /v1/layer-groups} on {@link LayerGroupsEndpoint} serves admins and ordinary users alike, since
 * that use case already permits GLOBAL scope only for global admins.
 */
@RestController
@RequestMapping("/v1")
public class AdminLayerGroupsEndpoint {

  private final LayerGroupService layerGroupService;

  AdminLayerGroupsEndpoint(LayerGroupService layerGroupService) {
    this.layerGroupService = layerGroupService;
  }

  /**
   * Every layer group in the system, including other users' PRIVATE groups, which is why the admin
   * gate is load-bearing. Feeds the admin assign pickers.
   */
  @PreAuthorize("hasRole('admin')")
  @GetMapping("/admin/layer-groups")
  public List<LayerGroupResponse> listGroups() {
    return layerGroupService.listGroups();
  }
}
