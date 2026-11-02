// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import org.springframework.stereotype.Component;

@Component
public class ToDoubleHandler implements TransformHandler {

  @Override
  public TransformOperation operation() {
    return TransformOperation.TO_DOUBLE;
  }

  @Override
  public Object apply(Object in, TransformStep step) throws MapperEngineException {
    if (in == null) {
      return null;
    }

    if (in instanceof Double d) {
      return d;
    }
    if (in instanceof Number n) {
      return n.doubleValue();
    }

    String s = String.valueOf(in).trim();
    if (s.isEmpty()) {
      throw new MapperEngineException(
          "TRANSFORM_ERROR", "Cannot convert blank value to double", null);
    }

    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException e) {
      throw new MapperEngineException("TRANSFORM_ERROR", "Cannot convert value to double: " + s, e);
    }
  }
}
