// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.watchbox.business.model.WatchboxLayerRole;
import com.opencop.core.api.watchbox.business.policy.WatchboxLayerAccessPolicy;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxLayerAccess;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.api.watchbox.technical.gateway.iam.PostgresWatchboxLayerAccess;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.iam.LayerAccessService;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Pins the effective-layer-role semantics of the set-based SQL read ({@link
 * PostgresWatchboxLayerAccess} over the reachable_layer_roles CTE): every membership shape the
 * reachability model supports is seeded once and must resolve to an exact expected role, strongest
 * role winning on overlap and unreachable layers absent from the map. The same seeds are also
 * compared against the live per-layer effective-role walk that still authorizes the layer
 * endpoints, so the two implementations cannot silently fork. A change this class catches is a
 * semantic fork of layer access, not a refactor.
 */
@ComponentWebIT(classes = WatchboxWebTestApplication.class)
class WatchboxLayerAccessSemanticsIT extends AbstractWebIT {

  private static final UUID CALLER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443001");
  private static final WatchboxCaller CALLER = new WatchboxCaller(CALLER_ID, false);
  private static final WatchboxCaller ADMIN_CALLER =
      new WatchboxCaller(UUID.fromString("550e8400-e29b-41d4-a716-446655443002"), true);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private NamedParameterJdbcTemplate namedJdbcTemplate;
  @Autowired private LayersRepository layersRepository;
  @Autowired private LayerManager<Layer, LayerMember> layerManager;

  private WatchboxLayerAccess sqlRead;

  private final Map<String, UUID> layersByShape = new HashMap<>();

  @BeforeEach
  void setUp() {
    sqlRead =
        new PostgresWatchboxLayerAccess(
            layersRepository, new LayerAccessService(namedJdbcTemplate));
    resetIam();
    seedMembershipShapes();
  }

  @Test
  void rolesForLayers_everyMembershipShape_resolvesItsExpectedRole() {
    Set<UUID> requested = Set.copyOf(layersByShape.values());

    Map<UUID, WatchboxLayerRole> roles = sqlRead.rolesForLayers(requested, CALLER);

    assertThat(roles)
        .isEqualTo(
            Map.of(
                layersByShape.get("direct"), WatchboxLayerRole.EDITOR,
                layersByShape.get("user-group"), WatchboxLayerRole.ADMIN,
                layersByShape.get("layer-group"), WatchboxLayerRole.EDITOR,
                layersByShape.get("user-group-layer-group"), WatchboxLayerRole.MEMBER,
                layersByShape.get("global-layer-group"), WatchboxLayerRole.EDITOR,
                layersByShape.get("global-layer"), WatchboxLayerRole.MEMBER,
                layersByShape.get("overlap-strongest-wins"), WatchboxLayerRole.OWNER,
                layersByShape.get("archived-direct"), WatchboxLayerRole.MEMBER));
    // Unreachable and missing layers are absent, not mapped to a null-ish role.
    assertThat(roles).doesNotContainKey(layersByShape.get("no-access"));
    assertThat(roles).doesNotContainKey(layersByShape.get("missing"));
  }

  /**
   * Cross-implementation drift guard: layer endpoints still authorize through the per-layer
   * effective-role walk ({@code LayerManager.getMemberRole}), while watchboxes read the
   * reachability CTE. The two must answer identically for every seeded membership shape, or
   * watchbox visibility silently forks from layer-surface authorization.
   */
  @Test
  void rolesForLayers_everyMembershipShape_agreesWithLiveEffectiveRoleWalk() {
    Set<UUID> requested = Set.copyOf(layersByShape.values());

    Map<UUID, WatchboxLayerRole> sqlRoles = sqlRead.rolesForLayers(requested, CALLER);

    Map<UUID, WatchboxLayerRole> walkRoles = new HashMap<>();
    for (UUID layerId : requested) {
      layerManager
          .getMemberRole(layerId, CALLER_ID)
          .ifPresent(role -> walkRoles.put(layerId, toWatchboxLayerRole(role)));
    }
    assertThat(sqlRoles).isEqualTo(walkRoles);
  }

  private static WatchboxLayerRole toWatchboxLayerRole(LayerRole role) {
    return switch (role) {
      case OWNER -> WatchboxLayerRole.OWNER;
      case ADMIN -> WatchboxLayerRole.ADMIN;
      case EDITOR -> WatchboxLayerRole.EDITOR;
      case MEMBER -> WatchboxLayerRole.MEMBER;
    };
  }

