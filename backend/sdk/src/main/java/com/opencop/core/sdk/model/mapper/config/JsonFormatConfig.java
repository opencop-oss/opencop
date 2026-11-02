// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.config;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonFormatConfig(boolean allowComments) implements FormatConfig {

  @Override
  public MapperFormat format() {
    return MapperFormat.JSON;
  }
}
