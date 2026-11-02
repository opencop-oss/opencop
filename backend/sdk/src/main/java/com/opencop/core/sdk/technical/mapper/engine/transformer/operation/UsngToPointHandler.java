// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.text.ParseException;
import mil.nga.mgrs.MGRS;
import org.geojson.Point;
import org.springframework.stereotype.Component;

@Component
public class UsngToPointHandler implements TransformHandler {

  @Override
  public TransformOperation operation() {
    return TransformOperation.USNG_TO_POINT;
  }

  @Override
  public Object apply(Object in, TransformStep step) throws MapperEngineException {
    if (in == null) {
      return null;
    }

    String usngText = String.valueOf(in).replaceAll("\\s+", "").trim();
    if (usngText.isBlank()) {
      return null;
    }

    try {
      var usng = MGRS.parse(usngText);
      var point = usng.toPoint();
      return new Point(point.getLongitude(), point.getLatitude());
    } catch (ParseException e) {
      throw new MapperEngineException("TRANSFORM_ERROR", "Invalid usng text " + usngText, e);
    }
  }
}
