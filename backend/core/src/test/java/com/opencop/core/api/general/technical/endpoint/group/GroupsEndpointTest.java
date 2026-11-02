// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.group.GroupService;
import com.opencop.core.api.general.technical.gateway.db.group.AppGroupLayerService;
import com.opencop.core.api.general.technical.model.group.GroupLayerAssignRequest;
import com.opencop.core.api.general.technical.model.group.GroupLayerResponse;
import com.opencop.core.api.general.technical.model.group.GroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.group.GroupSearchResult;
import com.opencop.core.sdk.model.iam.group.GroupManagementSource;
import com.opencop.core.sdk.model.iam.group.GroupReadFilter;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class GroupsEndpointTest {

  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442002");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442003");
  private static final UUID USER_A = UUID.fromString("550e8400-e29b-41d4-a716-446655442004");

  private GroupService groupService;
  private AppGroupLayerService groupLayerService;
  private GroupsEndpoint endpoint;

  @BeforeEach
  void setUp() {
    groupService = mock(GroupService.class);
    groupLayerService = mock(AppGroupLayerService.class);
    endpoint = new GroupsEndpoint(groupService, groupLayerService);
  }

  @Test
  void deleteGroup_delegatesToService() throws Exception {
    endpoint.deleteGroup(GROUP_ID);
    verify(groupService).deleteGroup(GROUP_ID);
  }

  @Test
  void patchMembers_delegatesToService() throws Exception {
    var request = new GroupMembersPatchRequest(Set.of(USER_A), null);
    endpoint.patchMembers(GROUP_ID, request);
    verify(groupService).patchMembers(GROUP_ID, request);
  }

  @Test
  void assignGroupToLayer_delegatesToService() throws Exception {
    var request = new GroupLayerAssignRequest(LAYER_ID, LayerRole.EDITOR);
    when(groupLayerService.assignGroupToLayer(GROUP_ID, request))
        .thenReturn(
            new GroupLayerResponse(
                GROUP_ID,
                "Alpha",
                GroupManagementSource.OPENCOP,
                LAYER_ID,
                "Ops",
                LayerRole.EDITOR,
                Instant.now()));
    endpoint.assignGroupToLayer(GROUP_ID, request);
    verify(groupLayerService).assignGroupToLayer(GROUP_ID, request);
  }

  @Test
  void searchGroups_passesSearchTermAndPageableThrough() {
    var pageable = PageRequest.of(1, 5);
    var expected =
        new PageResponse<>(
            List.of(new GroupSearchResult(GROUP_ID, "Alpha", GroupManagementSource.OPENCOP)),
            1,
            5,
            6L,
            2);
    when(groupService.getPage(new GroupReadFilter("alp"), pageable)).thenReturn(expected);

    var response = endpoint.searchGroups("alp", pageable);

    assertEquals(expected, response.getBody());
    verify(groupService).getPage(new GroupReadFilter("alp"), pageable);
  }

  @Test
  void searchGroups_withNoSearchTerm_passesAnUnconstrainedFilter() {
    var pageable = PageRequest.of(0, 25);
    when(groupService.getPage(new GroupReadFilter(null), pageable))
        .thenReturn(new PageResponse<>(List.of(), 0, 25, 0L, 0));

    endpoint.searchGroups(null, pageable);

    verify(groupService).getPage(new GroupReadFilter(null), pageable);
  }

  @Test
  void removeGroupFromLayer_delegatesToService() throws Exception {
    endpoint.removeGroupFromLayer(GROUP_ID, LAYER_ID);
    verify(groupLayerService).removeGroupFromLayer(GROUP_ID, LAYER_ID);
  }
}
