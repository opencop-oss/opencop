// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.ConnectionFixtures;
import com.opencop.core.api.component.support.fixture.LayerFixtures;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.technical.gateway.notification.NotificationWorker;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.user.IamServerUserRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.user.entity.ServerUserAccessEntity;
import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.business.model.iam.access.SourceUser;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.model.iam.source.UpdateSourcePermissionsRequest;
import com.opencop.core.sdk.model.notification.NotificationCommand;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.ConnectService;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationReader;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = SourceWebTestApplication.class)
class SourcesEndpointIT extends AbstractWebIT {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440162");
  private static final UUID MEMBER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440163");
  private static final UUID VIEWER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440164");
  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440165");
  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440168");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440169");
  private static final UUID SOURCE_TWO_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440170");

  private static final Authentication USER_AUTH = TestAuthentications.oidcUser(USER_ID);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private IamLayerRepository layerRepository;
  @Autowired private IamLayerMembersRepository layerMembersRepository;
  @Autowired private IamServerLayerRepository serverLayerRepository;
  @Autowired private IamServerUserRepository serverUserRepository;

  @MockitoBean private UserService userService;
  @MockitoBean private NotificationWorker notificationWorker;
  @MockitoBean private ConnectService connectService;
  @MockitoBean private SocketReconciliationNudgePublisher nudgePublisher;
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

    jdbcTemplate.update("DELETE FROM iam.server_user_access");
    layerFixtures.reset();
    connectionFixtures.reset();
    connectionFixtures.seedDefaultMapper(MAPPER_ID);
    seedUser(USER_ID);

    when(userService.exists(any(UUID.class))).thenReturn(true);
    when(userService.get(any(UUID.class)))
        .thenAnswer(invocation -> internalUser(invocation.getArgument(0)));
    when(sourceRuntimeObservationReader.findFreshForInstance(any(UUID.class), anyString()))
        .thenAnswer(
            invocation -> {
              var sourceId = invocation.getArgument(0, UUID.class);
              if (sourceId.equals(SOURCE_ID)) {
                return Optional.of(runtimeStatus(sourceId, "connect-1", ConnectorState.CONNECTED));
              }
              if (sourceId.equals(SOURCE_TWO_ID)) {
                return Optional.of(runtimeStatus(sourceId, "connect-2", ConnectorState.STOPPED));
              }
              return Optional.empty();
            });
    when(connectInstanceResolver.getAllInstanceIds()).thenReturn(List.of("connect-1", "connect-2"));
    when(connectInstanceResolver.getRoutableInstances(any()))
        .thenReturn(List.of(new ConnectInstanceInfo("connect-1", Instant.now(), 0, "reg-1", 1)));
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

  // Happy path
  @Test
  void getSourceLayers_enrichesPersistedLayersWithRuntimeMetadata() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, "connect-1");
    var persistedLayerId =
        layerFixtures.persistLayerWithMembers("Operations", Map.of(USER_ID, LayerRole.OWNER));
    layerFixtures.addServerAccess(SOURCE_ID, persistedLayerId, ServerRole.ADMIN);

    var result =
        mockMvc
            .perform(
                get("/v1/sources/{sourceId}/layers", SOURCE_ID).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<Set<SourceLayer>>() {});
    assertEquals(1, layers.size());
    var sourceLayer = layers.iterator().next();
    assertEquals(SOURCE_ID, sourceLayer.getServerId());
    assertEquals("Alpha", sourceLayer.getServerName());
    assertEquals(ConnectorState.CONNECTED.name(), sourceLayer.getServerState());
    assertEquals(ServerRole.ADMIN, sourceLayer.getRole());
    assertEquals("Operations", sourceLayer.getLayer().getName());
    assertEquals(LayerAccessScope.PRIVATE, sourceLayer.getLayer().getAccessScope());
    assertNotNull(sourceLayer.getLastMessageAt());
  }

  @Test
  void getSourceUsers_returnsDirectUsersWithResolvedIdentity() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, "connect-1");
    grantDirectAccess(SOURCE_ID, VIEWER_ID, ServerRole.VIEWER);
    grantDirectAccess(SOURCE_ID, OWNER_ID, ServerRole.OWNER);

    var result =
        mockMvc
            .perform(get("/v1/sources/{sourceId}/users", SOURCE_ID).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var users = readResponse(result, new TypeReference<Set<SourceUser>>() {});
    assertEquals(2, users.size());

    var byId =
        users.stream().collect(java.util.stream.Collectors.toMap(SourceUser::getUserId, u -> u));
    assertSourceUser(byId.get(VIEWER_ID), SOURCE_ID, VIEWER_ID, ServerRole.VIEWER);
    assertSourceUser(byId.get(OWNER_ID), SOURCE_ID, OWNER_ID, ServerRole.OWNER);
  }

  @Test
  void updateSourceUsers_persistsRolesAndSendsNotifications() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, "connect-1");
    grantEffectiveSourceOwner(SOURCE_ID);
    var request =
        new UpdateSourcePermissionsRequest(Set.of(VIEWER_ID), Set.of(MEMBER_ID), Set.of(OWNER_ID));

    mockMvc
        .perform(
            post("/v1/sources/{sourceId}/users", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(get("/v1/sources/{sourceId}/users", SOURCE_ID).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var users = readResponse(result, new TypeReference<Set<SourceUser>>() {});
    assertEquals(3, users.size());
    var byId =
        users.stream().collect(java.util.stream.Collectors.toMap(SourceUser::getUserId, u -> u));
    assertEquals(ServerRole.VIEWER, byId.get(VIEWER_ID).getRole());
    assertEquals(ServerRole.ADMIN, byId.get(MEMBER_ID).getRole());
    assertEquals(ServerRole.OWNER, byId.get(OWNER_ID).getRole());

    var commandsCaptor =
        ArgumentCaptor.forClass(
            (Class<Collection<NotificationCommand>>) (Class<?>) Collection.class);
    verify(notificationWorker, org.mockito.Mockito.times(3)).sendAllAsync(commandsCaptor.capture());
    assertEquals(3, commandsCaptor.getAllValues().size());
    assertEquals(1, commandsCaptor.getAllValues().get(0).size());
    assertEquals(1, commandsCaptor.getAllValues().get(1).size());
    assertEquals(1, commandsCaptor.getAllValues().get(2).size());
  }

  @Test
  void updateSourceUsers_callerCannotGrantThemselvesOwnership() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, "connect-1");
    var request = new UpdateSourcePermissionsRequest(Set.of(), Set.of(), Set.of(USER_ID));

    mockMvc
        .perform(
            post("/v1/sources/{sourceId}/users", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
        .andExpect(status().isForbidden());

    assertEquals(0, serverUserRepository.findAllByServerId(SOURCE_ID).size());
  }

  @Test
  void updateSourceLayers_persistsAdminRolesAndSendsSingleNotificationPerRoleBucket()
      throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, "connect-1");
    grantEffectiveSourceOwner(SOURCE_ID);
    var viewerLayerId =
        layerFixtures.persistLayerWithMembers("Viewer", Map.of(USER_ID, LayerRole.OWNER));
    var adminLayerId =
        layerFixtures.persistLayerWithMembers("Admin", Map.of(USER_ID, LayerRole.OWNER));
    var ownerLayerId =
        layerFixtures.persistLayerWithMembers("Owner", Map.of(USER_ID, LayerRole.OWNER));

    var request =
        new UpdateSourcePermissionsRequest(
            Set.of(viewerLayerId), Set.of(adminLayerId), Set.of(ownerLayerId));

    mockMvc
        .perform(
            post("/v1/sources/{sourceId}/layers", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(
                get("/v1/sources/{sourceId}/layers", SOURCE_ID).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<Set<SourceLayer>>() {});
    var byLayerId =
        layers.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    sourceLayer -> sourceLayer.getLayer().getId(), sourceLayer -> sourceLayer));
    assertEquals(ServerRole.VIEWER, byLayerId.get(viewerLayerId).getRole());
    assertEquals(ServerRole.ADMIN, byLayerId.get(adminLayerId).getRole());
    assertEquals(ServerRole.OWNER, byLayerId.get(ownerLayerId).getRole());

    var commandsCaptor =
        ArgumentCaptor.forClass(
            (Class<Collection<NotificationCommand>>) (Class<?>) Collection.class);
    verify(notificationWorker, org.mockito.Mockito.times(3)).sendAllAsync(commandsCaptor.capture());
    assertEquals(3, commandsCaptor.getAllValues().size());
  }

  @Test
  void updateSourceLayers_effectiveSourceOwnerMayAttachToMemberLayer() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, "connect-1");
    var ownershipLayerId =
        layerFixtures.persistLayerWithMembers("Ownership", Map.of(USER_ID, LayerRole.MEMBER));
    layerFixtures.addServerAccess(SOURCE_ID, ownershipLayerId, ServerRole.OWNER);
    var targetLayerId =
        layerFixtures.persistLayerWithMembers("Target", Map.of(USER_ID, LayerRole.MEMBER));
    var request = new UpdateSourcePermissionsRequest(Set.of(targetLayerId), Set.of(), Set.of());

    mockMvc
        .perform(
            post("/v1/sources/{sourceId}/layers", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
        .andExpect(status().isNoContent());

    assertEquals(2, countSourceLayerRows(SOURCE_ID));
  }

  @Test
  void updateSourceLayers_memberWithoutSourceOwnershipIsForbidden() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, "connect-1");
    var targetLayerId =
        layerFixtures.persistLayerWithMembers("Target", Map.of(USER_ID, LayerRole.MEMBER));
    var request = new UpdateSourcePermissionsRequest(Set.of(targetLayerId), Set.of(), Set.of());

    mockMvc
        .perform(
            post("/v1/sources/{sourceId}/layers", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
        .andExpect(status().isForbidden());

    assertEquals(0, countSourceLayerRows(SOURCE_ID));
  }

  @Test
  void updateSourceLayers_layerManagerMayAttachAsAdminButCannotBootstrapSourceOwnership()
      throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, "connect-1");
    var targetLayerId =
        layerFixtures.persistLayerWithMembers("Target", Map.of(USER_ID, LayerRole.ADMIN));
    var attachRequest =
        new UpdateSourcePermissionsRequest(Set.of(), Set.of(targetLayerId), Set.of());

    mockMvc
        .perform(
            post("/v1/sources/{sourceId}/layers", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(attachRequest)))
        .andExpect(status().isNoContent());

    var selfGrantRequest = new UpdateSourcePermissionsRequest(Set.of(), Set.of(), Set.of(USER_ID));
    mockMvc
        .perform(
            post("/v1/sources/{sourceId}/users", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(selfGrantRequest)))
        .andExpect(status().isForbidden());

    assertEquals(1, countSourceLayerRows(SOURCE_ID));
    assertEquals(0, serverUserRepository.findAllByServerId(SOURCE_ID).size());
  }

  @Test
  void deleteSourceLayers_layerManagerMayRemoveManagedLayerRelationship() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, "connect-1");
    var layerId =
        layerFixtures.persistLayerWithMembers("Managed", Map.of(USER_ID, LayerRole.ADMIN));
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.ADMIN);

    mockMvc
        .perform(
            delete("/v1/sources/{sourceId}/layers", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Set.of(layerId))))
        .andExpect(status().isNoContent());

    assertEquals(0, countSourceLayerRows(SOURCE_ID));
  }

  @Test
  void deleteSourceLayers_effectiveSourceOwnerMayRemoveUnrelatedLayerRelationship()
      throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, "connect-1");
    grantEffectiveSourceOwner(SOURCE_ID);
    var unrelatedLayerId = layerFixtures.persistLayer("Unrelated");
    layerFixtures.addServerAccess(SOURCE_ID, unrelatedLayerId, ServerRole.VIEWER);

    mockMvc
        .perform(
            delete("/v1/sources/{sourceId}/layers", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Set.of(unrelatedLayerId))))
        .andExpect(status().isNoContent());

    assertEquals(1, countSourceLayerRows(SOURCE_ID));
  }

  @Test
  void deleteSourceLayers_memberWithoutSourceOwnershipIsForbiddenAndLeavesRelationship()
      throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, "connect-1");
    var layerId =
        layerFixtures.persistLayerWithMembers("Member", Map.of(USER_ID, LayerRole.MEMBER));
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.VIEWER);

    mockMvc
        .perform(
            delete("/v1/sources/{sourceId}/layers", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Set.of(layerId))))
        .andExpect(status().isForbidden());

    assertEquals(1, countSourceLayerRows(SOURCE_ID));
  }

  // Access edge cases
  @Test
  void
      deleteSourceUsers_whenUserIsOnlyIndirectMember_returnsBadRequestAndLeavesDirectStateUntouched()
          throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Alpha", MAPPER_ID, true, "connect-1");
    grantEffectiveSourceOwner(SOURCE_ID);
    var indirectLayerId =
        layerFixtures.persistLayerWithMembers("Indirect", Map.of(MEMBER_ID, LayerRole.MEMBER));
    layerFixtures.addServerAccess(SOURCE_ID, indirectLayerId, ServerRole.VIEWER);

    var result =
        mockMvc
            .perform(
                delete("/v1/sources/{sourceId}/users", SOURCE_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(Set.of(MEMBER_ID))))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("SERVER-ACCESS-EXCEPTION-1006", error.code());
    assertEquals(0, serverUserRepository.findAllByServerId(SOURCE_ID).size());
  }

  @Test
  void updateSourceLayers_whenSourceIsArchived_returnsConflictAndDoesNotChangeRows()
      throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Archived Source", MAPPER_ID, true, "connect-1");
    dataSourceRepository.archive(SOURCE_ID, USER_ID);
    var layerId = layerFixtures.persistLayerWithMembers("Layer", Map.of(USER_ID, LayerRole.OWNER));
    var request = new UpdateSourcePermissionsRequest(Set.of(layerId), Set.of(), Set.of());
    var initialCount = countSourceLayerRows(SOURCE_ID);

    mockMvc
        .perform(
            post("/v1/sources/{sourceId}/layers", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
        .andExpect(status().isConflict());

    assertEquals(initialCount, countSourceLayerRows(SOURCE_ID));
  }

  @Test
  void sourceLayerMutations_whenTargetLayerIsArchived_returnConflictAndDoNotChangeRows()
      throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Active Source", MAPPER_ID, true, "connect-1");
    var activeLayerId =
        layerFixtures.persistLayerWithMembers("Active", Map.of(USER_ID, LayerRole.OWNER));
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers("Archived", Map.of(USER_ID, LayerRole.OWNER));
    jdbcTemplate.update(
        """
        UPDATE iam.layer
        SET archived_at = now(),
            archived_by = ?,
            lifecycle_state = 'ARCHIVED'::iam.layer_lifecycle_state
        WHERE id = ?
        """,
        USER_ID,
        archivedLayerId);
    var request =
        new UpdateSourcePermissionsRequest(
            Set.of(activeLayerId, archivedLayerId), Set.of(), Set.of());
    var initialCount = countSourceLayerRows(SOURCE_ID);

    mockMvc
        .perform(
            post("/v1/sources/{sourceId}/layers", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            delete("/v1/sources/{sourceId}/layers", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Set.of(activeLayerId, archivedLayerId))))
        .andExpect(status().isConflict());

    assertEquals(initialCount, countSourceLayerRows(SOURCE_ID));
  }

  @Test
  void sourceUsers_whenSourceIsArchived_canStillBeUpdatedAndDeleted() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Archived Source", MAPPER_ID, true, "connect-1");
    grantEffectiveSourceOwner(SOURCE_ID);
    dataSourceRepository.archive(SOURCE_ID, USER_ID);
    var request = new UpdateSourcePermissionsRequest(Set.of(VIEWER_ID), Set.of(), Set.of());

    mockMvc
        .perform(
            post("/v1/sources/{sourceId}/users", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
        .andExpect(status().isNoContent());

    var updatedResult =
        mockMvc
            .perform(get("/v1/sources/{sourceId}/users", SOURCE_ID).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var users = readResponse(updatedResult, new TypeReference<Set<SourceUser>>() {});
    assertEquals(1, users.size());
    assertEquals(VIEWER_ID, users.iterator().next().getUserId());

    mockMvc
        .perform(
            delete("/v1/sources/{sourceId}/users", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Set.of(VIEWER_ID))))
        .andExpect(status().isNoContent());

    var afterDelete =
        mockMvc
            .perform(get("/v1/sources/{sourceId}/users", SOURCE_ID).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(0, readResponse(afterDelete, new TypeReference<Set<SourceUser>>() {}).size());
  }

  @Test
  void getSourceLayers_whenSourceIsArchived_stillReturnsPersistedRelationships() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Archived Source", MAPPER_ID, true, "connect-1");
    dataSourceRepository.archive(SOURCE_ID, USER_ID);
    var layerId =
        layerFixtures.persistLayerWithMembers("Operations", Map.of(USER_ID, LayerRole.OWNER));
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.ADMIN);

    var result =
        mockMvc
            .perform(
                get("/v1/sources/{sourceId}/layers", SOURCE_ID).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var layers = readResponse(result, new TypeReference<Set<SourceLayer>>() {});
    assertEquals(1, layers.size());
    assertEquals(layerId, layers.iterator().next().getLayer().getId());
  }

  @Test
  void getSourceUsers_whenSourceIsArchived_stillReturnsDirectAccessRows() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Archived Source", MAPPER_ID, true, "connect-1");
    dataSourceRepository.archive(SOURCE_ID, USER_ID);
    grantDirectAccess(SOURCE_ID, VIEWER_ID, ServerRole.VIEWER);

    var result =
        mockMvc
            .perform(get("/v1/sources/{sourceId}/users", SOURCE_ID).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var users = readResponse(result, new TypeReference<Set<SourceUser>>() {});
    assertEquals(1, users.size());
    assertEquals(VIEWER_ID, users.iterator().next().getUserId());
  }

  @Test
  void updateSourceLayers_whenSourceIsArchived_returnsConflict() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Archived Source", MAPPER_ID, true, "connect-1");
    dataSourceRepository.archive(SOURCE_ID, USER_ID);
    var layerId = layerFixtures.persistLayerWithMembers("Layer", Map.of(USER_ID, LayerRole.OWNER));
    var request = new UpdateSourcePermissionsRequest(Set.of(layerId), Set.of(), Set.of());

    mockMvc
        .perform(
            post("/v1/sources/{sourceId}/layers", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)))
        .andExpect(status().isConflict());
  }

  @Test
  void deleteSourceLayers_whenSourceIsArchived_succeedsAndRemovesRelationship() throws Exception {
    connectionFixtures.persistTcpSource(SOURCE_ID, "Archived Source", MAPPER_ID, true, "connect-1");
    var layerId = layerFixtures.persistLayerWithMembers("Layer", Map.of(USER_ID, LayerRole.OWNER));
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.ADMIN);
    dataSourceRepository.archive(SOURCE_ID, USER_ID);

    mockMvc
        .perform(
            delete("/v1/sources/{sourceId}/layers", SOURCE_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Set.of(layerId))))
        .andExpect(status().isNoContent());

    assertEquals(0, countSourceLayerRows(SOURCE_ID));
  }

  private void grantDirectAccess(UUID sourceId, UUID userId, ServerRole role) {
    serverUserRepository.saveAndFlush(new ServerUserAccessEntity(sourceId, userId, role));
  }

  private void grantEffectiveSourceOwner(UUID sourceId) {
    var ownerLayerId =
        layerFixtures.persistLayerWithMembers(
            "Source owner context", Map.of(USER_ID, LayerRole.MEMBER));
    layerFixtures.addServerAccess(sourceId, ownerLayerId, ServerRole.OWNER);
  }

  private int countSourceLayerRows(UUID sourceId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM iam.server_layer_access WHERE server_id = ?",
        Integer.class,
        sourceId);
  }

  private static RuntimeStatus runtimeStatus(
      UUID sourceId, String instanceId, ConnectorState state) {
    return new RuntimeStatus(sourceId, state, Instant.parse("2026-03-16T12:00:00Z"), instanceId);
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

  private static void assertSourceUser(
      SourceUser user, UUID expectedSourceId, UUID expectedUserId, ServerRole expectedRole) {
    assertNotNull(user);
    assertEquals(expectedSourceId, user.getServerId());
    assertEquals(expectedUserId, user.getUserId());
    assertEquals(expectedRole, user.getRole());
    assertEquals("Test", user.getFirstName());
    assertEquals("User", user.getLastName());
    assertEquals("user-" + expectedUserId, user.getUsername());
  }
}
