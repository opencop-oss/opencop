// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.layer;

import com.opencop.core.api.general.ports.gateway.layer.MergeLayerValidationGateway;
import com.opencop.core.api.general.ports.gateway.layer.MergeLayerValidationGateway.PairCollisionCount;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresMergeLayerValidationGateway implements MergeLayerValidationGateway {

  private static final String LAYER_IDS_PARAMETER = "layerIds";
  private static final String ACTIVE_COUNTS_SQL =
      """
      SELECT helper.source AS layer_id,
             COUNT(*) AS active_count
      FROM geo.feature_latest_active_helper helper
      WHERE helper.source IN (:layerIds)
        AND (helper.stale_at IS NULL OR helper.stale_at > CURRENT_TIMESTAMP)
      GROUP BY helper.source
      """;
  private static final String PAIR_COLLISIONS_SQL =
      """
      SELECT first_helper.source AS first_layer_id,
             second_helper.source AS second_layer_id,
             COUNT(*) AS collision_count
      FROM geo.feature_latest_active_helper first_helper
      JOIN geo.feature_latest_active_helper second_helper
        ON second_helper.object_id = first_helper.object_id
       AND second_helper.source <> first_helper.source
      WHERE first_helper.source IN (:layerIds)
        AND second_helper.source IN (:layerIds)
        AND (first_helper.stale_at IS NULL OR first_helper.stale_at > CURRENT_TIMESTAMP)
        AND (second_helper.stale_at IS NULL OR second_helper.stale_at > CURRENT_TIMESTAMP)
      GROUP BY first_helper.source, second_helper.source
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public PostgresMergeLayerValidationGateway(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Long> countActiveFeatures(List<UUID> layerIds) {
    var counts = zeroCounts(layerIds);
    if (layerIds.isEmpty()) {
      return counts;
    }
    jdbc.query(
        ACTIVE_COUNTS_SQL,
        parameters(layerIds),
        (RowCallbackHandler)
            row -> counts.put(row.getObject("layer_id", UUID.class), row.getLong("active_count")));
    return counts;
  }

  @Override
  @Transactional(readOnly = true)
  public List<PairCollisionCount> countPairwiseCollisions(List<UUID> orderedLayerIds) {
    if (orderedLayerIds.size() < 2) {
      return List.of();
    }
    var collisions = readCollisionCounts(orderedLayerIds);
    return orderedPairs(orderedLayerIds).stream()
        .map(
            pair ->
                new PairCollisionCount(
                    pair.firstLayerId(), pair.secondLayerId(), pair.count(collisions)))
        .toList();
  }

  private Map<PairKey, Long> readCollisionCounts(List<UUID> orderedLayerIds) {
    var collisions = new HashMap<PairKey, Long>();
    jdbc.query(
        PAIR_COLLISIONS_SQL,
        parameters(orderedLayerIds),
        (RowCallbackHandler)
            row ->
                collisions.put(
                    new PairKey(
                        row.getObject("first_layer_id", UUID.class),
                        row.getObject("second_layer_id", UUID.class)),
                    row.getLong("collision_count")));
    return collisions;
  }

  private static MapSqlParameterSource parameters(List<UUID> layerIds) {
    return new MapSqlParameterSource(LAYER_IDS_PARAMETER, layerIds);
  }

  private static Map<UUID, Long> zeroCounts(List<UUID> layerIds) {
    var counts = new LinkedHashMap<UUID, Long>();
    layerIds.forEach(layerId -> counts.put(layerId, 0L));
    return counts;
  }

  private static List<PairKey> orderedPairs(List<UUID> orderedLayerIds) {
    return IntStream.range(0, orderedLayerIds.size())
        .boxed()
        .flatMap(
            firstIndex ->
                IntStream.range(firstIndex + 1, orderedLayerIds.size())
                    .mapToObj(
                        secondIndex ->
                            new PairKey(
                                orderedLayerIds.get(firstIndex), orderedLayerIds.get(secondIndex))))
        .toList();
  }

  private record PairKey(UUID firstLayerId, UUID secondLayerId) {
    long count(Map<PairKey, Long> counts) {
      return counts.getOrDefault(this, 0L);
    }
  }
}
