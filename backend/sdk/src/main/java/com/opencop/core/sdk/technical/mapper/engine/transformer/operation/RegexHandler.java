// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RegexHandler implements TransformHandler {

  public TransformOperation operation() {
    return TransformOperation.REPLACE_REGEX;
  }

  public Object apply(Object in, TransformStep step) {
    String value = in == null ? null : String.valueOf(in);
    if (value == null) {
      return null;
    }
    String pattern = step.pattern();
    if (pattern == null) {
      return value;
    }
    String replacement = step.replacement() == null ? "" : step.replacement();
    return Pattern.compile(pattern).matcher(value).replaceAll(replacement);
  }
}
