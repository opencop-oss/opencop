// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencop.core.sdk.model.mapper.config.FormatConfig;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.selector.RecordSelector;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DynamicMapper(
    UUID id,
    boolean enabled,
    FormatConfig formatConfig,
    RecordSelector recordSelector,
    List<MappingRule> rules) {
  public DynamicMapper {
    rules = rules == null ? List.of() : List.copyOf(rules);
  }
}
