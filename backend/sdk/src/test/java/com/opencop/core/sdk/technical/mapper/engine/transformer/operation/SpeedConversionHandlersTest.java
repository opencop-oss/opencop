// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.mapper.engine.transformer.operation;

import static org.junit.jupiter.api.Assertions.*;

import com.opencop.core.sdk.model.mapper.transform.TransformOperation;
import com.opencop.core.sdk.model.mapper.transform.TransformStep;
import com.opencop.core.sdk.model.mapper.transform.TransformStepBuilder;
import com.opencop.core.sdk.technical.mapper.engine.MapperEngineException;
import org.junit.jupiter.api.Test;

class SpeedConversionHandlersTest {

  @Test
  void kph_to_mps_converts_kph_to_meters_per_second() throws MapperEngineException {
    KphToMpsHandler handler = new KphToMpsHandler();

    assertEquals(TransformOperation.KPH_TO_MPS, handler.operation());
    assertEquals(10d, (Double) handler.apply(36d, TransformStepBuilder.kphToMps()), 1e-9);
    assertEquals(20d, (Double) handler.apply("72", step(handler)), 1e-9);
  }

  @Test
  void mph_to_mps_converts_mph_to_meters_per_second() throws MapperEngineException {
    MphToMpsHandler handler = new MphToMpsHandler();

    assertEquals(TransformOperation.MPH_TO_MPS, handler.operation());
    assertEquals(26.8224d, (Double) handler.apply(60d, TransformStepBuilder.mphToMps()), 1e-9);
    assertEquals(13.4112d, (Double) handler.apply("30", step(handler)), 1e-9);
  }

  @Test
  void knots_to_mps_converts_knots_to_meters_per_second() throws MapperEngineException {
    KnotsToMpsHandler handler = new KnotsToMpsHandler();

    assertEquals(TransformOperation.KNOTS_TO_MPS, handler.operation());
    assertEquals(10.28888d, (Double) handler.apply(20d, TransformStepBuilder.knotsToMps()), 1e-9);
    assertEquals(5.14444d, (Double) handler.apply("10", step(handler)), 1e-9);
  }

  @Test
  void fps_to_mps_converts_feet_per_second_to_meters_per_second() throws MapperEngineException {
    FpsToMpsHandler handler = new FpsToMpsHandler();

    assertEquals(TransformOperation.FPS_TO_MPS, handler.operation());
    assertEquals(3.048d, (Double) handler.apply(10d, TransformStepBuilder.fpsToMps()), 1e-9);
    assertEquals(30.48d, (Double) handler.apply("100", step(handler)), 1e-9);
  }

  @Test
  void speed_conversion_handlers_return_null_when_input_is_null() throws MapperEngineException {
    assertNull(new KphToMpsHandler().apply(null, TransformStepBuilder.kphToMps()));
    assertNull(new MphToMpsHandler().apply(null, TransformStepBuilder.mphToMps()));
    assertNull(new KnotsToMpsHandler().apply(null, TransformStepBuilder.knotsToMps()));
    assertNull(new FpsToMpsHandler().apply(null, TransformStepBuilder.fpsToMps()));
  }

  @Test
  void speed_conversion_handlers_reject_blank_or_invalid_input() {
    MapperEngineException blank =
        assertThrows(
            MapperEngineException.class,
            () -> new KphToMpsHandler().apply(" ", TransformStepBuilder.kphToMps()));
    assertEquals("TRANSFORM_ERROR", blank.getCode());

    MapperEngineException invalid =
        assertThrows(
            MapperEngineException.class,
            () -> new MphToMpsHandler().apply("fast", TransformStepBuilder.mphToMps()));
    assertEquals("TRANSFORM_ERROR", invalid.getCode());
  }

  @Test
  void speed_conversion_handlers_support_zero_negative_and_scientific_notation() throws Exception {
    assertEquals(0d, (Double) new KphToMpsHandler().apply(0d, TransformStepBuilder.kphToMps()));
    assertEquals(
        -4.4704d,
        (Double) new MphToMpsHandler().apply(-10d, TransformStepBuilder.mphToMps()),
        1e-9);
    assertEquals(
        51.4444d,
        (Double) new KnotsToMpsHandler().apply("1e2", TransformStepBuilder.knotsToMps()),
        1e-9);
    assertEquals(
        -3.048d,
        (Double) new FpsToMpsHandler().apply("-10", TransformStepBuilder.fpsToMps()),
        1e-9);
  }

  @Test
  void speed_conversion_handlers_reject_non_finite_values() {
    assertEquals(
        "TRANSFORM_ERROR",
        assertThrows(
                MapperEngineException.class,
                () -> new KphToMpsHandler().apply(Double.NaN, TransformStepBuilder.kphToMps()))
            .getCode());
    assertEquals(
        "TRANSFORM_ERROR",
        assertThrows(
                MapperEngineException.class,
                () -> new MphToMpsHandler().apply("Infinity", TransformStepBuilder.mphToMps()))
            .getCode());
    assertEquals(
        "TRANSFORM_ERROR",
        assertThrows(
                MapperEngineException.class,
                () ->
                    new FpsToMpsHandler()
                        .apply(Double.NEGATIVE_INFINITY, TransformStepBuilder.fpsToMps()))
            .getCode());
  }

  private static TransformStep step(TransformHandler handler) {
    return TransformStepBuilder.op(handler.operation()).build();
  }
}
