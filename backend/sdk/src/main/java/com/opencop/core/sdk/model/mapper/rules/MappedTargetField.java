// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MappedTargetField(TargetField field) {

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public MappedTargetField(String value) {
    this(TargetField.valueOf(value));
  }

  @JsonValue
  public String toJson() {
    return field.name();
  }

  public String path() {
    return field.path();
  }

  public ValueType type() {
    return field.valueType();
  }
}
