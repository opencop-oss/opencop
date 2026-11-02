// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.mapping.ingress;

import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import tools.jackson.databind.ObjectMapper;

public class GeoJsonIngressMapperV1 implements PayloadIngressMapper<Feature> {

  public static final UUID MAPPER_ID = UUID.fromString("4017d49f-2a68-4900-8e50-2dbb3bd435df");

  private final ObjectMapper mapper = new ObjectMapper();

  public GeoJsonIngressMapperV1() {}

  @Override
  public UUID id() {
    return MAPPER_ID;
  }

  @Override
  public String name() {
    return "GEOJSON-FEATURE";
  }

  @Override
  public String description() {
    return "GeoJson Feature mapper. To be used for OpenCOP ready GeoJSON";
  }

  @Override
  public int version() {
    return 1;
  }

  @Override
  public List<Feature> map(UUID objectId, MappingProperties properties, byte[] buffer)
      throws IllegalArgumentException {
    var feature = mapper.readValue(new String(buffer, StandardCharsets.UTF_8), Feature.class);
    if (feature != null) {
      return List.of(feature);
    }
    throw new IllegalArgumentException("Not a GeoJson");
  }
}
