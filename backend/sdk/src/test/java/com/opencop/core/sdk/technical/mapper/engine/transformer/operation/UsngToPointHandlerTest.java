// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import mil.nga.grid.features.Point;
import mil.nga.mgrs.MGRS;
import org.junit.jupiter.api.Test;

class UsngToPointHandlerTest {

  private final UsngToPointHandler handler = new UsngToPointHandler();

  @Test
  void operation_is_usng_to_point() {
    assertEquals(TransformOperation.USNG_TO_POINT, handler.operation());
  }

  @Test
  void returns_null_when_input_is_null_or_blank() throws Exception {
    assertNull(handler.apply(null, null));
    assertNull(handler.apply("   ", null));
  }

  @Test
  void converts_mgrs_compatible_usng_text_to_point() throws Exception {
    String usngText = MGRS.from(Point.degrees(-75.1652, 39.9526)).coordinate();

    Object out = handler.apply(usngText, TransformStepBuilder.usngToPoint());

    assertInstanceOf(org.geojson.Point.class, out);
    org.geojson.Point p = (org.geojson.Point) out;
    assertEquals(-75.1652, p.getCoordinates().getLongitude(), 1e-4);
    assertEquals(39.9526, p.getCoordinates().getLatitude(), 1e-4);
  }

  @Test
  void ignores_internal_whitespace_in_usng_text() throws Exception {
    String compact = MGRS.from(Point.degrees(-75.1652, 39.9526)).coordinate();
    String spaced =
        compact.substring(0, 3) + " " + compact.substring(3, 5) + "  " + compact.substring(5);

    org.geojson.Point p =
        (org.geojson.Point) handler.apply(spaced, TransformStepBuilder.usngToPoint());

    assertEquals(-75.1652, p.getCoordinates().getLongitude(), 1e-4);
    assertEquals(39.9526, p.getCoordinates().getLatitude(), 1e-4);
  }

  @Test
  void throws_mapper_engine_exception_on_invalid_usng() {
    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply("NOT_A_REAL_USNG", null));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Invalid usng text"));
  }
}
