// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper;

import java.util.List;
import org.geojson.Feature;

public record MappedRecord(int index, List<Feature> output, List<MappingWarning> warnings) {
  public MappedRecord {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
