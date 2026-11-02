// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import java.text.ParseException;
import mil.nga.mgrs.utm.UTM;
import org.geojson.Point;
import org.springframework.stereotype.Component;

@Component
public class UtmToPointHandler implements TransformHandler {

  @Override
  public TransformOperation operation() {
    return TransformOperation.UTM_TO_POINT;
  }

  @Override
  public Object apply(Object in, TransformStep step) throws MapperEngineException {
    if (in == null) {
      return null;
    }

    String utmText = String.valueOf(in).trim();
    if (utmText.isBlank()) {
      return null;
    }

    try {
      var utm = UTM.parse(utmText);
      var point = utm.toPoint();
      return new Point(point.getLongitude(), point.getLatitude());
    } catch (ParseException e) {
      throw new MapperEngineException("TRANSFORM_ERROR", "Invalid utm text " + utmText, e);
    }
  }
}
