// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import org.springframework.stereotype.Component;

@Component
public class FpsToMpsHandler extends AbstractDoubleConversionHandler {

  private static final double FEET_TO_METERS = 0.3048d;

  @Override
  public TransformOperation operation() {
    return TransformOperation.FPS_TO_MPS;
  }

  @Override
  protected double convert(double value) {
    return value * FEET_TO_METERS;
  }
}
