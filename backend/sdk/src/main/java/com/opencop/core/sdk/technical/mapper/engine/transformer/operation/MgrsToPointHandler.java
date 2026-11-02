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
public class MgrsToPointHandler implements TransformHandler {

  public TransformOperation operation() {
    return TransformOperation.MGRS_TO_POINT;
  }

  public Object apply(Object in, TransformStep step) throws MapperEngineException {
    if (in == null) {
      return null;
    }

    String mgrsText = String.valueOf(in).trim();
    if (mgrsText.isBlank()) {
      return null;
    }

    try {
      var mgrs = MGRS.parse(mgrsText);
      var point = mgrs.toPoint(); // NGA point (lon/lat in degrees)
      return new Point(point.getLongitude(), point.getLatitude());
    } catch (ParseException e) {
      throw new MapperEngineException("TRANSFORM_ERROR", "Invalid mgrs text " + mgrsText, e);
    }
  }
}
