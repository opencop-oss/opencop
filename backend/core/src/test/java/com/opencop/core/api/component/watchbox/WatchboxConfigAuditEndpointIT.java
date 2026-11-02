// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static com.opencop.core.api.component.watchbox.WatchboxTestMapperIdentity.MAPPER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.component.support.fixture.ConnectionFixtures;
import com.opencop.core.api.component.support.fixture.LayerFixtures;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.watchbox.business.model.WatchboxConfigAuditSnapshot;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigAuditRecorder;
import com.opencop.core.api.watchbox.technical.endpoint.WatchboxConfigAuditResponse;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.model.watchbox.WatchboxResetReason;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.IllegalTransactionStateException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

@ComponentWebIT(classes = WatchboxWebTestApplication.class)
@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
class WatchboxConfigAuditEndpointIT extends AbstractWebIT {

  private static final UUID ADMIN_ID = UUID.fromString("650e8400-e29b-41d4-a716-446655441001");
  private static final UUID VIEWER_ID = UUID.fromString("650e8400-e29b-41d4-a716-446655441002");
  private static final UUID SOURCE_ID = UUID.fromString("650e8400-e29b-41d4-a716-446655441003");

  private static final String ACTOR_FULL_NAME = "Watchbox Audit Administrator";
  private static final String ACTOR_EMAIL = "watchbox.audit.admin@example.test";
  private static final Authentication ADMIN_AUTH = adminAuthentication();
  private static final Authentication VIEWER_AUTH = authenticationFor(VIEWER_ID, false, null, null);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private MapperProfileService mapperProfileService;
  @Autowired private IamLayerRepository layerRepository;
  @Autowired private IamLayerMembersRepository layerMembersRepository;
  @Autowired private WatchboxConfigAuditRecorder auditRecorder;

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
    layerFixtures.reset();
    connectionFixtures.reset();
    insertUser(ADMIN_ID, true, ACTOR_FULL_NAME, ACTOR_EMAIL);
    insertUser(VIEWER_ID, false, "Audit Viewer", "watchbox.audit.viewer@example.test");
    connectionFixtures.seedDefaultMapper(MAPPER_ID);
    connectionFixtures.persistTcpSource(SOURCE_ID, "audit-test-source", MAPPER_ID, true, null);
    layerId =
        layerFixtures.persistLayerWithMembers(
            "Watchbox Audit Layer", Map.of(ADMIN_ID, LayerRole.OWNER));
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

  @AfterEach
  void tearDown() {
    resetWatchboxes();
  }

