// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper;

import com.opencop.core.sdk.model.mapper.config.MapperFormat;
import java.util.List;
import java.util.UUID;

public record MappingResponse(
    UUID profileId,
    MapperFormat format,
    List<MappedRecord> records,
    List<MappingError> errors,
    List<MappingWarning> warnings) {
  public MappingResponse {
    records = records == null ? List.of() : List.copyOf(records);
    errors = errors == null ? List.of() : List.copyOf(errors);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }
}
