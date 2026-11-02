// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.layergroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupService;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

class AdminLayerGroupsEndpointTest {

  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442001");

  private LayerGroupService layerGroupService;
  private AdminLayerGroupsEndpoint endpoint;

  @BeforeEach
  void setUp() {
    layerGroupService = mock(LayerGroupService.class);
    endpoint = new AdminLayerGroupsEndpoint(layerGroupService);
  }

  /**
   * This route returns every group in the system, other users' PRIVATE ones included, so losing the
   * gate would turn an admin directory into a public one.
   */
  @Test
  void listGroups_hasAdminPreAuthorizeAnnotation() throws Exception {
    var method = AdminLayerGroupsEndpoint.class.getDeclaredMethod("listGroups");
    var annotation = method.getAnnotation(PreAuthorize.class);

    assertNotNull(annotation, "listGroups must be annotated with @PreAuthorize");
    assertEquals("hasRole('admin')", annotation.value());
  }

  @Test
  void listGroups_delegatesToService() {
    var group =
        new LayerGroupResponse(
            GROUP_ID, "Alpha", "desc", LayerGroupAccessScope.PRIVATE, Instant.EPOCH);
    when(layerGroupService.listGroups()).thenReturn(List.of(group));

    var result = endpoint.listGroups();

    verify(layerGroupService).listGroups();
    assertEquals(List.of(group), result);
  }

  /**
   * Creation lives on {@code POST /v1/layer-groups}, which serves admins and ordinary users alike;
   * the use case permits GLOBAL scope only for global admins.
   */
  @Test
  void endpointExposesNoCreateRoute() {
    var hasCreate =
        Arrays.stream(AdminLayerGroupsEndpoint.class.getDeclaredMethods())
            .anyMatch(m -> m.isAnnotationPresent(PostMapping.class));

    assertFalse(hasCreate, "Layer group creation must live only on the shared route");
  }
}
