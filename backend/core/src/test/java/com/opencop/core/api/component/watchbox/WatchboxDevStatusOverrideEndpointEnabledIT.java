// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static com.opencop.core.api.component.watchbox.WatchboxTestMapperIdentity.MAPPER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.component.support.fixture.ConnectionFixtures;
import com.opencop.core.api.component.support.fixture.LayerFixtures;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.watchbox.business.service.WatchboxDevStatusOverrideService;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(
    classes = WatchboxWebTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "opencop.dev.mode=true")
@ActiveProfiles("it")
@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
class WatchboxDevStatusOverrideEndpointEnabledIT extends AbstractWebIT {

  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442001");
  private static final UUID VIEWER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442002");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442003");
  private static final Authentication OWNER_ADMIN_AUTH = TestAuthentications.oidcAdmin(OWNER_ID);
  private static final Authentication VIEWER_AUTH = TestAuthentications.oidcUser(VIEWER_ID);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private MapperProfileService mapperProfileService;
  @Autowired private IamLayerRepository layerRepository;
  @Autowired private IamLayerMembersRepository layerMembersRepository;
  @Autowired private WatchboxDevStatusOverrideService overrideService;

  @Autowired
  private com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository
      serverLayerRepository;

  private ConnectionFixtures connectionFixtures;
  private LayerFixtures layerFixtures;
  private UUID layerId;

  @BeforeEach
  void setUp() {
    connectionFixtures = new ConnectionFixtures(jdbcTemplate, dataSourceRepository, objectMapper);
    layerFixtures =
        new LayerFixtures(
            jdbcTemplate, layerRepository, layerMembersRepository, serverLayerRepository);

    resetWatchboxes();
    seedFreshDetectionHeartbeat();
    layerFixtures.reset();
    connectionFixtures.reset();
    insertUser(OWNER_ID);
    insertUser(VIEWER_ID);
    connectionFixtures.seedDefaultMapper(MAPPER_ID);
    connectionFixtures.persistTcpSource(SOURCE_ID, "test-source", MAPPER_ID, true, null);
    layerId =
        layerFixtures.persistLayerWithMembers(
            "Watchbox Dev Fixture Layer",
            Map.of(OWNER_ID, LayerRole.OWNER, VIEWER_ID, LayerRole.MEMBER));
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.VIEWER);
    when(mapperProfileService.getLatestMapperProfileFull(MAPPER_ID))
        .thenReturn(
            new MapperProfileFullMeta(
                MapperProfile.builder()
                    .withId(MAPPER_ID)
                    .withName("test-mapper")
                    .withEditable(false)
                    .withIntegrationId(WatchboxTestMapperIdentity.INTEGRATION_ID)
                    .withVersion(1)
                    .withArchived(false)
                    .build(),
                null,
                null));
  }

  @Test
  void setGetApplyAndClearStatusOverride() throws Exception {
    UUID watchboxId = createWatchbox();

    mockMvc
        .perform(
            get("/v1/dev/watchboxes/{watchboxId}/status-override", watchboxId)
                .with(authentication(OWNER_ADMIN_AUTH)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WATCHBOX_NOT_FOUND"));

    var putResult =
        mockMvc
            .perform(
                put("/v1/dev/watchboxes/{watchboxId}/status-override", watchboxId)
                    .with(csrf())
                    .with(authentication(OWNER_ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "alertingStatus": "FAILED",
                          "alertingReasons": ["WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN"],
                          "sourceStatuses": [{
                            "sourceId": "%s",
                            "sourceName": "fixture source",
                            "status": "FAILED",
                            "reason": "WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN",
                            "observedCount": 1001,
                            "cap": 1000
                          }]
                        }
                        """
                            .formatted(SOURCE_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alertingStatus").value("FAILED"))
            .andExpect(jsonPath("$.sourceStatuses[0].sourceName").value("fixture source"))
            .andReturn();
    assertThat(readResponse(putResult, WatchboxResponse.class).id()).isEqualTo(watchboxId);

    mockMvc
        .perform(
            get("/v1/dev/watchboxes/{watchboxId}/status-override", watchboxId)
                .with(authentication(OWNER_ADMIN_AUTH)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.alertingStatus").value("FAILED"));

    mockMvc
        .perform(get("/v1/watchboxes/{watchboxId}", watchboxId).with(authentication(VIEWER_AUTH)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.alertingStatus").value("FAILED"))
        .andExpect(jsonPath("$.sourceStatuses").isEmpty());

    mockMvc
        .perform(get("/v1/watchboxes").with(authentication(OWNER_ADMIN_AUTH)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].alertingStatus").value("FAILED"));

    mockMvc
        .perform(
            delete("/v1/dev/watchboxes/{watchboxId}/status-override", watchboxId)
                .with(csrf())
                .with(authentication(OWNER_ADMIN_AUTH)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            delete("/v1/dev/watchboxes/{watchboxId}/status-override", watchboxId)
                .with(csrf())
                .with(authentication(OWNER_ADMIN_AUTH)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/v1/watchboxes/{watchboxId}", watchboxId).with(authentication(OWNER_ADMIN_AUTH)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.alertingStatus").value("ACTIVE"));
  }

  @Test
  void deletingWatchboxClearsStoredOverride() throws Exception {
    UUID watchboxId = createWatchbox();
    mockMvc
        .perform(
            put("/v1/dev/watchboxes/{watchboxId}/status-override", watchboxId)
                .with(csrf())
                .with(authentication(OWNER_ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"alertingStatus":"FAILED","alertingReasons":["WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN"],"sourceStatuses":[]}
                    """))
        .andExpect(status().isOk());
    assertThat(overrideService.get(watchboxId)).isPresent();

    mockMvc
        .perform(
            delete("/v1/watchboxes/{watchboxId}", watchboxId)
                .with(csrf())
                .with(authentication(OWNER_ADMIN_AUTH)))
        .andExpect(status().isNoContent());

    assertThat(overrideService.get(watchboxId)).isEmpty();
  }

  @Test
  void nonAdminAuthenticatedUserIsForbiddenFromDevOverrideRoutes() throws Exception {
    UUID watchboxId = createWatchbox();

    mockMvc
        .perform(
            put("/v1/dev/watchboxes/{watchboxId}/status-override", watchboxId)
                .with(csrf())
                .with(authentication(VIEWER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"alertingStatus":"FAILED","alertingReasons":["WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN"],"sourceStatuses":[]}
                    """))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/v1/dev/watchboxes/{watchboxId}/status-override", watchboxId)
                .with(authentication(VIEWER_AUTH)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            delete("/v1/dev/watchboxes/{watchboxId}/status-override", watchboxId)
                .with(csrf())
                .with(authentication(VIEWER_AUTH)))
        .andExpect(status().isForbidden());
  }

  @Test
  void unreadableSetOverrideBodyReturnsWatchboxErrorEnvelope() throws Exception {
    UUID watchboxId = createWatchbox();

    var malformed =
        mockMvc
            .perform(
                put("/v1/dev/watchboxes/{watchboxId}/status-override", watchboxId)
                    .with(csrf())
                    .with(authentication(OWNER_ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"alertingStatus\": \"FAILED\""))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertUnreadableBodyError(malformed);

    var invalidEnum =
        mockMvc
            .perform(
                put("/v1/dev/watchboxes/{watchboxId}/status-override", watchboxId)
                    .with(csrf())
                    .with(authentication(OWNER_ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"alertingStatus\":\"UNKNOWN\",\"alertingReasons\":[],\"sourceStatuses\":[]}"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertUnreadableBodyError(invalidEnum);

    var numericEnum =
        mockMvc
            .perform(
                put("/v1/dev/watchboxes/{watchboxId}/status-override", watchboxId)
                    .with(csrf())
                    .with(authentication(OWNER_ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"alertingStatus\":0,\"alertingReasons\":[],\"sourceStatuses\":[]}"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertUnreadableBodyError(numericEnum);
  }

  @AfterEach
  void tearDown() {
    resetWatchboxes();
  }

  private UUID createWatchbox() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name":"dev-fixture-proof",
                          "description":"REST/config proof",
                          "direction":"BOTH",
                          "layerIds":["%s"],
                          "polygon":{"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2],[-100.2,40.2],[-100.2,39.8]]]},
                          "filter":{"predicates":[]},
                          "enabled":true
                        }
                        """
                            .formatted(layerId)))
            .andExpect(status().isCreated())
            .andReturn();
    return readResponse(createResult, WatchboxResponse.class).id();
  }

  private void assertUnreadableBodyError(org.springframework.test.web.servlet.MvcResult result)
      throws Exception {
    var error = readResponse(result, WatchboxErrorResponse.class);
    assertThat(error.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_INVALID_REQUEST);
    assertThat(error.message()).isEqualTo("Invalid request body");
    assertThat(error.fields()).containsExactly("body");
    assertThat(error.reasons()).isEmpty();
  }

  private void resetWatchboxes() {
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_detection_heartbeat");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_config_audit");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_source_status");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_layer");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox");
  }

  /**
   * The status rollup fails closed on detection liveness, so the revert-to-real-status assertion
   * needs a live Processor heartbeat on record - exactly what a healthy deployment has.
   */
  private void seedFreshDetectionHeartbeat() {
    jdbcTemplate.update(
        """
        INSERT INTO watchbox.watchbox_detection_heartbeat
          (instance_id, listener_started_at, last_heartbeat_at, heartbeat_expires_at,
           assigned_partitions, heartbeat_sequence)
        VALUES ('processor-it', now(), now(), now() + INTERVAL '60 seconds', 2, 1)
        ON CONFLICT (instance_id) DO UPDATE SET
          heartbeat_expires_at = now() + INTERVAL '60 seconds',
          stopped_at = NULL
        """);
  }

  private void insertUser(UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
            id, idp_user_id, idp_username, idp_issuer, username, active, administrator, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
        ON CONFLICT (id) DO NOTHING
        """,
        userId,
        "idp-user-" + userId,
        "user-" + userId,
        "issuer",
        "user-" + userId,
        true,
        false);
  }
}
