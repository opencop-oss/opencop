// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import com.opencop.core.sdk.technical.mapper.engine.transformer.operation.TransformHandler;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public final class TransformRegistryEngine implements TransformEngine {

  private final Map<TransformOperation, TransformHandler> handlers;

  public TransformRegistryEngine(List<TransformHandler> handlers) {
    this.handlers =
        handlers.stream()
            .collect(Collectors.toUnmodifiableMap(TransformHandler::operation, handler -> handler));
  }

  @Override
  public Object apply(Object input, List<TransformStep> steps) throws MapperEngineException {
    Object current = input;
    for (TransformStep step : steps) {
      var handler = handlers.get(step.op());
      if (handler == null) {
        throw new MapperEngineException(
            "TRANSFORM_ENGINE_ERROR", "No handler for " + step.op(), null);
      }
      if (current == null && !handler.acceptsNullInput()) {
        throw new MapperEngineException(
            "TRANSFORM_ENGINE_ERROR", "Encountered a null value " + step.op(), null);
      }
      current = handler.apply(current, step);
    }
    return current;
  }

  @Override
  public boolean acceptsNullInput(TransformStep step) {
    var handler = handlers.get(step.op());
    return handler != null && handler.acceptsNullInput();
  }
}
