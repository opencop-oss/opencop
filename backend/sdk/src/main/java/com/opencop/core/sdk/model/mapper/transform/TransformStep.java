// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.transform;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransformStep(
    TransformOperation op,
    String pattern,
    String replacement,
    String format,
    String value, // defaultIfBlank, ZoneId, etc.
    Map<String, String> mapValues,
    String mapDefault) {
  public TransformStep {
    mapValues = mapValues == null ? null : Map.copyOf(mapValues);
  }
}
