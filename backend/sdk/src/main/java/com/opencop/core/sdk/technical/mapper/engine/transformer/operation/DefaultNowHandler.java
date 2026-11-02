// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class DefaultNowHandler implements TransformHandler {

  @Override
  public TransformOperation operation() {
    return TransformOperation.DEFAULT_NOW;
  }

  @Override
  public boolean acceptsNullInput() {
    return true;
  }

  @Override
  public Object apply(Object in, TransformStep step) {
    if (in == null) {
      return Instant.now();
    }
    if (in instanceof String value && value.isBlank()) {
      return Instant.now();
    }
    return in;
  }
}
