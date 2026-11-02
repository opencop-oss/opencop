// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MapValuesHandler implements TransformHandler {

  public TransformOperation operation() {
    return TransformOperation.MAP_VALUES;
  }

  public Object apply(Object in, TransformStep step) {
    String value = in == null ? null : String.valueOf(in);
    if (value == null) {
      return null;
    }
    Map<String, String> map = step.mapValues();
    if (map == null) {
      return value;
    }
    String mapped = map.get(value);
    return (mapped != null) ? mapped : step.mapDefault();
  }
}
