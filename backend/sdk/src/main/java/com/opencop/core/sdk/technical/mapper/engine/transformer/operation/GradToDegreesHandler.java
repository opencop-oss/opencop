// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import org.springframework.stereotype.Component;

@Component
public class GradToDegreesHandler extends AbstractDoubleConversionHandler {

  private static final double GRADS_PER_DEGREE = 10.0 / 9.0;

  @Override
  public TransformOperation operation() {
    return TransformOperation.GRAD_TO_DEGREES;
  }

  @Override
  protected double convert(double value) {
    return value / GRADS_PER_DEGREE;
  }
}
