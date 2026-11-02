// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.component.support.fixture.ConnectionFixtures;
import com.opencop.core.api.component.support.fixture.LayerFixtures;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Watchbox admission when a layer carries archived or stopped sources (opencop-java #217). Source A
 * has an eligible (stable-id) mapper; source B has a user-owned mapper without rules, which
 * admission rejects. Both are attached to one layer. Archiving is performed through the
 * repository's own archive method, the same write the delete endpoint performs; the endpoint's
 * lifecycle side effects are covered by the connection tests and are not part of this proof.
 * Re-enabling after an archive is covered in {@link WatchboxesEndpointIT}.
 */
@ComponentWebIT(classes = WatchboxWebTestApplication.class)
@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
class WatchboxArchivedSourceAdmissionIT extends AbstractWebIT {

  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447001");
  private static final UUID SOURCE_A = UUID.fromString("550e8400-e29b-41d4-a716-446655447002");
  private static final UUID SOURCE_B = UUID.fromString("550e8400-e29b-41d4-a716-446655447003");
  private static final UUID MAPPER_B = UUID.fromString("550e8400-e29b-41d4-a716-446655447008");
  private static final Authentication OWNER_AUTH = TestAuthentications.oidcUser(OWNER_ID);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private MapperProfileService mapperProfileService;
  @Autowired private IamLayerRepository layerRepository;
  @Autowired private IamLayerMembersRepository layerMembersRepository;
  @Autowired private IamServerLayerRepository serverLayerRepository;

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

    connectionFixtures.seedDefaultMapper(WatchboxTestMapperIdentity.MAPPER_ID);
    connectionFixtures.seedMapper(MAPPER_B, "admission-mapper-b", "INGRESS", false);
    connectionFixtures.persistTcpSource(
        SOURCE_A, "admission-source-a", WatchboxTestMapperIdentity.MAPPER_ID, true, null);
    connectionFixtures.persistKafkaSource(SOURCE_B, "admission-source-b", MAPPER_B, true);

    layerId =
        layerFixtures.persistLayerWithMembers("Admission Layer", Map.of(OWNER_ID, LayerRole.OWNER));
    layerFixtures.addServerAccess(SOURCE_A, layerId, ServerRole.VIEWER);
    layerFixtures.addServerAccess(SOURCE_B, layerId, ServerRole.VIEWER);

    when(mapperProfileService.getLatestMapperProfileFull(WatchboxTestMapperIdentity.MAPPER_ID))
        .thenReturn(
            new MapperProfileFullMeta(
                MapperProfile.builder()
                    .withId(WatchboxTestMapperIdentity.MAPPER_ID)
                    .withName("stable-test-mapper")
                    .withEditable(false)
                    .withIntegrationId(WatchboxTestMapperIdentity.INTEGRATION_ID)
                    .withVersion(1)
                    .withArchived(false)
                    .build(),
                null,
                null));
    when(mapperProfileService.getLatestMapperProfileFull(MAPPER_B))
        .thenReturn(
            new MapperProfileFullMeta(
                MapperProfile.builder()
                    .withId(MAPPER_B)
                    .withName("user-mapper-without-rules")
                    .withEditable(true)
                    .withVersion(1)
                    .withArchived(false)
                    .build(),
                null,
                null));
  }

  @AfterEach
  void tearDown() {
    resetWatchboxes();
  }

  @Test
  void createAndEditSucceedWhenOneLayerSourceIsArchived() throws Exception {
    dataSourceRepository.archive(SOURCE_B, OWNER_ID);

    var created = createWatchbox("archived-sibling-proof");
    assertThat(created.enabled()).isTrue();

    var edited =
        patchExpectingOk(created.id(), "{\"description\":\"edited beside an archived source\"}");
    assertThat(edited.enabled()).isTrue();
  }

  @Test
  void createIsRejectedWithNoEligibleSourcesWhenEveryLayerSourceIsArchived() throws Exception {
    dataSourceRepository.archive(SOURCE_A, OWNER_ID);
    dataSourceRepository.archive(SOURCE_B, OWNER_ID);

    var result =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("all-archived-proof")))
            .andExpect(status().isConflict())
            .andReturn();

    var error = readResponse(result, WatchboxErrorResponse.class);
    assertThat(error.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_NO_ELIGIBLE_SOURCES);
    assertThat(error.fields()).containsExactly("layerIds");
    assertThat(error.reasons()).isEmpty();
    assertThat(countAllAuditRows()).isZero();
  }

  @Test
  void stoppedSourceWithUnusableMapperStillBlocksOnItsMapper() throws Exception {
    dataSourceRepository.setEnabled(SOURCE_B, false, null);

    var result =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("stopped-unusable-mapper-proof")))
            .andExpect(status().isConflict())
            .andReturn();

    var error = readResponse(result, WatchboxErrorResponse.class);
    assertThat(error.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_SOURCE_ELIGIBILITY_FAILED);
    assertThat(error.fields()).containsExactly("layerIds");
    assertThat(error.context()).containsEntry("sourceNames", List.of("admission-source-b"));
    assertThat(countAllAuditRows()).isZero();
  }

  @Test
  void createAggregatesDifferentMapperFailuresWithSortedSourceNames() throws Exception {
    when(mapperProfileService.getLatestMapperProfileFull(WatchboxTestMapperIdentity.MAPPER_ID))
        .thenReturn(
            new MapperProfileFullMeta(
                MapperProfile.builder()
                    .withId(WatchboxTestMapperIdentity.MAPPER_ID)
                    .withName("archived-stable-test-mapper")
                    .withEditable(false)
                    .withIntegrationId(WatchboxTestMapperIdentity.INTEGRATION_ID)
                    .withVersion(1)
                    .withArchived(true)
                    .build(),
                null,
                null));

    var result =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("mixed-mapper-failure-proof")))
            .andExpect(status().isConflict())
            .andReturn();

    var error = readResponse(result, WatchboxErrorResponse.class);
    assertThat(error.code()).isEqualTo(WatchboxErrorCode.WATCHBOX_SOURCE_ELIGIBILITY_FAILED);
    assertThat(error)
        .extracting(WatchboxErrorResponse::message)
        .isEqualTo("Selected sources require object ID mapping correction");
    assertThat(error.fields()).containsExactly("layerIds");
    assertThat(error.reasons()).isEmpty();
    assertThat(error.context())
        .containsEntry("sourceNames", List.of("admission-source-a", "admission-source-b"));
    assertThat(countAllAuditRows()).isZero();
  }

  @Test
  void stoppedSourceWithStableMapperIsAdmittedBesideArchivedSource() throws Exception {
    dataSourceRepository.setEnabled(SOURCE_A, false, null);
    dataSourceRepository.archive(SOURCE_B, OWNER_ID);

    var created = createWatchbox("stopped-stable-mapper-proof");
    assertThat(created.enabled()).isTrue();
  }

  private WatchboxResponse createWatchbox(String name) throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest(name)))
            .andExpect(status().isCreated())
            .andReturn();
    return readResponse(createResult, WatchboxResponse.class);
  }

  private WatchboxResponse patchExpectingOk(UUID watchboxId, String body) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", watchboxId)
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(result, WatchboxResponse.class);
  }

  private void resetWatchboxes() {
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_detection_heartbeat");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_config_audit");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_source_status");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_layer");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox");
  }

  private void seedFreshDetectionHeartbeat() {
    jdbcTemplate.update(
        """
        INSERT INTO watchbox.watchbox_detection_heartbeat
          (instance_id, listener_started_at, last_heartbeat_at, heartbeat_expires_at,
           assigned_partitions, heartbeat_sequence)
        VALUES ('processor-it', now(), now(), now() + INTERVAL '60 seconds', 2, 1)
        ON CONFLICT (instance_id) DO UPDATE SET
          heartbeat_expires_at = now() + INTERVAL '60 seconds',
          stopped_at = NULL,
          retained_retry_partitions = NULL,
          retained_retry_reason = NULL,
          retained_retry_since = NULL,
          retained_retry_attempts = NULL
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

  private int countAllAuditRows() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM watchbox.watchbox_config_audit", Integer.class);
  }

  private String validCreateRequest(String name) {
    return """
        {
          "name":"%s",
          "description":"admission proof",
          "direction":"BOTH",
          "layerIds":["%s"],
          "polygon":{"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2],[-100.2,40.2],[-100.2,39.8]]]},
          "filter":{"combine":"ALL","predicates":[{"path":"properties.opencop.element.symbol","operation":"IN","values":["10061000001211000000"]}]},
          "enabled":true
        }
        """
        .formatted(name, layerId);
  }
}
