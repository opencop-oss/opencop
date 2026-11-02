// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class WatchboxConfigAuditSnapshotTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void from_whenSerialized_hasExactVersionedAllowlistAndStableLayerOrder() {
    var firstLayer = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var secondLayer = UUID.fromString("00000000-0000-0000-0000-000000000002");
    var config =
        new WatchboxConfig(
            UUID.fromString("00000000-0000-0000-0000-000000000010"),
            "audit snapshot",
            "allowlisted fields only",
            true,
            WatchboxDirection.BOTH,
            square(),
            WatchboxFilter.empty(),
            Set.of(secondLayer, firstLayer),
            3,
            Instant.parse("2026-08-19T12:00:00Z"),
            Instant.parse("2026-08-19T12:05:00Z"));

    var snapshot = WatchboxConfigAuditSnapshot.from(config);
    String json = objectMapper.writeValueAsString(snapshot);
    Map<String, Object> serialized =
        objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

    assertThat(serialized.keySet())
        .containsExactlyInAnyOrder(
            "schemaVersion",
            "id",
            "name",
            "description",
            "enabled",
            "direction",
            "polygon",
            "filter",
            "layerIds",
            "configEpoch",
            "createdAt",
            "updatedAt");
    assertThat(snapshot.layerIds()).containsExactly(firstLayer, secondLayer);
    assertThat(serialized.get("layerIds"))
        .isEqualTo(List.of(firstLayer.toString(), secondLayer.toString()));
    assertThat(objectMapper.readValue(json, WatchboxConfigAuditSnapshot.class)).isEqualTo(snapshot);
  }

  private static Polygon square() {
    return new Polygon(
        List.of(
            new LngLatAlt(-100.2, 39.8),
            new LngLatAlt(-99.8, 39.8),
            new LngLatAlt(-99.8, 40.2),
            new LngLatAlt(-100.2, 40.2),
            new LngLatAlt(-100.2, 39.8)));
  }
}
