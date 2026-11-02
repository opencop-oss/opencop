// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;

abstract class AbstractDoubleConversionHandler implements TransformHandler {

  @Override
  public Object apply(Object in, TransformStep step) throws MapperEngineException {
    if (in == null) {
      return null;
    }

    double value;
    if (in instanceof Number number) {
      value = number.doubleValue();
    } else {
      String s = String.valueOf(in).trim();
      if (s.isEmpty()) {
        throw new MapperEngineException(
            "TRANSFORM_ERROR", "Cannot convert blank value with " + operation(), null);
      }
      try {
        value = Double.parseDouble(s);
      } catch (NumberFormatException e) {
        throw new MapperEngineException(
            "TRANSFORM_ERROR", "Cannot convert value with " + operation() + ": " + s, e);
      }
    }

    if (!Double.isFinite(value)) {
      throw new MapperEngineException(
          "TRANSFORM_ERROR", "Cannot convert non-finite value with " + operation(), null);
    }

    return convert(value);
  }

  protected abstract double convert(double value);
}
