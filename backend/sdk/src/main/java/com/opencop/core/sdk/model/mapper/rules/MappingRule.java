// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.rules;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.source.SourceRef;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MappingRule(
    UUID id,
    boolean enabled,
    MappedTargetField target,
    String contextRef,
    SourceRef source,
    List<TransformStep> transforms,
    OnMissing onMissing,
    String defaultValue,
    String attributeKey) {

  public MappingRule(
      UUID id,
      boolean enabled,
      MappedTargetField target,
      String contextRef,
      SourceRef source,
      List<TransformStep> transforms,
      OnMissing onMissing,
      String defaultValue) {
    this(id, enabled, target, contextRef, source, transforms, onMissing, defaultValue, null);
  }

  public MappingRule {
    id = id == null ? UUID.randomUUID() : id;
    transforms = transforms == null ? List.of() : List.copyOf(transforms);
    onMissing = onMissing == null ? OnMissing.NULL : onMissing;
    attributeKey = normalizeAttributeKey(attributeKey);
  }

  public ValueType outputType() {
    if (isAttributeEntry()) {
      return ValueType.STRING;
    }
    return target.field().valueType();
  }

  @JsonIgnore
  public boolean isAttributeEntry() {
    return target.field() == TargetField.ATTRIBUTES && attributeKey != null;
  }

  private static String normalizeAttributeKey(String attributeKey) {
    if (attributeKey == null) {
      return null;
    }
    String trimmed = attributeKey.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
