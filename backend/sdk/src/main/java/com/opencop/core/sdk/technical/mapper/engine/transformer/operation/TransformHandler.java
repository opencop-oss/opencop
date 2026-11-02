// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import org.springframework.stereotype.Component;

@Component
public interface TransformHandler {

  TransformOperation operation();

  default boolean acceptsNullInput() {
    return false;
  }

  Object apply(Object in, TransformStep step) throws MapperEngineException;
}
