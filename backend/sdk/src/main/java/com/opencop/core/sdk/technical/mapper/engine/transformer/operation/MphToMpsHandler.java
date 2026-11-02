// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import org.springframework.stereotype.Component;

@Component
public class MphToMpsHandler extends AbstractDoubleConversionHandler {

  private static final double MPH_TO_MPS_FACTOR = 0.44704d;

  @Override
  public TransformOperation operation() {
    return TransformOperation.MPH_TO_MPS;
  }

  @Override
  protected double convert(double value) {
    return value * MPH_TO_MPS_FACTOR;
  }
}
