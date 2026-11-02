// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.TrackingDataSourceRepository;
import com.opencop.core.api.component.support.fixture.ConnectionFixtures;
import com.opencop.core.api.component.support.fixture.LayerFixtures;
import com.opencop.core.api.component.support.fixture.UserFixtures;
import com.opencop.core.api.general.business.model.me.MyLayerResponse;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupMemberManagement;
import com.opencop.core.api.general.technical.gateway.notification.NotificationWorker;
import com.opencop.core.api.general.technical.model.datasourcegroup.MyDataSourceGroupResponse;
import com.opencop.core.api.general.technical.model.layergroup.MyLayerGroupResponse;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.user.IamServerUserRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.user.entity.ServerUserAccessEntity;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.model.iam.layer.LayerPatchRequest;
import com.opencop.core.sdk.model.iam.source.AccessVia;
import com.opencop.core.sdk.model.iam.source.SourceInfo;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationReader;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupLayerMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupLayerMemberRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = PermissionsWebTestApplication.class)
class PermissionsEndpointIT extends AbstractWebIT {

  private static final UUID AUTH_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
  private static final UUID TARGET_USER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440122");
  private static final UUID OTHER_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440123");
  private static final UUID GLOBAL_ADMIN_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440130");
  private static final Authentication AUTH_USER = TestAuthentications.oidcUser(AUTH_USER_ID);
  private static final Authentication OTHER_USER = TestAuthentications.oidcUser(OTHER_USER_ID);
  private static final Authentication GLOBAL_ADMIN_AUTH = globalAdminAuth();

  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440126");

  private static final UUID DIRECT_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440127");
  private static final UUID INDIRECT_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440128");
  private static final UUID BOTH_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440129");
  private static final UUID GLOBAL_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440131");
  private static final UUID PRIVATE_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440132");
  private static final UUID DIRECT_DATA_SOURCE_GROUP_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440133");
  private static final UUID USER_GROUP_DATA_SOURCE_GROUP_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440134");
  private static final UUID EMBEDDED_DATA_SOURCE_GROUP_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440135");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private TrackingDataSourceRepository trackingDataSourceRepository;
  @Autowired private IamLayerRepository layerRepository;
  @Autowired private IamLayerMembersRepository layerMembersRepository;
  @Autowired private IamServerLayerRepository serverLayerRepository;
  @Autowired private IamServerUserRepository serverUserRepository;
  @Autowired private IamGroupRepository groupRepository;
  @Autowired private IamGroupMembersRepository groupMembersRepository;
  @Autowired private IamGroupLayerMemberRepository groupLayerMemberRepository;
  @Autowired private UserPreferenceRepository userPreferenceRepository;

  @MockitoBean private ConnectInstanceResolver connectInstanceResolver;
  @MockitoBean private SourceRuntimeObservationReader sourceRuntimeObservationReader;
  @MockitoBean private UserService userService;
  @MockitoBean private NotificationWorker notificationWorker;
  @MockitoBean private LayerGroupMemberManagement layerGroupMemberManagement;

  private ConnectionFixtures connectionFixtures;
  private LayerFixtures layerFixtures;
  private UserFixtures userFixtures;

  @BeforeEach
  void setUp() throws Exception {
    connectionFixtures = new ConnectionFixtures(jdbcTemplate, dataSourceRepository, objectMapper);
    layerFixtures =
        new LayerFixtures(
            jdbcTemplate, layerRepository, layerMembersRepository, serverLayerRepository);
    userFixtures = new UserFixtures(jdbcTemplate, userPreferenceRepository);

    jdbcTemplate.update("DELETE FROM iam.server_user_access");
    jdbcTemplate.update("DELETE FROM iam.layer_data_source_group");
    jdbcTemplate.update("DELETE FROM iam.data_source_group_data_source");
    jdbcTemplate.update("DELETE FROM iam.data_source_group_user_group");
    jdbcTemplate.update("DELETE FROM iam.data_source_group_member");
    jdbcTemplate.update("DELETE FROM iam.data_source_group");
    jdbcTemplate.update("DELETE FROM iam.group_layer_member");
    jdbcTemplate.update("DELETE FROM iam.user_group_member");
    jdbcTemplate.update("DELETE FROM iam.user_group");
    userFixtures.reset();
    // These are authentication principals, not necessarily rows: a member grant needs no user
    // record, but a write now records the caller in created_by/updated_by, foreign keys to
    // opencop_user that the mocked userService cannot satisfy.
    userFixtures.seedUser(AUTH_USER_ID);
    userFixtures.seedUser(OTHER_USER_ID);
    userFixtures.seedUser(GLOBAL_ADMIN_ID);
    layerFixtures.reset();
    connectionFixtures.reset();
    connectionFixtures.seedDefaultMapper(MAPPER_ID);

    when(userService.exists(ArgumentMatchers.any(UUID.class))).thenReturn(true);
    when(userService.get(ArgumentMatchers.any(UUID.class)))
        .thenAnswer(invocation -> internalUser(invocation.getArgument(0)));
    when(sourceRuntimeObservationReader.findFreshForInstance(
            ArgumentMatchers.any(UUID.class), ArgumentMatchers.anyString()))
        .thenAnswer(
            invocation -> {
              var sourceId = invocation.getArgument(0, UUID.class);
              if (sourceId.equals(DIRECT_SOURCE_ID)) {
                return Optional.of(
                    runtimeStatus(sourceId, ConnectorState.CONNECTED, "connect-direct"));
              }
              if (sourceId.equals(INDIRECT_SOURCE_ID)) {
                return Optional.of(
                    runtimeStatus(sourceId, ConnectorState.STOPPED, "connect-indirect"));
              }
              if (sourceId.equals(BOTH_SOURCE_ID)) {
                return Optional.of(
                    runtimeStatus(sourceId, ConnectorState.CONNECTED, "connect-both"));
              }
              if (sourceId.equals(GLOBAL_SOURCE_ID)) {
                return Optional.of(
                    runtimeStatus(sourceId, ConnectorState.CONNECTED, "connect-global"));
              }
              if (sourceId.equals(PRIVATE_SOURCE_ID)) {
                return Optional.of(
                    runtimeStatus(sourceId, ConnectorState.CONNECTED, "connect-private"));
              }
              return Optional.empty();
            });
    when(connectInstanceResolver.getAllInstanceIds())
        .thenReturn(
            List.of(
                "connect-direct",
                "connect-indirect",
                "connect-both",
                "connect-global",
                "connect-private"));
    when(connectInstanceResolver.getRoutableInstances(ArgumentMatchers.any()))
        .thenReturn(
            List.of(
                new ConnectInstanceInfo("connect-direct", Instant.now(), 0, "reg-direct", 1),
                new ConnectInstanceInfo("connect-indirect", Instant.now(), 0, "reg-indirect", 1),
                new ConnectInstanceInfo("connect-both", Instant.now(), 0, "reg-both", 1),
                new ConnectInstanceInfo("connect-global", Instant.now(), 0, "reg-global", 1),
                new ConnectInstanceInfo("connect-private", Instant.now(), 0, "reg-private", 1)));
  }

