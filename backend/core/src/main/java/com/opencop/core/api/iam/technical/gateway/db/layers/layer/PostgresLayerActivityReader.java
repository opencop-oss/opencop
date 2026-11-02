// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.layer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Reads layer activity out of {@code activity.resource_activity} for the layer reads that serve it.
 *
 * <p>A batched lookup rather than a mapped column or a Hibernate {@code @Formula}: {@code
 * LayerEntity} is loaded on authorization and watchbox paths that have no use for activity, and a
 * {@code @Formula} is eager on every load, so it would put a correlated subquery on all of them.
 * One extra statement per layer read keeps the cost where the value is actually served.
 *
 * <p>A layer that has never been active has no row in the projection and is simply absent from the
 * returned map, which is what makes "never active" reach the client as null.
 */
public class PostgresLayerActivityReader implements LayerActivityReader {

  private static final String SQL =
      """
      SELECT resource_id, last_event_at
        FROM activity.resource_activity
       WHERE resource_kind = 'LAYER'
         AND resource_id IN (:ids)
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public PostgresLayerActivityReader(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Maps layer id to last activity, omitting layers that have none.
   *
   * <p>Empty input issues no query: an empty {@code IN} list is a syntax error in Postgres, not an
   * empty result.
   */
  public Map<UUID, Instant> findAll(Collection<UUID> layerIds) {
    if (layerIds == null || layerIds.isEmpty()) {
      return Map.of();
    }
    var activity = new HashMap<UUID, Instant>();
    jdbc.query(
        SQL,
        new MapSqlParameterSource().addValue("ids", layerIds),
        rs -> {
          // Block body, not an expression: Map.put returns a value, which would make the lambda
          // ambiguous between RowCallbackHandler and ResultSetExtractor.
          activity.put(
              rs.getObject("resource_id", UUID.class),
              rs.getObject("last_event_at", OffsetDateTime.class).toInstant());
        });
    return activity;
  }
}
