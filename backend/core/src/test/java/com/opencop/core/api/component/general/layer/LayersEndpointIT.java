// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.ConnectionFixtures;
import com.opencop.core.api.component.support.fixture.LayerFixtures;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.technical.gateway.notification.NotificationWorker;
import com.opencop.core.api.general.technical.model.CloneLayerRequest;
import com.opencop.core.api.general.technical.model.LayerCreateWithUsersRequest;
import com.opencop.core.api.general.technical.model.MergeLayerRequest;
import com.opencop.core.api.general.technical.model.MergeLayerResponse;
import com.opencop.core.api.general.technical.model.MergeLayerValidationRequest;
import com.opencop.core.api.general.technical.model.MergeLayerValidationResponse;
import com.opencop.core.api.general.technical.model.group.GroupLayerResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayerResponse;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerExceptionCodes;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.model.iam.layer.LayerCreateRequest;
import com.opencop.core.sdk.model.iam.layer.LayerPatchRequest;
import com.opencop.core.sdk.model.iam.layer.UpdateLayerRoleRequest;
import com.opencop.core.sdk.model.job.LayerMergeJobSubmission;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationReader;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupLayerMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupLayerMemberRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.geojson.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = LayerWebTestApplication.class)
class LayersEndpointIT extends AbstractWebIT {

