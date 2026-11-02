// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.LayerFixtures;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.technical.gateway.notification.NotificationWorker;
import com.opencop.core.api.general.technical.model.CloneLayerRequest;
import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.iam.layer.LayerPatchRequest;
import com.opencop.core.sdk.model.iam.layer.UpdateLayerRoleRequest;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationReader;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupLayerMemberRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = LayerWebTestApplication.class)
class LayerAccessArchiveIT extends AbstractWebIT {

  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449001");
  private static final UUID MEMBER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449002");
  private static final UUID ARCHIVER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449003");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655449004");
  private static final UUID MEMBER_TO_ADD_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655449005");

  private static final Authentication OWNER_AUTH = TestAuthentications.oidcUser(OWNER_ID);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private LayersRepository layersRepository;
  @Autowired private LayerMemberRepository layerMemberRepository;
  @Autowired private IamLayerRepository iamLayerRepository;
  @Autowired private IamLayerMembersRepository iamLayerMembersRepository;
  @Autowired private IamServerLayerRepository iamServerLayerRepository;
  @Autowired private ServerLayerRepository serverLayerRepository;
  @Autowired private IamGroupMembersRepository groupMembersRepository;
  @Autowired private IamGroupLayerMemberRepository groupLayerRepository;
  @Autowired private IamLayerGroupMembersRepository layerGroupMembersRepository;
  @Autowired private IamLayerGroupLayerRepository layerGroupLayerRepository;
  @Autowired private IamLayerGroupUserGroupRepository layerGroupUserGroupRepository;
  @Autowired private IamLayerGroupRepository iamLayerGroupRepository;

  @MockitoBean private UserService userService;
  @MockitoBean private NotificationWorker notificationWorker;
  @MockitoBean private ConnectInstanceResolver connectInstanceResolver;
  @MockitoBean private SourceRuntimeObservationReader sourceRuntimeObservationReader;

  private EffectiveLayerAccessService effectiveLayerAccessService;
  private LayerFixtures layerFixtures;

  @BeforeEach
  void setUp() throws Exception {
    layerFixtures =
        new LayerFixtures(
            jdbcTemplate, iamLayerRepository, iamLayerMembersRepository, iamServerLayerRepository);
    // Watchbox references FK-block layer deletion, so they go before the layer reset.
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_config_audit");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_layer");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox");
    layerFixtures.reset();
    jdbcTemplate.update("DELETE FROM iam.group_layer_member");
    jdbcTemplate.update("DELETE FROM iam.user_group_member");
    seedUser(ARCHIVER_ID);

    effectiveLayerAccessService =
        new EffectiveLayerAccessService(
            layersRepository,
            layerMemberRepository,
            groupMembersRepository,
            groupLayerRepository,
            layerGroupMembersRepository,
            layerGroupLayerRepository,
            layerGroupUserGroupRepository,
            iamLayerGroupRepository);
    when(userService.get(any(UUID.class)))
        .thenAnswer(invocation -> user(invocation.getArgument(0)));
  }

  @Test
  void getEffectiveRole_returnsRoleForArchivedLayerDirectMember() {
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers("archived-role", Map.of(MEMBER_ID, LayerRole.ADMIN));
    archiveLayer(archivedLayerId, ARCHIVER_ID);

    var result = effectiveLayerAccessService.getEffectiveRole(archivedLayerId, MEMBER_ID);

    assertEquals(Optional.of(LayerRole.ADMIN), result);
  }

  @Test
  void archivedLayer_patchReturnsConflictAndPreservesArchiveMetadata() throws Exception {
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers(
            "archived-workflow", Map.of(OWNER_ID, LayerRole.OWNER));
    archiveLayer(archivedLayerId, ARCHIVER_ID);
    var archivedAtBefore = loadArchivedAt(archivedLayerId);

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}", archivedLayerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new LayerPatchRequest("renamed-should-not-apply", null, null))))
        .andExpect(status().isConflict());

    assertEquals("archived-workflow", loadLayerName(archivedLayerId));
    assertEquals(ARCHIVER_ID, loadArchivedBy(archivedLayerId));
    var archivedAtAfter = loadArchivedAt(archivedLayerId);
    assertNotNull(archivedAtAfter);
    assertEquals(archivedAtBefore, archivedAtAfter);
  }

  @Test
  void archivedLayer_cloneReturnsConflictAndPreservesArchiveMetadata() throws Exception {
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers("archived-clone", Map.of(OWNER_ID, LayerRole.OWNER));
    archiveLayer(archivedLayerId, ARCHIVER_ID);
    var archivedAtBefore = loadArchivedAt(archivedLayerId);

    mockMvc
        .perform(
            post("/v1/layers/{sourceLayerId}/clone", archivedLayerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new CloneLayerRequest("clone-should-fail", Map.of(), null, false, false))))
        .andExpect(status().isConflict());

    assertEquals("archived-clone", loadLayerName(archivedLayerId));
    assertEquals(ARCHIVER_ID, loadArchivedBy(archivedLayerId));
    var archivedAtAfter = loadArchivedAt(archivedLayerId);
    assertNotNull(archivedAtAfter);
    assertEquals(archivedAtBefore, archivedAtAfter);
  }

  @Test
  void archivedLayer_repeatedArchiveReturnsConflictAndPreservesArchiveMetadata() throws Exception {
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers("archived-repeat", Map.of(OWNER_ID, LayerRole.OWNER));
    archiveLayer(archivedLayerId, ARCHIVER_ID);
    var archivedAtBefore = loadArchivedAt(archivedLayerId);

    mockMvc
        .perform(
            delete("/v1/layers/{layerId}", archivedLayerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH)))
        .andExpect(status().isConflict());

    assertEquals("archived-repeat", loadLayerName(archivedLayerId));
    assertEquals(ARCHIVER_ID, loadArchivedBy(archivedLayerId));
    var archivedAtAfter = loadArchivedAt(archivedLayerId);
    assertNotNull(archivedAtAfter);
    assertEquals(archivedAtBefore, archivedAtAfter);
  }

  @Test
  void archivedLayer_memberAddRemainsAllowed() throws Exception {
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers(
            "archived-members", Map.of(OWNER_ID, LayerRole.OWNER));
    archiveLayer(archivedLayerId, ARCHIVER_ID);

    mockMvc
        .perform(
            post("/v1/layers/{layerId}/members", archivedLayerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(Map.of(MEMBER_TO_ADD_ID, LayerRole.MEMBER))))
        .andExpect(status().isNoContent());

    var membersResult =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}/members", archivedLayerId)
                    .with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var members = readResponse(membersResult, new TypeReference<List<LayerMember>>() {});
    assertTrue(
        members.stream()
            .anyMatch(
                member ->
                    MEMBER_TO_ADD_ID.equals(member.getUserId())
                        && LayerRole.MEMBER.equals(member.getRole())));
  }

  @Test
  void archivedLayer_memberRoleUpdateRemainsAllowed() throws Exception {
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers(
            "archived-members-update", Map.of(OWNER_ID, LayerRole.OWNER));
    archiveLayer(archivedLayerId, ARCHIVER_ID);

    mockMvc
        .perform(
            post("/v1/layers/{layerId}/members", archivedLayerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(Map.of(MEMBER_TO_ADD_ID, LayerRole.MEMBER))))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            patch("/v1/layers/{layerId}/members", archivedLayerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new UpdateLayerRoleRequest(
                            Set.of(), Set.of(), Set.of(MEMBER_TO_ADD_ID), Set.of()))))
        .andExpect(status().isNoContent());

    var membersResult =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}/members", archivedLayerId)
                    .with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var members = readResponse(membersResult, new TypeReference<List<LayerMember>>() {});
    assertTrue(
        members.stream()
            .anyMatch(
                member ->
                    MEMBER_TO_ADD_ID.equals(member.getUserId())
                        && LayerRole.ADMIN.equals(member.getRole())));
  }

  @Test
  void archivedLayer_memberDeleteRemainsAllowedAndMembersReadReflectsChange() throws Exception {
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers(
            "archived-members-delete", Map.of(OWNER_ID, LayerRole.OWNER));
    archiveLayer(archivedLayerId, ARCHIVER_ID);

    mockMvc
        .perform(
            post("/v1/layers/{layerId}/members", archivedLayerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(Map.of(MEMBER_TO_ADD_ID, LayerRole.MEMBER))))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            delete("/v1/layers/{layerId}/members", archivedLayerId)
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Set.of(MEMBER_TO_ADD_ID))))
        .andExpect(status().isNoContent());

    var membersResult =
        mockMvc
            .perform(
                get("/v1/layers/{layerId}/members", archivedLayerId)
                    .with(authentication(OWNER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var members = readResponse(membersResult, new TypeReference<List<LayerMember>>() {});
    assertTrue(members.stream().noneMatch(member -> MEMBER_TO_ADD_ID.equals(member.getUserId())));
    assertTrue(members.stream().anyMatch(member -> OWNER_ID.equals(member.getUserId())));
  }

  @Test
  void getLayers_omitsArchivedLayerRelationsForActiveReadPath() {
    var activeLayer = layerFixtures.persistLayer("ops-active");
    var archivedLayer = layerFixtures.persistLayer("ops-archived");
    archiveLayer(archivedLayer, ARCHIVER_ID);
    layerFixtures.addServerAccess(SOURCE_ID, activeLayer, ServerRole.VIEWER);
    layerFixtures.addServerAccess(SOURCE_ID, archivedLayer, ServerRole.ADMIN);

    var layers = serverLayerRepository.getLayers(SOURCE_ID);

    assertTrue(layers.stream().anyMatch(layer -> activeLayer.equals(layer.getLayer().getId())));
    assertTrue(layers.stream().noneMatch(layer -> archivedLayer.equals(layer.getLayer().getId())));
  }

  @Test
  void getEffectiveRole_returnsStoredRoleForActiveLayerDirectMember() {
    var activeLayer =
        layerFixtures.persistLayerWithMembers("active-role", Map.of(MEMBER_ID, LayerRole.OWNER));

    var result = effectiveLayerAccessService.getEffectiveRole(activeLayer, MEMBER_ID);

    assertEquals(Optional.of(LayerRole.OWNER), result);
  }

  @Test
  void addLayers_persistsActiveLayerAssignments() throws Exception {
    var activeLayer = layerFixtures.persistLayer("to-assign");

    serverLayerRepository.addLayers(SOURCE_ID, Set.of(activeLayer), ServerRole.VIEWER);

    var rows = iamServerLayerRepository.findAllByServerAndLayer(SOURCE_ID, activeLayer);
    assertEquals(1, rows.size());
  }

  @Test
  void addLayers_skipsArchivedLayerAssignments() throws Exception {
    var archivedLayer = layerFixtures.persistLayer("to-archive");
    archiveLayer(archivedLayer, ARCHIVER_ID);

    serverLayerRepository.addLayers(SOURCE_ID, Set.of(archivedLayer), ServerRole.VIEWER);

    var rows = iamServerLayerRepository.findAllByServerAndLayer(SOURCE_ID, archivedLayer);
    assertTrue(rows.isEmpty());
  }

  private String loadLayerName(UUID layerId) {
    return jdbcTemplate.queryForObject(
        "SELECT name FROM iam.layer WHERE id = ?", String.class, layerId);
  }

  private UUID loadArchivedBy(UUID layerId) {
    return jdbcTemplate.queryForObject(
        "SELECT archived_by FROM iam.layer WHERE id = ?", UUID.class, layerId);
  }

  private Timestamp loadArchivedAt(UUID layerId) {
    return jdbcTemplate.queryForObject(
        "SELECT archived_at FROM iam.layer WHERE id = ?", Timestamp.class, layerId);
  }

  @Test
  void activeLayer_archiveBlockedByActiveWatchboxReturnsConflictAndStaysActive() throws Exception {
    seedUser(OWNER_ID);
    var layerId =
        layerFixtures.persistLayerWithMembers(
            "watchbox-blocked", Map.of(OWNER_ID, LayerRole.OWNER));
    jdbcTemplate.update(
        "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)",
        insertWatchbox(),
        layerId);

    mockMvc
        .perform(
            delete("/v1/layers/{layerId}", layerId).with(csrf()).with(authentication(OWNER_AUTH)))
        .andExpect(status().isConflict());

    assertEquals(
        "ACTIVE",
        jdbcTemplate.queryForObject(
            "SELECT lifecycle_state FROM iam.layer WHERE id = ?", String.class, layerId));
  }

  @Test
  void archivedLayer_watchboxReferenceRejectedWithMigrationOwnedSqlState() {
    var archivedLayerId =
        layerFixtures.persistLayerWithMembers("watchbox-ref", Map.of(OWNER_ID, LayerRole.OWNER));
    archiveLayer(archivedLayerId, ARCHIVER_ID);
    var watchboxId = insertWatchbox();

    var thrown =
        assertThrows(
            DataAccessException.class,
            () ->
                jdbcTemplate.update(
                    "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)",
                    watchboxId,
                    archivedLayerId));

    // GlobalExceptionManager translates by these migration-owned SQLSTATEs; the identity is the
    // contract, not the wrapper type Spring happens to produce.
    assertEquals("23U01", sqlState(thrown));
  }

  @Test
  void overlappingWatchboxReferenceAndLayerArchiveCommitExactlyOneWinner() throws Exception {
    // The READ COMMITTED interleaving V37's deferred triggers exist to close: the reference
    // INSERT's FK lock (FOR KEY SHARE) does not conflict with the archive UPDATE's row lock
    // (FOR NO KEY UPDATE), so both transactions validate clean snapshots while overlapping. The
    // insert's commit-time trigger must then block on the archive's row lock and fail once the
    // archive commits. Under true overlap the archive always wins — the insert-wins ordering is
    // only reachable sequentially and is covered by the two tests above.
    seedUser(OWNER_ID);
    var layerId =
        layerFixtures.persistLayerWithMembers("watchbox-race", Map.of(OWNER_ID, LayerRole.OWNER));
    var watchboxId = insertWatchbox();
    var dataSource = jdbcTemplate.getDataSource();
    assertNotNull(dataSource);

    try (var insertSide = dataSource.getConnection();
        var archiveSide = dataSource.getConnection()) {
      insertSide.setAutoCommit(false);
      archiveSide.setAutoCommit(false);
      try (var insert =
          insertSide.prepareStatement(
              "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)")) {
        insert.setObject(1, watchboxId);
        insert.setObject(2, layerId);
        insert.executeUpdate();
      }
      try (var archive =
          archiveSide.prepareStatement(
              """
              UPDATE iam.layer
              SET archived_at = now(),
                  archived_by = ?,
                  lifecycle_state = 'ARCHIVED'::iam.layer_lifecycle_state
              WHERE id = ?
              """)) {
        archive.setObject(1, ARCHIVER_ID);
        archive.setObject(2, layerId);
        archive.executeUpdate();
      }

      var insertCommit =
          CompletableFuture.runAsync(
              () -> {
                try {
                  insertSide.commit();
                } catch (SQLException exception) {
                  throw new CompletionException(exception);
                }
              });
      // The deferred watchbox-side trigger locks the layer row FOR NO KEY UPDATE, so the commit
      // must wait behind the uncommitted archive rather than validating a stale snapshot.
      Thread.sleep(500);
      assertFalse(insertCommit.isDone(), "reference commit must block behind the archive's lock");

      archiveSide.commit();
      var thrown = assertThrows(CompletionException.class, insertCommit::join);
      assertEquals("23U01", sqlState(thrown));
    }

    assertEquals(
        "ARCHIVED",
        jdbcTemplate.queryForObject(
            "SELECT lifecycle_state FROM iam.layer WHERE id = ?", String.class, layerId));
    assertEquals(
        0,
        jdbcTemplate
            .queryForObject(
                "SELECT COUNT(*) FROM watchbox.watchbox_layer WHERE watchbox_id = ? AND layer_id = ?",
                Integer.class,
                watchboxId,
                layerId)
            .intValue());
  }

  private UUID insertWatchbox() {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO watchbox.watchbox (name, direction, polygon_json)
        VALUES ('lifecycle-guard', 'BOTH', '{}'::jsonb)
        RETURNING id
        """,
        UUID.class);
  }

  private static String sqlState(Throwable thrown) {
    for (var cause = thrown; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql) {
        return sql.getSQLState();
      }
    }
    return null;
  }

  private void archiveLayer(UUID layerId, UUID archivedBy) {
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

  private InternalUser user(UUID userId) {
    return new InternalUser(
        userId,
        userId.toString(),
        "user-" + userId.toString().substring(0, 8),
        "test-issuer",
        "user-" + userId.toString().substring(0, 8),
        "user+" + userId + "@example.test",
        "Test",
        "User",
        "Test User",
        true,
        false,
        0,
        0,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"),
        null);
  }
}
