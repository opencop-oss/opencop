// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import org.springframework.stereotype.Component;

@Component
public class MilsToDegreesHandler extends AbstractDoubleConversionHandler {

  private static final double NATO_MILS_PER_DEGREE = 6400d / 360d;

  @Override
  public TransformOperation operation() {
    return TransformOperation.MILS_TO_DEGREES;
  }

  @Override
  protected double convert(double value) {
    return value / NATO_MILS_PER_DEGREE;
  }
}
