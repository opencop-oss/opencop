// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.iam;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class EffectiveSourceAccessFunctionTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  private EmbeddedPostgres postgres;
  private NamedParameterJdbcTemplate jdbc;

  @BeforeEach
  void setUp() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    migrateAll(postgres.getPostgresDatabase());
    jdbc = new NamedParameterJdbcTemplate(postgres.getPostgresDatabase());
  }

  @AfterEach
  void tearDown() throws Exception {
    postgres.close();
  }

  @Test
  void effectiveSourceRoles_returnsDirectGrantRow() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertServerUserAccess(sourceId, userId, "ADMIN");

    var rows =
        jdbc.queryForList(
            """
            SELECT source_id, role::text AS role, via
            FROM iam.effective_source_roles(:userId, :sourceId, false)
            """,
            Map.of("userId", userId, "sourceId", sourceId));

    assertThat(rows)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("source_id")).isEqualTo(sourceId);
              assertThat(row.get("role")).isEqualTo("ADMIN");
              assertThat(row.get("via")).isEqualTo("DIRECT");
            });
  }

  @Test
  void effectiveSourceRoles_returnsCollapsedRowsForSourceGrantPathsAndLayerReachability() {
    var userId = UUID.randomUUID();
    var userGroupId = UUID.randomUUID();
    insertUser(userId);
    insertUserGroup(userGroupId, "source-function-user-group");
    insertUserGroupMember(userGroupId, userId);
    var directSourceId = insertSourceWithDirectAccess(userId, "VIEWER");
    var directGroupSourceId = insertSourceWithDirectDataSourceGroupAccess(userId, "ADMIN");
    var userGroupSourceId = insertSourceWithUserGroupDataSourceGroupAccess(userGroupId, "OWNER");
    var directLayerSourceId = insertSourceWithLayerAccess(userId, "VIEWER");
    var userGroupLayerSourceId = insertSourceWithUserGroupLayerAccess(userGroupId, "ADMIN");
    var layerGroupSourceId = insertSourceWithLayerGroupAccess(userId, "ADMIN");
    var userGroupLayerGroupSourceId =
        insertSourceWithUserGroupLayerGroupAccess(userGroupId, "VIEWER");
    var globalLayerSourceId = insertSourceWithGlobalLayerAccess("VIEWER");
    var globalLayerGroupSourceId = insertSourceWithGlobalLayerGroupDataSourceAccess("OWNER");

    var rows = effectiveRows(userId);

    assertThat(rows)
        .containsEntry(directSourceId, new EffectiveRow("VIEWER", "DIRECT"))
        .containsEntry(directGroupSourceId, new EffectiveRow("ADMIN", "INDIRECT"))
        .containsEntry(userGroupSourceId, new EffectiveRow("OWNER", "INDIRECT"))
        .containsEntry(directLayerSourceId, new EffectiveRow("VIEWER", "INDIRECT"))
        .containsEntry(userGroupLayerSourceId, new EffectiveRow("ADMIN", "INDIRECT"))
        .containsEntry(layerGroupSourceId, new EffectiveRow("ADMIN", "INDIRECT"))
        .containsEntry(userGroupLayerGroupSourceId, new EffectiveRow("VIEWER", "INDIRECT"))
        .containsEntry(globalLayerSourceId, new EffectiveRow("VIEWER", "INDIRECT"))
        .containsEntry(globalLayerGroupSourceId, new EffectiveRow("OWNER", "INDIRECT"));
  }

  @Test
  void effectiveSourceRoles_ignoresInactiveLayerDerivedSourceAccess() {
    var userId = UUID.randomUUID();
    var activeSourceId = UUID.randomUUID();
    var inactiveSourceId = UUID.randomUUID();
    var activeLayerId = UUID.randomUUID();
    var inactiveLayerId = UUID.randomUUID();
    insertUser(userId);
    insertSource(activeSourceId);
    insertSource(inactiveSourceId);
    insertLayer(activeLayerId, "active-function-layer");
    insertLayer(inactiveLayerId, "pending-function-layer");
    insertLayerMember(activeLayerId, userId);
    insertLayerMember(inactiveLayerId, userId);
    insertServerLayerAccess(activeSourceId, activeLayerId, "VIEWER");
    insertServerLayerAccess(inactiveSourceId, inactiveLayerId, "ADMIN");
    updateLayerLifecycle(inactiveLayerId, "PENDING_MERGE");

    assertThat(effectiveRows(userId))
        .containsEntry(activeSourceId, new EffectiveRow("VIEWER", "INDIRECT"))
        .doesNotContainKey(inactiveSourceId);
  }

  @Test
  void effectiveSourceRoles_collapsesHighestRoleAndBothViaAcrossOverlappingPaths() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertServerUserAccess(sourceId, userId, "VIEWER");
    insertDataSourceGroup(groupId, "overlapping-function-source-group");
    insertDataSourceGroupMember(groupId, userId, "MEMBER");
    insertDataSourceGroupDataSource(groupId, sourceId, "ADMIN");

    var rows = effectiveRows(userId);

    assertThat(rows).containsEntry(sourceId, new EffectiveRow("ADMIN", "BOTH"));
  }

  @Test
  void effectiveSourceRoles_appliesSourceNarrowingAndArchivedSourceMode() {
    var userId = UUID.randomUUID();
    var activeSourceId = UUID.randomUUID();
    var otherSourceId = UUID.randomUUID();
    var archivedSourceId = UUID.randomUUID();
    var archivedBy = UUID.randomUUID();
    insertUser(userId);
    insertUser(archivedBy);
    insertSource(activeSourceId);
    insertSource(otherSourceId);
    insertSource(archivedSourceId);
    insertServerUserAccess(activeSourceId, userId, "ADMIN");
    insertServerUserAccess(otherSourceId, userId, "VIEWER");
    insertServerUserAccess(archivedSourceId, userId, "OWNER");
    archiveSource(archivedSourceId, archivedBy);

    assertThat(effectiveRows(userId, activeSourceId, false))
        .containsExactly(Map.entry(activeSourceId, new EffectiveRow("ADMIN", "DIRECT")));
    assertThat(effectiveRows(userId, archivedSourceId, false)).isEmpty();
    assertThat(effectiveRowsIncludingArchived(userId))
        .containsEntry(archivedSourceId, new EffectiveRow("OWNER", "DIRECT"));
  }

  private void insertServerUserAccess(UUID sourceId, UUID userId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.server_user_access(server_id, user_id, role)
        VALUES (:sourceId, :userId, CAST(:role AS iam.server_role))
        """,
        Map.of("sourceId", sourceId, "userId", userId, "role", role));
  }

  private UUID insertSourceWithDirectAccess(UUID userId, String role) {
    var sourceId = UUID.randomUUID();
    insertSource(sourceId);
    insertServerUserAccess(sourceId, userId, role);
    return sourceId;
  }

  private UUID insertSourceWithDirectDataSourceGroupAccess(UUID userId, String sourceRole) {
    var sourceId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    insertSource(sourceId);
    insertDataSourceGroup(groupId, "direct-function-source-group-" + groupId);
    insertDataSourceGroupMember(groupId, userId, "MEMBER");
    insertDataSourceGroupDataSource(groupId, sourceId, sourceRole);
    return sourceId;
  }

  private UUID insertSourceWithUserGroupDataSourceGroupAccess(UUID userGroupId, String sourceRole) {
    var sourceId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    insertSource(sourceId);
    insertDataSourceGroup(groupId, "user-group-function-source-group-" + groupId);
    insertDataSourceGroupUserGroup(groupId, userGroupId, "MEMBER");
    insertDataSourceGroupDataSource(groupId, sourceId, sourceRole);
    return sourceId;
  }

  private UUID insertSourceWithLayerAccess(UUID userId, String sourceRole) {
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, "direct-function-layer-" + layerId);
    insertLayerMember(layerId, userId);
    insertServerLayerAccess(sourceId, layerId, sourceRole);
    return sourceId;
  }

  private UUID insertSourceWithUserGroupLayerAccess(UUID userGroupId, String sourceRole) {
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, "user-group-function-layer-" + layerId);
    insertGroupLayerMember(userGroupId, layerId, "MEMBER");
    insertServerLayerAccess(sourceId, layerId, sourceRole);
    return sourceId;
  }

  private UUID insertSourceWithLayerGroupAccess(UUID userId, String sourceRole) {
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, "layer-group-function-layer-" + layerId);
    insertLayerGroup(layerGroupId, "function-layer-group-" + layerGroupId);
    insertLayerGroupMember(layerGroupId, userId, "MEMBER");
    insertLayerGroupLayer(layerGroupId, layerId, "MEMBER");
    insertServerLayerAccess(sourceId, layerId, sourceRole);
    return sourceId;
  }

  private UUID insertSourceWithUserGroupLayerGroupAccess(UUID userGroupId, String sourceRole) {
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, "user-group-layer-group-function-layer-" + layerId);
    insertLayerGroup(layerGroupId, "user-group-function-layer-group-" + layerGroupId);
    insertLayerGroupUserGroup(layerGroupId, userGroupId, "MEMBER");
    insertLayerGroupLayer(layerGroupId, layerId, "MEMBER");
    insertServerLayerAccess(sourceId, layerId, sourceRole);
    return sourceId;
  }

  private UUID insertSourceWithGlobalLayerAccess(String sourceRole) {
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, "global-function-layer-" + layerId, "GLOBAL");
    insertServerLayerAccess(sourceId, layerId, sourceRole);
    return sourceId;
  }

  private UUID insertSourceWithGlobalLayerGroupDataSourceAccess(String sourceRole) {
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    var dataSourceGroupId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, "global-layer-group-function-layer-" + layerId);
    insertLayerGroup(layerGroupId, "global-function-layer-group-" + layerGroupId, "GLOBAL");
    insertLayerGroupLayer(layerGroupId, layerId, "MEMBER");
    insertDataSourceGroup(dataSourceGroupId, "global-layer-group-function-source-group");
    insertDataSourceGroupDataSource(dataSourceGroupId, sourceId, sourceRole);
    insertLayerDataSourceGroup(layerId, dataSourceGroupId);
    return sourceId;
  }

  private Map<UUID, EffectiveRow> effectiveRows(UUID userId) {
    return effectiveRows(
        """
        SELECT source_id, role::text AS role, via
        FROM iam.effective_source_roles(:userId)
        """,
        Map.of("userId", userId));
  }

  private Map<UUID, EffectiveRow> effectiveRows(
      UUID userId, UUID sourceId, boolean includeArchived) {
    return effectiveRows(
        """
        SELECT source_id, role::text AS role, via
        FROM iam.effective_source_roles(:userId, :sourceId, :includeArchived)
        """,
        Map.of("userId", userId, "sourceId", sourceId, "includeArchived", includeArchived));
  }

  private Map<UUID, EffectiveRow> effectiveRowsIncludingArchived(UUID userId) {
    return effectiveRows(
        """
        SELECT source_id, role::text AS role, via
        FROM iam.effective_source_roles(:userId, NULL, true)
        """,
        Map.of("userId", userId));
  }

  private Map<UUID, EffectiveRow> effectiveRows(String sql, Map<String, ?> params) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
    var result = new HashMap<UUID, EffectiveRow>();
    for (var row : rows) {
      result.put(
          (UUID) row.get("source_id"),
          new EffectiveRow((String) row.get("role"), (String) row.get("via")));
    }
    return result;
  }

  private void insertDataSourceGroup(UUID groupId, String name) {
    jdbc.update(
        "INSERT INTO iam.data_source_group(id, name) VALUES (:id, :name)",
        Map.of("id", groupId, "name", name));
  }

  private void insertDataSourceGroupMember(UUID groupId, UUID userId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.data_source_group_member(data_source_group_id, user_id, role)
        VALUES (:groupId, :userId, CAST(:role AS iam.data_source_group_role))
        """,
        Map.of("groupId", groupId, "userId", userId, "role", role));
  }

  private void insertDataSourceGroupUserGroup(UUID groupId, UUID userGroupId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.data_source_group_user_group(data_source_group_id, user_group_id, role)
        VALUES (:groupId, :userGroupId, CAST(:role AS iam.data_source_group_role))
        """,
        Map.of("groupId", groupId, "userGroupId", userGroupId, "role", role));
  }

  private void insertDataSourceGroupDataSource(UUID groupId, UUID sourceId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.data_source_group_data_source(data_source_group_id, data_source_id, role)
        VALUES (:groupId, :sourceId, CAST(:role AS iam.server_role))
        """,
        Map.of("groupId", groupId, "sourceId", sourceId, "role", role));
  }

  private void insertLayer(UUID layerId, String name) {
    insertLayer(layerId, name, "PRIVATE");
  }

  private void insertLayer(UUID layerId, String name, String accessScope) {
    jdbc.update(
        """
        INSERT INTO iam.layer(id, name, access_scope)
        VALUES (:id, :name, CAST(:accessScope AS iam.layer_access_scope))
        """,
        Map.of("id", layerId, "name", name, "accessScope", accessScope));
  }

  private void insertLayerMember(UUID layerId, UUID userId) {
    jdbc.update(
        "INSERT INTO iam.layer_member(layer_id, user_id, role) VALUES (:layerId, :userId, 'MEMBER')",
        Map.of("layerId", layerId, "userId", userId));
  }

  private void insertGroupLayerMember(UUID userGroupId, UUID layerId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.group_layer_member(group_id, layer_id, role)
        VALUES (:userGroupId, :layerId, CAST(:role AS iam.layer_role))
        """,
        Map.of("userGroupId", userGroupId, "layerId", layerId, "role", role));
  }

  private void insertServerLayerAccess(UUID sourceId, UUID layerId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.server_layer_access(server_id, layer_id, role)
        VALUES (:sourceId, :layerId, CAST(:role AS iam.server_role))
        """,
        Map.of("sourceId", sourceId, "layerId", layerId, "role", role));
  }

  private void insertLayerGroup(UUID layerGroupId, String name) {
    insertLayerGroup(layerGroupId, name, "PRIVATE");
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

  private void insertLayerDataSourceGroup(UUID layerId, UUID groupId) {
    jdbc.update(
        """
        INSERT INTO iam.layer_data_source_group(layer_id, data_source_group_id)
        VALUES (:layerId, :groupId)
        """,
        Map.of("layerId", layerId, "groupId", groupId));
  }

  private void updateLayerLifecycle(UUID layerId, String lifecycleState) {
    jdbc.update(
        """
        UPDATE iam.layer
        SET lifecycle_state = CAST(:lifecycleState AS iam.layer_lifecycle_state)
        WHERE id = :id
        """,
        Map.of("lifecycleState", lifecycleState, "id", layerId));
  }

  private void insertSource(UUID sourceId) {
    var mapperId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO profiles.mapper(id, qualifier)
        VALUES (:id, 'INGRESS'::profiles.mapper_qualifier)
        ON CONFLICT (id) DO NOTHING
        """,
        Map.of("id", mapperId));
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper(id, version, name, mapper)
        VALUES (:id, 1, :name, '{}'::jsonb)
        """,
        Map.of("id", mapperId, "name", "mapper-" + mapperId));
    NativeIntegrationFixture.seed(jdbc.getJdbcOperations());
    jdbc.update(
        """
        INSERT INTO profiles.data_source(
          id, name, tenant, provider, access, ingress_mapper_id,
          connection_spec, integration_id, connection_schema_version
        )
        VALUES(
          :id, :name, 'default', 'TCP'::profiles.transport_kind, 'R'::profiles.access_mode,
          :mapperId, '{}'::jsonb,
          :integrationId, :schemaVersion
        )
        """,
        Map.of(
            "id",
            sourceId,
            "name",
            "source-" + sourceId,
            "mapperId",
            mapperId,
            "integrationId",
            NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
            "schemaVersion",
            NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion()));
  }

  private void archiveSource(UUID sourceId, UUID archivedBy) {
    jdbc.update(
        """
        UPDATE profiles.data_source
        SET archived_at = now(),
            archived_by = :archivedBy
        WHERE id = :id
        """,
        Map.of("archivedBy", archivedBy, "id", sourceId));
  }

  private void insertUser(UUID userId) {
    jdbc.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (:id, :idpUserId, :username, :issuer, :username, :email, :firstName, :lastName, :displayName)
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

  private record EffectiveRow(String role, String via) {}
}
