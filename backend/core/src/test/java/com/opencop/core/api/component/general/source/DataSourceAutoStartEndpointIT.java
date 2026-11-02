// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.ConnectionFixtures;
import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.technical.endpoint.source.DataSourceAutoStartEndpoint;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.ConnectService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ComponentWebIT(classes = DataSourceAutoStartWebTestApplication.class)
class DataSourceAutoStartEndpointIT extends AbstractWebIT {

  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440181");
  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440184");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440185");
  private static final UUID SOURCE_TWO_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440186");
  private static final String STALE_INSTANCE_ID = "connect-old";
  private static final String REPLACEMENT_INSTANCE_ID = "connect-2";
  private static final String REPLACEMENT_A = "connect-a";
  private static final String REPLACEMENT_B = "connect-b";

  private static final Authentication ADMIN_AUTH = TestAuthentications.oidcAdmin(ADMIN_ID);
  private static final Authentication USER_AUTH = TestAuthentications.oidcUser();

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSourceRepository dataSourceRepository;

  @MockitoBean private ConnectService connectService;
  @MockitoBean private SocketReconciliationNudgePublisher nudgePublisher;
  @MockitoBean private ConnectInstanceResolver connectInstanceResolver;

  private ConnectionFixtures connectionFixtures;

  @BeforeEach
  void setUp() {
    connectionFixtures = new ConnectionFixtures(jdbcTemplate, dataSourceRepository, objectMapper);
    connectionFixtures.reset();
    connectionFixtures.seedDefaultMapper(MAPPER_ID);
    seedUser(ADMIN_ID);

    whenBatchRoutableInstances(List.of(connectInfo("connect-1", 0)));
  }

  private static ConnectInstanceInfo connectInfo(String instanceId, int connectionCount) {
    return new ConnectInstanceInfo(
        instanceId, Instant.now(), connectionCount, instanceId + "-reg", 1);
  }

  private void whenBatchRoutableInstances(List<ConnectInstanceInfo> instances) {
    when(connectInstanceResolver.getRoutableInstancesForCurrentRegistry(any()))
        .thenReturn(instances);
  }

  private void seedUser(UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "https://issuer.test",
        "user-" + userId,
        "user@example.test",
        "Test",
        "User",
        "Test User");
  }

  // Auth
  @Test
  void autoStartEnabledDataSources_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(post("/v1/admin/data-sources/start-enabled").with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void autoStartEnabledDataSources_whenNonAdmin_returnsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/v1/admin/data-sources/start-enabled")
                .with(csrf())
                .with(authentication(USER_AUTH)))
        .andExpect(status().isForbidden());
  }

  // Happy path
  @Test
  void autoStartEnabledDataSources_whenNoEnabledSources_returnsZeroSummary() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/admin/data-sources/start-enabled")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, DataSourceAutoStartEndpoint.AutoStartResponse.class);
    assertEquals(0, response.started());
    assertEquals(0, response.failed());
    assertEquals(0, response.total());
    verifyNoInteractions(connectService, nudgePublisher);
  }

  @Test
  void autoStartEnabledDataSources_asAdminStartsEnabledSocketSourcesAndReturnsSummary()
      throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, null);
    connectionFixtures.persistTcpSource(SOURCE_TWO_ID, "Bravo", MAPPER_ID, false, null);

    var result =
        mockMvc
            .perform(
                post("/v1/admin/data-sources/start-enabled")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, DataSourceAutoStartEndpoint.AutoStartResponse.class);
    assertEquals(1, response.started());
    assertEquals(0, response.failed());
    assertEquals(1, response.total());

    var row = connectionFixtures.loadEnabledAndAssignedInstance(SOURCE_ID);
    assertEquals(Boolean.TRUE, row.get("enabled"));
    assertEquals("connect-1", row.get("assigned_instance"));
    verify(nudgePublisher).requestReconciliation();
    verifyNoInteractions(connectService);
  }

  @Test
  void autoStartEnabledDataSources_doesNotStartArchivedEnabledSources() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, null);
    connectionFixtures.persistTcpSource(SOURCE_TWO_ID, "Bravo", MAPPER_ID, true, null);
    dataSourceRepository.archive(SOURCE_TWO_ID, ADMIN_ID);

    var result =
        mockMvc
            .perform(
                post("/v1/admin/data-sources/start-enabled")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, DataSourceAutoStartEndpoint.AutoStartResponse.class);
    assertEquals(1, response.started());
    assertEquals(0, response.failed());
    assertEquals(1, response.total());
    verify(nudgePublisher).requestReconciliation();
  }

  @Test
  void autoStartEnabledDataSources_whenAssignedInstanceIsStale_reassignsToRoutableReplacement()
      throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, STALE_INSTANCE_ID);
    whenBatchRoutableInstances(List.of(connectInfo(REPLACEMENT_INSTANCE_ID, 0)));
    doAnswer(
            _ -> {
              var row = connectionFixtures.loadEnabledAndAssignedInstance(SOURCE_ID);
              assertEquals(Boolean.TRUE, row.get("enabled"));
              assertEquals(REPLACEMENT_INSTANCE_ID, row.get("assigned_instance"));
              return null;
            })
        .when(nudgePublisher)
        .requestReconciliation();

    var result =
        mockMvc
            .perform(
                post("/v1/admin/data-sources/start-enabled")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, DataSourceAutoStartEndpoint.AutoStartResponse.class);
    assertEquals(1, response.started());
    assertEquals(0, response.failed());
    assertEquals(1, response.total());

    var row = connectionFixtures.loadEnabledAndAssignedInstance(SOURCE_ID);
    assertEquals(Boolean.TRUE, row.get("enabled"));
    assertEquals(REPLACEMENT_INSTANCE_ID, row.get("assigned_instance"));
    verify(nudgePublisher).requestReconciliation();
    verifyNoInteractions(connectService);
  }

  @ParameterizedTest
  @CsvSource({"UDP,W", "UDP,RW", "RTSP,W", "RTSP,RW"})
  void autoStartEnabledDataSources_whenLegacySocketAccessIsIncompatible_reportsFailureWithoutNudge(
      String transport, String access) throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Legacy", MAPPER_ID, true, null);
    connectionFixtures.setSocketTransportAndAccess(SOURCE_ID, transport, access);

    var result =
        mockMvc
            .perform(
                post("/v1/admin/data-sources/start-enabled")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, DataSourceAutoStartEndpoint.AutoStartResponse.class);
    assertEquals(0, response.started());
    assertEquals(1, response.failed());
    assertEquals(1, response.total());
    assertEquals(
        null,
        connectionFixtures.loadEnabledAndAssignedInstance(SOURCE_ID).get("assigned_instance"));
    verifyNoInteractions(connectService, nudgePublisher);
  }

  @Test
  void autoStartEnabledDataSources_whenEqualLoadWorkers_spreadsStaleAssignmentsAcrossBatch()
      throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, STALE_INSTANCE_ID);
    connectionFixtures.persistTcpSource(SOURCE_TWO_ID, "Bravo", MAPPER_ID, true, STALE_INSTANCE_ID);
    whenBatchRoutableInstances(
        List.of(connectInfo(REPLACEMENT_B, 0), connectInfo(REPLACEMENT_A, 0)));

    var result =
        mockMvc
            .perform(
                post("/v1/admin/data-sources/start-enabled")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, DataSourceAutoStartEndpoint.AutoStartResponse.class);
    assertEquals(2, response.started());
    assertEquals(0, response.failed());
    assertEquals(2, response.total());

    var first = connectionFixtures.loadEnabledAndAssignedInstance(SOURCE_ID);
    var second = connectionFixtures.loadEnabledAndAssignedInstance(SOURCE_TWO_ID);
    var assignments =
        Set.of((String) first.get("assigned_instance"), (String) second.get("assigned_instance"));
    assertEquals(Set.of(REPLACEMENT_A, REPLACEMENT_B), assignments);
    verify(connectInstanceResolver, times(1)).getRoutableInstancesForCurrentRegistry(any());
    verify(nudgePublisher, times(1)).requestReconciliation();
    verifyNoInteractions(connectService);
  }

  @Test
  void autoStartEnabledDataSources_gatesConnectFreshnessOnceForBatch() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, STALE_INSTANCE_ID);
    connectionFixtures.persistTcpSource(SOURCE_TWO_ID, "Bravo", MAPPER_ID, true, STALE_INSTANCE_ID);
    whenBatchRoutableInstances(List.of(connectInfo(REPLACEMENT_INSTANCE_ID, 0)));

    mockMvc
        .perform(
            post("/v1/admin/data-sources/start-enabled")
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isOk());

    verify(connectInstanceResolver, times(1)).getRoutableInstancesForCurrentRegistry(any());
    verify(nudgePublisher, times(1)).requestReconciliation();
    verifyNoInteractions(connectService);
  }
}
