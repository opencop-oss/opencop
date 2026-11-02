// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.profile;

import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.config.FormatConfig;
import com.opencop.core.sdk.model.mapper.config.XmlFormatConfig;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.rules.MappingRuleBuilder;
import com.opencop.core.sdk.model.mapper.rules.TargetField;
import com.opencop.core.sdk.model.mapper.selector.RecordSelector;
import com.opencop.core.sdk.model.mapper.source.XPathSource;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DynamicMapperBuilder {

  private final FormatConfig formatConfig;

  private RecordSelector recordSelector;
  private final List<MappingRule> rules = new ArrayList<>();

  private DynamicMapperBuilder(FormatConfig formatConfig) {
    this.formatConfig = formatConfig;
  }

  public static DynamicMapperBuilder xml() {
    return new DynamicMapperBuilder(new XmlFormatConfig(Map.of(), true));
  }

  public DynamicMapperBuilder recordXPath(String xpath) {
    this.recordSelector = new RecordSelector(new XPathSource(xpath), OnMissing.ERROR);
    return this;
  }

  public DynamicMapperBuilder addRule(MappingRule... rule) {
    rules.addAll(Arrays.asList(rule));
    return this;
  }

  public DynamicMapperBuilder addRule(MappingRule rule) {
    rules.add(rule);
    return this;
  }

  private DynamicMapperBuilder addRule(String xpathExpr, TargetField target, OnMissing onMissing) {
    MappingRule rule =
        MappingRuleBuilder.rule()
            .id(
                UUID.nameUUIDFromBytes(
                    (xpathExpr + "->" + target.path()).getBytes(StandardCharsets.UTF_8)))
            .enabled(true)
            .target(target)
            .xpath(xpathExpr)
            .onMissing(onMissing)
            .build();
    rules.add(rule);
    return this;
  }

  public DynamicMapper build() {
    if (recordSelector == null) {
      throw new IllegalStateException("recordSelector must be defined");
    }
    if (rules.isEmpty()) {
      throw new IllegalStateException("At least one mapping rule must be defined");
    }

    return new DynamicMapper(
        UUID.randomUUID(), true, formatConfig, recordSelector, List.copyOf(rules));
  }
}
