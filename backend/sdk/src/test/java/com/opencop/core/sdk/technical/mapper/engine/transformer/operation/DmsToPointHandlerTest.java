// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import org.junit.jupiter.api.Test;

class DmsToPointHandlerTest {

  private final DmsToPointHandler handler = new DmsToPointHandler();

  @Test
  void operation_is_dms_to_point() {
    assertEquals(TransformOperation.DMS_TO_POINT, handler.operation());
  }

  @Test
  void returns_null_when_input_is_null_or_blank() throws Exception {
    assertNull(handler.apply(null, null));
    assertNull(handler.apply("   ", null));
  }

  @Test
  void converts_valid_dms_to_point() throws Exception {
    Object out = handler.apply("39°57'09.36\"N 75°09'54.72\"W", TransformStepBuilder.dmsToPoint());

    assertInstanceOf(org.geojson.Point.class, out);
    org.geojson.Point p = (org.geojson.Point) out;
    assertEquals(-75.1652, p.getCoordinates().getLongitude(), 1e-6);
    assertEquals(39.9526, p.getCoordinates().getLatitude(), 1e-6);
  }

  @Test
  void accepts_lowercase_hemispheres_and_reversed_coordinate_order() throws Exception {
    Object out = handler.apply("75d09m54.72sw 39d57m09.36sn", TransformStepBuilder.dmsToPoint());

    assertInstanceOf(org.geojson.Point.class, out);
    org.geojson.Point p = (org.geojson.Point) out;
    assertEquals(-75.1652, p.getCoordinates().getLongitude(), 1e-6);
    assertEquals(39.9526, p.getCoordinates().getLatitude(), 1e-6);
  }

  @Test
  void throws_mapper_engine_exception_on_invalid_dms() {
    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply("39.5,-75.5", null));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Invalid dms text"));
  }

  @Test
  void rejects_out_of_range_dms_components() {
    MapperEngineException invalidMinutes =
        assertThrows(
            MapperEngineException.class,
            () -> handler.apply("39°60'00\"N 75°09'54.72\"W", TransformStepBuilder.dmsToPoint()));
    assertEquals("TRANSFORM_ERROR", invalidMinutes.getCode());

    MapperEngineException invalidLatitude =
        assertThrows(
            MapperEngineException.class,
            () -> handler.apply("90°00'01\"N 75°09'54.72\"W", TransformStepBuilder.dmsToPoint()));
    assertEquals("TRANSFORM_ERROR", invalidLatitude.getCode());
  }

  @Test
  void rejects_missing_or_duplicate_coordinate_components() {
    assertEquals(
        "TRANSFORM_ERROR",
        assertThrows(
                MapperEngineException.class,
                () -> handler.apply("39°57'09.36\"N", TransformStepBuilder.dmsToPoint()))
            .getCode());

    assertEquals(
        "TRANSFORM_ERROR",
        assertThrows(
                MapperEngineException.class,
                () ->
                    handler.apply(
                        "39°57'09.36\"N 40°00'00\"N 75°09'54.72\"W",
                        TransformStepBuilder.dmsToPoint()))
            .getCode());
  }

  @Test
  void rejects_dms_strings_with_prefix_or_suffix_garbage() {
    assertEquals(
        "TRANSFORM_ERROR",
        assertThrows(
                MapperEngineException.class,
                () ->
                    handler.apply(
                        "garbage 39°57'09.36\"N 75°09'54.72\"W tail",
                        TransformStepBuilder.dmsToPoint()))
            .getCode());
  }
}
