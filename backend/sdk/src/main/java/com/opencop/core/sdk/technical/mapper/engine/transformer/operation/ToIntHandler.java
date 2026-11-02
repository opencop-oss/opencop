// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import org.springframework.stereotype.Component;

@Component
public class ToIntHandler implements TransformHandler {

  @Override
  public TransformOperation operation() {
    return TransformOperation.TO_INT;
  }

  @Override
  public Object apply(Object in, TransformStep step) throws MapperEngineException {
    if (in == null) {
      return null;
    }

    if (in instanceof Integer i) {
      return i;
    }

    if (in instanceof Number n) {
      return n.intValue();
    }

    String s = String.valueOf(in).trim();
    if (s.isEmpty()) {
      throw new MapperEngineException("TRANSFORM_ERROR", "Cannot convert blank value to int", null);
    }

    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      throw new MapperEngineException("TRANSFORM_ERROR", "Cannot convert value to int: " + s, e);
    }
  }
}
