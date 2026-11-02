// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import org.junit.jupiter.api.Test;

class DmToPointHandlerTest {

  private final DmToPointHandler handler = new DmToPointHandler();

  @Test
  void operation_is_dm_to_point() {
    assertEquals(TransformOperation.DM_TO_POINT, handler.operation());
  }

  @Test
  void returns_null_when_input_is_null_or_blank() throws Exception {
    assertNull(handler.apply(null, null));
    assertNull(handler.apply("   ", null));
  }

  @Test
  void converts_valid_dm_to_point() throws Exception {
    Object out = handler.apply("39°57.156'N 75°09.912'W", TransformStepBuilder.dmToPoint());

    assertInstanceOf(org.geojson.Point.class, out);
    org.geojson.Point p = (org.geojson.Point) out;
    assertEquals(-75.1652, p.getCoordinates().getLongitude(), 1e-6);
    assertEquals(39.9526, p.getCoordinates().getLatitude(), 1e-6);
  }

  @Test
  void accepts_lowercase_hemispheres_and_reversed_coordinate_order() throws Exception {
    Object out = handler.apply("75d09.912w 39d57.156n", TransformStepBuilder.dmToPoint());

    assertInstanceOf(org.geojson.Point.class, out);
    org.geojson.Point p = (org.geojson.Point) out;
    assertEquals(-75.1652, p.getCoordinates().getLongitude(), 1e-6);
    assertEquals(39.9526, p.getCoordinates().getLatitude(), 1e-6);
  }

  @Test
  void throws_mapper_engine_exception_on_invalid_dm() {
    MapperEngineException ex =
        assertThrows(MapperEngineException.class, () -> handler.apply("39.5,-75.5", null));

    assertEquals("TRANSFORM_ERROR", ex.getCode());
    assertTrue(ex.getMessage().contains("Invalid dm text"));
  }

  @Test
  void rejects_out_of_range_dm_components() {
    MapperEngineException invalidMinutes =
        assertThrows(
            MapperEngineException.class,
            () -> handler.apply("39°60.0'N 75°09.912'W", TransformStepBuilder.dmToPoint()));
    assertEquals("TRANSFORM_ERROR", invalidMinutes.getCode());

    MapperEngineException invalidLatitude =
        assertThrows(
            MapperEngineException.class,
            () -> handler.apply("90°00.1'N 75°09.912'W", TransformStepBuilder.dmToPoint()));
    assertEquals("TRANSFORM_ERROR", invalidLatitude.getCode());
  }

  @Test
  void rejects_missing_or_duplicate_coordinate_components() {
    assertEquals(
        "TRANSFORM_ERROR",
        assertThrows(
                MapperEngineException.class,
                () -> handler.apply("39°57.156'N", TransformStepBuilder.dmToPoint()))
            .getCode());

    assertEquals(
        "TRANSFORM_ERROR",
        assertThrows(
                MapperEngineException.class,
                () ->
                    handler.apply(
                        "39°57.156'N 40°00.0'N 75°09.912'W", TransformStepBuilder.dmToPoint()))
            .getCode());
  }

  @Test
  void rejects_dm_strings_with_prefix_or_suffix_garbage() {
    assertEquals(
        "TRANSFORM_ERROR",
        assertThrows(
                MapperEngineException.class,
                () ->
                    handler.apply(
                        "garbage 39°57.156'N 75°09.912'W tail", TransformStepBuilder.dmToPoint()))
            .getCode());
  }
}
