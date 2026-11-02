// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.plugin.annotation;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class OnPluginCondition implements Condition {
  @Override
  public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata md) {
    var required =
        (String) md.getAnnotationAttributes(ConditionalOnPlugin.class.getName()).get("value");
    var active = ctx.getEnvironment().getProperty("opencop.plugin.id");
    return required != null && required.equalsIgnoreCase(active);
  }
}
