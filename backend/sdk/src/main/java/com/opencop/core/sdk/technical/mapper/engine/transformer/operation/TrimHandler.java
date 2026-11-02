// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import org.springframework.stereotype.Component;

@Component
public class TrimHandler implements TransformHandler {

  public TransformOperation operation() {
    return TransformOperation.TRIM;
  }

  public Object apply(Object in, TransformStep step) {
    return in == null ? null : in.toString().trim();
  }
}
