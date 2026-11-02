// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer;

import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.util.List;

public interface TransformEngine {

  Object apply(Object input, List<TransformStep> steps) throws MapperEngineException;

  default boolean acceptsNullInput(TransformStep step) {
    return false;
  }
}
