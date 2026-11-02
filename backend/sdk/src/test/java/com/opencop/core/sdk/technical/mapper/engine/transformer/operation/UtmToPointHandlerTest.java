// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import mil.nga.grid.features.Point;
import mil.nga.mgrs.utm.UTM;
import org.junit.jupiter.api.Test;

class UtmToPointHandlerTest {

  private final UtmToPointHandler handler = new UtmToPointHandler();

  @Test
  void operation_is_utm_to_point() {
    assertEquals(TransformOperation.UTM_TO_POINT, handler.operation());
  }

  @Test
  void returns_null_when_input_is_null_or_blank() throws Exception {
    assertNull(handler.apply(null, null));
    assertNull(handler.apply("   ", null));
  }

  @Test
  void converts_valid_utm_to_point() throws Exception {
    String utmText = UTM.from(Point.degrees(-75.1652, 39.9526)).format();

    Object out = handler.apply(utmText, TransformStepBuilder.utmToPoint());

    assertInstanceOf(org.geojson.Point.class, out);
    org.geojson.Point p = (org.geojson.Point) out;
    assertEquals(-75.1652, p.getCoordinates().getLongitude(), 1e-4);
    assertEquals(39.9526, p.getCoordinates().getLatitude(), 1e-4);
  }

  @Test
  void trims_surrounding_whitespace_from_utm_text() throws Exception {
    String utmText = "  " + UTM.from(Point.degrees(-75.1652, 39.9526)).format() + "  ";

    org.geojson.Point p =
        (org.geojson.Point) handler.apply(utmText, TransformStepBuilder.utmToPoint());

    assertEquals(-75.1652, p.getCoordinates().getLongitude(), 1e-4);
    assertEquals(39.9526, p.getCoordinates().getLatitude(), 1e-4);
  }

  @Test
  void throws_mapper_engine_exception_on_invalid_utm() {
    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply("NOT_A_REAL_UTM", null));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Invalid utm text"));
  }
}
