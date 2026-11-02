// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import org.junit.jupiter.api.Test;

class AngleConversionHandlersTest {

  @Test
  void rad_to_degrees_converts_radians_to_degrees() throws MapperEngineException {
    RadToDegreesHandler handler = new RadToDegreesHandler();

    assertEquals(TransformOperation.RAD_TO_DEGREES, handler.operation());
    assertEquals(180d, (Double) handler.apply(Math.PI, TransformStepBuilder.radToDegrees()), 1e-9);
    assertEquals(90d, (Double) handler.apply(String.valueOf(Math.PI / 2d), step(handler)), 1e-9);
  }

  @Test
  void grad_to_degrees_converts_grads_to_degrees() throws MapperEngineException {
    GradToDegreesHandler handler = new GradToDegreesHandler();

    assertEquals(TransformOperation.GRAD_TO_DEGREES, handler.operation());
    assertEquals(180d, (Double) handler.apply(200d, TransformStepBuilder.gradToDegrees()), 1e-9);
    assertEquals(90d, (Double) handler.apply("100", step(handler)), 1e-9);
  }

  @Test
  void mils_to_degrees_converts_nato_mils_to_degrees() throws MapperEngineException {
    MilsToDegreesHandler handler = new MilsToDegreesHandler();

    assertEquals(TransformOperation.MILS_TO_DEGREES, handler.operation());
    assertEquals(180d, (Double) handler.apply(3200d, TransformStepBuilder.milsToDegrees()), 1e-9);
    assertEquals(90d, (Double) handler.apply("1600", step(handler)), 1e-9);
  }

  @Test
  void conversion_handlers_return_null_when_input_is_null() throws MapperEngineException {
    assertNull(new RadToDegreesHandler().apply(null, TransformStepBuilder.radToDegrees()));
    assertNull(new GradToDegreesHandler().apply(null, TransformStepBuilder.gradToDegrees()));
    assertNull(new MilsToDegreesHandler().apply(null, TransformStepBuilder.milsToDegrees()));
  }

  @Test
  void conversion_handlers_reject_blank_or_invalid_input() {
    MapperEngineException blank =
        assertThrows(
            MapperEngineException.class,
            () -> new RadToDegreesHandler().apply("   ", TransformStepBuilder.radToDegrees()));
    assertEquals("TRANSFORM_ERROR", blank.getCode());

    MapperEngineException invalid =
        assertThrows(
            MapperEngineException.class,
            () -> new MilsToDegreesHandler().apply("abc", TransformStepBuilder.milsToDegrees()));
    assertEquals("TRANSFORM_ERROR", invalid.getCode());
  }

  @Test
  void conversion_handlers_support_zero_negative_and_scientific_notation() throws Exception {
    assertEquals(
        0d, (Double) new RadToDegreesHandler().apply(0d, TransformStepBuilder.radToDegrees()));
    assertEquals(
        -180d,
        (Double) new RadToDegreesHandler().apply(-Math.PI, TransformStepBuilder.radToDegrees()),
        1e-9);
    assertEquals(
        90d,
        (Double) new GradToDegreesHandler().apply("1e2", TransformStepBuilder.gradToDegrees()),
        1e-9);
    assertEquals(
        -90d,
        (Double) new MilsToDegreesHandler().apply("-1600", TransformStepBuilder.milsToDegrees()),
        1e-9);
  }

  @Test
  void conversion_handlers_reject_non_finite_values() {
    assertEquals(
        "TRANSFORM_ERROR",
        assertThrows(
                MapperEngineException.class,
                () ->
                    new RadToDegreesHandler()
                        .apply(Double.NaN, TransformStepBuilder.radToDegrees()))
            .getCode());
    assertEquals(
        "TRANSFORM_ERROR",
        assertThrows(
                MapperEngineException.class,
                () ->
                    new GradToDegreesHandler()
                        .apply("Infinity", TransformStepBuilder.gradToDegrees()))
            .getCode());
    assertEquals(
        "TRANSFORM_ERROR",
        assertThrows(
                MapperEngineException.class,
                () ->
                    new MilsToDegreesHandler()
                        .apply(Double.NEGATIVE_INFINITY, TransformStepBuilder.milsToDegrees()))
            .getCode());
  }

  private static TransformStep step(TransformHandler handler) {
    return TransformStepBuilder.op(handler.operation()).build();
  }
}