  private static final String OPERATIONS = "Operations";
  private static final String OPERATIONS_UPDATED = "Operations Updated";
  private static final String ALPHA = "Alpha";
  private static final String BRAVO = "Bravo";
  private static final String CONNECT_INSTANCE = "connect-1";
  private static final String ADMIN_RENAME = "Admin rename";
  private static final Instant FIXED_INSTANT = Instant.parse("2000-01-01T00:00:00Z");
  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440101");
  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440102");
  private static final UUID MEMBER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440103");
  private static final UUID EDITOR_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440104");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440105");
  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440108");

  private static final Authentication OWNER_AUTH = TestAuthentications.oidcUser(OWNER_ID);
  private static final Authentication ADMIN_LAYER_AUTH = TestAuthentications.oidcUser(ADMIN_ID);
  private static final Authentication MEMBER_AUTH = TestAuthentications.oidcUser(MEMBER_ID);
  private static final Authentication GLOBAL_ADMIN_AUTH = globalAdminAuth();

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private IamLayerRepository layerRepository;
  @Autowired private IamLayerMembersRepository layerMembersRepository;
  @Autowired private IamServerLayerRepository serverLayerRepository;
  @Autowired private IamGroupRepository groupRepository;
  @Autowired private IamGroupLayerMemberRepository groupLayerMemberRepository;
  @Autowired private JobPublisher jobPublisher;

  @MockitoBean private NotificationWorker notificationWorker;
  @MockitoBean private UserService userService;
  @MockitoBean private ConnectInstanceResolver connectInstanceResolver;
  @MockitoBean private SourceRuntimeObservationReader sourceRuntimeObservationReader;

  private ConnectionFixtures connectionFixtures;
  private LayerFixtures layerFixtures;

  @BeforeEach
  void setUp() throws Exception {
    connectionFixtures = new ConnectionFixtures(jdbcTemplate, dataSourceRepository, objectMapper);
    layerFixtures =
        new LayerFixtures(
            jdbcTemplate, layerRepository, layerMembersRepository, serverLayerRepository);

    jdbcTemplate.update("DELETE FROM processor.layer_merge_operation");
    jdbcTemplate.update("DELETE FROM iam.group_layer_member");
    jdbcTemplate.update("DELETE FROM iam.user_group");
    // Cascades to iam.layer_group_layer. LayerFixtures.reset() doesn't reach either table, so
    // without this the layer-group assignment tests leak groups into each other.
    jdbcTemplate.update("DELETE FROM iam.layer_group");
    layerFixtures.reset();
    connectionFixtures.reset();
    recordingJobPublisher().clear();
    connectionFixtures.seedDefaultMapper(MAPPER_ID);
    insertUser(OWNER_ID);
    insertUser(ADMIN_ID);
    insertUser(MEMBER_ID);

    when(userService.get(any(UUID.class)))
        .thenAnswer(invocation -> user(invocation.getArgument(0)));
    when(sourceRuntimeObservationReader.findFreshForInstance(any(UUID.class), anyString()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    new RuntimeStatus(
                        invocation.getArgument(0),
                        ConnectorState.CONNECTED,
                        FIXED_INSTANT,
                        CONNECT_INSTANCE)));
    when(connectInstanceResolver.getAllInstanceIds()).thenReturn(List.of(CONNECT_INSTANCE));
    when(connectInstanceResolver.getRoutableInstances(any()))
        .thenReturn(
            List.of(new ConnectInstanceInfo(CONNECT_INSTANCE, Instant.now(), 0, "reg-1", 1)));
  }

  // Happy path
  @Test
  void createLayer_persistsOwnerAndInitialMembers() throws Exception {
    var create = new LayerCreateWithUsersRequest(OPERATIONS, Map.of(ADMIN_ID, LayerRole.ADMIN));

    var createResult =
        mockMvc
            .perform(
                post("/v1/layers")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isCreated())
            .andReturn();

    var createdLayer = readResponse(createResult, Layer.class);
    assertNotNull(createdLayer.getId());
    assertEquals(OPERATIONS, createdLayer.getName());
    assertNotNull(createdLayer.getCreatedAt());

    var membersResult =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}/members", createdLayer.getId())
                    .with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var members = readResponse(membersResult, new TypeReference<List<LayerMember>>() {});
    assertEquals(2, members.size());
    assertEquals(LayerRole.OWNER, layerFixtures.loadMemberRole(createdLayer.getId(), OWNER_ID));
    assertEquals(LayerRole.ADMIN, layerFixtures.loadMemberRole(createdLayer.getId(), ADMIN_ID));
    verify(notificationWorker, atLeastOnce()).sendAllAsync(any());
  }

  @Test
  void cloneLayer_createsNewLayerForRequesterAndCopiesFeatures() throws Exception {
    var sourceLayerId =
        layerFixtures.persistLayerWithMembers(ALPHA, Map.of(OWNER_ID, LayerRole.OWNER));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440401"),
        "clone-object-1",
        sourceLayerId,
        Instant.parse("2026-03-31T12:00:00Z"));

    var request =
        new CloneLayerRequest(
            OPERATIONS_UPDATED,
            Map.of(ADMIN_ID, LayerRole.ADMIN),
            LayerAccessScope.PRIVATE,
            false,
            false);

    var result =
        mockMvc
            .perform(
                post("/v1/layers/{sourceLayerId}/clone", sourceLayerId)
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isCreated())
            .andReturn();

    var createdLayer = readResponse(result, Layer.class);
    assertNotNull(createdLayer.getId());
    assertEquals(OPERATIONS_UPDATED, createdLayer.getName());
    assertEquals(LayerRole.OWNER, layerFixtures.loadMemberRole(createdLayer.getId(), OWNER_ID));
    assertEquals(LayerRole.ADMIN, layerFixtures.loadMemberRole(createdLayer.getId(), ADMIN_ID));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM geo.features WHERE source = ?",
            Integer.class,
            createdLayer.getId()));
    assertEquals(
        createdLayer.getId(),
        jdbcTemplate.queryForObject(
            "SELECT source FROM geo.features WHERE source = ? LIMIT 1",
            UUID.class,
            createdLayer.getId()));
    verify(notificationWorker, atLeastOnce()).sendAllAsync(any());
  }

  @Test
  void cloneLayer_clonesRemarksAndMediaForMultipleFeatures() throws Exception {
    var sourceLayerId =
        layerFixtures.persistLayerWithMembers(ALPHA, Map.of(OWNER_ID, LayerRole.OWNER));
    var objectA = "fanout-object-a";
    var objectB = "fanout-object-b";
    var objectC = "fanout-object-c";
    var featureAId = UUID.fromString("550e8400-e29b-41d4-a716-446655440411");
    var featureBId = UUID.fromString("550e8400-e29b-41d4-a716-446655440412");
    var featureCId = UUID.fromString("550e8400-e29b-41d4-a716-446655440413");
    var mediaA1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440414");
    var mediaA2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440415");
    var mediaB1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440416");
    var layerMedia1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440417");
    var layerMedia2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440418");

    insertUser(OWNER_ID);
    insertFeature(featureAId, objectA, sourceLayerId, Instant.parse("2026-03-31T12:10:00Z"));
    insertFeature(featureBId, objectB, sourceLayerId, Instant.parse("2026-03-31T12:11:00Z"));
    insertFeature(featureCId, objectC, sourceLayerId, Instant.parse("2026-03-31T12:12:00Z"));

    insertRemark(UUID.randomUUID(), sourceLayerId, objectA, OWNER_ID, "remark-a-1");
    insertRemark(UUID.randomUUID(), sourceLayerId, objectA, OWNER_ID, "remark-a-2");
    insertRemark(UUID.randomUUID(), sourceLayerId, objectB, OWNER_ID, "remark-b-1");
    insertRemark(UUID.randomUUID(), sourceLayerId, objectC, OWNER_ID, "remark-c-1");

    insertMedia(mediaA1, OWNER_ID, "blob/event-a-1", "event-a-1.png", null, "EVENT_DETAIL");
    insertMedia(mediaA2, OWNER_ID, "blob/event-a-2", "event-a-2.png", null, "EVENT_DETAIL");
    insertMedia(mediaB1, OWNER_ID, "blob/event-b-1", "event-b-1.png", null, "EVENT_DETAIL");
    insertMedia(layerMedia1, OWNER_ID, "blob/layer-1", "layer-1.png", null, "LAYER_ICON");
    insertMedia(layerMedia2, OWNER_ID, "blob/layer-2", "layer-2.png", null, "LAYER_ICON");

    insertEventMedia(sourceLayerId, objectA, mediaA1);
    insertEventMedia(sourceLayerId, objectA, mediaA2);
    insertEventMedia(sourceLayerId, objectB, mediaB1);
    insertLayerMedia(sourceLayerId, layerMedia1);
    insertLayerMedia(sourceLayerId, layerMedia2);

    var result =
        mockMvc
            .perform(
                post("/v1/layers/{sourceLayerId}/clone", sourceLayerId)
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new CloneLayerRequest("Fanout clone", Map.of(), null, true, true))))
            .andExpect(status().isCreated())
            .andReturn();

    var createdLayer = readResponse(result, Layer.class);

    assertEquals(
        3,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM geo.features WHERE source = ?",
            Integer.class,
            createdLayer.getId()));

    var remarkCounts =
        jdbcTemplate.queryForList(
            """
            SELECT object_id, COUNT(*) AS remark_count
            FROM geo.remarks
            WHERE source_id = ?
            GROUP BY object_id
            ORDER BY object_id
            """,
            createdLayer.getId());
    assertEquals(3, remarkCounts.size());
    assertEquals(objectA, remarkCounts.get(0).get("object_id"));
    assertEquals(2L, ((Number) remarkCounts.get(0).get("remark_count")).longValue());
    assertEquals(objectB, remarkCounts.get(1).get("object_id"));
    assertEquals(1L, ((Number) remarkCounts.get(1).get("remark_count")).longValue());
    assertEquals(objectC, remarkCounts.get(2).get("object_id"));
    assertEquals(1L, ((Number) remarkCounts.get(2).get("remark_count")).longValue());

    var targetEventMedia =
        jdbcTemplate.queryForList(
            """
            SELECT ea.object_id, ea.attachment_id AS media_id, mf.blob_key
            FROM event_attachment ea
            JOIN media_file mf ON mf.id = ea.attachment_id
            WHERE ea.source_id = ?
            ORDER BY ea.object_id, mf.blob_key
            """,
            createdLayer.getId());
    assertEquals(3, targetEventMedia.size());
    assertEquals(
        Set.of("blob/event-a-1", "blob/event-a-2", "blob/event-b-1"),
        targetEventMedia.stream()
            .map(row -> (String) row.get("blob_key"))
            .collect(Collectors.toSet()));
    assertTrue(
        targetEventMedia.stream()
            .map(row -> (UUID) row.get("media_id"))
            .noneMatch(Set.of(mediaA1, mediaA2, mediaB1)::contains));

    var targetLayerMedia =
        jdbcTemplate.queryForList(
            """
            SELECT lm.media_id, mf.blob_key
            FROM layer_media lm
            JOIN media_file mf ON mf.id = lm.media_id
            WHERE lm.layer_id = ?
            ORDER BY mf.blob_key
            """,
            createdLayer.getId());
    assertEquals(2, targetLayerMedia.size());
    assertEquals(
        Set.of("blob/layer-1", "blob/layer-2"),
        targetLayerMedia.stream()
            .map(row -> (String) row.get("blob_key"))
            .collect(Collectors.toSet()));
    assertTrue(
        targetLayerMedia.stream()
            .map(row -> (UUID) row.get("media_id"))
            .noneMatch(Set.of(layerMedia1, layerMedia2)::contains));
  }

  @Test
  void cloneLayer_whenRequesterIsOnlyMember_returnsForbidden() throws Exception {
    var sourceLayerId =
        layerFixtures.persistLayerWithMembers(
            ALPHA,
            Map.of(
                OWNER_ID, LayerRole.OWNER, MEMBER_ID, LayerRole.MEMBER, ADMIN_ID, LayerRole.ADMIN));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440402"),
        "clone-object-2",
        sourceLayerId,
        Instant.parse("2026-03-31T12:01:00Z"));

    mockMvc
        .perform(
            post("/v1/layers/{sourceLayerId}/clone", sourceLayerId)
                .with(csrf())
                .with(authentication(MEMBER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new CloneLayerRequest(
                            "Member clone", Map.of(), LayerAccessScope.PRIVATE, false, false))))
        .andExpect(status().isForbidden());
  }

  @Test
  void cloneLayer_whenGlobalAdminHasNoDirectMembership_canClone() throws Exception {
    var sourceLayerId =
        layerFixtures.persistLayerWithMembers(ALPHA, Map.of(OWNER_ID, LayerRole.OWNER));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440403"),
        "clone-object-3",
        sourceLayerId,
        Instant.parse("2026-03-31T12:02:00Z"));

    var result =
        mockMvc
            .perform(
                post("/v1/layers/{sourceLayerId}/clone", sourceLayerId)
                    .with(csrf())
                    .with(authentication(GLOBAL_ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new CloneLayerRequest(
                                "Admin clone", Map.of(), LayerAccessScope.PRIVATE, false, false))))
            .andExpect(status().isCreated())
            .andReturn();

    var createdLayer = readResponse(result, Layer.class);
    assertEquals(LayerRole.OWNER, layerFixtures.loadMemberRole(createdLayer.getId(), ADMIN_ID));
    assertEquals(LayerAccessScope.PRIVATE, createdLayer.getAccessScope());
  }

  @Test
  void cloneLayer_whenSourceHasNoFeatures_rollsBackCreatedLayer() throws Exception {
    var sourceLayerId =
        layerFixtures.persistLayerWithMembers(ALPHA, Map.of(OWNER_ID, LayerRole.OWNER));

    mockMvc
        .perform(
            post("/v1/layers/{sourceLayerId}/clone", sourceLayerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new CloneLayerRequest(
                            "Rollback clone", Map.of(), LayerAccessScope.PRIVATE, false, false))))
        .andExpect(status().isBadRequest());

    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.layer WHERE name = ?", Integer.class, "Rollback clone"));
  }

  @Test
  void cloneLayer_whenRequestTargetsGlobal_returnsBadRequest() throws Exception {
    var sourceLayerId =
        layerFixtures.persistLayerWithMembers(ALPHA, Map.of(OWNER_ID, LayerRole.OWNER));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440404"),
        "clone-object-4",
        sourceLayerId,
        Instant.parse("2026-03-31T12:03:00Z"));

    mockMvc
        .perform(
            post("/v1/layers/{sourceLayerId}/clone", sourceLayerId)
                .with(csrf())
                .with(authentication(GLOBAL_ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new CloneLayerRequest(
                            "Global clone", Map.of(), LayerAccessScope.GLOBAL, false, false))))
        .andExpect(status().isBadRequest());

    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.layer WHERE name = ?", Integer.class, "Global clone"));
  }

  @Test
  void validateMergeLayers_whenRequesterOwnsNonEmptyLayers_returnsAdvisoryCollisionSummary()
      throws Exception {
    var firstLayerId =
        layerFixtures.persistLayerWithMembers(
            "First validate merge source", Map.of(OWNER_ID, LayerRole.OWNER));
    var secondLayerId =
        layerFixtures.persistLayerWithMembers(
            "Second validate merge source", Map.of(OWNER_ID, LayerRole.OWNER));
    var thirdLayerId =
        layerFixtures.persistLayerWithMembers(
            "Third validate merge source", Map.of(OWNER_ID, LayerRole.OWNER));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440501"),
        "shared-object",
        firstLayerId,
        Instant.parse("2026-04-01T12:00:00Z"));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440502"),
        "first-only",
        firstLayerId,
        Instant.parse("2026-04-01T12:01:00Z"));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440503"),
        "shared-object",
        secondLayerId,
        Instant.parse("2026-04-01T12:02:00Z"));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440504"),
        "shared-object",
        thirdLayerId,
        Instant.parse("2026-04-01T12:03:00Z"));

    var result =
        mockMvc
            .perform(
                post("/v1/layers/merge/validate")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new MergeLayerValidationRequest(
                                List.of(firstLayerId, secondLayerId, thirdLayerId)))))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, MergeLayerValidationResponse.class);
    assertEquals(List.of(firstLayerId, secondLayerId, thirdLayerId), response.layerIds());
    assertEquals(2L, response.activeFeatureCounts().get(firstLayerId));
    assertEquals(1L, response.activeFeatureCounts().get(secondLayerId));
    assertEquals(1L, response.activeFeatureCounts().get(thirdLayerId));
    assertEquals(3L, response.collisionSummary().totalCollisions());
    assertEquals(3, response.collisionSummary().pairwiseCounts().size());
    assertEquals(1L, response.collisionSummary().pairwiseCounts().get(0).count());
    assertEquals(1L, response.collisionSummary().pairwiseCounts().get(1).count());
    assertEquals(1L, response.collisionSummary().pairwiseCounts().get(2).count());
    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(firstLayerId));
    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(secondLayerId));
    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(thirdLayerId));
    assertTrue(recordingJobPublisher().submissions().isEmpty());
  }

  @Test
  void
      mergeLayers_whenRequesterOwnsNonEmptyLayers_createsPendingTargetAndOperationallyLocksSources()
          throws Exception {
    var firstLayerId =
        layerFixtures.persistLayerWithMembers(
            "First merge source", Map.of(OWNER_ID, LayerRole.OWNER));
    var secondLayerId =
        layerFixtures.persistLayerWithMembers(
            "Second merge source", Map.of(OWNER_ID, LayerRole.OWNER));
    var thirdLayerId =
        layerFixtures.persistLayerWithMembers(
            "Third merge source", Map.of(OWNER_ID, LayerRole.OWNER));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440511"),
        "shared-object",
        firstLayerId,
        Instant.parse("2026-04-01T12:00:00Z"));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440512"),
        "first-only",
        firstLayerId,
        Instant.parse("2026-04-01T12:01:00Z"));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440513"),
        "shared-object",
        secondLayerId,
        Instant.parse("2026-04-01T12:02:00Z"));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440514"),
        "shared-object",
        thirdLayerId,
        Instant.parse("2026-04-01T12:03:00Z"));

    var result =
        mockMvc
            .perform(
                post("/v1/layers/merge")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new MergeLayerRequest(
                                "Merged endpoint layer",
                                List.of(firstLayerId, secondLayerId, thirdLayerId)))))
            .andExpect(status().isCreated())
            .andReturn();

    var response = readResponse(result, MergeLayerResponse.class);
    assertNotNull(response.targetLayer().getId());
    assertEquals("Merged endpoint layer", response.targetLayer().getName());
    assertEquals(LayerLifecycleState.PENDING_MERGE, response.targetLayer().getLifecycleState());
    assertEquals(LayerAccessScope.PRIVATE, response.targetLayer().getAccessScope());
    assertEquals(List.of(firstLayerId, secondLayerId, thirdLayerId), response.sourceLayerIds());
    assertEquals(
        LayerRole.OWNER, layerFixtures.loadMemberRole(response.targetLayer().getId(), OWNER_ID));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.layer_member WHERE layer_id = ?",
            Integer.class,
            response.targetLayer().getId()));
    assertEquals(LayerLifecycleState.MERGE_SOURCE_LOCKED, loadLifecycleState(firstLayerId));
    assertEquals(LayerLifecycleState.MERGE_SOURCE_LOCKED, loadLifecycleState(secondLayerId));
    assertEquals(LayerLifecycleState.MERGE_SOURCE_LOCKED, loadLifecycleState(thirdLayerId));
    var mergeSubmission =
        recordingJobPublisher().submissions().stream()
            .filter(LayerMergeJobSubmission.class::isInstance)
            .map(LayerMergeJobSubmission.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(response.targetLayer().getId(), mergeSubmission.targetLayerId());
    assertEquals(
        List.of(firstLayerId, secondLayerId, thirdLayerId), mergeSubmission.sourceLayerIds());
    assertEquals(
        mergeSubmission.jobId(),
        jdbcTemplate.queryForObject(
            "SELECT job_id FROM processor.layer_merge_operation WHERE target_layer_id = ?",
            UUID.class,
            response.targetLayer().getId()));
    assertEquals(
        "{%s,%s,%s}".formatted(firstLayerId, secondLayerId, thirdLayerId),
        jdbcTemplate.queryForObject(
            "SELECT source_layer_ids::text FROM processor.layer_merge_operation WHERE target_layer_id = ?",
            String.class,
            response.targetLayer().getId()));
    assertEquals(
        "PENDING",
        jdbcTemplate.queryForObject(
            "SELECT phase::text FROM processor.layer_merge_operation WHERE target_layer_id = ?",
            String.class,
            response.targetLayer().getId()));
  }

  @Test
  void mergeLayers_whenPublishFails_marksOperationFailedHidesTargetAndAllowsRetry()
      throws Exception {
    var firstLayerId =
        layerFixtures.persistLayerWithMembers(
            "First publish failure source", Map.of(OWNER_ID, LayerRole.OWNER));
    var secondLayerId =
        layerFixtures.persistLayerWithMembers(
            "Second publish failure source", Map.of(OWNER_ID, LayerRole.OWNER));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440521"),
        "first-publish-failure-object",
        firstLayerId,
        Instant.parse("2026-04-01T12:10:00Z"));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440522"),
        "second-publish-failure-object",
        secondLayerId,
        Instant.parse("2026-04-01T12:11:00Z"));

    var result =
        mockMvc
            .perform(
                post("/v1/layers/merge")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new MergeLayerRequest(
                                "Retryable failed endpoint merge",
                                List.of(firstLayerId, secondLayerId)))))
            .andExpect(status().isCreated())
            .andReturn();

    var failedResponse = readResponse(result, MergeLayerResponse.class);
    var failedTargetId = failedResponse.targetLayer().getId();

    recordingJobPublisher().failLastPublish(new IllegalStateException("simulated publish failure"));

    assertEquals(LayerLifecycleState.MERGE_FAILED, loadLifecycleState(failedTargetId));
    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(firstLayerId));
    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(secondLayerId));
    assertEquals(
        "FAILED",
        jdbcTemplate.queryForObject(
            "SELECT phase::text FROM processor.layer_merge_operation WHERE target_layer_id = ?",
            String.class,
            failedTargetId));
    assertTrue(
        jdbcTemplate
            .queryForObject(
                "SELECT failure_reason FROM processor.layer_merge_operation WHERE target_layer_id = ?",
                String.class,
                failedTargetId)
            .startsWith("PUBLISH_FAILED: IllegalStateException"));

    var listResult =
        mockMvc
            .perform(get("/v1/admin/layers").with(authentication(GLOBAL_ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var layers = readResponse(listResult, new TypeReference<List<Layer>>() {});
    assertTrue(layers.stream().noneMatch(layer -> failedTargetId.equals(layer.getId())));

    var retryResult =
        mockMvc
            .perform(
                post("/v1/layers/merge")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new MergeLayerRequest(
                                "Retryable failed endpoint merge",
                                List.of(firstLayerId, secondLayerId)))))
            .andExpect(status().isCreated())
            .andReturn();

    var retryResponse = readResponse(retryResult, MergeLayerResponse.class);
    assertNotNull(retryResponse.targetLayer().getId());
    assertFalse(failedTargetId.equals(retryResponse.targetLayer().getId()));
    assertEquals(
        LayerLifecycleState.PENDING_MERGE, loadLifecycleState(retryResponse.targetLayer().getId()));
    assertEquals(
        "PENDING",
        jdbcTemplate.queryForObject(
            "SELECT phase::text FROM processor.layer_merge_operation WHERE target_layer_id = ?",
            String.class,
            retryResponse.targetLayer().getId()));
  }

  @Test
  void mergeLayers_whenRequesterHasOnlyMemberRole_returnsForbiddenWithoutMutatingState()
      throws Exception {
    var ownedLayerId =
        layerFixtures.persistLayerWithMembers(
            "Owned merge source", Map.of(OWNER_ID, LayerRole.OWNER));
    var memberLayerId =
        layerFixtures.persistLayerWithMembers(
            "Member merge source", Map.of(OWNER_ID, LayerRole.MEMBER));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440505"),
        "owned-object",
        ownedLayerId,
        Instant.parse("2026-04-01T12:04:00Z"));
    insertFeature(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440506"),
        "admin-object",
        memberLayerId,
        Instant.parse("2026-04-01T12:05:00Z"));

    mockMvc
        .perform(
            post("/v1/layers/merge")
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new MergeLayerRequest(
                            "Rejected endpoint merge", List.of(ownedLayerId, memberLayerId)))))
        .andExpect(status().isForbidden());

    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.layer WHERE name = ?",
            Integer.class,
            "Rejected endpoint merge"));
    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(ownedLayerId));
    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(memberLayerId));
    assertTrue(recordingJobPublisher().submissions().isEmpty());
  }

  @Test
  void validateMergeLayers_whenRequestHasMoreThanTenLayers_returnsBadRequest() throws Exception {
    var tooManyLayerIds =
        java.util.stream.IntStream.range(0, 11).mapToObj(_ -> UUID.randomUUID()).toList();

    mockMvc
        .perform(
            post("/v1/layers/merge/validate")
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new MergeLayerValidationRequest(tooManyLayerIds))))
        .andExpect(status().isBadRequest());

    assertTrue(recordingJobPublisher().submissions().isEmpty());
  }

  @Test
  void mergeLayers_whenRequestHasMoreThanTenLayers_returnsBadRequestWithoutCreatingTarget()
      throws Exception {
    var tooManyLayerIds =
        java.util.stream.IntStream.range(0, 11).mapToObj(_ -> UUID.randomUUID()).toList();

    mockMvc
        .perform(
            post("/v1/layers/merge")
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new MergeLayerRequest("Too many merge sources", tooManyLayerIds))))
        .andExpect(status().isBadRequest());

    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.layer WHERE name = ?",
            Integer.class,
            "Too many merge sources"));
    assertTrue(recordingJobPublisher().submissions().isEmpty());
  }

  @Test
  void lockedMergeSourceLayerOperationalWritesReturnConflict() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            "Locked endpoint source", Map.of(OWNER_ID, LayerRole.OWNER));
    lockLayerForMerge(layerId);

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerPatchRequest("Should not apply", null, null))))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            post("/v1/layers/{sourceLayerId}/clone", layerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new CloneLayerRequest(
                            "Rejected locked clone", Map.of(), null, false, false))))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            delete("/v1/layers/{layerId}", layerId).with(csrf()).with(authentication(OWNER_AUTH)))
        .andExpect(status().isConflict());

    assertEquals(LayerLifecycleState.MERGE_SOURCE_LOCKED, loadLifecycleState(layerId));
    assertEquals(
        "Locked endpoint source",
        jdbcTemplate.queryForObject(
            "SELECT name FROM iam.layer WHERE id = ?", String.class, layerId));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.layer WHERE name = ?",
            Integer.class,
            "Rejected locked clone"));
  }

  @Test
  void failedMergeUnlocksSourcesForWritesAndHidesTargetFromActiveLists() throws Exception {
    var sourceLayerId =
        layerFixtures.persistLayerWithMembers(
            "Unlocked failed source", Map.of(OWNER_ID, LayerRole.OWNER));
    var failedTargetId =
        layerFixtures.persistLayerWithMembers(
            "Failed inactive target", Map.of(OWNER_ID, LayerRole.OWNER));
    setLayerLifecycle(sourceLayerId, LayerLifecycleState.ACTIVE);
    setLayerLifecycle(failedTargetId, LayerLifecycleState.MERGE_FAILED);

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", sourceLayerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerPatchRequest("Unlocked source renamed", null, null))))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(get("/v1/admin/layers").with(authentication(GLOBAL_ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var layers = readResponse(result, new TypeReference<List<Layer>>() {});

    assertEquals(LayerLifecycleState.ACTIVE, loadLifecycleState(sourceLayerId));
    assertEquals(LayerLifecycleState.MERGE_FAILED, loadLifecycleState(failedTargetId));
    assertTrue(layers.stream().anyMatch(layer -> sourceLayerId.equals(layer.getId())));
    assertTrue(layers.stream().noneMatch(layer -> failedTargetId.equals(layer.getId())));
  }

  @Test
  void getAndListLayers_readPersistedLayers() throws Exception {
    var alphaId = layerFixtures.persistLayerWithMembers(ALPHA, Map.of(OWNER_ID, LayerRole.OWNER));
    layerFixtures.persistLayerWithMembers(BRAVO, Map.of(OWNER_ID, LayerRole.OWNER));

    var getResult =
        mockMvc
            .perform(get("/v1/layers/{layerId}", alphaId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var layer = readResponse(getResult, Layer.class);
    assertEquals(alphaId, layer.getId());
    assertEquals(ALPHA, layer.getName());

    var listResult =
        mockMvc
            .perform(get("/v1/admin/layers").with(authentication(GLOBAL_ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var layers = readResponse(listResult, new TypeReference<List<Layer>>() {});
    assertEquals(2, layers.size());
  }

  @Test
  void getLayer_whenPrivateLayerAndUserHasNoEffectiveAccess_returnsForbidden() throws Exception {
    var layerId = layerFixtures.persistLayerWithMembers(ALPHA, Map.of(OWNER_ID, LayerRole.OWNER));

    mockMvc
        .perform(get("/v1/layers/{layerId}", layerId).with(authentication(MEMBER_AUTH)))
        .andExpect(status().isForbidden());
  }

  @Test
  void getLayers_whenRequesterIsNotAdmin_returnsForbidden() throws Exception {
    layerFixtures.persistLayerWithMembers(ALPHA, Map.of(OWNER_ID, LayerRole.OWNER));

    mockMvc
        .perform(get("/v1/admin/layers").with(authentication(OWNER_AUTH)))
        .andExpect(status().isForbidden());
  }

  @Test
  void getLayer_whenGlobalLayerAndUserHasNoDirectMembership_returnsLayer() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            ALPHA, LayerAccessScope.GLOBAL, Map.of(OWNER_ID, LayerRole.OWNER));

    var result =
        mockMvc
            .perform(get("/v1/layers/{layerId}", layerId).with(authentication(MEMBER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var layer = readResponse(result, Layer.class);
    assertEquals(layerId, layer.getId());
    assertEquals(LayerAccessScope.GLOBAL, layer.getAccessScope());
  }

  @Test
  void ownerCanAddMembersAndReadThemBack() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));

    mockMvc
        .perform(
            post("/v1/layers/{layerId}/members", layerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        Map.of(MEMBER_ID, LayerRole.MEMBER, ADMIN_ID, LayerRole.ADMIN))))
        .andExpect(status().isNoContent());

    var membersResult =
        mockMvc
            .perform(get("/v1/layers/{layerId}/members", layerId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var members = readResponse(membersResult, new TypeReference<List<LayerMember>>() {});

    assertEquals(3, members.size());
    assertEquals(LayerRole.ADMIN, layerFixtures.loadMemberRole(layerId, ADMIN_ID));
    assertEquals(LayerRole.MEMBER, layerFixtures.loadMemberRole(layerId, MEMBER_ID));
    verify(notificationWorker, atLeastOnce()).sendAllAsync(any());
  }

  @Test
  void adminCanAddMembersToLayer() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER, ADMIN_ID, LayerRole.ADMIN));

    mockMvc
        .perform(
            post("/v1/layers/{layerId}/members", layerId)
                .with(csrf())
                .with(authentication(ADMIN_LAYER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(MEMBER_ID, LayerRole.MEMBER))))
        .andExpect(status().isNoContent());

    assertEquals(LayerRole.MEMBER, layerFixtures.loadMemberRole(layerId, MEMBER_ID));
  }

  @Test
  void adminCannotAddOwnerToLayer() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER, ADMIN_ID, LayerRole.ADMIN));

    mockMvc
        .perform(
            post("/v1/layers/{layerId}/members", layerId)
                .with(csrf())
                .with(authentication(ADMIN_LAYER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(MEMBER_ID, LayerRole.OWNER))))
        .andExpect(status().isForbidden());
  }

  @Test
  void ownerCanAddOwnerToLayer() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));

    mockMvc
        .perform(
            post("/v1/layers/{layerId}/members", layerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(MEMBER_ID, LayerRole.OWNER))))
        .andExpect(status().isNoContent());

    assertEquals(LayerRole.OWNER, layerFixtures.loadMemberRole(layerId, MEMBER_ID));
  }

  @Test
  void layerAuditAttribution_survivesCreateAndPatchThroughTheResponse() throws Exception {
    // End to end rather than at the repository: the audit pair has to survive the entity mapping,
    // the presenter and JSON serialization, any of which can silently drop a field.
    var createResult =
        mockMvc
            .perform(
                post("/v1/layers")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            new LayerCreateWithUsersRequest(
                                OPERATIONS, Map.of(ADMIN_ID, LayerRole.OWNER)))))
            .andExpect(status().isCreated())
            .andReturn();

    var created = readResponse(createResult, Layer.class);
    assertEquals(OWNER_ID, created.getCreatedBy(), "the creator is the authenticated caller");
    assertNull(created.getUpdatedAt(), "a create is not an edit");
    assertNull(created.getUpdatedBy());

    // A different user edits it, so a creator echoed into updatedBy would be visible.
    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", created.getId())
                .with(csrf())
                .with(authentication(ADMIN_LAYER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerPatchRequest(OPERATIONS_UPDATED, null, null))))
        .andExpect(status().isNoContent());

    var readResult =
        mockMvc
            .perform(get("/v1/layers/{layerId}", created.getId()).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var patched = readResponse(readResult, Layer.class);
    assertEquals(OPERATIONS_UPDATED, patched.getName());
    assertEquals(OWNER_ID, patched.getCreatedBy(), "an edit must not rewrite the creator");
    assertEquals(ADMIN_ID, patched.getUpdatedBy(), "the editor is whoever patched");
    assertNotNull(patched.getUpdatedAt(), "a real edit records when it happened");
  }

  @Test
  void ownerCanRenameLayer() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerPatchRequest(OPERATIONS_UPDATED, null, null))))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(get("/v1/layers/{layerId}", layerId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var layer = readResponse(result, Layer.class);
    assertEquals(OPERATIONS_UPDATED, layer.getName());
  }

  @Test
  void globalAdminCanChangeLayerAccessScope() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(GLOBAL_ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerPatchRequest(OPERATIONS_UPDATED, LayerAccessScope.GLOBAL, null))))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(get("/v1/layers/{layerId}", layerId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var layer = readResponse(result, Layer.class);
    assertEquals(OPERATIONS_UPDATED, layer.getName());
    assertEquals(LayerAccessScope.GLOBAL, layer.getAccessScope());
  }

  @Test
  void globalAdminCannotDowngradeLayerAssignedToGlobalLayerGroup() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS, LayerAccessScope.GLOBAL, Map.of(OWNER_ID, LayerRole.OWNER));
    var groupId = insertLayerGroup("GlobalGroupContainingLayer", "GLOBAL");
    insertLayerGroupLayer(groupId, layerId);

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(GLOBAL_ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerPatchRequest(null, LayerAccessScope.PRIVATE, null))))
        .andExpect(status().isConflict());

    var scope =
        jdbcTemplate.queryForObject(
            "SELECT access_scope::text FROM iam.layer WHERE id = ?", String.class, layerId);
    assertEquals("GLOBAL", scope, "layer access_scope must remain GLOBAL after rejected downgrade");
  }

  @Test
  void renameLayer_whenNameAlreadyExists_returnsConflict() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));
    layerFixtures.persistLayer(BRAVO);

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new LayerCreateRequest(BRAVO))))
        .andExpect(status().isConflict());

    var result =
        mockMvc
            .perform(get("/v1/layers/{layerId}", layerId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(OPERATIONS, readResponse(result, Layer.class).getName());
  }

  @Test
  void adminCanUpdateMemberRolesWithoutChangingOwners() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS,
            Map.of(
                OWNER_ID, LayerRole.OWNER, ADMIN_ID, LayerRole.ADMIN, MEMBER_ID, LayerRole.MEMBER));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}/members", layerId)
                .with(csrf())
                .with(authentication(ADMIN_LAYER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new UpdateLayerRoleRequest(
                            Set.of(), Set.of(), Set.of(MEMBER_ID), Set.of()))))
        .andExpect(status().isNoContent());

    assertEquals(LayerRole.ADMIN, layerFixtures.loadMemberRole(layerId, MEMBER_ID));
    verify(notificationWorker, atLeastOnce()).sendAllAsync(any());
  }

  @Test
  void adminCanPromoteMemberToEditor() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS,
            Map.of(
                OWNER_ID, LayerRole.OWNER, ADMIN_ID, LayerRole.ADMIN, MEMBER_ID, LayerRole.MEMBER));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}/members", layerId)
                .with(csrf())
                .with(authentication(ADMIN_LAYER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new UpdateLayerRoleRequest(
                            Set.of(), Set.of(MEMBER_ID), Set.of(), Set.of()))))
        .andExpect(status().isNoContent());

    assertEquals(LayerRole.EDITOR, layerFixtures.loadMemberRole(layerId, MEMBER_ID));
    verify(notificationWorker, atLeastOnce()).sendAllAsync(any());
  }

  @Test
  void ownerCanPromoteMemberToOwner() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER, MEMBER_ID, LayerRole.MEMBER));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}/members", layerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new UpdateLayerRoleRequest(
                            Set.of(), Set.of(), Set.of(), Set.of(MEMBER_ID)))))
        .andExpect(status().isNoContent());

    assertEquals(LayerRole.OWNER, layerFixtures.loadMemberRole(layerId, MEMBER_ID));
  }

  @Test
  void getLayerSources_enrichesStoredServerAccessWithRuntimeData() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));
    connectionFixtures.persistTcpSource(SOURCE_ID, ALPHA, MAPPER_ID, true, CONNECT_INSTANCE);
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.VIEWER);

    var result =
        mockMvc
            .perform(get("/v1/layers/{layerId}/sources", layerId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var sources = readResponse(result, new TypeReference<Set<SourceLayer>>() {});
    assertEquals(1, sources.size());
    var source = sources.iterator().next();
    assertEquals(SOURCE_ID, source.getServerId());
    assertEquals(ALPHA, source.getServerName());
    assertEquals(ConnectorState.CONNECTED.name(), source.getServerState());
    assertEquals(ServerRole.VIEWER, source.getRole());
  }

  @Test
  void getLayerSources_whenRuntimeIsMissing_keepsStoredAccessWithoutEnrichment() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.VIEWER);

    var result =
        mockMvc
            .perform(get("/v1/layers/{layerId}/sources", layerId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var sources = readResponse(result, new TypeReference<Set<SourceLayer>>() {});
    assertEquals(1, sources.size());
    var source = sources.iterator().next();
    assertEquals(SOURCE_ID, source.getServerId());
    assertNull(source.getServerName());
    assertNull(source.getServerState());
    assertEquals(ServerRole.VIEWER, source.getRole());
  }

  // Forbidden mutation
  @Test
  void memberCannotRenameLayer() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER, MEMBER_ID, LayerRole.MEMBER));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(MEMBER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(new LayerCreateRequest("Forbidden rename"))))
        .andExpect(status().isForbidden());

    var result =
        mockMvc
            .perform(get("/v1/layers/{layerId}", layerId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(OPERATIONS, readResponse(result, Layer.class).getName());
  }

  @Test
  void ownerCannotChangeLayerAccessScope() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerPatchRequest(OPERATIONS_UPDATED, LayerAccessScope.GLOBAL, null))))
        .andExpect(status().isForbidden());

    var result =
        mockMvc
            .perform(get("/v1/layers/{layerId}", layerId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var layer = readResponse(result, Layer.class);
    assertEquals(OPERATIONS, layer.getName());
    assertEquals(LayerAccessScope.PRIVATE, layer.getAccessScope());
  }

  @Test
  void ownerCanPatchDefaultVisible_whenAccessScopeUnchanged() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerPatchRequest(null, LayerAccessScope.PRIVATE, false))))
        .andExpect(status().isNoContent());

    var dbValue =
        jdbcTemplate.queryForObject(
            "SELECT default_visible FROM iam.layer WHERE id = ?", Boolean.class, layerId);
    assertEquals(Boolean.FALSE, dbValue);
  }

  @Test
  void patchLayer_defaultVisibleFalse_persistsAndReadsBack() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new LayerPatchRequest(null, null, false))))
        .andExpect(status().isNoContent());

    var dbValue =
        jdbcTemplate.queryForObject(
            "SELECT default_visible FROM iam.layer WHERE id = ?", Boolean.class, layerId);
    assertEquals(Boolean.FALSE, dbValue);

    var getResult =
        mockMvc
            .perform(get("/v1/layers/{layerId}", layerId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertFalse(readResponse(getResult, Layer.class).isDefaultVisible());
  }

  @Test
  void patchLayer_nameOnlyPatch_preservesExistingDefaultVisible() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));
    jdbcTemplate.update("UPDATE iam.layer SET default_visible = false WHERE id = ?", layerId);

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerPatchRequest(OPERATIONS_UPDATED, null, null))))
        .andExpect(status().isNoContent());

    var dbValue =
        jdbcTemplate.queryForObject(
            "SELECT default_visible FROM iam.layer WHERE id = ?", Boolean.class, layerId);
    assertEquals(Boolean.FALSE, dbValue, "name-only patch must not reset default_visible");
  }

  @Test
  void adminCannotChangeOwnerList() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS,
            Map.of(
                OWNER_ID, LayerRole.OWNER, ADMIN_ID, LayerRole.ADMIN, MEMBER_ID, LayerRole.MEMBER));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}/members", layerId)
                .with(csrf())
                .with(authentication(ADMIN_LAYER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new UpdateLayerRoleRequest(
                            Set.of(), Set.of(), Set.of(), Set.of(MEMBER_ID)))))
        .andExpect(status().isForbidden());

    assertEquals(LayerRole.MEMBER, layerFixtures.loadMemberRole(layerId, MEMBER_ID));
  }

  @Test
  void adminCannotDemoteOwnerToEditor() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS,
            Map.of(
                OWNER_ID, LayerRole.OWNER, ADMIN_ID, LayerRole.ADMIN, EDITOR_ID, LayerRole.EDITOR));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}/members", layerId)
                .with(csrf())
                .with(authentication(ADMIN_LAYER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new UpdateLayerRoleRequest(
                            Set.of(), Set.of(OWNER_ID), Set.of(), Set.of()))))
        .andExpect(status().isForbidden());

    assertEquals(LayerRole.OWNER, layerFixtures.loadMemberRole(layerId, OWNER_ID));
    assertEquals(LayerRole.EDITOR, layerFixtures.loadMemberRole(layerId, EDITOR_ID));
  }

  @Test
  void memberCannotDeleteLayer() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER, MEMBER_ID, LayerRole.MEMBER));

    mockMvc
        .perform(
            delete("/v1/layers/{layerId}", layerId).with(csrf()).with(authentication(MEMBER_AUTH)))
        .andExpect(status().isForbidden());

    assertTrue(layerFixtures.layerExists(layerId));
  }

  @Test
  void globalAdminCanRenameLayerWithoutMembership() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(GLOBAL_ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new LayerCreateRequest(ADMIN_RENAME))))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(get("/v1/layers/{layerId}", layerId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(ADMIN_RENAME, readResponse(result, Layer.class).getName());
  }

  // Validation failures
  @Test
  void deletingLastOwnerFromLayer_returnsBadRequest() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));

    var result =
        mockMvc
            .perform(
                delete("/v1/layers/{layerId}/members", layerId)
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(Set.of(OWNER_ID))))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(LayerExceptionCodes.LAYER_REQUIRES_OWNER.code(), error.code());
    assertEquals(LayerRole.OWNER, layerFixtures.loadMemberRole(layerId, OWNER_ID));
    verifyNoInteractions(notificationWorker);
  }

  @Test
  void createLayer_whenNameAlreadyExists_returnsConflict() throws Exception {
    layerFixtures.persistLayer(OPERATIONS);

    mockMvc
        .perform(
            post("/v1/layers")
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerCreateWithUsersRequest(OPERATIONS, Map.of()))))
        .andExpect(status().isConflict());
  }

  // Missing resource
  @Test
  void getLayer_whenMissing_returnsNotFound() throws Exception {
    mockMvc
        .perform(get("/v1/layers/{layerId}", UUID.randomUUID()).with(authentication(OWNER_AUTH)))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteLayer_archivesLayerAndPreservesMembersAndServerAccess() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER, MEMBER_ID, LayerRole.MEMBER));
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.VIEWER);

    var memberCountBefore =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.layer_member WHERE layer_id = ?", Integer.class, layerId);
    var serverAccessCountBefore =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.server_layer_access WHERE layer_id = ?",
            Integer.class,
            layerId);

    mockMvc
        .perform(
            delete("/v1/layers/{layerId}", layerId).with(csrf()).with(authentication(OWNER_AUTH)))
        .andExpect(status().isNoContent());

    var archivedAt =
        jdbcTemplate.queryForObject(
            "SELECT archived_at FROM iam.layer WHERE id = ?", Timestamp.class, layerId);
    assertNotNull(archivedAt);
    assertEquals(
        memberCountBefore,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.layer_member WHERE layer_id = ?", Integer.class, layerId));
    assertEquals(
        serverAccessCountBefore,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.server_layer_access WHERE layer_id = ?",
            Integer.class,
            layerId));

    mockMvc
        .perform(get("/v1/layers/{layerId}", layerId).with(authentication(OWNER_AUTH)))
        .andExpect(status().isOk());
    verify(notificationWorker, atLeastOnce()).sendAllAsync(any());
  }

  @Test
  void getLayer_whenArchived_returnsArchivedMetadataWithoutRecordVisibility() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));

    mockMvc
        .perform(
            delete("/v1/layers/{layerId}", layerId).with(csrf()).with(authentication(OWNER_AUTH)))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(get("/v1/layers/{layerId}", layerId).with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var layer = readResponse(result, Layer.class);
    assertEquals(layerId, layer.getId());
    assertNotNull(layer.getArchivedAt());
  }

  @Test
  void getLayer_whenRecordVisibilityIsInvalid_ignoresSingleGetQueryParamAndReturnsArchivedLayer()
      throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER, MEMBER_ID, LayerRole.MEMBER));
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.VIEWER);

    mockMvc
        .perform(
            delete("/v1/layers/{layerId}", layerId).with(csrf()).with(authentication(OWNER_AUTH)))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}", layerId)
                    .queryParam("recordVisibility", "PURGED")
                    .with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var layer = readResponse(result, Layer.class);
    assertEquals(layerId, layer.getId());
    assertNotNull(layer.getArchivedAt());
  }

  @Test
  void getLayer_whenRecordVisibilityIsActive_ignoresSingleGetQueryParamAndReturnsArchivedLayer()
      throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER, MEMBER_ID, LayerRole.MEMBER));
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.VIEWER);

    mockMvc
        .perform(
            delete("/v1/layers/{layerId}", layerId).with(csrf()).with(authentication(OWNER_AUTH)))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}", layerId)
                    .queryParam("recordVisibility", "ACTIVE")
                    .with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var layer = readResponse(result, Layer.class);
    assertEquals(layerId, layer.getId());
    assertNotNull(layer.getArchivedAt());
  }

  @Test
  void getLayer_whenRecordVisibilityIsAll_ignoresSingleGetQueryParamAndReturnsArchivedLayer()
      throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER, MEMBER_ID, LayerRole.MEMBER));
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.VIEWER);

    mockMvc
        .perform(
            delete("/v1/layers/{layerId}", layerId).with(csrf()).with(authentication(OWNER_AUTH)))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}", layerId)
                    .queryParam("recordVisibility", "ALL")
                    .with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var layer = readResponse(result, Layer.class);
    assertEquals(layerId, layer.getId());
    assertNotNull(layer.getArchivedAt());
  }

  @Test
  void getLayers_whenRecordVisibilityIsArchived_returnsArchivedLayers() throws Exception {
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));
    var activeLayerId = layerFixtures.persistLayer(BRAVO);

    mockMvc
        .perform(
            delete("/v1/layers/{layerId}", archivedLayerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH)))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(
                get("/v1/admin/layers")
                    .queryParam("recordVisibility", "ARCHIVED")
                    .with(authentication(GLOBAL_ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var layers = readResponse(result, new TypeReference<List<Layer>>() {});
    assertTrue(
        layers.stream().anyMatch(layer -> archivedLayerId.equals(layer.getId())),
        "expected archived layer id in ARCHIVED response");
    assertTrue(
        layers.stream()
            .filter(layer -> archivedLayerId.equals(layer.getId()))
            .allMatch(layer -> layer.getArchivedAt() != null),
        "expected archivedAt to be populated for archived layer");
    assertTrue(
        layers.stream().noneMatch(layer -> activeLayerId.equals(layer.getId())),
        "expected active layer id to be absent from ARCHIVED response");
  }

  @Test
  void getLayers_whenRecordVisibilityIsAll_returnsActiveAndArchivedLayers() throws Exception {
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));
    var activeLayerId = layerFixtures.persistLayer(BRAVO);

    mockMvc
        .perform(
            delete("/v1/layers/{layerId}", archivedLayerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH)))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(
                get("/v1/admin/layers")
                    .queryParam("recordVisibility", "ALL")
                    .with(authentication(GLOBAL_ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var layers = readResponse(result, new TypeReference<List<Layer>>() {});

    assertTrue(
        layers.stream().anyMatch(layer -> activeLayerId.equals(layer.getId())),
        "expected active layer id in ALL response");
    assertTrue(
        layers.stream().anyMatch(layer -> archivedLayerId.equals(layer.getId())),
        "expected archived layer id in ALL response");
    assertTrue(
        layers.stream()
            .filter(layer -> activeLayerId.equals(layer.getId()))
            .allMatch(layer -> layer.getArchivedAt() == null),
        "expected active layer archivedAt to be null");
    assertTrue(
        layers.stream()
            .filter(layer -> archivedLayerId.equals(layer.getId()))
            .allMatch(layer -> layer.getArchivedAt() != null),
        "expected archived layer archivedAt to be populated");
  }

  @Test
  void getLayers_whenRecordVisibilityIsInvalid_returnsBadRequestAndLeavesCountsUnchanged()
      throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER, MEMBER_ID, LayerRole.MEMBER));
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.VIEWER);

    var layerCountBefore =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.layer", Integer.class);
    var memberCountBefore =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.layer_member", Integer.class);
    var serverAccessCountBefore =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.server_layer_access", Integer.class);

    mockMvc
        .perform(
            get("/v1/admin/layers")
                .queryParam("recordVisibility", "PURGED")
                .with(authentication(GLOBAL_ADMIN_AUTH)))
        .andExpect(status().isBadRequest());

    assertEquals(
        layerCountBefore,
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.layer", Integer.class));
    assertEquals(
        memberCountBefore,
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.layer_member", Integer.class));
    assertEquals(
        serverAccessCountBefore,
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.server_layer_access", Integer.class));
  }

  @Test
  void getLayers_withRecordVisibilityAccessScopeAndName_returnsOnlyMatchingLayer()
      throws Exception {
    var archivedGlobalOps =
        layerFixtures.persistLayerWithMembers(
            "Ops", LayerAccessScope.GLOBAL, Map.of(OWNER_ID, LayerRole.OWNER));
    mockMvc
        .perform(
            delete("/v1/layers/{layerId}", archivedGlobalOps)
                .with(csrf())
                .with(authentication(OWNER_AUTH)))
        .andExpect(status().isNoContent());
    var activeGlobalOps =
        layerFixtures.persistLayerWithMembers(
            "Ops", LayerAccessScope.GLOBAL, Map.of(OWNER_ID, LayerRole.OWNER));
    layerFixtures.persistLayerWithMembers(
        "Ops Private", LayerAccessScope.PRIVATE, Map.of(OWNER_ID, LayerRole.OWNER));

    var result =
        mockMvc
            .perform(
                get("/v1/admin/layers")
                    .queryParam("recordVisibility", "ACTIVE")
                    .queryParam("accessScope", "GLOBAL")
                    .queryParam("name", "Ops")
                    .with(authentication(GLOBAL_ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<Layer>>() {});
    assertEquals(1, layers.size());
    assertEquals(activeGlobalOps, layers.get(0).getId());
    assertEquals("Ops", layers.get(0).getName());
    assertEquals(LayerAccessScope.GLOBAL, layers.get(0).getAccessScope());
    assertNull(layers.get(0).getArchivedAt());
  }

  @Test
  void getLayers_whenAccessScopeIsInvalid_returnsBadRequestAndLeavesCountsUnchanged()
      throws Exception {
    layerFixtures.persistLayerWithMembers(
        OPERATIONS, LayerAccessScope.GLOBAL, Map.of(OWNER_ID, LayerRole.OWNER));

    var layerCountBefore =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.layer", Integer.class);
    var memberCountBefore =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.layer_member", Integer.class);
    var serverAccessCountBefore =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.server_layer_access", Integer.class);

    mockMvc
        .perform(
            get("/v1/admin/layers")
                .queryParam("accessScope", "PURGED")
                .with(authentication(GLOBAL_ADMIN_AUTH)))
        .andExpect(status().isBadRequest());

    assertEquals(
        layerCountBefore,
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.layer", Integer.class));
    assertEquals(
        memberCountBefore,
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.layer_member", Integer.class));
    assertEquals(
        serverAccessCountBefore,
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.server_layer_access", Integer.class));
  }

  @Test
  void getLayers_whenNoQueryParams_returnsOnlyActiveLayers() throws Exception {
    var activeLayerId =
        layerFixtures.persistLayerWithMembers(
            "Ops Active", LayerAccessScope.GLOBAL, Map.of(OWNER_ID, LayerRole.OWNER));
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers(
            "Ops Archived", LayerAccessScope.GLOBAL, Map.of(OWNER_ID, LayerRole.OWNER));
    mockMvc
        .perform(
            delete("/v1/layers/{layerId}", archivedLayerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH)))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(get("/v1/admin/layers").with(authentication(GLOBAL_ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var layers = readResponse(result, new TypeReference<List<Layer>>() {});

    assertTrue(layers.stream().anyMatch(layer -> activeLayerId.equals(layer.getId())));
    assertTrue(layers.stream().noneMatch(layer -> archivedLayerId.equals(layer.getId())));
  }

  @Test
  void deleteMembers_whenLayerIsMissing_returnsForbidden() throws Exception {
    mockMvc
        .perform(
            delete("/v1/layers/{layerId}/members", UUID.randomUUID())
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Set.of(MEMBER_ID))))
        .andExpect(status().isForbidden());
  }

  // Auth
  @Test
  void createLayer_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/v1/layers")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerCreateWithUsersRequest(OPERATIONS, Map.of()))))
        .andExpect(status().isUnauthorized());
  }

  // ── GET /{layerId}/user-groups ────────────────────────────────────────────

  @Test
  void getGroupsForLayer_whenMemberAndNoGroupsAssigned_returnsEmptyList() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(MEMBER_ID, LayerRole.MEMBER));

    var result =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}/user-groups", layerId).with(authentication(MEMBER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var groups = readResponse(result, new TypeReference<List<GroupLayerResponse>>() {});
    assertTrue(groups.isEmpty());
  }

  @Test
  void getGroupsForLayer_whenGroupAssigned_memberSeesIt() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(MEMBER_ID, LayerRole.MEMBER));
    seedGroupOnLayer(layerId, LayerRole.MEMBER);

    var result =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}/user-groups", layerId).with(authentication(MEMBER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var groups = readResponse(result, new TypeReference<List<GroupLayerResponse>>() {});
    assertEquals(1, groups.size());
    assertEquals(layerId, groups.get(0).layerId());
  }

  @Test
  void getGroupsForLayer_whenNotMember_returnsForbidden() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));

    mockMvc
        .perform(get("/v1/layers/{layerId}/user-groups", layerId).with(authentication(MEMBER_AUTH)))
        .andExpect(status().isForbidden());
  }

  // ── GET /{layerId}/layer-groups ───────────────────────────────────────────

  @Test
  void getLayerGroupsForLayer_whenLayerInNoGroups_returnsEmptyList() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(MEMBER_ID, LayerRole.MEMBER));

    var result =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}/layer-groups", layerId).with(authentication(MEMBER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var groups = readResponse(result, new TypeReference<List<LayerGroupLayerResponse>>() {});
    assertTrue(groups.isEmpty(), "A layer in no layer groups should return an empty list, not 404");
  }

  @Test
  void getLayerGroupsForLayer_whenInMultipleGroups_returnsEachWithItsRole() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(MEMBER_ID, LayerRole.MEMBER));
    var alphaId = insertLayerGroup(ALPHA, "PRIVATE");
    var bravoId = insertLayerGroup(BRAVO, "PRIVATE");
    insertLayerGroupLayer(alphaId, layerId, LayerRole.EDITOR);
    insertLayerGroupLayer(bravoId, layerId, LayerRole.MEMBER);
    // Both groups are private, so the caller needs a role on each to see them at all — the response
    // is filtered to viewable groups. Visibility is covered separately by the omits/global tests.
    insertLayerGroupMember(alphaId, MEMBER_ID, "MEMBER");
    insertLayerGroupMember(bravoId, MEMBER_ID, "MEMBER");

    // A third group with a different layer — proves the query filters by layer, not by group.
    var otherLayerId = layerFixtures.persistLayer("Unrelated");
    var charlieId = insertLayerGroup("Charlie", "PRIVATE");
    insertLayerGroupMember(charlieId, MEMBER_ID, "MEMBER");
    insertLayerGroupLayer(charlieId, otherLayerId, LayerRole.MEMBER);

    var result =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}/layer-groups", layerId).with(authentication(MEMBER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var groups = readResponse(result, new TypeReference<List<LayerGroupLayerResponse>>() {});
    assertEquals(2, groups.size());
    var rolesByGroupName =
        groups.stream()
            .collect(
                Collectors.toMap(
                    LayerGroupLayerResponse::layerGroupName, LayerGroupLayerResponse::role));
    assertEquals(Map.of(ALPHA, LayerRole.EDITOR, BRAVO, LayerRole.MEMBER), rolesByGroupName);
    assertTrue(
        groups.stream().allMatch(group -> layerId.equals(group.layerId())),
        "Every row should be an assignment of the requested layer");
  }

  @Test
  void getLayerGroupsForLayer_whenGlobalAdminHasNoDirectMembership_returnsGroups()
      throws Exception {
    // The admin layer drawer is the primary consumer, and an admin browsing the admin layers page
    // is usually not a member of the layer they open. `assertLayerRole` short-circuits on
    // isGlobalAdmin(); without that, the drawer's Layer Groups section would 403 into an empty
    // list.
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));
    insertLayerGroupLayer(insertLayerGroup(ALPHA, "PRIVATE"), layerId, LayerRole.EDITOR);

    var result =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}/layer-groups", layerId)
                    .with(authentication(GLOBAL_ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var groups = readResponse(result, new TypeReference<List<LayerGroupLayerResponse>>() {});
    assertEquals(1, groups.size());
    assertEquals(ALPHA, groups.get(0).layerGroupName());
  }

  @Test
  void getLayerGroupsForLayer_omitsPrivateGroupsTheCallerHasNoRoleOn() throws Exception {
    // The leak this guards: the caller reaches the layer through group Alpha, but the layer is also
    // attached to private group Bravo, which they hold no role on. Layer access must not expose
    // Bravo's name, role, or assignment time — permissions-reference.md: no role means no view.
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(MEMBER_ID, LayerRole.MEMBER));
    var alphaId = insertLayerGroup(ALPHA, "PRIVATE");
    insertLayerGroupMember(alphaId, MEMBER_ID, "MEMBER");
    insertLayerGroupLayer(alphaId, layerId, LayerRole.EDITOR);

    var bravoId = insertLayerGroup(BRAVO, "PRIVATE");
    insertLayerGroupLayer(bravoId, layerId, LayerRole.MEMBER);

    var result =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}/layer-groups", layerId).with(authentication(MEMBER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var groups = readResponse(result, new TypeReference<List<LayerGroupLayerResponse>>() {});
    assertEquals(1, groups.size());
    assertEquals(ALPHA, groups.get(0).layerGroupName());
  }

  @Test
  void getLayerGroupsForLayer_includesGlobalGroupsWithoutAnExplicitRole() throws Exception {
    // GLOBAL groups grant MEMBER to everyone, so they stay visible without a direct assignment —
    // the filter must not over-restrict.
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(MEMBER_ID, LayerRole.MEMBER));
    insertLayerGroupLayer(insertLayerGroup(ALPHA, "GLOBAL"), layerId, LayerRole.MEMBER);

    var result =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}/layer-groups", layerId).with(authentication(MEMBER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var groups = readResponse(result, new TypeReference<List<LayerGroupLayerResponse>>() {});
    assertEquals(1, groups.size());
    assertEquals(ALPHA, groups.get(0).layerGroupName());
  }

  @Test
  void getLayerGroupsForLayer_whenGlobalAdminQueriesUnknownLayer_returnsEmptyList()
      throws Exception {
    var result =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}/layer-groups", UUID.randomUUID())
                    .with(authentication(GLOBAL_ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var groups = readResponse(result, new TypeReference<List<LayerGroupLayerResponse>>() {});
    assertTrue(groups.isEmpty(), "An unknown layer id should return 200 [] for a global admin");
  }

  @Test
  void getLayerGroupsForLayer_whenNotLayerMember_returnsForbidden() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(OPERATIONS, Map.of(OWNER_ID, LayerRole.OWNER));
    insertLayerGroupLayer(insertLayerGroup(ALPHA, "PRIVATE"), layerId, LayerRole.MEMBER);

    mockMvc
        .perform(
            get("/v1/layers/{layerId}/layer-groups", layerId).with(authentication(MEMBER_AUTH)))
        .andExpect(status().isForbidden());
  }

  private void seedGroupOnLayer(UUID layerId, LayerRole role) {
    var group = new GroupEntity();
    group.setName("test-group-" + UUID.randomUUID());
    var savedGroup = groupRepository.saveAndFlush(group);
    var layer =
        layerRepository
            .findById(layerId)
            .orElseThrow(() -> new IllegalStateException("Missing layer " + layerId));
    groupLayerMemberRepository.saveAndFlush(new GroupLayerMemberEntity(savedGroup, layer, role));
  }

  private UUID insertLayerGroup(String name, String accessScope) {
    var id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO iam.layer_group (id, name, access_scope)"
            + " VALUES (?, ?, CAST(? AS iam.layer_group_access_scope))",
        id,
        name,
        accessScope);
    return id;
  }

  private void insertLayerGroupMember(UUID groupId, UUID userId, String role) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer_group_member (layer_group_id, user_id, role)"
            + " VALUES (?, ?, CAST(? AS iam.layer_group_role))",
        groupId,
        userId,
        role);
  }

  private void insertLayerGroupLayer(UUID groupId, UUID layerId) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer_group_layer (layer_group_id, layer_id) VALUES (?, ?)",
        groupId,
        layerId);
  }

  private void insertLayerGroupLayer(UUID groupId, UUID layerId, LayerRole role) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer_group_layer (layer_group_id, layer_id, role)"
            + " VALUES (?, ?, CAST(? AS iam.layer_role))",
        groupId,
        layerId,
        role.name());
  }

  private void insertFeature(UUID id, String objectId, UUID sourceId, Instant createdAt) {
    var properties = new OpenCopProperties();
    properties.setId(id);
    properties.setObjectId(objectId);
    properties.setSource(sourceId);
    Feature feature = OpenCopPropertiesParser.writeToFeature(properties, null, id.toString());

    try {
      jdbcTemplate.update(
          "INSERT INTO geo.features (id, source, object_id, created_at, start_at, geom, payload) "
              + "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))",
          id,
          sourceId,
          objectId,
          Timestamp.from(createdAt),
          Timestamp.from(createdAt),
          "POINT Z (0 0 0)",
          objectMapper.writeValueAsString(feature));
      jdbcTemplate.update(
          """
          INSERT INTO geo.feature_latest_active_helper
            (source, object_id, event_id, playback_at, first_emission_at, created_at, stale_at)
          VALUES (?, ?, ?, ?, ?, ?, NULL)
          ON CONFLICT (source, object_id) DO UPDATE SET
            event_id = EXCLUDED.event_id,
            playback_at = GREATEST(geo.feature_latest_active_helper.playback_at, EXCLUDED.playback_at),
            first_emission_at = LEAST(geo.feature_latest_active_helper.first_emission_at, EXCLUDED.first_emission_at),
            created_at = EXCLUDED.created_at,
            stale_at = EXCLUDED.stale_at
          """,
          sourceId,
          objectId,
          id,
          Timestamp.from(createdAt),
          Timestamp.from(createdAt),
          Timestamp.from(createdAt));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to insert feature fixture", e);
    }
  }

  private void insertRemark(
      UUID remarkId, UUID sourceId, String objectId, UUID userId, String content) {
    jdbcTemplate.update(
        """
        INSERT INTO geo.remarks (id, source_id, object_id, content, user_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        remarkId,
        sourceId,
        objectId,
        content,
        userId,
        Timestamp.from(Instant.parse("2026-03-12T12:00:00Z")),
        Timestamp.from(Instant.parse("2026-03-12T12:05:00Z")));
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

  private void insertMedia(
      UUID mediaId,
      UUID userId,
      String blobKey,
      String originalFilename,
      UUID thumbnailOf,
      String usage) {
    jdbcTemplate.update(
        """
        INSERT INTO media_attachment (
            id, creator_id, owner_id, access, created_at, usage
        ) VALUES (?, ?, ?, CAST(? AS media_access), now(), CAST(? AS media_usage))
        """,
        mediaId,
        userId,
        userId,
        "LOGGED_IN",
        usage);
    jdbcTemplate.update(
        """
        INSERT INTO media_file (
            id, blob_key, original_filename, content_type, size_bytes, mime_type, thumbnail_of
        ) VALUES (?, ?, ?, CAST(? AS media_content_type), ?, ?, ?)
        """,
        mediaId,
        blobKey,
        originalFilename,
        "IMAGE",
        42L,
        "image/png",
        thumbnailOf);
  }

  private void insertEventMedia(UUID sourceId, String objectId, UUID mediaId) {
    jdbcTemplate.update(
        "INSERT INTO event_attachment (source_id, object_id, attachment_id) VALUES (?, ?, ?)",
        sourceId,
        objectId,
        mediaId);
  }

  private void insertLayerMedia(UUID layerId, UUID mediaId) {
    jdbcTemplate.update(
        "INSERT INTO layer_media (layer_id, media_id) VALUES (?, ?)", layerId, mediaId);
  }

  private void lockLayerForMerge(UUID layerId) {
    setLayerLifecycle(layerId, LayerLifecycleState.MERGE_SOURCE_LOCKED);
  }

  private void setLayerLifecycle(UUID layerId, LayerLifecycleState lifecycleState) {
    jdbcTemplate.update(
        "UPDATE iam.layer SET lifecycle_state = CAST(? AS iam.layer_lifecycle_state) WHERE id = ?",
        lifecycleState.name(),
        layerId);
  }

  private static InternalUser user(UUID userId) {
    return new InternalUser(
        userId,
        "idp-" + userId,
        "user-" + userId,
        "http://localhost/test-issuer",
        "user-" + userId,
        "user-" + userId + "@example.com",
        "Test",
        "User",
        "Test User",
        true,
        false,
        0,
        0,
        FIXED_INSTANT,
        FIXED_INSTANT,
        null);
  }

  private LayerLifecycleState loadLifecycleState(UUID layerId) {
    return jdbcTemplate.queryForObject(
        "SELECT lifecycle_state FROM iam.layer WHERE id = ?", LayerLifecycleState.class, layerId);
  }

  private LayerWebTestApplication.RecordingLayerJobPublisher recordingJobPublisher() {
    return (LayerWebTestApplication.RecordingLayerJobPublisher) jobPublisher;
  }

  private static Authentication globalAdminAuth() {
    var authority = new SimpleGrantedAuthority(Roles.ADMIN);
    var principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(
            LayersEndpointIT.ADMIN_ID, true);
    return new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
  }
}
