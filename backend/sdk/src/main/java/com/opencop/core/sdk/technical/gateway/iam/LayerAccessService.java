// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.iam;

import com.opencop.core.sdk.ports.gateway.iam.AccessRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LayerAccessService implements AccessRepository<LayerRole> {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public LayerAccessService(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public boolean userHasAccess(UUID userId, UUID layerId) {
    return userRole(userId, layerId) != null;
  }

  @Override
  public LayerRole userRole(UUID userId, UUID layerId) {
    var params =
        new MapSqlParameterSource().addValue("layerId", layerId).addValue("userId", userId);

    String role =
        jdbcTemplate.queryForObject(
            """
            WITH
            """
                + LayerReachabilitySql.accessContextCtes(LayerReachabilitySql.LayerScope.ANY)
                + """
            SELECT MIN(role)::text
            FROM reachable_layer_roles
            WHERE layer_id = :layerId
            """,
            params,
            String.class);

    return (role == null) ? null : LayerRole.valueOf(role);
  }

  /**
   * Bulk form of {@link #userRole}: one statement resolving the user's effective role for every
   * layer in {@code layerIds}. Layers the user cannot reach are absent from the result. MIN(role)
   * picks the strongest role because iam.layer_role declares roles strongest-first, the same
   * property {@link #userRole} relies on.
   */
  public Map<UUID, LayerRole> userRoles(UUID userId, Set<UUID> layerIds) {
    if (layerIds.isEmpty()) {
      return Map.of();
    }
    var params =
        new MapSqlParameterSource().addValue("layerIds", layerIds).addValue("userId", userId);

    Map<UUID, LayerRole> roles = new HashMap<>();
    // JPA won't work here: the roles come from the hand-assembled reachable_layer_roles CTE
    // prologue (LayerReachabilitySql), which has no entity mapping to derive or declare a query
    // against, and the bulk read must stay on the exact CTE text userRole uses so the two reads
    // cannot drift.
    jdbcTemplate.query(
        """
        WITH
        """
            + LayerReachabilitySql.accessContextCtes(LayerReachabilitySql.LayerScope.ANY)
            + """
        SELECT layer_id, MIN(role)::text AS role
        FROM reachable_layer_roles
        WHERE layer_id IN (:layerIds)
        GROUP BY layer_id
        """,
        params,
        rs -> {
          roles.put(rs.getObject("layer_id", UUID.class), LayerRole.valueOf(rs.getString("role")));
        });
    return Map.copyOf(roles);
  }
}
