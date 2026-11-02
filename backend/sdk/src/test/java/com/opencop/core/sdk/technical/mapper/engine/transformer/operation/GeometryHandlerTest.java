// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import org.geojson.GeoJsonObject;
import org.geojson.LineString;
import org.geojson.MultiPoint;
import org.geojson.Polygon;
import org.junit.jupiter.api.Test;

class GeometryHandlerTest {

  private final GeometryHandler handler = new GeometryHandler();

  @Test
  void operation_is_geometry() {
    assertEquals(TransformOperation.GEOMETRY, handler.operation());
  }

  @Test
  void returns_null_when_input_is_null() throws Exception {
    assertNull(handler.apply(null, null));
  }

  @Test
  void returns_null_when_input_is_blank() throws Exception {
    assertNull(handler.apply("   ", null));
  }

  @Test
  void converts_linestring_wkt_to_geojson_linestring() throws Exception {
    Object out = handler.apply("LINESTRING(10 10,20 20,30 10)", null);

    assertInstanceOf(LineString.class, out);
    LineString line = (LineString) out;
    assertEquals(3, line.getCoordinates().size());
    assertEquals(10.0, line.getCoordinates().get(0).getLongitude(), 1e-6);
    assertEquals(10.0, line.getCoordinates().get(0).getLatitude(), 1e-6);
  }

  @Test
  void converts_polygon_wkt_to_geojson_polygon() throws Exception {
    Object out = handler.apply("POLYGON((10 10,20 10,20 20,10 20,10 10))", null);

    assertInstanceOf(Polygon.class, out);
    Polygon polygon = (Polygon) out;
    assertEquals(1, polygon.getCoordinates().size());
    assertEquals(5, polygon.getCoordinates().get(0).size());
    assertEquals(10.0, polygon.getCoordinates().get(0).get(0).getLongitude(), 1e-6);
    assertEquals(10.0, polygon.getCoordinates().get(0).get(0).getLatitude(), 1e-6);
  }

  @Test
  void supports_other_geojson_geometry_types_through_wkt_reader() throws Exception {
    Object out = handler.apply("MULTIPOINT((10 40),(40 30),(20 20),(30 10))", null);
    assertInstanceOf(GeoJsonObject.class, out);
    assertInstanceOf(MultiPoint.class, out);
  }

  @Test
  void throws_mapper_engine_exception_on_invalid_geometry() {
    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply("NOT_A_REAL_GEOMETRY", null));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Invalid geometry text"));
  }
}
