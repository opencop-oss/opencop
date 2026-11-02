// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupLayerEmbeddingManagement;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerAssignRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupLayerResponse;
import com.opencop.core.sdk.component.security.TestPrincipals;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LayerDataSourceGroupsEndpointTest {

  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448001");
  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448002");
  private static final UUID DATA_SOURCE_GROUP_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655448005");

  private DataSourceGroupLayerEmbeddingManagement dataSourceGroupLayerUseCase;
  private LayerDataSourceGroupsEndpoint endpoint;

  @BeforeEach
  void setUp() {
    dataSourceGroupLayerUseCase = mock(DataSourceGroupLayerEmbeddingManagement.class);
    endpoint = new LayerDataSourceGroupsEndpoint(dataSourceGroupLayerUseCase);
  }

  @Test
  void getDataSourceGroupsForLayer_delegatesToUseCase() {
    var authentication = TestPrincipals.mockPrincipal(ADMIN_ID);
    var expected =
        List.of(
            new DataSourceGroupLayerResponse(
                DATA_SOURCE_GROUP_ID, "Sources", LAYER_ID, "Ops", Instant.EPOCH));
    when(dataSourceGroupLayerUseCase.getDataSourceGroupsForLayer(LAYER_ID, authentication))
        .thenReturn(expected);

    var response = endpoint.getDataSourceGroupsForLayer(LAYER_ID, authentication);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(expected, response.getBody());
  }

  @Test
  void assignDataSourceGroupsToLayer_delegatesToUseCase() {
    var authentication = TestPrincipals.mockPrincipal(ADMIN_ID);
    var request = new DataSourceGroupLayerAssignRequest(Set.of(DATA_SOURCE_GROUP_ID));

    var response = endpoint.assignDataSourceGroupsToLayer(LAYER_ID, request, authentication);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    verify(dataSourceGroupLayerUseCase)
        .assignDataSourceGroupsToLayer(LAYER_ID, request, authentication);
  }

  @Test
  void removeDataSourceGroupFromLayer_delegatesToUseCase() {
    var authentication = TestPrincipals.mockPrincipal(ADMIN_ID);

    var response =
        endpoint.removeDataSourceGroupFromLayer(LAYER_ID, DATA_SOURCE_GROUP_ID, authentication);

    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    verify(dataSourceGroupLayerUseCase)
        .removeDataSourceGroupFromLayer(LAYER_ID, DATA_SOURCE_GROUP_ID, authentication);
  }
}
