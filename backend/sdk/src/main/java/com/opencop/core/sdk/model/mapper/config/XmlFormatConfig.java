// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.config;

import java.util.Map;

public record XmlFormatConfig(Map<String, String> namespaces, boolean strict)
    implements FormatConfig {

  public XmlFormatConfig {
    namespaces = namespaces == null ? Map.of() : Map.copyOf(namespaces);
  }

  @Override
  public MapperFormat format() {
    return MapperFormat.XML;
  }
}
