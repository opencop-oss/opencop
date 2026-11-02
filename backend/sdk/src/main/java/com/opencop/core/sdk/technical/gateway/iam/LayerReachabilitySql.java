// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.iam;

final class LayerReachabilitySql {

  enum LayerScope {
    ANY("TRUE"),
    ACTIVE("l.lifecycle_state = 'ACTIVE'::iam.layer_lifecycle_state");

    private final String predicate;

    LayerScope(String predicate) {
      this.predicate = predicate;
    }
  }

  private LayerReachabilitySql() {}

  // Emits user_groups and reachable_layer_roles CTEs for queries that bind :userId.
  static String accessContextCtes(LayerScope layerScope) {
    return """
        user_groups AS (
            SELECT group_id
            FROM iam.user_group_member
            WHERE user_id = :userId
        ),
        reachable_layer_roles AS (
            SELECT l.id AS layer_id, lm.role AS role
            FROM iam.layer l
            JOIN iam.layer_member lm
              ON lm.layer_id = l.id
             AND lm.user_id = :userId
            WHERE ${layerPredicate}

            UNION ALL

            SELECT l.id AS layer_id, 'MEMBER'::iam.layer_role AS role
            FROM iam.layer l
            WHERE ${layerPredicate}
              AND l.access_scope = 'GLOBAL'::iam.layer_access_scope

            UNION ALL

            SELECT l.id AS layer_id, glm.role AS role
            FROM iam.layer l
            JOIN iam.group_layer_member glm
              ON glm.layer_id = l.id
            JOIN user_groups ug
              ON ug.group_id = glm.group_id
            WHERE ${layerPredicate}

            UNION ALL

            SELECT l.id AS layer_id, lgl.role AS role
            FROM iam.layer l
            JOIN iam.layer_group_layer lgl
              ON lgl.layer_id = l.id
            JOIN iam.layer_group_member lgm
              ON lgm.layer_group_id = lgl.layer_group_id
             AND lgm.user_id = :userId
            WHERE ${layerPredicate}

            UNION ALL

            SELECT l.id AS layer_id, lgl.role AS role
            FROM iam.layer l
            JOIN iam.layer_group_layer lgl
              ON lgl.layer_id = l.id
            JOIN iam.layer_group_user_group lgug
              ON lgug.layer_group_id = lgl.layer_group_id
            JOIN user_groups ug
              ON ug.group_id = lgug.user_group_id
            WHERE ${layerPredicate}

            UNION ALL

            SELECT l.id AS layer_id, lgl.role AS role
            FROM iam.layer l
            JOIN iam.layer_group_layer lgl
              ON lgl.layer_id = l.id
            JOIN iam.layer_group lg
              ON lg.id = lgl.layer_group_id
            WHERE ${layerPredicate}
              AND lg.access_scope = 'GLOBAL'::iam.layer_group_access_scope
        )
        """
        .replace("${layerPredicate}", layerScope.predicate);
  }
}