  @Test
  void patch_whenMeaningful_persistsCompleteEntryAndRestrictsReadToGlobalAdmin() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest()))
            .andExpect(status().isCreated())
            .andReturn();
    var created = readResponse(createResult, WatchboxResponse.class);

    var patchResult =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "polygon":{"type":"Polygon","coordinates":[[[-100.1,39.9],[-99.9,39.9],[-99.9,40.1],[-100.1,40.1],[-100.1,39.9]]]},
                          "confirmTrackingReset":true
                        }
                        """))
            .andExpect(status().isOk())
            .andReturn();
    var patched = readResponse(patchResult, WatchboxResponse.class);

    assertThat(patched.configEpoch()).isEqualTo(2);
    assertThat(countAuditRows(created.id())).isEqualTo(2);
    var rawRow = loadRawAuditRow(created.id(), "PATCH");
    assertRawPostgresRow(rawRow, created, patched);

    var historyResult =
        mockMvc
            .perform(
                get("/v1/admin/watchboxes/config-history")
                    .queryParam("watchboxId", created.id().toString())
                    .queryParam("operation", "PATCH")
                    .queryParam("page", "0")
                    .queryParam("size", "10")
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var history =
        readResponse(
            historyResult, new TypeReference<PageResponse<WatchboxConfigAuditResponse>>() {});

    assertThat(history.page()).isZero();
    assertThat(history.size()).isEqualTo(10);
    assertThat(history.totalElements()).isEqualTo(1);
    assertThat(history.totalPages()).isEqualTo(1);
    assertThat(history.content()).hasSize(1);
    assertHistoryEntry(history.content().getFirst(), rawRow, created, patched);

    mockMvc
        .perform(
            get("/v1/admin/watchboxes/config-history")
                .queryParam("watchboxId", created.id().toString())
                .with(authentication(VIEWER_AUTH)))
        .andExpect(status().isForbidden());
  }

  @Test
  void lifecycle_whenNoOpPatchAndArchiveOccur_persistsExactShapes() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest()))
            .andExpect(status().isCreated())
            .andReturn();
    var created = readResponse(createResult, WatchboxResponse.class);

    var patchResult =
        mockMvc
            .perform(
                patch("/v1/watchboxes/{watchboxId}", created.id())
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {"name":"  audit-history-proof  "}
                    """))
            .andExpect(status().isOk())
            .andReturn();
    var patched = readResponse(patchResult, WatchboxResponse.class);
    assertThat(patched.createdAt()).isEqualTo(created.createdAt());
    assertThat(patched.updatedAt()).isAfter(created.updatedAt());

    mockMvc
        .perform(
            delete("/v1/watchboxes/{watchboxId}", created.id())
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/v1/watchboxes/{watchboxId}", created.id()).with(authentication(ADMIN_AUTH)))
        .andExpect(status().isNotFound());

    var historyResult =
        mockMvc
            .perform(
                get("/v1/admin/watchboxes/config-history")
                    .queryParam("watchboxId", created.id().toString())
                    .queryParam("page", "0")
                    .queryParam("size", "10")
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var history =
        readResponse(
            historyResult, new TypeReference<PageResponse<WatchboxConfigAuditResponse>>() {});

    assertThat(history.totalElements()).isEqualTo(3);
    assertThat(history.content())
        .extracting(entry -> entry.operation().name())
        .containsExactly("ARCHIVE", "PATCH", "CREATE");
    var archiveEntry = history.content().get(0);
    var patchEntry = history.content().get(1);
    var createEntry = history.content().get(2);

    assertThat(createEntry.beforeSnapshot()).isNull();
    assertSnapshotMatches(createEntry.afterSnapshot(), created);
    assertThat(createEntry.configEpochBefore()).isNull();
    assertThat(createEntry.configEpochAfter()).isEqualTo(1);
    assertThat(createEntry.resetReasons()).isEmpty();

    assertSnapshotMatches(patchEntry.beforeSnapshot(), created);
    assertSnapshotMatches(patchEntry.afterSnapshot(), patched);
    assertLogicalConfigEqual(patchEntry.beforeSnapshot(), patchEntry.afterSnapshot());
    assertThat(patchEntry.configEpochBefore()).isEqualTo(1);
    assertThat(patchEntry.configEpochAfter()).isEqualTo(1);
    assertThat(patchEntry.resetReasons()).isEmpty();

    assertSnapshotMatches(archiveEntry.beforeSnapshot(), patched);
    assertThat(archiveEntry.afterSnapshot()).isNull();
    assertThat(archiveEntry.configEpochBefore()).isEqualTo(1);
    assertThat(archiveEntry.configEpochAfter()).isNull();
    assertThat(archiveEntry.resetReasons()).isEmpty();

    history.content().forEach(this::assertEventTimeActor);
  }

  @Test
  void actor_whenFullNameIsAbsent_doesNotUsePreferredUsername() throws Exception {
    var noFullNameAdmin = authenticationFor(ADMIN_ID, true, null, ACTOR_EMAIL);
    var createResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(noFullNameAdmin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequest()))
            .andExpect(status().isCreated())
            .andReturn();
    var created = readResponse(createResult, WatchboxResponse.class);

    var rawRow = loadRawAuditRow(created.id(), "CREATE");
    assertThat(rawRow.actorFullName()).isNull();
    assertThat(rawRow.actorEmail()).isEqualTo(ACTOR_EMAIL);

    var historyResult =
        mockMvc
            .perform(
                get("/v1/admin/watchboxes/config-history")
                    .queryParam("watchboxId", created.id().toString())
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var history =
        readResponse(
            historyResult, new TypeReference<PageResponse<WatchboxConfigAuditResponse>>() {});

    assertThat(history.content()).hasSize(1);
    assertThat(history.content().getFirst().actor().fullName()).isNull();
    assertThat(history.content().getFirst().actor().email()).isEqualTo(ACTOR_EMAIL);
  }

  @Test
  void create_whenDescriptionIsOmitted_persistsHydratableNullDescriptionSnapshot()
      throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createRequestWithoutDescription()))
            .andExpect(status().isCreated())
            .andReturn();
    var created = readResponse(createResult, WatchboxResponse.class);

    assertThat(created.description()).isNull();
    var rawRow = loadRawAuditRow(created.id(), "CREATE");
    var snapshot =
        objectMapper.readValue(rawRow.afterSnapshot(), WatchboxConfigAuditSnapshot.class);
    assertThat(snapshot.description()).isNull();
    assertSnapshotMatches(snapshot, created);
  }

  @Test
  void recorder_whenCalledOutsideTransaction_enforcesMandatoryPropagationThroughSpringProxy() {
    assertThatThrownBy(() -> auditRecorder.recordCreate(null, null))
        .isInstanceOf(IllegalTransactionStateException.class)
        .hasMessageContaining("No existing transaction");
  }

  @Test
  void openApi_whenHistorySchemaIsGenerated_usesUniqueCompleteActorComponent() throws Exception {
    var result =
        mockMvc
            .perform(get("/v3/api-docs").with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var apiDocs = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    assertThat(
            apiDocs
                .path("paths")
                .path("/v1/admin/watchboxes/config-history")
                .path("get")
                .path("tags")
                .path(0)
                .asText())
        .isEqualTo("Watchboxes");
    var actorSchema =
        apiDocs
            .path("components")
            .path("schemas")
            .path("WatchboxConfigAuditActorResponse")
            .path("properties");
    assertThat(actorSchema.path("userId").path("format").asText()).isEqualTo("uuid");
    assertThat(actorSchema.path("fullName").path("type").asText()).isEqualTo("string");
    assertThat(actorSchema.path("email").path("type").asText()).isEqualTo("string");
    assertThat(actorSchema.path("globalAdmin").path("type").asText()).isEqualTo("boolean");
  }

  private void assertRawPostgresRow(
      RawAuditRow row, WatchboxResponse created, WatchboxResponse patched) throws Exception {
    assertThat(row.id()).isPositive();
    assertThat(row.watchboxId()).isEqualTo(created.id());
    assertThat(row.operation()).isEqualTo("PATCH");
    assertThat(row.configEpochBefore()).isEqualTo(1);
    assertThat(row.configEpochAfter()).isEqualTo(2);
    assertThat(row.occurredAt()).isNotNull();
    assertThat(row.actorUserId()).isEqualTo(ADMIN_ID);
    assertThat(row.actorFullName()).isEqualTo(ACTOR_FULL_NAME);
    assertThat(row.actorEmail()).isEqualTo(ACTOR_EMAIL);
    assertThat(row.actorGlobalAdmin()).isTrue();

    var before = objectMapper.readValue(row.beforeSnapshot(), WatchboxConfigAuditSnapshot.class);
    var after = objectMapper.readValue(row.afterSnapshot(), WatchboxConfigAuditSnapshot.class);
    assertSnapshotMatches(before, created);
    assertSnapshotMatches(after, patched);
    assertThat(
            Arrays.asList(objectMapper.readValue(row.resetReasons(), WatchboxResetReason[].class)))
        .containsExactly(WatchboxResetReason.POLYGON_CHANGED);
  }

  private void assertHistoryEntry(
      WatchboxConfigAuditResponse entry,
      RawAuditRow rawRow,
      WatchboxResponse created,
      WatchboxResponse patched) {
    assertThat(entry.id()).isEqualTo(rawRow.id());
    assertThat(entry.watchboxId()).isEqualTo(created.id());
    assertThat(entry.operation().name()).isEqualTo("PATCH");
    assertSnapshotMatches(entry.beforeSnapshot(), created);
    assertSnapshotMatches(entry.afterSnapshot(), patched);
    assertThat(entry.resetReasons()).containsExactly(WatchboxResetReason.POLYGON_CHANGED);
    assertThat(entry.configEpochBefore()).isEqualTo(1);
    assertThat(entry.configEpochAfter()).isEqualTo(2);
    assertThat(entry.occurredAt()).isEqualTo(rawRow.occurredAt());
    assertThat(entry.actor().userId()).isEqualTo(ADMIN_ID);
    assertThat(entry.actor().fullName()).isEqualTo(ACTOR_FULL_NAME);
    assertThat(entry.actor().email()).isEqualTo(ACTOR_EMAIL);
    assertThat(entry.actor().globalAdmin()).isTrue();
  }

  private void assertLogicalConfigEqual(
      WatchboxConfigAuditSnapshot before, WatchboxConfigAuditSnapshot after) {
    assertThat(after.id()).isEqualTo(before.id());
    assertThat(after.name()).isEqualTo(before.name());
    assertThat(after.description()).isEqualTo(before.description());
    assertThat(after.enabled()).isEqualTo(before.enabled());
    assertThat(after.direction()).isEqualTo(before.direction());
    JsonNode afterPolygon = objectMapper.valueToTree(after.polygon());
    JsonNode beforePolygon = objectMapper.valueToTree(before.polygon());
    assertThat(afterPolygon).isEqualTo(beforePolygon);
    assertThat(after.filter()).isEqualTo(before.filter());
    assertThat(after.layerIds()).containsExactlyElementsOf(before.layerIds());
    assertThat(after.configEpoch()).isEqualTo(before.configEpoch());
  }

  private void assertEventTimeActor(WatchboxConfigAuditResponse entry) {
    assertThat(entry.actor().userId()).isEqualTo(ADMIN_ID);
    assertThat(entry.actor().fullName()).isEqualTo(ACTOR_FULL_NAME);
    assertThat(entry.actor().email()).isEqualTo(ACTOR_EMAIL);
    assertThat(entry.actor().globalAdmin()).isTrue();
  }

  private void assertSnapshotMatches(
      WatchboxConfigAuditSnapshot snapshot, WatchboxResponse response) {
    assertThat(snapshot.schemaVersion())
        .isEqualTo(WatchboxConfigAuditSnapshot.CURRENT_SCHEMA_VERSION);
    assertThat(snapshot.id()).isEqualTo(response.id());
    assertThat(snapshot.name()).isEqualTo(response.name());
    assertThat(snapshot.description()).isEqualTo(response.description());
    assertThat(snapshot.enabled()).isEqualTo(response.enabled());
    assertThat(snapshot.direction()).isEqualTo(response.direction());
    JsonNode snapshotPolygon = objectMapper.valueToTree(snapshot.polygon());
    JsonNode responsePolygon = objectMapper.valueToTree(response.polygon());
    assertThat(snapshotPolygon).isEqualTo(responsePolygon);
    assertThat(snapshot.filter()).isEqualTo(response.filter());
    assertThat(snapshot.layerIds()).containsExactlyInAnyOrderElementsOf(response.layerIds());
    assertThat(snapshot.configEpoch()).isEqualTo(response.configEpoch());
    assertThat(snapshot.createdAt()).isEqualTo(response.createdAt());
    assertThat(snapshot.updatedAt()).isEqualTo(response.updatedAt());
  }

  private RawAuditRow loadRawAuditRow(UUID watchboxId, String operation) {
    return jdbcTemplate.queryForObject(
        """
        SELECT id,
               watchbox_id,
               operation,
               before_snapshot::text AS before_snapshot,
               after_snapshot::text AS after_snapshot,
               reset_reasons::text AS reset_reasons,
               config_epoch_before,
               config_epoch_after,
               occurred_at,
               actor_user_id,
               actor_full_name,
               actor_email,
               actor_global_admin
          FROM watchbox.watchbox_config_audit
         WHERE watchbox_id = ? AND operation = ?
        """,
        (resultSet, _) ->
            new RawAuditRow(
                resultSet.getLong("id"),
                resultSet.getObject("watchbox_id", UUID.class),
                resultSet.getString("operation"),
                resultSet.getString("before_snapshot"),
                resultSet.getString("after_snapshot"),
                resultSet.getString("reset_reasons"),
                resultSet.getObject("config_epoch_before", Long.class),
                resultSet.getObject("config_epoch_after", Long.class),
                resultSet.getTimestamp("occurred_at").toInstant(),
                resultSet.getObject("actor_user_id", UUID.class),
                resultSet.getString("actor_full_name"),
                resultSet.getString("actor_email"),
                resultSet.getBoolean("actor_global_admin")),
        watchboxId,
        operation);
  }

  private int countAuditRows(UUID watchboxId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM watchbox.watchbox_config_audit WHERE watchbox_id = ?",
        Integer.class,
        watchboxId);
  }

  private void resetWatchboxes() {
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_config_audit");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_detection_heartbeat");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_source_status");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_layer");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox");
  }

  private void insertUser(UUID userId, boolean globalAdmin, String displayName, String email) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
            id, idp_user_id, idp_username, idp_issuer, username, display_name, email,
            active, administrator, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, true, ?, now(), now())
        ON CONFLICT (id) DO UPDATE SET
            display_name = EXCLUDED.display_name,
            email = EXCLUDED.email,
            administrator = EXCLUDED.administrator,
            active = true,
            deleted_at = NULL,
            updated_at = now()
        """,
        userId,
        "idp-user-" + userId,
        "user-" + userId,
        "http://localhost/test-issuer",
        "user-" + userId,
        displayName,
        email,
        globalAdmin);
  }

  private String createRequest() {
    return """
        {
          "name":"audit-history-proof",
          "description":"Assembled audit history proof",
          "direction":"BOTH",
          "layerIds":["%s"],
          "polygon":{"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2],[-100.2,40.2],[-100.2,39.8]]]},
          "filter":{"combine":"ALL","predicates":[{"path":"properties.opencop.element.symbol","operation":"IN","values":["10061000001211000000"]}]},
          "enabled":true
        }
        """
        .formatted(layerId);
  }

  private String createRequestWithoutDescription() {
    return """
        {
          "name":"audit-history-null-description",
          "direction":"BOTH",
          "layerIds":["%s"],
          "polygon":{"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2],[-100.2,40.2],[-100.2,39.8]]]},
          "filter":{"combine":"ALL","predicates":[]},
          "enabled":false
        }
        """
        .formatted(layerId);
  }

  private static Authentication adminAuthentication() {
    return authenticationFor(ADMIN_ID, true, ACTOR_FULL_NAME, ACTOR_EMAIL);
  }

  private static Authentication authenticationFor(
      UUID userId, boolean globalAdmin, String displayName, String email) {
    var principal =
        new OpenCopPrincipal(
                userId,
                "http://localhost/test-issuer",
                "idp-user-" + userId,
                "subject-" + userId,
                new OpenCopPrincipal.Profile(
                    "user-" + userId, displayName, null, null, email, email != null),
                0,
                Instant.parse("2026-08-19T12:00:00Z"))
            .withRequestCurrentGlobalAdministrator(globalAdmin);
    return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
  }

  private record RawAuditRow(
      long id,
      UUID watchboxId,
      String operation,
      String beforeSnapshot,
      String afterSnapshot,
      String resetReasons,
      Long configEpochBefore,
      Long configEpochAfter,
      Instant occurredAt,
      UUID actorUserId,
      String actorFullName,
      String actorEmail,
      boolean actorGlobalAdmin) {}
}
