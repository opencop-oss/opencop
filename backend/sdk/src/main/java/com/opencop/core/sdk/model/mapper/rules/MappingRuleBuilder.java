// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.rules;

import static java.util.Objects.requireNonNull;

import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.source.SourceRef;
import com.opencop.core.sdk.model.mapper.source.XPathSource;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class MappingRuleBuilder {

  private UUID id;
  private boolean enabled = true;
  private MappedTargetField target;
  private String contextRef;
  private SourceRef source;
  private final List<TransformStep> transforms = new ArrayList<>();
  private OnMissing onMissing = OnMissing.ERROR;
  private String defaultValue = null;
  private String attributeKey = null;

  private MappingRuleBuilder() {}

  public static MappingRuleBuilder rule() {
    return new MappingRuleBuilder();
  }

  public MappingRuleBuilder id(UUID id) {
    this.id = id;
    return this;
  }

  public MappingRuleBuilder enabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public MappingRuleBuilder target(TargetField targetField) {
    this.target = new MappedTargetField(targetField);
    return this;
  }

  public MappingRuleBuilder attributeKey(String attributeKey) {
    this.attributeKey = attributeKey;
    return this;
  }

  public MappingRuleBuilder contextRef(String contextRef) {
    this.contextRef = contextRef;
    return this;
  }

  public MappingRuleBuilder xpath(String expr) {
    this.source = new XPathSource(expr);
    return this;
  }

  public MappingRuleBuilder transforms(TransformStep... steps) {
    if (steps != null) this.transforms.addAll(Arrays.asList(steps));
    return this;
  }

  public MappingRuleBuilder addTransform(TransformStep step) {
    if (step != null) this.transforms.add(step);
    return this;
  }

  public MappingRuleBuilder onMissing(OnMissing onMissing) {
    this.onMissing = onMissing;
    return this;
  }

  public MappingRuleBuilder defaultValue(String defaultValue) {
    this.onMissing = OnMissing.DEFAULT;
    this.defaultValue = defaultValue;
    return this;
  }

  public MappingRule build() {
    requireNonNull(target, "target is required");
    requireNonNull(source, "source is required");

    if (id == null) {
      // stable id based on "source->target"
      String key = sourceKey(source) + "->" + target.path() + attributeKeyPart();
      id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    return new MappingRule(
        id,
        enabled,
        target,
        contextRef,
        source,
        List.copyOf(transforms),
        onMissing,
        defaultValue,
        attributeKey);
  }

  private static String sourceKey(SourceRef source) {
    if (source instanceof XPathSource x) return "xpath:" + x.expr();
    return source.toString();
  }

  private String attributeKeyPart() {
    if (attributeKey == null || attributeKey.isBlank()) {
      return "";
    }
    return "#" + attributeKey.trim();
  }
}
