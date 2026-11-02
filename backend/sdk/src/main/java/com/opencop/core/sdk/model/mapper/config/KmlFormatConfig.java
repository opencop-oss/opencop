// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KmlFormatConfig(Map<String, String> namespaces, boolean strict)
    implements FormatConfig {

  public KmlFormatConfig {
    namespaces = namespaces == null ? Map.of() : Map.copyOf(namespaces);
  }

  @Override
  public MapperFormat format() {
    return MapperFormat.KML_KMZ;
  }
}
