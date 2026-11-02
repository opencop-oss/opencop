// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.iam.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.component.general.iam.LayerGroupScopeWebTestApplication;
import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@ComponentWebIT(classes = LayerGroupScopeWebTestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EffectiveLayerAccessScopeIT extends AbstractWebIT {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-bb0000000001");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EffectiveLayerAccessService effectiveLayerAccessService;

  @BeforeEach
  void seedUser() {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        USER_ID,
        "idp-" + USER_ID,
        "user-" + USER_ID,
        "https://issuer.test",
        "user-" + USER_ID,
        "user@example.test",
        "Test",
        "User",
        "Test User");
  }

  @Test
  void getEffectiveRole_layerInGlobalGroupNoExplicitGrant_returnsMember() {
    var layerGroupId = insertLayerGroup("GlobalGroupIT", "GLOBAL");
    var layerId = insertLayer("LayerInGlobalGroup", "PRIVATE");
    insertLayerGroupLayer(layerGroupId, layerId, "EDITOR");

    var result = effectiveLayerAccessService.getEffectiveRole(layerId, USER_ID);

    assertEquals(Optional.of(LayerRole.EDITOR), result);
  }

  @Test
  void getEffectiveRole_layerInPrivateGroupNoExplicitGrant_returnsEmpty() {
    var layerGroupId = insertLayerGroup("PrivateGroupIT", "PRIVATE");
    var layerId = insertLayer("LayerInPrivateGroup", "PRIVATE");
    insertLayerGroupLayer(layerGroupId, layerId, "EDITOR");

    var result = effectiveLayerAccessService.getEffectiveRole(layerId, USER_ID);

    assertTrue(result.isEmpty());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

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

  private UUID insertLayer(String name, String accessScope) {
    var id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO iam.layer (id, name, access_scope)"
            + " VALUES (?, ?, CAST(? AS iam.layer_access_scope))",
        id,
        name,
        accessScope);
    return id;
  }

  private void insertLayerGroupLayer(UUID layerGroupId, UUID layerId, String role) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer_group_layer (layer_group_id, layer_id, role)"
            + " VALUES (?, ?, CAST(? AS iam.layer_role))",
        layerGroupId,
        layerId,
        role);
  }
}
