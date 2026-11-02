// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = XmlFormatConfig.class, name = "xml"),
  @JsonSubTypes.Type(value = JsonFormatConfig.class, name = "json"),
  @JsonSubTypes.Type(value = CsvFormatConfig.class, name = "csv"),
  @JsonSubTypes.Type(value = KmlFormatConfig.class, name = "kml+kmz")
})
public sealed interface FormatConfig
    permits XmlFormatConfig, JsonFormatConfig, CsvFormatConfig, KmlFormatConfig {

  MapperFormat format();
}
