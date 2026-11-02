// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.api.component.support.CoreComponentTestDatabaseConfig;
import com.opencop.core.api.general.ports.gateway.layer.MergeLayerValidationGateway;
import com.opencop.core.api.general.technical.gateway.db.layer.PostgresMergeLayerValidationGateway;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@ComponentWebIT(classes = PostgresMergeLayerValidationGatewayIT.TestApplication.class)
@TestPropertySource(
    properties = "spring.flyway.locations=filesystem:../sdk/src/main/resources/db/migration")
class PostgresMergeLayerValidationGatewayIT extends AbstractDatabaseIT {

  private static final String DELETE_HELPER = "DELETE FROM geo.feature_latest_active_helper";
  private static final String DELETE_FEATURES = "DELETE FROM geo.features";
  private static final UUID FIRST_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655446101");
  private static final UUID SECOND_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655446102");
  private static final UUID EMPTY_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655446103");
  private static final String SHARED_ALPHA_OBJECT_ID = "shared-alpha";
  private static final String SHARED_BRAVO_OBJECT_ID = "shared-bravo";
  private static final String FIRST_ONLY_OBJECT_ID = "first-only";
  private static final String FEATURE_HISTORY_ONLY_OBJECT_ID = "feature-history-only";
  private static final String FEATURE_PAYLOAD =
      """
      {"type":"Feature","geometry":{"type":"Point","coordinates":[1,2]},"properties":{}}
      """;
  private static final OffsetDateTime EXPIRED_AT = OffsetDateTime.parse("2026-06-08T12:00:00Z");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private NamedParameterJdbcTemplate namedJdbc;

  private MergeLayerValidationGateway gateway;

  @BeforeEach
  void setUp() {
    gateway = new PostgresMergeLayerValidationGateway(namedJdbc);
    jdbc.execute(DELETE_HELPER);
    jdbc.execute(DELETE_FEATURES);
  }

  @Test
  void countsActiveHelperRowsAndDuplicateObjectIdsForOrderedLayerPairs() {
    insertHelper(FIRST_LAYER_ID, SHARED_ALPHA_OBJECT_ID);
    insertHelper(FIRST_LAYER_ID, SHARED_BRAVO_OBJECT_ID);
    insertHelper(FIRST_LAYER_ID, FIRST_ONLY_OBJECT_ID);
    insertHelper(SECOND_LAYER_ID, SHARED_ALPHA_OBJECT_ID);
    insertHelper(SECOND_LAYER_ID, SHARED_BRAVO_OBJECT_ID);
    insertFeatureHistoryOnly(FIRST_LAYER_ID, FEATURE_HISTORY_ONLY_OBJECT_ID);

    var layerIds = List.of(FIRST_LAYER_ID, SECOND_LAYER_ID, EMPTY_LAYER_ID);

    var activeCounts = gateway.countActiveFeatures(layerIds);
    var collisionCounts = gateway.countPairwiseCollisions(layerIds);

    assertEquals(3L, activeCounts.get(FIRST_LAYER_ID));
    assertEquals(2L, activeCounts.get(SECOND_LAYER_ID));
    assertEquals(0L, activeCounts.get(EMPTY_LAYER_ID));
    assertEquals(
        List.of(
            new MergeLayerValidationGateway.PairCollisionCount(FIRST_LAYER_ID, SECOND_LAYER_ID, 2),
            new MergeLayerValidationGateway.PairCollisionCount(FIRST_LAYER_ID, EMPTY_LAYER_ID, 0),
            new MergeLayerValidationGateway.PairCollisionCount(SECOND_LAYER_ID, EMPTY_LAYER_ID, 0)),
        collisionCounts);
  }

  @Test
  void ignoresExpiredHelperRowsWhenCountingActiveRowsAndCollisions() {
    insertHelper(FIRST_LAYER_ID, SHARED_ALPHA_OBJECT_ID);
    insertHelper(FIRST_LAYER_ID, SHARED_BRAVO_OBJECT_ID, EXPIRED_AT);
    insertHelper(SECOND_LAYER_ID, SHARED_ALPHA_OBJECT_ID);
    insertHelper(SECOND_LAYER_ID, SHARED_BRAVO_OBJECT_ID);

    var layerIds = List.of(FIRST_LAYER_ID, SECOND_LAYER_ID);

    var activeCounts = gateway.countActiveFeatures(layerIds);
    var collisionCounts = gateway.countPairwiseCollisions(layerIds);

    assertEquals(1L, activeCounts.get(FIRST_LAYER_ID));
    assertEquals(2L, activeCounts.get(SECOND_LAYER_ID));
    assertEquals(
        List.of(
            new MergeLayerValidationGateway.PairCollisionCount(FIRST_LAYER_ID, SECOND_LAYER_ID, 1)),
        collisionCounts);
  }

  private void insertHelper(UUID sourceId, String objectId) {
    insertHelper(sourceId, objectId, null);
  }

  private void insertHelper(UUID sourceId, String objectId, OffsetDateTime staleAt) {
    var eventId = UUID.randomUUID();
    var playbackAt = OffsetDateTime.parse("2026-06-08T11:00:00Z");
    jdbc.update(
        """
        INSERT INTO geo.feature_latest_active_helper (
          source, object_id, event_id, playback_at, created_at, first_emission_at, stale_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        sourceId,
        objectId,
        eventId,
        playbackAt,
        playbackAt,
        playbackAt,
        staleAt);
  }

  private void insertFeatureHistoryOnly(UUID sourceId, String objectId) {
    jdbc.update(
        """
        INSERT INTO geo.features (
          id, feature_kind, source, object_id, created_at, start_at, geom, payload
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
        """,
        UUID.randomUUID(),
        "event",
        sourceId,
        objectId,
        OffsetDateTime.parse("2026-06-08T12:00:00Z"),
        OffsetDateTime.parse("2026-06-08T12:00:00Z"),
        "POINT(1 2)",
        FEATURE_PAYLOAD);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import(CoreComponentTestDatabaseConfig.class)
  static class TestApplication {}
}
