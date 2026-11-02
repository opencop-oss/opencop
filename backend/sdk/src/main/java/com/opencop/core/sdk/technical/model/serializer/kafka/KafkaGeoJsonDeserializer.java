// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.serializer.kafka;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.serialization.Deserializer;
import org.geojson.GeoJsonObject;
import tools.jackson.databind.ObjectMapper;

public class KafkaGeoJsonDeserializer implements Deserializer<GeoJsonObject> {

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public GeoJsonObject deserialize(String topic, byte[] data) {
    if (data == null) {
      return null;
    }
    return mapper.readValue(new String(data, StandardCharsets.UTF_8), GeoJsonObject.class);
  }
}
