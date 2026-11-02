// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.testfixture.mapping;

import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;

public final class SelectedPluginMapper implements PayloadIngressMapper<Feature> {

  private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000401");

  @Override
  public UUID id() {
    return ID;
  }

  @Override
  public String name() {
    return "SELECTED-PLUGIN";
  }

  @Override
  public String description() {
    return "Selected plugin mapper fixture";
  }

  @Override
  public int version() {
    return 7;
  }

  @Override
  public List<Feature> map(UUID id, MappingProperties properties, byte[] input) {
    var feature = new Feature();
    feature.setProperty("mappedBy", name());
    feature.setProperty("payload", new String(input, StandardCharsets.UTF_8));
    return List.of(feature);
  }
}
