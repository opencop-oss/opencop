// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.datasourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupService;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupContainerManagement;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupCreateRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminDataSourceGroupsEndpointTest {

  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-556655442001");

  private DataSourceGroupContainerManagement containerUseCase;
  private DataSourceGroupService dataSourceGroupService;
  private AdminDataSourceGroupsEndpoint endpoint;

  @BeforeEach
  void setUp() {
    containerUseCase = mock(DataSourceGroupContainerManagement.class);
    dataSourceGroupService = mock(DataSourceGroupService.class);
    endpoint = new AdminDataSourceGroupsEndpoint(containerUseCase, dataSourceGroupService);
  }

  @Test
  void createGroup_hasAdminPreAuthorizeAnnotation() throws Exception {
    var method =
        AdminDataSourceGroupsEndpoint.class.getDeclaredMethod(
            "createGroup", DataSourceGroupCreateRequest.class, OpenCopPrincipal.class);
    var annotation = method.getAnnotation(PreAuthorize.class);
    assertNotNull(annotation, "createGroup must be annotated with @PreAuthorize");
    assertEquals("hasRole('admin')", annotation.value());
  }

  @Test
  void listGroups_hasAdminPreAuthorizeAnnotation() throws Exception {
    var method = AdminDataSourceGroupsEndpoint.class.getDeclaredMethod("listGroups");
    var annotation = method.getAnnotation(PreAuthorize.class);
    assertNotNull(annotation, "listGroups must be annotated with @PreAuthorize");
    assertEquals("hasRole('admin')", annotation.value());
  }

  @Test
  void createGroup_delegatesToUseCase() {
    var request = new DataSourceGroupCreateRequest("Alpha", "desc");
    var principal = mock(OpenCopPrincipal.class);
    var response = new DataSourceGroupResponse(GROUP_ID, "Alpha", "desc", Instant.now());
    when(containerUseCase.create(request, principal)).thenReturn(response);

    var result = endpoint.createGroup(request, principal);

    verify(containerUseCase).create(request, principal);
    assertEquals(response, result.getBody());
    assertEquals(
        "/v1/data-source-groups/" + GROUP_ID, result.getHeaders().getLocation().toString());
  }
}