  @Test
  void decide_forAdminAndNoAccessCallers_returnsExpectedDecisions() {
    Set<UUID> requested = Set.of(layersByShape.get("direct"), layersByShape.get("no-access"));
    var policy = new WatchboxLayerAccessPolicy(sqlRead);

    var adminDecision = policy.decide(requested, ADMIN_CALLER);
    assertThat(adminDecision.fullyVisible()).isTrue();
    assertThat(adminDecision.managesAll()).isTrue();

    // The no-access layer breaks the all-layers predicate for the non-admin caller.
    var callerDecision = policy.decide(requested, CALLER);
    assertThat(callerDecision.fullyVisible()).isFalse();
    assertThat(callerDecision.managesAll()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = LayerRole.class,
      names = {"OWNER", "ADMIN"})
  void decide_globalLayers_honorsDelegatedManagement(LayerRole role) {
    var direct = layersByShape.get("global-layer");
    insertLayerMember(direct, CALLER_ID, role.name());

    var inherited = insertLayer("global-via-user-group", "GLOBAL");
    var userGroup = insertUserGroup("global-managers");
    insertUserGroupMember(userGroup, CALLER_ID);
    insertGroupLayerMember(userGroup, inherited, role.name());

    var viaGlobalGroup = insertLayer("global-via-global-layer-group", "GLOBAL");
    var layerGroup = insertLayerGroup("global-layer-managers", "GLOBAL");
    insertLayerGroupLayer(layerGroup, viaGlobalGroup, role.name());

    var requested = Set.of(direct, inherited, viaGlobalGroup);
    var expectedRole = toWatchboxLayerRole(role);
    assertThat(sqlRead.rolesForLayers(requested, CALLER))
        .isEqualTo(
            Map.of(direct, expectedRole, inherited, expectedRole, viaGlobalGroup, expectedRole));
    for (var id : requested) {
      assertThat(layerManager.getMemberRole(id, CALLER_ID)).contains(role);
    }
    var policy = new WatchboxLayerAccessPolicy(sqlRead);
    assertThat(policy.decide(requested, CALLER).managesAll()).isTrue();

    jdbcTemplate.update(
        "DELETE FROM iam.layer_member WHERE layer_id = ? AND user_id = ?", direct, CALLER_ID);
    var afterRevocation = policy.decide(requested, CALLER);
    assertThat(sqlRead.rolesForLayers(Set.of(direct), CALLER))
        .containsEntry(direct, WatchboxLayerRole.MEMBER);
    assertThat(afterRevocation.fullyVisible()).isTrue();
    assertThat(afterRevocation.managesAll()).isFalse();
  }

  /**
   * One layer per membership shape: direct member, user-group->layer, user->layer-group->layer,
   * user-group->layer-group->layer, GLOBAL layer group, GLOBAL layer, overlapping grants (strongest
   * role wins), archived layer with retained membership, unreachable private layer, and a
   * never-created id.
   */
  private void seedMembershipShapes() {
    insertUser(CALLER_ID);

    UUID userGroupId = insertUserGroup("semantics-user-group");
    insertUserGroupMember(userGroupId, CALLER_ID);
    UUID directLayerGroupId = insertLayerGroup("semantics-direct-layer-group", "PRIVATE");
    insertLayerGroupMember(directLayerGroupId, CALLER_ID);
    UUID transitiveLayerGroupId = insertLayerGroup("semantics-transitive-layer-group", "PRIVATE");
    insertLayerGroupUserGroup(transitiveLayerGroupId, userGroupId);
    UUID globalLayerGroupId = insertLayerGroup("semantics-global-layer-group", "GLOBAL");

    UUID direct = insertLayer("semantics-direct", "PRIVATE");
    insertLayerMember(direct, CALLER_ID, "EDITOR");
    layersByShape.put("direct", direct);

    UUID viaUserGroup = insertLayer("semantics-user-group-layer", "PRIVATE");
    insertGroupLayerMember(userGroupId, viaUserGroup, "ADMIN");
    layersByShape.put("user-group", viaUserGroup);

    UUID viaLayerGroup = insertLayer("semantics-layer-group-layer", "PRIVATE");
    insertLayerGroupLayer(directLayerGroupId, viaLayerGroup, "EDITOR");
    layersByShape.put("layer-group", viaLayerGroup);

    UUID viaBothGroups = insertLayer("semantics-transitive-layer", "PRIVATE");
    insertLayerGroupLayer(transitiveLayerGroupId, viaBothGroups, "MEMBER");
    layersByShape.put("user-group-layer-group", viaBothGroups);

    UUID viaGlobalLayerGroup = insertLayer("semantics-global-group-layer", "PRIVATE");
    insertLayerGroupLayer(globalLayerGroupId, viaGlobalLayerGroup, "EDITOR");
    layersByShape.put("global-layer-group", viaGlobalLayerGroup);

    UUID globalLayer = insertLayer("semantics-global-layer", "GLOBAL");
    layersByShape.put("global-layer", globalLayer);

    UUID overlap = insertLayer("semantics-overlap-layer", "PRIVATE");
    insertLayerMember(overlap, CALLER_ID, "MEMBER");
    insertGroupLayerMember(userGroupId, overlap, "OWNER");
    layersByShape.put("overlap-strongest-wins", overlap);

    UUID archived = insertLayer("semantics-archived-layer", "PRIVATE");
    insertLayerMember(archived, CALLER_ID, "MEMBER");
    jdbcTemplate.update(
        """
        UPDATE iam.layer
           SET lifecycle_state = 'ARCHIVED'::iam.layer_lifecycle_state,
               archived_at = now(),
               archived_by = ?
         WHERE id = ?
        """,
        CALLER_ID,
        archived);
    layersByShape.put("archived-direct", archived);

    layersByShape.put("no-access", insertLayer("semantics-no-access-layer", "PRIVATE"));
    layersByShape.put("missing", UUID.randomUUID());
  }

  private void resetIam() {
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_detection_heartbeat");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_config_audit");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_source_status");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_layer");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox");
    jdbcTemplate.update("DELETE FROM iam.layer_group_layer");
    jdbcTemplate.update("DELETE FROM iam.layer_group_member");
    jdbcTemplate.update("DELETE FROM iam.layer_group_user_group");
    jdbcTemplate.update("DELETE FROM iam.layer_group");
    jdbcTemplate.update("DELETE FROM iam.group_layer_member");
    jdbcTemplate.update("DELETE FROM iam.user_group_member");
    jdbcTemplate.update("DELETE FROM iam.user_group");
    jdbcTemplate.update("DELETE FROM iam.server_layer_access");
    jdbcTemplate.update("DELETE FROM iam.layer_member");
    jdbcTemplate.update("DELETE FROM iam.layer");
  }

  private UUID insertLayer(String name, String accessScope) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO iam.layer (id, name, access_scope) VALUES (?, ?, CAST(? AS iam.layer_access_scope))",
        id,
        name,
        accessScope);
    return id;
  }

  private void insertLayerMember(UUID layerId, UUID userId, String role) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer_member (layer_id, user_id, role) VALUES (?, ?, CAST(? AS iam.layer_role))",
        layerId,
        userId,
        role);
  }

  private UUID insertUserGroup(String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO iam.user_group (id, name) VALUES (?, ?)", id, name);
    return id;
  }

  private void insertUserGroupMember(UUID groupId, UUID userId) {
    jdbcTemplate.update(
        "INSERT INTO iam.user_group_member (group_id, user_id) VALUES (?, ?)", groupId, userId);
  }

  private void insertGroupLayerMember(UUID groupId, UUID layerId, String role) {
    jdbcTemplate.update(
        "INSERT INTO iam.group_layer_member (group_id, layer_id, role) VALUES (?, ?, CAST(? AS iam.layer_role))",
        groupId,
        layerId,
        role);
  }

  private UUID insertLayerGroup(String name, String accessScope) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO iam.layer_group (id, name, access_scope) VALUES (?, ?, CAST(? AS iam.layer_group_access_scope))",
        id,
        name,
        accessScope);
    return id;
  }

  private void insertLayerGroupMember(UUID layerGroupId, UUID userId) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer_group_member (layer_group_id, user_id, role) VALUES (?, ?, CAST('MEMBER' AS iam.layer_group_role))",
        layerGroupId,
        userId);
  }

  private void insertLayerGroupUserGroup(UUID layerGroupId, UUID userGroupId) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer_group_user_group (layer_group_id, user_group_id, role) VALUES (?, ?, CAST('MEMBER' AS iam.layer_group_role))",
        layerGroupId,
        userGroupId);
  }

  private void insertLayerGroupLayer(UUID layerGroupId, UUID layerId, String role) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer_group_layer (layer_group_id, layer_id, role) VALUES (?, ?, CAST(? AS iam.layer_role))",
        layerGroupId,
        layerId,
        role);
  }

  private void insertUser(UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
            id, idp_user_id, idp_username, idp_issuer, username, active, administrator, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, true, false, now(), now())
        ON CONFLICT (id) DO NOTHING
        """,
        userId,
        "idp-user-" + userId,
        "user-" + userId,
        "issuer",
        "user-" + userId);
  }
}