  // Auth
  @Test
  void getMyLayers_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(get("/v1/me/layers")).andExpect(status().isUnauthorized());
  }

  // Happy path
  @Test
  void getMyLayers_returnsAuthenticatedUsersLayerMemberships() throws Exception {
    var operationsLayerId =
        layerFixtures.persistLayerWithMembers(
            "Operations", Map.of(AUTH_USER_ID, LayerRole.OWNER, TARGET_USER_ID, LayerRole.MEMBER));
    var dispatchLayerId =
        layerFixtures.persistLayerWithMembers(
            "Dispatch", Map.of(AUTH_USER_ID, LayerRole.ADMIN, OTHER_USER_ID, LayerRole.MEMBER));

    var result =
        mockMvc
            .perform(get("/v1/me/layers").with(authentication(AUTH_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<MyLayerResponse>>() {});
    assertEquals(2, layers.size());

    var layersById =
        layers.stream()
            .collect(java.util.stream.Collectors.toMap(MyLayerResponse::layerId, l -> l));
    assertLayer(
        layersById.get(operationsLayerId), "Operations", LayerRole.OWNER, LayerAccessScope.PRIVATE);
    assertLayer(
        layersById.get(dispatchLayerId), "Dispatch", LayerRole.ADMIN, LayerAccessScope.PRIVATE);
  }

  @Test
  void getMyLayers_carriesAuditFieldsAndLastEventAtOnlyForLayersWithActivity() throws Exception {
    var activeLayerId =
        layerFixtures.persistLayerWithMembers("Active Ops", Map.of(AUTH_USER_ID, LayerRole.OWNER));
    var idleLayerId =
        layerFixtures.persistLayerWithMembers("Idle Ops", Map.of(AUTH_USER_ID, LayerRole.MEMBER));
    var lastEventAt = Instant.parse("2026-03-16T10:15:30Z");
    jdbcTemplate.update(
        "INSERT INTO activity.resource_activity (resource_kind, resource_id, last_event_at)"
            + " VALUES ('LAYER', ?, ?)",
        activeLayerId,
        java.sql.Timestamp.from(lastEventAt));
    var createdAt = Instant.parse("2026-03-01T08:00:00Z");
    var updatedAt = Instant.parse("2026-03-10T09:30:00Z");
    jdbcTemplate.update(
        "UPDATE iam.layer SET created_by = ?, created_at = ?, updated_by = ?, updated_at = ?,"
            + " default_visible = false WHERE id = ?",
        AUTH_USER_ID,
        java.sql.Timestamp.from(createdAt),
        OTHER_USER_ID,
        java.sql.Timestamp.from(updatedAt),
        activeLayerId);

    var result =
        mockMvc
            .perform(get("/v1/me/layers").with(authentication(AUTH_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var layersById =
        readResponse(result, new TypeReference<List<MyLayerResponse>>() {}).stream()
            .collect(java.util.stream.Collectors.toMap(MyLayerResponse::layerId, l -> l));
    var active = layersById.get(activeLayerId);
    assertEquals(createdAt, active.createdAt());
    assertEquals(AUTH_USER_ID, active.createdBy());
    assertEquals(updatedAt, active.updatedAt());
    assertEquals(OTHER_USER_ID, active.updatedBy());
    assertEquals(lastEventAt, active.lastEventAt());
    assertNull(active.archivedAt());
    assertNull(active.archivedBy());
    assertFalse(active.defaultVisible());

    var idle = layersById.get(idleLayerId);
    assertNull(idle.createdBy());
    assertNull(idle.updatedAt());
    assertNull(idle.updatedBy());
    assertNull(idle.lastEventAt());
    assertTrue(idle.defaultVisible());
  }

  @Test
  void getMyLayers_includesGlobalLayersAndKeepsHigherExplicitRole() throws Exception {
    var privateLayerId =
        layerFixtures.persistLayerWithMembers(
            "Private Ops", Map.of(AUTH_USER_ID, LayerRole.OWNER, TARGET_USER_ID, LayerRole.MEMBER));
    var implicitGlobalLayerId = layerFixtures.persistLayer("Shared Intel", LayerAccessScope.GLOBAL);
    var explicitGlobalLayerId =
        layerFixtures.persistLayerWithMembers(
            "Shared Admin",
            LayerAccessScope.GLOBAL,
            Map.of(AUTH_USER_ID, LayerRole.ADMIN, OTHER_USER_ID, LayerRole.MEMBER));

    var result =
        mockMvc
            .perform(get("/v1/me/layers").with(authentication(AUTH_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<MyLayerResponse>>() {});
    assertEquals(3, layers.size());

    var layersById =
        layers.stream()
            .collect(java.util.stream.Collectors.toMap(MyLayerResponse::layerId, l -> l));
    assertLayer(
        layersById.get(privateLayerId), "Private Ops", LayerRole.OWNER, LayerAccessScope.PRIVATE);
    assertLayer(
        layersById.get(implicitGlobalLayerId),
        "Shared Intel",
        LayerRole.MEMBER,
        LayerAccessScope.GLOBAL);
    assertLayer(
        layersById.get(explicitGlobalLayerId),
        "Shared Admin",
        LayerRole.ADMIN,
        LayerAccessScope.GLOBAL);
  }

  @Test
  void getMyLayers_unrelatedUserSeesGlobalLayer() throws Exception {
    var globalLayerId = layerFixtures.persistLayer("Shared Intel", LayerAccessScope.GLOBAL);

    var result =
        mockMvc
            .perform(get("/v1/me/layers").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<MyLayerResponse>>() {});
    assertEquals(1, layers.size());
    assertLayer(layers.getFirst(), "Shared Intel", LayerRole.MEMBER, LayerAccessScope.GLOBAL);
    assertEquals(globalLayerId, layers.getFirst().layerId());
  }

  @Test
  void getMyLayers_unrelatedUserDoesNotSeePrivateLayer() throws Exception {
    layerFixtures.persistLayerWithMembers(
        "Private Ops", Map.of(AUTH_USER_ID, LayerRole.OWNER, TARGET_USER_ID, LayerRole.MEMBER));

    var result =
        mockMvc
            .perform(get("/v1/me/layers").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<MyLayerResponse>>() {});
    assertTrue(layers.isEmpty());
  }

  /**
   * Effective layer access is resolved from membership alone, so the global-admin flag does not
   * widen an operator's own layer list. Pinned because imagery deliberately matches it: the tile
   * catalogue is reached through the admin routes, not by every admin's map filling up with layers
   * and tiles they were never granted.
   */
  @Test
  void getMyLayers_globalAdminDoesNotSeePrivateLayerTheyAreNotAMemberOf() throws Exception {
    layerFixtures.persistLayerWithMembers(
        "Private Ops", Map.of(AUTH_USER_ID, LayerRole.OWNER, TARGET_USER_ID, LayerRole.MEMBER));

    var result =
        mockMvc
            .perform(get("/v1/me/layers").with(authentication(GLOBAL_ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<MyLayerResponse>>() {});
    assertTrue(layers.isEmpty());
  }

  @Test
  void getMyLayers_afterPrivateToGlobalPatch_unrelatedUserGainsVisibility() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers("Ops", Map.of(AUTH_USER_ID, LayerRole.OWNER));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(GLOBAL_ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerPatchRequest(null, LayerAccessScope.GLOBAL, null))))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(get("/v1/me/layers").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<MyLayerResponse>>() {});
    assertEquals(1, layers.size());
    assertEquals(layerId, layers.getFirst().layerId());
    assertEquals(LayerRole.MEMBER, layers.getFirst().role());
  }

  @Test
  void getMyLayers_afterGlobalToPrivatePatch_unrelatedUserLosesVisibility() throws Exception {
    var layerId = layerFixtures.persistLayer("Shared Intel", LayerAccessScope.GLOBAL);

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(GLOBAL_ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerPatchRequest(null, LayerAccessScope.PRIVATE, null))))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(get("/v1/me/layers").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<MyLayerResponse>>() {});
    assertTrue(layers.isEmpty());
  }

  @Test
  void getMyLayers_explicitMemberStillSeesFormerGlobalLayerAfterPatchToPrivate() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers(
            "Shared Intel",
            LayerAccessScope.GLOBAL,
            Map.of(AUTH_USER_ID, LayerRole.OWNER, OTHER_USER_ID, LayerRole.MEMBER));

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", layerId)
                .with(csrf())
                .with(authentication(GLOBAL_ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerPatchRequest(null, LayerAccessScope.PRIVATE, null))))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(get("/v1/me/layers").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<MyLayerResponse>>() {});
    assertEquals(1, layers.size());
    assertLayer(layers.getFirst(), "Shared Intel", LayerRole.MEMBER, LayerAccessScope.PRIVATE);
  }

  @Test
  void getMySources_mergesPermissionsAndPreservesSourceMetadata() throws Exception {
    var alphaLayerId =
        layerFixtures.persistLayerWithMembers("Alpha", Map.of(AUTH_USER_ID, LayerRole.MEMBER));
    var bravoLayerId =
        layerFixtures.persistLayerWithMembers("Bravo", Map.of(AUTH_USER_ID, LayerRole.ADMIN));

    connectionFixtures.persistTcpSource(
        DIRECT_SOURCE_ID,
        "Direct Source",
        MAPPER_ID,
        AccessMode.READ_WRITE,
        true,
        "connect-direct");
    connectionFixtures.persistUdpSource(INDIRECT_SOURCE_ID, "Indirect Source", MAPPER_ID, false);
    connectionFixtures.persistTcpSource(
        BOTH_SOURCE_ID, "Both Source", MAPPER_ID, true, "connect-both");

    grantDirectSourceAccess(DIRECT_SOURCE_ID, AUTH_USER_ID, ServerRole.OWNER);
    grantDirectSourceAccess(BOTH_SOURCE_ID, AUTH_USER_ID, ServerRole.VIEWER);
    layerFixtures.addServerAccess(INDIRECT_SOURCE_ID, alphaLayerId, ServerRole.ADMIN);
    layerFixtures.addServerAccess(BOTH_SOURCE_ID, bravoLayerId, ServerRole.OWNER);
    trackingDataSourceRepository.resetReadCounts();

    var result =
        mockMvc
            .perform(get("/v1/me/sources").with(authentication(AUTH_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var sources = readResponse(result, new TypeReference<List<SourceInfo>>() {});
    var sourcePayloads = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    sourcePayloads.forEach(source -> assertTrue(source.has("directRole")));
    assertEquals(3, sources.size());

    var sourcesById =
        sources.stream().collect(java.util.stream.Collectors.toMap(SourceInfo::serverId, s -> s));
    assertSource(
        sourcesById.get(DIRECT_SOURCE_ID),
        "Direct Source",
        ConnectorState.CONNECTED,
        ServerRole.OWNER,
        ServerRole.OWNER,
        AccessVia.DIRECT,
        AccessMode.READ_WRITE,
        TransportKind.TCP);
    assertSource(
        sourcesById.get(INDIRECT_SOURCE_ID),
        "Indirect Source",
        ConnectorState.STOPPED,
        ServerRole.ADMIN,
        null,
        AccessVia.INDIRECT,
        AccessMode.READ,
        TransportKind.UDP);
    assertSource(
        sourcesById.get(BOTH_SOURCE_ID),
        "Both Source",
        ConnectorState.CONNECTED,
        ServerRole.OWNER,
        ServerRole.VIEWER,
        AccessVia.BOTH,
        AccessMode.READ,
        TransportKind.TCP);
    assertEquals(1, trackingDataSourceRepository.aggregateReadCount());
    assertEquals(0, trackingDataSourceRepository.singleReadCount());
  }

  @Test
  void getMySources_includesSourceViaGlobalLayerForUnrelatedUser() throws Exception {
    var globalLayerId = layerFixtures.persistLayer("Shared Intel", LayerAccessScope.GLOBAL);
    connectionFixtures.persistTcpSource(
        GLOBAL_SOURCE_ID, "Global Source", MAPPER_ID, true, "connect-global");
    layerFixtures.addServerAccess(GLOBAL_SOURCE_ID, globalLayerId, ServerRole.VIEWER);

    var result =
        mockMvc
            .perform(get("/v1/me/sources").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var sources = readResponse(result, new TypeReference<List<SourceInfo>>() {});
    assertEquals(1, sources.size());
    assertSource(
        sources.getFirst(),
        "Global Source",
        ConnectorState.CONNECTED,
        ServerRole.VIEWER,
        null,
        AccessVia.INDIRECT,
        AccessMode.READ,
        TransportKind.TCP);
  }

  @Test
  void getMySources_excludesSourceBehindPrivateLayerForUnrelatedUser() throws Exception {
    var privateLayerId =
        layerFixtures.persistLayerWithMembers("Private Ops", Map.of(AUTH_USER_ID, LayerRole.OWNER));
    connectionFixtures.persistTcpSource(
        PRIVATE_SOURCE_ID, "Private Source", MAPPER_ID, true, "connect-private");
    layerFixtures.addServerAccess(PRIVATE_SOURCE_ID, privateLayerId, ServerRole.VIEWER);

    var result =
        mockMvc
            .perform(get("/v1/me/sources").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var sources = readResponse(result, new TypeReference<List<SourceInfo>>() {});
    assertTrue(sources.isEmpty());
  }

  @Test
  void getUserPermissions_whenDifferentNonAdminUser_returnsForbidden() throws Exception {
    mockMvc
        .perform(get("/v1/users/{userId}/layers", TARGET_USER_ID).with(authentication(OTHER_USER)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/v1/users/{userId}/sources", TARGET_USER_ID).with(authentication(OTHER_USER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void getUserPermissions_whenSameNonAdminUser_preservesOwnSourceMetadata() throws Exception {
    var ownLayerId =
        layerFixtures.persistLayerWithMembers("Own Layer", Map.of(OTHER_USER_ID, LayerRole.MEMBER));
    connectionFixtures.persistTcpSource(
        DIRECT_SOURCE_ID, "Own TCP Source", MAPPER_ID, AccessMode.READ_WRITE, false, "connect-own");
    connectionFixtures.persistUdpSource(INDIRECT_SOURCE_ID, "Own UDP Source", MAPPER_ID, false);
    layerFixtures.addServerAccess(DIRECT_SOURCE_ID, ownLayerId, ServerRole.VIEWER);
    layerFixtures.addServerAccess(INDIRECT_SOURCE_ID, ownLayerId, ServerRole.VIEWER);
    trackingDataSourceRepository.resetReadCounts();

    var layersResult =
        mockMvc
            .perform(
                get("/v1/users/{userId}/layers", OTHER_USER_ID).with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();
    var layers = readResponse(layersResult, new TypeReference<List<MyLayerResponse>>() {});

    assertEquals(1, layers.size());
    assertLayer(layers.getFirst(), "Own Layer", LayerRole.MEMBER, LayerAccessScope.PRIVATE);

    var sourcesResult =
        mockMvc
            .perform(
                get("/v1/users/{userId}/sources", OTHER_USER_ID).with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();
    var sources = readResponse(sourcesResult, new TypeReference<List<SourceInfo>>() {});

    assertEquals(2, sources.size());
    var sourcesById =
        sources.stream().collect(java.util.stream.Collectors.toMap(SourceInfo::serverId, s -> s));
    assertSource(
        sourcesById.get(DIRECT_SOURCE_ID),
        "Own TCP Source",
        ConnectorState.STOPPED,
        ServerRole.VIEWER,
        null,
        AccessVia.INDIRECT,
        AccessMode.READ_WRITE,
        TransportKind.TCP);
    assertSource(
        sourcesById.get(INDIRECT_SOURCE_ID),
        "Own UDP Source",
        ConnectorState.STOPPED,
        ServerRole.VIEWER,
        null,
        AccessVia.INDIRECT,
        AccessMode.READ,
        TransportKind.UDP);
    assertEquals(1, trackingDataSourceRepository.aggregateReadCount());
    assertEquals(0, trackingDataSourceRepository.singleReadCount());
  }

  @Test
  void getUserPermissions_whenGlobalAdmin_returnsSpecifiedUsersPermissions() throws Exception {
    var targetLayerId =
        layerFixtures.persistLayerWithMembers(
            "Target Layer", Map.of(TARGET_USER_ID, LayerRole.MEMBER));
    connectionFixtures.persistTcpSource(
        INDIRECT_SOURCE_ID, "Target Source", MAPPER_ID, false, "connect-indirect");
    layerFixtures.addServerAccess(INDIRECT_SOURCE_ID, targetLayerId, ServerRole.VIEWER);

    var layersResult =
        mockMvc
            .perform(
                get("/v1/users/{userId}/layers", TARGET_USER_ID)
                    .with(authentication(GLOBAL_ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var userLayers = readResponse(layersResult, new TypeReference<List<MyLayerResponse>>() {});

    assertEquals(1, userLayers.size());
    assertLayer(userLayers.getFirst(), "Target Layer", LayerRole.MEMBER, LayerAccessScope.PRIVATE);

    var sourcesResult =
        mockMvc
            .perform(
                get("/v1/users/{userId}/sources", TARGET_USER_ID)
                    .with(authentication(GLOBAL_ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var userSources = readResponse(sourcesResult, new TypeReference<List<SourceInfo>>() {});

    assertEquals(1, userSources.size());
    assertSource(
        userSources.getFirst(),
        "Target Source",
        ConnectorState.STOPPED,
        ServerRole.VIEWER,
        null,
        AccessVia.INDIRECT,
        AccessMode.READ,
        TransportKind.TCP);
  }

  // Group-based layer access

  @Test
  void getMyLayers_includesPrivateLayerAccessibleViaGroup() throws Exception {
    var layerId = layerFixtures.persistLayer("Private Ops");
    seedGroupAccess(layerId, OTHER_USER_ID, LayerRole.EDITOR);

    var result =
        mockMvc
            .perform(get("/v1/me/layers").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<MyLayerResponse>>() {});
    assertEquals(1, layers.size());
    assertLayer(layers.getFirst(), "Private Ops", LayerRole.EDITOR, LayerAccessScope.PRIVATE);
  }

  @Test
  void getMyLayers_takesHigherOfDirectAndGroupRole() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers("Ops", Map.of(OTHER_USER_ID, LayerRole.MEMBER));
    seedGroupAccess(layerId, OTHER_USER_ID, LayerRole.ADMIN);

    var result =
        mockMvc
            .perform(get("/v1/me/layers").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<MyLayerResponse>>() {});
    assertEquals(1, layers.size());
    assertLayer(layers.getFirst(), "Ops", LayerRole.ADMIN, LayerAccessScope.PRIVATE);
  }

  @Test
  void getMyLayers_directRoleWinsOverLowerGroupRole() throws Exception {
    var layerId =
        layerFixtures.persistLayerWithMembers("Ops", Map.of(OTHER_USER_ID, LayerRole.OWNER));
    seedGroupAccess(layerId, OTHER_USER_ID, LayerRole.MEMBER);

    var result =
        mockMvc
            .perform(get("/v1/me/layers").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<MyLayerResponse>>() {});
    assertEquals(1, layers.size());
    assertLayer(layers.getFirst(), "Ops", LayerRole.OWNER, LayerAccessScope.PRIVATE);
  }

  @Test
  void getMySources_includesSourceBehindPrivateLayerAccessibleViaGroup() throws Exception {
    var layerId = layerFixtures.persistLayer("Group Layer");
    connectionFixtures.persistTcpSource(
        PRIVATE_SOURCE_ID, "Private Source", MAPPER_ID, true, "connect-private");
    layerFixtures.addServerAccess(PRIVATE_SOURCE_ID, layerId, ServerRole.VIEWER);
    seedGroupAccess(layerId, OTHER_USER_ID, LayerRole.MEMBER);

    var result =
        mockMvc
            .perform(get("/v1/me/sources").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var sources = readResponse(result, new TypeReference<List<SourceInfo>>() {});
    assertEquals(1, sources.size());
    assertSource(
        sources.getFirst(),
        "Private Source",
        ConnectorState.CONNECTED,
        ServerRole.VIEWER,
        null,
        AccessVia.INDIRECT,
        AccessMode.READ,
        TransportKind.TCP);
  }

  @Test
  void getMySources_includesSourceViaDirectDataSourceGroupMembership() throws Exception {
    connectionFixtures.persistTcpSource(
        PRIVATE_SOURCE_ID, "Direct DSG Source", MAPPER_ID, true, "connect-private");
    seedDataSourceGroup(DIRECT_DATA_SOURCE_GROUP_ID, "Direct DSG");
    seedDataSourceGroupMember(DIRECT_DATA_SOURCE_GROUP_ID, OTHER_USER_ID);
    seedDataSourceGroupSource(DIRECT_DATA_SOURCE_GROUP_ID, PRIVATE_SOURCE_ID, ServerRole.ADMIN);

    var result =
        mockMvc
            .perform(get("/v1/me/sources").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var sources = readResponse(result, new TypeReference<List<SourceInfo>>() {});
    assertEquals(1, sources.size());
    assertSource(
        sources.getFirst(),
        "Direct DSG Source",
        ConnectorState.CONNECTED,
        ServerRole.ADMIN,
        null,
        AccessVia.INDIRECT,
        AccessMode.READ,
        TransportKind.TCP);
  }

  @Test
  void getMySources_includesSourceViaUserGroupDataSourceGroupAssignment() throws Exception {
    connectionFixtures.persistTcpSource(
        PRIVATE_SOURCE_ID, "User Group DSG Source", MAPPER_ID, true, "connect-private");
    var userGroupId = seedUserGroupMembership(OTHER_USER_ID);
    seedDataSourceGroup(USER_GROUP_DATA_SOURCE_GROUP_ID, "User Group DSG");
    seedDataSourceGroupUserGroup(USER_GROUP_DATA_SOURCE_GROUP_ID, userGroupId);
    seedDataSourceGroupSource(USER_GROUP_DATA_SOURCE_GROUP_ID, PRIVATE_SOURCE_ID, ServerRole.ADMIN);

    var result =
        mockMvc
            .perform(get("/v1/me/sources").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var sources = readResponse(result, new TypeReference<List<SourceInfo>>() {});
    assertEquals(1, sources.size());
    assertSource(
        sources.getFirst(),
        "User Group DSG Source",
        ConnectorState.CONNECTED,
        ServerRole.ADMIN,
        null,
        AccessVia.INDIRECT,
        AccessMode.READ,
        TransportKind.TCP);
  }

  @Test
  void getMySources_includesSourceViaGlobalLayerEmbeddedDataSourceGroup() throws Exception {
    var globalLayerId = layerFixtures.persistLayer("Public DSG Layer", LayerAccessScope.GLOBAL);
    connectionFixtures.persistTcpSource(
        GLOBAL_SOURCE_ID, "Embedded DSG Source", MAPPER_ID, true, "connect-global");
    seedDataSourceGroup(EMBEDDED_DATA_SOURCE_GROUP_ID, "Embedded DSG");
    seedDataSourceGroupSource(EMBEDDED_DATA_SOURCE_GROUP_ID, GLOBAL_SOURCE_ID, ServerRole.VIEWER);
    seedLayerDataSourceGroup(globalLayerId, EMBEDDED_DATA_SOURCE_GROUP_ID);

    var result =
        mockMvc
            .perform(get("/v1/me/sources").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var sources = readResponse(result, new TypeReference<List<SourceInfo>>() {});
    assertEquals(1, sources.size());
    assertSource(
        sources.getFirst(),
        "Embedded DSG Source",
        ConnectorState.CONNECTED,
        ServerRole.VIEWER,
        null,
        AccessVia.INDIRECT,
        AccessMode.READ,
        TransportKind.TCP);
  }

  @Test
  void getMySources_mergesDirectAndDataSourceGroupAccess() throws Exception {
    connectionFixtures.persistTcpSource(
        BOTH_SOURCE_ID, "Direct Plus DSG Source", MAPPER_ID, true, "connect-both");
    grantDirectSourceAccess(BOTH_SOURCE_ID, OTHER_USER_ID, ServerRole.VIEWER);
    seedDataSourceGroup(DIRECT_DATA_SOURCE_GROUP_ID, "Merge DSG");
    seedDataSourceGroupMember(DIRECT_DATA_SOURCE_GROUP_ID, OTHER_USER_ID);
    seedDataSourceGroupSource(DIRECT_DATA_SOURCE_GROUP_ID, BOTH_SOURCE_ID, ServerRole.ADMIN);

    var result =
        mockMvc
            .perform(get("/v1/me/sources").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var sources = readResponse(result, new TypeReference<List<SourceInfo>>() {});
    assertEquals(1, sources.size());
    assertSource(
        sources.getFirst(),
        "Direct Plus DSG Source",
        ConnectorState.CONNECTED,
        ServerRole.ADMIN,
        ServerRole.VIEWER,
        AccessVia.BOTH,
        AccessMode.READ,
        TransportKind.TCP);
  }

  @Test
  void getMyDataSourceGroups_listsDirectAndUserGroupMemberships() throws Exception {
    connectionFixtures.persistTcpSource(
        DIRECT_SOURCE_ID, "Direct Group Source", MAPPER_ID, true, "connect-direct");
    connectionFixtures.persistTcpSource(
        PRIVATE_SOURCE_ID, "User Group Source", MAPPER_ID, true, "connect-private");
    seedDataSourceGroup(DIRECT_DATA_SOURCE_GROUP_ID, "Direct DSG");
    seedDataSourceGroupMember(DIRECT_DATA_SOURCE_GROUP_ID, OTHER_USER_ID);
    seedDataSourceGroupSource(DIRECT_DATA_SOURCE_GROUP_ID, DIRECT_SOURCE_ID, ServerRole.ADMIN);
    var userGroupId = seedUserGroupMembership(OTHER_USER_ID);
    seedDataSourceGroup(USER_GROUP_DATA_SOURCE_GROUP_ID, "User Group DSG");
    seedDataSourceGroupUserGroup(USER_GROUP_DATA_SOURCE_GROUP_ID, userGroupId);
    seedDataSourceGroupSource(
        USER_GROUP_DATA_SOURCE_GROUP_ID, PRIVATE_SOURCE_ID, ServerRole.VIEWER);
    seedDataSourceGroup(EMBEDDED_DATA_SOURCE_GROUP_ID, "Unrelated DSG");

    var result =
        mockMvc
            .perform(get("/v1/me/data-source-groups").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var groups = readResponse(result, new TypeReference<List<MyDataSourceGroupResponse>>() {});
    assertEquals(2, groups.size());
    var groupsById =
        groups.stream()
            .collect(java.util.stream.Collectors.toMap(MyDataSourceGroupResponse::id, g -> g));
    assertEquals("Direct DSG", groupsById.get(DIRECT_DATA_SOURCE_GROUP_ID).name());
    assertEquals(
        ServerRole.ADMIN,
        groupsById.get(DIRECT_DATA_SOURCE_GROUP_ID).dataSources().getFirst().role());
    assertEquals(
        "Direct Group Source",
        groupsById.get(DIRECT_DATA_SOURCE_GROUP_ID).dataSources().getFirst().name());
    assertEquals("User Group DSG", groupsById.get(USER_GROUP_DATA_SOURCE_GROUP_ID).name());
    assertEquals(
        ServerRole.VIEWER,
        groupsById.get(USER_GROUP_DATA_SOURCE_GROUP_ID).dataSources().getFirst().role());
    assertEquals(
        "User Group Source",
        groupsById.get(USER_GROUP_DATA_SOURCE_GROUP_ID).dataSources().getFirst().name());
  }

  @Test
  void getMyLayerGroups_isServedFromPermissionsEndpoint() throws Exception {
    var layerGroupId = UUID.fromString("550e8400-e29b-41d4-a716-446655440136");
    when(layerGroupMemberManagement.listMyLayerGroups(ArgumentMatchers.any(OpenCopPrincipal.class)))
        .thenReturn(
            List.of(
                new MyLayerGroupResponse(
                    layerGroupId,
                    "Layer Folder",
                    "desc",
                    LayerGroupRole.MEMBER,
                    null,
                    Instant.EPOCH,
                    List.of())));

    var result =
        mockMvc
            .perform(get("/v1/me/layer-groups").with(authentication(OTHER_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var groups = readResponse(result, new TypeReference<List<MyLayerGroupResponse>>() {});
    assertEquals(1, groups.size());
    assertEquals(layerGroupId, groups.getFirst().id());
    assertEquals("Layer Folder", groups.getFirst().name());
  }

  // RecordVisibility filtering on /v1/me

  @Test
  void getMyLayers_defaultsToActiveAndExcludesArchivedMemberships() throws Exception {
    layerFixtures.persistLayerWithMembers("Active Ops", Map.of(AUTH_USER_ID, LayerRole.OWNER));
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers(
            "Archived Ops", Map.of(AUTH_USER_ID, LayerRole.OWNER));
    archiveLayerRow(archivedLayerId, AUTH_USER_ID);

    var result =
        mockMvc
            .perform(get("/v1/me/layers").with(authentication(AUTH_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<MyLayerResponse>>() {});
    assertEquals(1, layers.size());
    assertEquals("Active Ops", layers.getFirst().layerName());
  }

  @Test
  void getMyLayers_recordVisibilityArchived_returnsArchivedMembershipsOnly() throws Exception {
    layerFixtures.persistLayerWithMembers("Active Ops", Map.of(AUTH_USER_ID, LayerRole.OWNER));
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers(
            "Archived Ops", Map.of(AUTH_USER_ID, LayerRole.OWNER));
    archiveLayerRow(archivedLayerId, AUTH_USER_ID);

    var result =
        mockMvc
            .perform(
                get("/v1/me/layers")
                    .queryParam("recordVisibility", "ARCHIVED")
                    .with(authentication(AUTH_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<List<MyLayerResponse>>() {});
    assertEquals(1, layers.size());
    assertEquals(archivedLayerId, layers.getFirst().layerId());
    assertEquals("Archived Ops", layers.getFirst().layerName());
    assertNotNull(layers.getFirst().archivedAt());
  }

  @Test
  void getMySources_carriesAuditFieldsAndLastEventAtOnlyForSourcesWithActivity() throws Exception {
    var lastEventAt = Instant.parse("2026-03-16T10:15:30Z");
    connectionFixtures.persistTcpSource(
        DIRECT_SOURCE_ID, "Busy Source", MAPPER_ID, true, "connect-busy");
    connectionFixtures.persistTcpSource(
        PRIVATE_SOURCE_ID, "Idle Source", MAPPER_ID, true, "connect-idle");
    grantDirectSourceAccess(DIRECT_SOURCE_ID, AUTH_USER_ID, ServerRole.VIEWER);
    grantDirectSourceAccess(PRIVATE_SOURCE_ID, AUTH_USER_ID, ServerRole.VIEWER);
    jdbcTemplate.update(
        "INSERT INTO activity.resource_activity (resource_kind, resource_id, last_event_at)"
            + " VALUES ('DATA_SOURCE', ?, ?)",
        DIRECT_SOURCE_ID,
        java.sql.Timestamp.from(lastEventAt));
    jdbcTemplate.update(
        "UPDATE profiles.data_source SET created_by = ?, updated_by = ? WHERE id = ?",
        AUTH_USER_ID,
        OTHER_USER_ID,
        DIRECT_SOURCE_ID);

    var result =
        mockMvc
            .perform(get("/v1/me/sources").with(authentication(AUTH_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var sourcesById =
        readResponse(result, new TypeReference<List<SourceInfo>>() {}).stream()
            .collect(java.util.stream.Collectors.toMap(SourceInfo::serverId, s -> s));
    var busy = sourcesById.get(DIRECT_SOURCE_ID);
    assertNotNull(busy.createdAt());
    assertNotNull(busy.updatedAt());
    assertEquals(AUTH_USER_ID, busy.createdBy());
    assertEquals(OTHER_USER_ID, busy.updatedBy());
    assertEquals(lastEventAt, busy.lastEventAt());

    var idle = sourcesById.get(PRIVATE_SOURCE_ID);
    assertNull(idle.createdBy());
    assertNull(idle.updatedBy());
    assertNull(idle.lastEventAt());
  }

  @Test
  void getMySources_defaultsToActiveAndExcludesArchivedDirectGrants() throws Exception {
    connectionFixtures.persistTcpSource(
        DIRECT_SOURCE_ID, "Active Source", MAPPER_ID, true, "connect-active");
    connectionFixtures.persistTcpSource(
        PRIVATE_SOURCE_ID, "Archived Source", MAPPER_ID, true, "connect-archived");
    grantDirectSourceAccess(DIRECT_SOURCE_ID, AUTH_USER_ID, ServerRole.VIEWER);
    grantDirectSourceAccess(PRIVATE_SOURCE_ID, AUTH_USER_ID, ServerRole.VIEWER);
    archiveDataSourceRow(PRIVATE_SOURCE_ID, AUTH_USER_ID);

    var result =
        mockMvc
            .perform(get("/v1/me/sources").with(authentication(AUTH_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var sources = readResponse(result, new TypeReference<List<SourceInfo>>() {});
    assertEquals(1, sources.size());
    assertEquals(DIRECT_SOURCE_ID, sources.getFirst().serverId());
  }

  @Test
  void getMySources_recordVisibilityArchived_returnsArchivedDirectGrantsOnly() throws Exception {
    connectionFixtures.persistTcpSource(
        DIRECT_SOURCE_ID, "Active Source", MAPPER_ID, true, "connect-active");
    connectionFixtures.persistTcpSource(
        PRIVATE_SOURCE_ID, "Archived Source", MAPPER_ID, true, "connect-archived");
    grantDirectSourceAccess(DIRECT_SOURCE_ID, AUTH_USER_ID, ServerRole.VIEWER);
    grantDirectSourceAccess(PRIVATE_SOURCE_ID, AUTH_USER_ID, ServerRole.VIEWER);
    archiveDataSourceRow(PRIVATE_SOURCE_ID, AUTH_USER_ID);

    var result =
        mockMvc
            .perform(
                get("/v1/me/sources")
                    .queryParam("recordVisibility", "ARCHIVED")
                    .with(authentication(AUTH_USER)))
            .andExpect(status().isOk())
            .andReturn();

    var sources = readResponse(result, new TypeReference<List<SourceInfo>>() {});
    assertEquals(1, sources.size());
    assertEquals(PRIVATE_SOURCE_ID, sources.getFirst().serverId());
  }

  private void archiveLayerRow(UUID layerId, UUID archivedBy) {
    userFixtures.seedUser(archivedBy);
    jdbcTemplate.update(
        """
        UPDATE iam.layer
        SET archived_at = now(),
            archived_by = ?,
            lifecycle_state = 'ARCHIVED'::iam.layer_lifecycle_state
        WHERE id = ?
        """,
        archivedBy,
        layerId);
  }

  private void archiveDataSourceRow(UUID sourceId, UUID archivedBy) {
    userFixtures.seedUser(archivedBy);
    jdbcTemplate.update(
        "UPDATE profiles.data_source SET archived_at = now(), archived_by = ? WHERE id = ?",
        archivedBy,
        sourceId);
  }

  private void seedGroupAccess(UUID layerId, UUID userId, LayerRole role) {
    userFixtures.seedUser(userId);

    var savedGroup = seedUserGroupMembership(userId);

    var layer =
        layerRepository
            .findById(layerId)
            .orElseThrow(() -> new IllegalStateException("Missing layer " + layerId));
    var group =
        groupRepository
            .findById(savedGroup)
            .orElseThrow(() -> new IllegalStateException("Missing group " + savedGroup));
    groupLayerMemberRepository.saveAndFlush(new GroupLayerMemberEntity(group, layer, role));
  }

  private UUID seedUserGroupMembership(UUID userId) {
    userFixtures.seedUser(userId);

    var group = new GroupEntity();
    group.setName("test-group-" + UUID.randomUUID());
    var savedGroup = groupRepository.saveAndFlush(group);

    groupMembersRepository.saveAndFlush(new GroupMemberEntity(savedGroup, userId));
    return savedGroup.getId();
  }

  private void seedDataSourceGroup(UUID groupId, String name) {
    jdbcTemplate.update("INSERT INTO iam.data_source_group(id, name) VALUES (?, ?)", groupId, name);
  }

  private void seedDataSourceGroupMember(UUID groupId, UUID userId) {
    userFixtures.seedUser(userId);
    jdbcTemplate.update(
        """
        INSERT INTO iam.data_source_group_member(data_source_group_id, user_id, role)
        VALUES (?, ?, 'MEMBER'::iam.data_source_group_role)
        """,
        groupId,
        userId);
  }

  private void seedDataSourceGroupUserGroup(UUID dataSourceGroupId, UUID userGroupId) {
    jdbcTemplate.update(
        """
        INSERT INTO iam.data_source_group_user_group(data_source_group_id, user_group_id, role)
        VALUES (?, ?, 'MEMBER'::iam.data_source_group_role)
        """,
        dataSourceGroupId,
        userGroupId);
  }

  private void seedDataSourceGroupSource(UUID groupId, UUID sourceId, ServerRole role) {
    jdbcTemplate.update(
        """
        INSERT INTO iam.data_source_group_data_source(data_source_group_id, data_source_id, role)
        VALUES (?, ?, ?::iam.server_role)
        """,
        groupId,
        sourceId,
        role.name());
  }

  private void seedLayerDataSourceGroup(UUID layerId, UUID groupId) {
    jdbcTemplate.update(
        """
        INSERT INTO iam.layer_data_source_group(layer_id, data_source_group_id)
        VALUES (?, ?)
        """,
        layerId,
        groupId);
  }

  private void grantDirectSourceAccess(UUID sourceId, UUID userId, ServerRole role) {
    serverUserRepository.saveAndFlush(new ServerUserAccessEntity(sourceId, userId, role));
  }

  private static Authentication globalAdminAuth() {
    var authority = new SimpleGrantedAuthority(Roles.ADMIN);
    var principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(GLOBAL_ADMIN_ID, true);
    return new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
  }

  private static RuntimeStatus runtimeStatus(
      UUID sourceId, ConnectorState state, String instanceId) {
    return new RuntimeStatus(sourceId, state, Instant.parse("2026-03-16T10:15:30Z"), instanceId);
  }

  private static InternalUser internalUser(UUID userId) {
    return new InternalUser(
        userId,
        "idp-" + userId,
        "user-" + userId,
        "https://issuer.test",
        "user-" + userId,
        "user@example.com",
        "Test",
        "User",
        "Test User",
        true,
        false,
        0,
        0,
        Instant.parse("2026-03-16T09:00:00Z"),
        Instant.parse("2026-03-16T09:00:00Z"),
        null);
  }

  private static void assertLayer(
      MyLayerResponse layer,
      String expectedName,
      LayerRole expectedRole,
      LayerAccessScope expectedAccessScope) {
    assertNotNull(layer);
    assertEquals(expectedName, layer.layerName());
    assertEquals(expectedRole, layer.role());
    assertEquals(expectedAccessScope, layer.accessScope());
    assertNotNull(layer.createdAt());
  }

  private static void assertSource(
      SourceInfo source,
      String expectedName,
      ConnectorState expectedState,
      ServerRole expectedRole,
      ServerRole expectedDirectRole,
      AccessVia expectedVia,
      AccessMode expectedAccess,
      TransportKind expectedTransport) {
    assertNotNull(source);
    assertEquals(expectedName, source.serverName());
    assertEquals(expectedState.name(), source.serverState());
    assertEquals(expectedRole, source.role());
    assertEquals(expectedDirectRole, source.directRole());
    assertEquals(expectedVia, source.via());
    assertEquals(expectedAccess, source.access());
    assertEquals(expectedTransport, source.transportType());
    assertTrue(source.serverId() != null);
    assertNotNull(source.createdAt());
  }
}
