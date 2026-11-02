// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import org.geojson.Point;
import org.junit.jupiter.api.Test;

class MgrsToPointHandlerTest {

  private final MgrsToPointHandler handler = new MgrsToPointHandler();

  @Test
  void operation_is_mgrs_to_point() {
    assertEquals(TransformOperation.MGRS_TO_POINT, handler.operation());
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
  void converts_valid_mgrs_to_point() throws Exception {
    Object out = handler.apply("33X VG 74596 43594", null);

    assertInstanceOf(Point.class, out);
    Point p = (Point) out;

    assertEquals(13.9173539, p.getCoordinates().getLongitude(), 1e-6);
    assertEquals(77.8654997, p.getCoordinates().getLatitude(), 1e-6);
  }

  @Test
  void trims_surrounding_whitespace_from_mgrs_text() throws Exception {
    Point p = (Point) handler.apply("  33X VG 74596 43594  ", null);

    assertEquals(13.9173539, p.getCoordinates().getLongitude(), 1e-6);
    assertEquals(77.8654997, p.getCoordinates().getLatitude(), 1e-6);
  }

  @Test
  void throws_mapper_engine_exception_on_invalid_mgrs() {
    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply("NOT_A_REAL_MGRS", null));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Invalid mgrs text"));
  }
}
