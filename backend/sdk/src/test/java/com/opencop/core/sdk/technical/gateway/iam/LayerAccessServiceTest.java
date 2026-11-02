// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class LayerAccessServiceTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  private EmbeddedPostgres postgres;
  private NamedParameterJdbcTemplate jdbc;
  private LayerAccessService layerAccessService;

  @BeforeEach
  void setUp() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    migrateAll(postgres.getPostgresDatabase());
    jdbc = new NamedParameterJdbcTemplate(postgres.getPostgresDatabase());
    layerAccessService = new LayerAccessService(jdbc);
  }

  @AfterEach
  void tearDown() throws Exception {
    postgres.close();
  }

  @Test
  void userRole_returnsNull_whenLayerMissing() {
    var userId = UUID.randomUUID();
    var layerId = UUID.randomUUID();

    assertNull(layerAccessService.userRole(userId, layerId));
    assertFalse(layerAccessService.userHasAccess(userId, layerId));
  }

  @Test
  void userRole_returnsNullForPrivateLayerWithoutMembership() {
    var userId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    insertLayer(layerId, "private-layer", "PRIVATE");

    assertNull(layerAccessService.userRole(userId, layerId));
    assertFalse(layerAccessService.userHasAccess(userId, layerId));
  }

  @Test
  void userRole_returnsMemberForGlobalLayerWithoutExplicitMembership() {
    var userId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    insertLayer(layerId, "global-layer", "GLOBAL");

    assertEquals(LayerRole.MEMBER, layerAccessService.userRole(userId, layerId));
    assertTrue(layerAccessService.userHasAccess(userId, layerId));
  }

  @Test
  void userRole_returnsHighestRoleAcrossDirectAndUserGroupMemberships() {
    var userId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    insertUser(userId);
    insertLayer(layerId, "direct-and-group-layer", "PRIVATE");
    insertLayerMember(layerId, userId, "MEMBER");
    insertUserGroup(groupId, "layer-user-group");
    insertUserGroupMember(groupId, userId);
    insertGroupLayerMember(groupId, layerId, "ADMIN");

    assertEquals(LayerRole.ADMIN, layerAccessService.userRole(userId, layerId));
  }

  @Test
  void userRole_returnsLayerGroupRoleForDirectLayerGroupMember() {
    var userId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    insertUser(userId);
    insertLayer(layerId, "direct-layer-group-layer", "PRIVATE");
    insertLayerGroup(layerGroupId, "direct-layer-group", "PRIVATE");
    insertLayerGroupMember(layerGroupId, userId, "MEMBER");
    insertLayerGroupLayer(layerGroupId, layerId, "EDITOR");

    assertEquals(LayerRole.EDITOR, layerAccessService.userRole(userId, layerId));
  }

  @Test
  void userRole_returnsLayerGroupRoleForUserGroupLayerGroupMember() {
    var userId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var userGroupId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    insertUser(userId);
    insertLayer(layerId, "user-group-layer-group-layer", "PRIVATE");
    insertUserGroup(userGroupId, "layer-group-user-group");
    insertUserGroupMember(userGroupId, userId);
    insertLayerGroup(layerGroupId, "user-group-layer-group", "PRIVATE");
    insertLayerGroupUserGroup(layerGroupId, userGroupId, "MEMBER");
    insertLayerGroupLayer(layerGroupId, layerId, "EDITOR");

    assertEquals(LayerRole.EDITOR, layerAccessService.userRole(userId, layerId));
  }

  @Test
  void userRole_returnsLayerGroupRoleForGlobalLayerGroup() {
    var userId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    insertLayer(layerId, "global-layer-group-layer", "PRIVATE");
    insertLayerGroup(layerGroupId, "global-layer-group", "GLOBAL");
    insertLayerGroupLayer(layerGroupId, layerId, "EDITOR");

    assertEquals(LayerRole.EDITOR, layerAccessService.userRole(userId, layerId));
    assertTrue(layerAccessService.userHasAccess(userId, layerId));
  }

  @Test
  void userRole_preservesArchivedLayerAccessForLayerAuthorization() {
    var userId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var archivedBy = UUID.randomUUID();
    insertUser(userId);
    insertUser(archivedBy);
    insertLayer(layerId, "archived-layer", "PRIVATE");
    insertLayerMember(layerId, userId, "MEMBER");
    archiveLayer(layerId, archivedBy);

    assertEquals(LayerRole.MEMBER, layerAccessService.userRole(userId, layerId));
    assertTrue(layerAccessService.userHasAccess(userId, layerId));
  }

  @Test
  void userRoles_returnsEmptyMapForEmptyRequest() {
    assertEquals(Map.of(), layerAccessService.userRoles(UUID.randomUUID(), Set.of()));
  }

  @Test
  void userRoles_answersEveryMembershipShapeInOneBulkRead() {
    var userId = UUID.randomUUID();
    var userGroupId = UUID.randomUUID();
    var directLayerGroupId = UUID.randomUUID();
    var transitiveLayerGroupId = UUID.randomUUID();
    var globalLayerGroupId = UUID.randomUUID();
    insertUser(userId);
    insertUserGroup(userGroupId, "bulk-user-group");
    insertUserGroupMember(userGroupId, userId);
    insertLayerGroup(directLayerGroupId, "bulk-direct-layer-group", "PRIVATE");
    insertLayerGroupMember(directLayerGroupId, userId, "MEMBER");
    insertLayerGroup(transitiveLayerGroupId, "bulk-transitive-layer-group", "PRIVATE");
    insertLayerGroupUserGroup(transitiveLayerGroupId, userGroupId, "MEMBER");
    insertLayerGroup(globalLayerGroupId, "bulk-global-layer-group", "GLOBAL");

    var directLayerId = UUID.randomUUID();
    insertLayer(directLayerId, "bulk-direct", "PRIVATE");
    insertLayerMember(directLayerId, userId, "EDITOR");

    var userGroupLayerId = UUID.randomUUID();
    insertLayer(userGroupLayerId, "bulk-user-group-layer", "PRIVATE");
    insertGroupLayerMember(userGroupId, userGroupLayerId, "ADMIN");

    var layerGroupLayerId = UUID.randomUUID();
    insertLayer(layerGroupLayerId, "bulk-layer-group-layer", "PRIVATE");
    insertLayerGroupLayer(directLayerGroupId, layerGroupLayerId, "EDITOR");

    var transitiveLayerId = UUID.randomUUID();
    insertLayer(transitiveLayerId, "bulk-transitive-layer", "PRIVATE");
    insertLayerGroupLayer(transitiveLayerGroupId, transitiveLayerId, "MEMBER");

    var globalGroupLayerId = UUID.randomUUID();
    insertLayer(globalGroupLayerId, "bulk-global-group-layer", "PRIVATE");
    insertLayerGroupLayer(globalLayerGroupId, globalGroupLayerId, "EDITOR");

    var globalLayerId = UUID.randomUUID();
    insertLayer(globalLayerId, "bulk-global-layer", "GLOBAL");

    var overlapLayerId = UUID.randomUUID();
    insertLayer(overlapLayerId, "bulk-overlap-layer", "PRIVATE");
    insertLayerMember(overlapLayerId, userId, "MEMBER");
    insertGroupLayerMember(userGroupId, overlapLayerId, "OWNER");

    var unreachableLayerId = UUID.randomUUID();
    insertLayer(unreachableLayerId, "bulk-unreachable-layer", "PRIVATE");

    var missingLayerId = UUID.randomUUID();

    var requested =
        Set.of(
            directLayerId,
            userGroupLayerId,
            layerGroupLayerId,
            transitiveLayerId,
            globalGroupLayerId,
            globalLayerId,
            overlapLayerId,
            unreachableLayerId,
            missingLayerId);

    var bulk = layerAccessService.userRoles(userId, requested);

    assertEquals(
        Map.of(
            directLayerId, LayerRole.EDITOR,
            userGroupLayerId, LayerRole.ADMIN,
            layerGroupLayerId, LayerRole.EDITOR,
            transitiveLayerId, LayerRole.MEMBER,
            globalGroupLayerId, LayerRole.EDITOR,
            globalLayerId, LayerRole.MEMBER,
            overlapLayerId, LayerRole.OWNER),
        bulk);

    // The bulk read must never drift from the single-layer read it consolidates.
    for (UUID layerId : requested) {
      assertEquals(layerAccessService.userRole(userId, layerId), bulk.get(layerId));
    }
  }

  private void insertLayer(UUID layerId, String name, String accessScope) {
    jdbc.update(
        """
        INSERT INTO iam.layer(id, name, access_scope)
        VALUES (:id, :name, CAST(:accessScope AS iam.layer_access_scope))
        """,
        Map.of("id", layerId, "name", name, "accessScope", accessScope));
  }

  private void insertLayerMember(UUID layerId, UUID userId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.layer_member(layer_id, user_id, role)
        VALUES (:layerId, :userId, CAST(:role AS iam.layer_role))
        """,
        Map.of("layerId", layerId, "userId", userId, "role", role));
  }

  private void insertGroupLayerMember(UUID groupId, UUID layerId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.group_layer_member(group_id, layer_id, role)
        VALUES (:groupId, :layerId, CAST(:role AS iam.layer_role))
        """,
        Map.of("groupId", groupId, "layerId", layerId, "role", role));
  }

  private void insertLayerGroup(UUID layerGroupId, String name, String accessScope) {
    jdbc.update(
        """
        INSERT INTO iam.layer_group(id, name, access_scope)
        VALUES (:id, :name, CAST(:accessScope AS iam.layer_group_access_scope))
        """,
        Map.of("id", layerGroupId, "name", name, "accessScope", accessScope));
  }

  private void insertLayerGroupMember(UUID layerGroupId, UUID userId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.layer_group_member(layer_group_id, user_id, role)
        VALUES (:layerGroupId, :userId, CAST(:role AS iam.layer_group_role))
        """,
        Map.of("layerGroupId", layerGroupId, "userId", userId, "role", role));
  }

  private void insertLayerGroupUserGroup(UUID layerGroupId, UUID userGroupId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.layer_group_user_group(layer_group_id, user_group_id, role)
        VALUES (:layerGroupId, :userGroupId, CAST(:role AS iam.layer_group_role))
        """,
        Map.of("layerGroupId", layerGroupId, "userGroupId", userGroupId, "role", role));
  }

  private void insertLayerGroupLayer(UUID layerGroupId, UUID layerId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.layer_group_layer(layer_group_id, layer_id, role)
        VALUES (:layerGroupId, :layerId, CAST(:role AS iam.layer_role))
        """,
        Map.of("layerGroupId", layerGroupId, "layerId", layerId, "role", role));
  }

  private void archiveLayer(UUID layerId, UUID archivedBy) {
    jdbc.update(
        """
        UPDATE iam.layer
        SET archived_at = now(),
            archived_by = :archivedBy,
            lifecycle_state = 'ARCHIVED'::iam.layer_lifecycle_state
        WHERE id = :id
        """,
        Map.of("archivedBy", archivedBy, "id", layerId));
  }

  private void insertUser(UUID userId) {
    jdbc.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (:id, :idpUserId, :username, :issuer, :username, :email, :firstName, :lastName, :displayName)
        ON CONFLICT (id) DO NOTHING
        """,
        Map.of(
            "id",
            userId,
            "idpUserId",
            "idp-" + userId,
            "username",
            "user-" + userId,
            "issuer",
            "https://issuer.test",
            "email",
            "user@example.test",
            "firstName",
            "Test",
            "lastName",
            "User",
            "displayName",
            "Test User"));
  }

  private void insertUserGroup(UUID groupId, String name) {
    jdbc.update(
        """
        INSERT INTO iam.user_group(id, name)
        VALUES (:id, :name)
        """,
        Map.of("id", groupId, "name", name));
  }

  private void insertUserGroupMember(UUID groupId, UUID userId) {
    jdbc.update(
        """
        INSERT INTO iam.user_group_member(group_id, user_id)
        VALUES (:groupId, :userId)
        """,
        Map.of("groupId", groupId, "userId", userId));
  }

  private static void migrateAll(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();
  }
}
