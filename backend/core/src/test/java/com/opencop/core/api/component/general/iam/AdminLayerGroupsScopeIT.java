// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupCreateRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayersAssignRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;

@ComponentWebIT(classes = LayerGroupScopeWebTestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminLayerGroupsScopeIT extends AbstractWebIT {

  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-aa0000000001");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-aa0000000002");

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void seedUsers() {
    insertUser(jdbcTemplate, ADMIN_ID);
    insertUser(jdbcTemplate, USER_ID);
  }

  /**
   * The non-admin counterpart lives in {@code LayerGroupsEndpointIT} alongside the rest of that
   * route's gates; this suite keeps only what is specific to an admin exercising GLOBAL scope.
   */
  @Test
  void createLayerGroup_admin_globalScope_returns201AndPersistsGlobal() throws Exception {
    var request = new LayerGroupCreateRequest("GlobalByAdmin", null, LayerGroupAccessScope.GLOBAL);
    var body = objectMapper.writeValueAsString(request);

    var result =
        mockMvc
            .perform(
                post("/v1/layer-groups")
                    .with(csrf())
                    .with(authentication(adminAuthentication(ADMIN_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    var response = readResponse(result, LayerGroupResponse.class);
    assertNotNull(response.id());
    assertEquals(LayerGroupAccessScope.GLOBAL, response.accessScope());

    var scope =
        jdbcTemplate.queryForObject(
            "SELECT access_scope::text FROM iam.layer_group WHERE id = ?",
            String.class,
            response.id());
    assertEquals("GLOBAL", scope, "DB must store access_scope = GLOBAL");
  }

  @Test
  void patchLayerGroup_adminFlipsPrivateToGlobal_withPrivateLayers_returns409() throws Exception {
    // Create a PRIVATE group with a PRIVATE layer assigned
    var groupId = insertLayerGroup(jdbcTemplate, "PrivateWithLayers", "PRIVATE");
    insertLayerGroupMember(jdbcTemplate, groupId, ADMIN_ID, LayerGroupRole.OWNER.name());
    var layerId = insertLayer(jdbcTemplate, "PrivateLayer", "PRIVATE");
    insertLayerGroupLayer(jdbcTemplate, groupId, layerId);

    var patchRequest = new LayerGroupPatchRequest(null, null, LayerGroupAccessScope.GLOBAL);
    var body = objectMapper.writeValueAsString(patchRequest);

    mockMvc
        .perform(
            patch("/v1/layer-groups/{id}", groupId)
                .with(csrf())
                .with(authentication(adminAuthentication(ADMIN_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict());

    // Verify the group's access_scope was NOT changed
    var scope =
        jdbcTemplate.queryForObject(
            "SELECT access_scope::text FROM iam.layer_group WHERE id = ?", String.class, groupId);
    assertEquals("PRIVATE", scope, "access_scope must remain PRIVATE after rejected flip");
  }

  @Test
  void assignLayer_globalGroupMaintainer_privateLayer_returns409() throws Exception {
    // Create a GLOBAL group with a MAINTAINER user
    var groupId = insertLayerGroup(jdbcTemplate, "GlobalGroupForAssign", "GLOBAL");
    insertLayerGroupMember(jdbcTemplate, groupId, USER_ID, LayerGroupRole.MAINTAINER.name());
    var privateLayerId = insertLayer(jdbcTemplate, "PrivateLayerForAssign", "PRIVATE");

    var assignRequest = new LayerGroupLayersAssignRequest(Set.of(privateLayerId), LayerRole.MEMBER);
    var body = objectMapper.writeValueAsString(assignRequest);

    mockMvc
        .perform(
            post("/v1/layer-groups/{id}/layers", groupId)
                .with(csrf())
                .with(authentication(userAuthentication(USER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict());

    // Verify no row was inserted in layer_group_layer
    var count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.layer_group_layer WHERE layer_group_id = ? AND layer_id = ?",
            Integer.class,
            groupId,
            privateLayerId);
    assertEquals(0, count, "No layer_group_layer row should be added on 409");
  }

  @Test
  void assignLayers_oneLayerMissing_rollsBackEntireBatch() throws Exception {
    // A batch with one valid layer and one non-existent layer must persist NEITHER — the
    // missing layer fails the batch before any row is written.
    var groupId = insertLayerGroup(jdbcTemplate, "BatchRollbackGroup", "PRIVATE");
    insertLayerGroupMember(jdbcTemplate, groupId, ADMIN_ID, LayerGroupRole.OWNER.name());
    var validLayerId = insertLayer(jdbcTemplate, "ValidBatchLayer", "PRIVATE");
    var missingLayerId = UUID.fromString("550e8400-e29b-41d4-a716-aa00000000ff");

    var assignRequest =
        new LayerGroupLayersAssignRequest(Set.of(validLayerId, missingLayerId), LayerRole.MEMBER);
    var body = objectMapper.writeValueAsString(assignRequest);

    mockMvc
        .perform(
            post("/v1/layer-groups/{id}/layers", groupId)
                .with(csrf())
                .with(authentication(adminAuthentication(ADMIN_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());

    var count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.layer_group_layer WHERE layer_group_id = ?",
            Integer.class,
            groupId);
    assertEquals(0, count, "No rows should persist when any layer in the batch fails");
  }

  @Test
  void assignLayers_adminAssignsMultipleLayers_returns201AndPersistsRows() throws Exception {
    var groupId = insertLayerGroup(jdbcTemplate, "BatchSuccessGroup", "PRIVATE");
    insertLayerGroupMember(jdbcTemplate, groupId, ADMIN_ID, LayerGroupRole.OWNER.name());
    var firstLayerId = insertLayer(jdbcTemplate, "BatchSuccessLayerOne", "PRIVATE");
    var secondLayerId = insertLayer(jdbcTemplate, "BatchSuccessLayerTwo", "PRIVATE");

    var assignRequest =
        new LayerGroupLayersAssignRequest(Set.of(firstLayerId, secondLayerId), LayerRole.EDITOR);
    var body = objectMapper.writeValueAsString(assignRequest);

    mockMvc
        .perform(
            post("/v1/layer-groups/{id}/layers", groupId)
                .with(csrf())
                .with(authentication(adminAuthentication(ADMIN_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[?(@.role == 'EDITOR')]").isArray());

    var count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM iam.layer_group_layer
            WHERE layer_group_id = ?
              AND layer_id IN (?, ?)
              AND role = CAST(? AS iam.layer_role)
            """,
            Integer.class,
            groupId,
            firstLayerId,
            secondLayerId,
            LayerRole.EDITOR.name());
    assertEquals(2, count, "Both requested layers should be assigned with the requested role");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static UsernamePasswordAuthenticationToken adminAuthentication(UUID userId) {
    var principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(userId, true);
    return new UsernamePasswordAuthenticationToken(
        principal, null, List.of(new SimpleGrantedAuthority(Roles.ADMIN)));
  }

  private static UsernamePasswordAuthenticationToken userAuthentication(UUID userId) {
    var principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(userId, false);
    return new UsernamePasswordAuthenticationToken(principal, null, List.of());
  }

  private static void insertUser(JdbcTemplate jdbc, UUID userId) {
    jdbc.update(
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

  private static UUID insertLayerGroup(JdbcTemplate jdbc, String name, String accessScope) {
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO iam.layer_group (id, name, access_scope)"
            + " VALUES (?, ?, CAST(? AS iam.layer_group_access_scope))",
        id,
        name,
        accessScope);
    return id;
  }

  private static void insertLayerGroupMember(
      JdbcTemplate jdbc, UUID groupId, UUID userId, String role) {
    jdbc.update(
        "INSERT INTO iam.layer_group_member (layer_group_id, user_id, role)"
            + " VALUES (?, ?, CAST(? AS iam.layer_group_role))",
        groupId,
        userId,
        role);
  }

  private static UUID insertLayer(JdbcTemplate jdbc, String name, String accessScope) {
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO iam.layer (id, name, access_scope)"
            + " VALUES (?, ?, CAST(? AS iam.layer_access_scope))",
        id,
        name,
        accessScope);
    return id;
  }

  private static void insertLayerGroupLayer(JdbcTemplate jdbc, UUID groupId, UUID layerId) {
    jdbc.update(
        "INSERT INTO iam.layer_group_layer (layer_group_id, layer_id) VALUES (?, ?)",
        groupId,
        layerId);
  }
}
