// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import org.springframework.stereotype.Component;

@Component
public class KphToMpsHandler extends AbstractDoubleConversionHandler {

  @Override
  public TransformOperation operation() {
    return TransformOperation.KPH_TO_MPS;
  }

  @Override
  protected double convert(double value) {
    return value / 3.6d;
  }
}
