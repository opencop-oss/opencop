// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import org.geojson.GeoJsonObject;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class GeometryHandler implements TransformHandler {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public TransformOperation operation() {
    return TransformOperation.GEOMETRY;
  }

  @Override
  public Object apply(Object in, TransformStep step) throws MapperEngineException {
    if (in == null) {
      return null;
    }

    if (in instanceof GeoJsonObject geoJsonObject) {
      return geoJsonObject;
    }

    String geometryText = String.valueOf(in).trim();
    if (geometryText.isBlank()) {
      return null;
    }

    try {
      var geometry = new WKTReader().read(geometryText);
      String geometryJson = new GeoJsonWriter().write(geometry);
      return MAPPER.readValue(geometryJson, GeoJsonObject.class);
    } catch (ParseException e) {
      throw new MapperEngineException(
          "TRANSFORM_ERROR", "Invalid geometry text " + geometryText, e);
    } catch (Exception e) {
      throw new MapperEngineException(
          "TRANSFORM_ERROR", "Failed to convert geometry to GeoJSON " + geometryText, e);
    }
  }
}
