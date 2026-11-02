// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.serializer.kafka;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;

class KafkaGeoJsonDeserializerTest {

  private final KafkaGeoJsonDeserializer deserializer = new KafkaGeoJsonDeserializer();

  @Test
  void leavesASymbolCodeAlone() {
    var feature =
        deserialize(
            """
            {"type":"Feature","geometry":{"type":"Point","coordinates":[1.0,2.0]},
             "properties":{"opencop":{"element":{"type":"symbol","symbol":"130310000011010000000000000000"}}}}
            """);

    assertEquals(
        Map.of("type", "symbol", "symbol", "130310000011010000000000000000"), elementOf(feature));
  }

  @Test
  void returnsNullForANullRecord() {
    assertNull(deserializer.deserialize("opencop.geo.validated", null));
  }

  private Feature deserialize(String json) {
    return (Feature)
        deserializer.deserialize("opencop.geo.validated", json.getBytes(StandardCharsets.UTF_8));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> opencopOf(Feature feature) {
    return (Map<String, Object>) feature.getProperty("opencop");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> elementOf(Feature feature) {
    return (Map<String, Object>) opencopOf(feature).get("element");
  }
}
