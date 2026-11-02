// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.config;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CsvFormatConfig(char delimiter, char quote, boolean hasHeader)
    implements FormatConfig {

  @Override
  public MapperFormat format() {
    return MapperFormat.CSV;
  }

  public static CsvFormatConfig defaults() {
    return new CsvFormatConfig(',', '"', true);
  }
}
